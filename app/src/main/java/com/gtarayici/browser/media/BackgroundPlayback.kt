package com.gtarayici.browser.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gtarayici.browser.MainActivity
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.diagnostics.Diagnostics
import com.gtarayici.browser.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.lib.state.ext.flow

/**
 * Ekran kapalıyken oynatma listesinin (YouTube Music, YouTube…) sıradaki şarkıya geçebilmesi için uygulamayı
 * "medya oynatan" durumda tutar.
 *
 * Mozilla'nın medya hizmeti şarkı bitip duraklayınca ön plandan hemen çıkar. Ekran kapalıyken Android bu boşlukta
 * uygulamayı uyutur ve internetini keser (ekran açılınca "Tekrar çevrimiçi" görünür); Android 15'te ayrıca sıradaki
 * şarkı başlarken hizmet arka plandan yeniden ön plana alınamaz ve ses odağı alınamaz. Bu hizmet oynatma boyunca ve
 * oynatma durduktan sonra [GRACE_MS] süre ön planda kalır; bu sürede işlemciyi ve Wi-Fi'yi uyanık tutar.
 */
class BackgroundPlayback(
    private val context: Context,
    private val store: BrowserStore,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            combine(store.flow(), settings.backgroundPlay) { state, enabled ->
                enabled && (state.tabs + state.customTabs).any { it.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING }
            }.distinctUntilChanged().collect { playing ->
                val intent = Intent(context, BackgroundPlaybackService::class.java)
                    .setAction(if (playing) BackgroundPlaybackService.ACTION_PLAYING else BackgroundPlaybackService.ACTION_STOPPED)
                if (playing) {
                    // Oynatma başladığında (çoğunlukla uygulama açıkken) başlatılır; ekran kapanınca zaten ön plandadır.
                    runCatching { ContextCompat.startForegroundService(context, intent) }
                        .onFailure { Diagnostics.log("arka plan oynatma hizmeti başlatılamadı: ${it.javaClass.simpleName}", "medya") }
                } else if (BackgroundPlaybackService.running) {
                    runCatching { context.startService(intent) }
                }
            }
        }
    }
}

class BackgroundPlaybackService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val stopRunnable = Runnable {
        Diagnostics.log("oynatma ${GRACE_MS / 60_000} dk duruk kaldı; arka plan hizmeti kapandı", "medya")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.background_playback_channel), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_pip_play)
            .setContentTitle(getString(R.string.background_playback_title))
            .setContentText(getString(R.string.background_playback_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, ID, notification, type) }
            .onFailure {
                Diagnostics.log("arka plan oynatma hizmeti ön plana alınamadı: ${it.javaClass.simpleName}", "medya")
                stopSelf()
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAYING -> {
                handler.removeCallbacks(stopRunnable)
                acquireLocks()
            }
            ACTION_STOPPED -> {
                // Şarkılar arası boşluk: kısa bir süre daha ayakta kal, işlemci ve Wi-Fi uyanık kalsın.
                acquireLocks()
                handler.removeCallbacks(stopRunnable)
                handler.postDelayed(stopRunnable, GRACE_MS)
            }
        }
        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gtarayici:playback")
                ?.apply {
                    setReferenceCounted(false)
                    acquire(MAX_LOCK_MS)
                }
        }
        if (wifiLock?.isHeld != true) {
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = runCatching {
                applicationContext.getSystemService(WifiManager::class.java)?.createWifiLock(mode, "gtarayici:playback")
                    ?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            }.getOrNull()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        running = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAYING = "com.gtarayici.browser.media.PLAYING"
        const val ACTION_STOPPED = "com.gtarayici.browser.media.STOPPED"
        private const val CHANNEL = "background_playback"
        private const val ID = 4208
        /** Oynatma durduktan sonra hizmetin ayakta kalacağı süre (sıradaki şarkının yüklenmesi için). */
        const val GRACE_MS = 3 * 60_000L
        /** Kilit güvenlik sınırı: unutulursa bile en fazla bu kadar tutulur (uzun dinlemelerde yenilenir). */
        private const val MAX_LOCK_MS = 6 * 60 * 60_000L

        @Volatile var running = false
            private set
    }
}
