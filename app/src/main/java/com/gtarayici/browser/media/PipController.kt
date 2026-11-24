package com.gtarayici.browser.media

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gtarayici.browser.Components
import com.gtarayici.browser.R
import com.gtarayici.browser.diagnostics.Diagnostics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.lib.state.ext.flow

/**
 * Resim içinde resim (PiP).
 *
 * - Video oynarken uygulamadan çıkılınca (ana ekran, son uygulamalar, başka uygulamaya geçiş),
 *   video tam ekranda olmasa da otomatik olarak küçük pencereye geçer; oynayan video tam ekrana alınır.
 * - Küçük pencerede önceki / oynat-duraklat / sonraki düğmeleri bulunur.
 * - Küçük pencere kapatılınca oynatma duraklatılır.
 */
class PipController(
    private val activity: ComponentActivity,
    private val components: Components,
    /** Yüklü web uygulaması penceresi gibi tek bir sekmeye bağlıysa o sekmenin kimliği. */
    private val fixedTabId: String? = null,
    /** Sekmenin gösterildiği motor görünümü. */
    private val engineView: () -> android.view.View? = { null },
) {
    private val native = NativeVideoPip(
        activity,
        components.runtime,
        sourceView = { NativeVideoPip.findGeckoView(engineView()) },
        videoRect = { view -> components.extensions.videoRect.value?.toViewPixels(view.width, view.height) },
    )

    /** Küçük pencerede gösterilen, yalnızca videoyu içeren görünüm (arayüz bunu sayfanın üstüne yerleştirir). */
    val pipView: android.view.View get() = native.pipView

    private data class MediaInfo(val tabId: String, val playing: Boolean, val features: Long, val width: Long, val height: Long)

    private var media: MediaInfo? = null
    private val supported = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    /** Küçük pencere yalnızca görünür bir video oynarken anlamlıdır; yalnızca ses varsa arka planda çalmak yeterli. */
    private val enabled get() = supported && components.settings.autoPictureInPicture.value && !components.extensions.videoHidden.value

    /** Küçük penceredeki önceki / oynat-duraklat / sonraki düğmeleri. */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val controller = mediaTab()?.mediaSessionState?.controller ?: return
            when (intent.getStringExtra(EXTRA_COMMAND)) {
                CMD_PLAY -> controller.play()
                CMD_PAUSE -> controller.pause()
                CMD_NEXT -> controller.nextTrack()
                CMD_PREVIOUS -> controller.previousTrack()
            }
        }
    }

    fun start() {
        if (!supported) return
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        activity.lifecycle.addObserver(lifecycleObserver)
        activity.lifecycleScope.launch {
            components.store.flow()
                .map { state -> state.mediaInfo() }
                .distinctUntilChanged()
                .collect { info ->
                    media = info
                    updateParams()
                }
        }
        // Videonun ekrandaki konumu değişince geçiş animasyonu ipucu güncellenir.
        activity.lifecycleScope.launch {
            components.extensions.videoRect.collect { if (media?.playing == true) updateParams() }
        }
        activity.lifecycleScope.launch {
            components.extensions.videoHidden.collect { if (media?.playing == true) updateParams() }
        }
    }

    fun stop() {
        handler.removeCallbacks(earlyEntry)
        if (supported) runCatching { activity.unregisterReceiver(receiver) }
    }

    /**
     * Kullanıcı uygulamadan ayrılıyor (ana ekran, son uygulamalar, başka uygulamaya geçiş, bildirim).
     *
     * Android 12+ ana ekran hareketinde otomatik geçişi kendisi yapar ve bu durumda bu çağrı gelmez.
     * Otomatik geçiş kapalıyken (bkz. [onTopResumedChanged]) sistem, geçiş tamamlandığında bu ipucunu
     * verir ve PiP isteğini kabul eder. Uygulama içinde açılan pencerelerde PiP başlamaz.
     */
    fun onUserLeaveHint() {
        leaveHintSeen = true
        log("ayrılma ipucu")
        enterManually("ayrılma ipucu")
    }

    /**
     * Etkinlik ön plandaki "en üst" konumunu kaybetti ya da geri kazandı.
     *
     * Bazı üreticilerin hızlı uygulama değiştirme hareketi (ör. vivo'da alt çubuğu yana kaydırmak)
     * otomatik PiP'i yapmaz. Hareket başlayınca otomatik geçiş bayrağı kapatılır: böylece hareket
     * tamamlanırsa sistem ayrılma ipucu verip PiP'e izin verir, vazgeçilirse hiçbir şey olmaz ve bayrak
     * eski haline döner.
     *
     * Cihaz bu yolu da engelliyorsa (bir kez fark edilir ve hatırlanır) PiP hareket sürerken, uygulama
     * henüz açıkken istenir; yarım kalan hafif kaydırmalarda açılmaması için kısa bir süre beklenir.
     */
    fun onTopResumedChanged(isTop: Boolean) {
        log("en üst etkinlik=$isTop")
        handler.removeCallbacks(earlyEntry)
        if (isTop) {
            if (leaving) {
                leaving = false
                updateParams()
            }
            return
        }
        val ownLaunch = android.os.SystemClock.uptimeMillis() - lastOwnLaunch < OWN_LAUNCH_GRACE_MS
        if (ownLaunch || !enabled || media?.playing != true) return
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        leaving = true
        updateParams()
        if (earlyMode) handler.postDelayed(earlyEntry, EARLY_ENTRY_DELAY_MS)
    }

    /** Uygulama kendisi bir ekran açarken çağrılır (bu sırada PiP'e geçilmez). */
    fun noteOwnLaunch() {
        lastOwnLaunch = android.os.SystemClock.uptimeMillis()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val prefs = activity.getSharedPreferences("gtarayici_state", Context.MODE_PRIVATE)
    private var earlyMode = prefs.getBoolean(KEY_EARLY_MODE, false)
    private var leaving = false
    private var leaveHintSeen = false
    private var lastOwnLaunch = 0L

    private val earlyEntry = Runnable {
        if (leaving && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) enterManually("hareket sürerken")
    }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            leaveHintSeen = false
        }

        // Ayrılma ipucu hiç gelmeden başka bir uygulama öne geçtiyse son bir deneme yapılır.
        override fun onPause(owner: LifecycleOwner) {
            log("duraklatıldı")
            if (!leaveHintSeen && !activity.isFinishing && !activity.isChangingConfigurations) enterManually("duraklatma")
        }

        override fun onStop(owner: LifecycleOwner) {
            log("durduruldu")
            handler.removeCallbacks(earlyEntry)
            // Geçiş tamamlandı ama PiP açılamadı: bu cihaz tamamlanmış geçişte izin vermiyor.
            if (leaving && !earlyMode && media?.playing == true && !activity.isInPictureInPictureMode &&
                !activity.isChangingConfigurations && !activity.isFinishing && screenUnlocked()
            ) {
                earlyMode = true
                prefs.edit { putBoolean(KEY_EARLY_MODE, true) }
                Diagnostics.pip("PiP: bu cihaz tamamlanmış geçişte PiP'e izin vermiyor; bundan sonra hareket sürerken istenecek")
            }
            leaving = false
        }
    }

    private fun screenUnlocked(): Boolean {
        val power = activity.getSystemService(android.os.PowerManager::class.java)
        val keyguard = activity.getSystemService(android.app.KeyguardManager::class.java)
        return power?.isInteractive == true && keyguard?.isKeyguardLocked != true
    }

    /** Yalnızca video oynarken (PiP'in söz konusu olduğu durumlarda) tanılama kaydı tutulur. */
    private fun log(event: String) {
        if (media?.playing == true) Diagnostics.pip("PiP olayı: $event (PiP=${activity.isInPictureInPictureMode})")
    }

    private fun enterManually(reason: String) {
        if (!enabled || media?.playing != true || activity.isInPictureInPictureMode) return
        // Otomatik geçiş bayrağı açıkken sistem, duraklatılan etkinliğin elle isteğini reddeder.
        val entered = runCatching { activity.enterPictureInPictureMode(buildParams(autoEnter = false)) }
            .onFailure { Diagnostics.pip("PiP elle istenemedi ($reason): ${it.message}") }
            .getOrDefault(false)
        Diagnostics.pip("PiP elle istendi ($reason): ${if (entered) "kabul edildi" else "sistem reddetti"}")
    }

    fun onPictureInPictureModeChanged(inPip: Boolean) {
        val tab = mediaTab()
        Diagnostics.pip("PiP ${if (inPip) "başladı" else "bitti"} (yaşam döngüsü=${activity.lifecycle.currentState})")
        if (inPip) {
            // Geçiş başarıyla PiP'e döndü; sonradan pencere kapatılınca bu "başarısız geçiş" sayılmamalı.
            leaving = false
            if (tab != null && !native.enter()) Diagnostics.pip("PiP: kaynak görünüm bulunamadı")
        } else {
            // Pencere kapatıldıysa etkinlik artık görünür değildir; büyütüldüyse tarayıcıya dönülür.
            val dismissed = !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            native.exit(dismissed)
            if (dismissed) tab?.mediaSessionState?.controller?.pause()
            updateParams()
        }
    }

    private fun updateParams() {
        if (!supported) return
        runCatching { activity.setPictureInPictureParams(buildParams()) }
    }

    private fun buildParams(autoEnter: Boolean = !leaving): PictureInPictureParams {
        val info = media
        val builder = PictureInPictureParams.Builder()
        val hint = sourceRectHint()
        val ratio = when {
            info != null && info.width > 0 && info.height > 0 -> Rational(info.width.toInt(), info.height.toInt())
            hint != null -> Rational(hint.width(), hint.height())
            else -> Rational(16, 9)
        }
        // Android yalnızca 1:2.39 ile 2.39:1 arasındaki oranları kabul eder.
        val clamped = ratio.toFloat().coerceIn(1 / 2.39f, 2.39f)
        builder.setAspectRatio(if (clamped == ratio.toFloat()) ratio else Rational((clamped * 1000).toInt(), 1000))
        // Geçiş animasyonu tüm sayfadan değil, videonun ekrandaki yerinden başlar.
        hint?.let { builder.setSourceRectHint(it) }
        builder.setActions(actions(info))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter && enabled && info?.playing == true)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    /** Videonun pencere koordinatlarındaki yeri (yardımcı eklentinin bildirdiği konumdan). */
    private fun sourceRectHint(): android.graphics.Rect? {
        if (activity.isInPictureInPictureMode) return null
        val view = NativeVideoPip.findGeckoView(engineView()) ?: return null
        val rect = components.extensions.videoRect.value?.toViewPixels(view.width, view.height) ?: return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        rect.offset(location[0], location[1])
        return rect
    }

    private fun actions(info: MediaInfo?): List<RemoteAction> {
        if (info == null) return emptyList()
        val list = mutableListOf<RemoteAction>()
        if (info.features and MediaSession.Feature.PREVIOUS_TRACK != 0L) {
            list += action(CMD_PREVIOUS, R.drawable.ic_pip_previous, R.string.pip_previous)
        }
        list += if (info.playing) {
            action(CMD_PAUSE, R.drawable.ic_pip_pause, R.string.pip_pause)
        } else {
            action(CMD_PLAY, R.drawable.ic_pip_play, R.string.pip_play)
        }
        if (info.features and MediaSession.Feature.NEXT_TRACK != 0L) {
            list += action(CMD_NEXT, R.drawable.ic_pip_next, R.string.pip_next)
        }
        return list
    }

    private fun action(command: String, icon: Int, title: Int): RemoteAction {
        val intent = Intent(ACTION).setPackage(activity.packageName).putExtra(EXTRA_COMMAND, command)
        val pending = PendingIntent.getBroadcast(
            activity,
            command.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = activity.getString(title)
        return RemoteAction(Icon.createWithResource(activity, icon), label, label, pending)
    }

    private fun mediaTab(): SessionState? {
        val id = media?.tabId ?: return null
        val state = components.store.state
        return state.tabs.firstOrNull { it.id == id } ?: state.customTabs.firstOrNull { it.id == id }
    }

    private fun BrowserState.mediaInfo(): MediaInfo? {
        val candidates: List<SessionState> = if (fixedTabId != null) {
            listOfNotNull(tabs.firstOrNull { it.id == fixedTabId } ?: customTabs.firstOrNull { it.id == fixedTabId })
        } else {
            listOfNotNull(selectedTab) + tabs
        }
        val tab = candidates.firstOrNull { it.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING }
            ?: candidates.firstOrNull { it.mediaSessionState?.playbackState == MediaSession.PlaybackState.PAUSED }
            ?: return null
        val state = tab.mediaSessionState ?: return null
        return MediaInfo(
            tabId = tab.id,
            playing = state.playbackState == MediaSession.PlaybackState.PLAYING,
            features = state.features.flags,
            width = state.elementMetadata?.width ?: 0,
            height = state.elementMetadata?.height ?: 0,
        )
    }

    private companion object {
        const val ACTION = "com.gtarayici.browser.PIP_CONTROL"
        const val EXTRA_COMMAND = "command"
        const val CMD_PLAY = "play"
        const val CMD_PAUSE = "pause"
        const val CMD_NEXT = "next"
        const val CMD_PREVIOUS = "previous"
        const val OWN_LAUNCH_GRACE_MS = 2_000L
        const val EARLY_ENTRY_DELAY_MS = 250L
        const val KEY_EARLY_MODE = "pip_quick_switch_early"
    }
}
