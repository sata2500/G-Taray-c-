package com.gtarayici.browser.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gtarayici.browser.MainActivity
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gtarayici.browser.diagnostics.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * VPN açıkken sabit bir bildirim gösterir: hangi sunucuya bağlı olduğu ve "Bağlantıyı kes" düğmesi. Yüklü web
 * uygulamalarında adres çubuğu ve menü olmadığından VPN buradan kapatılabilir; diğer uygulamalardayken de görünür.
 *
 * "VPN arka planda açık kalsın" ayarı açıksa bildirim bir ön plan hizmetine aittir: Android uygulamayı arka planda
 * kapatmaz, VPN bağlı kalır ve uygulama yeniden açıldığında beklemeden doğrudan açılır.
 */
class VpnNotifier(
    private val context: Context,
    private val vpn: VpnManager,
    private val scope: CoroutineScope,
    private val keepAlive: () -> Boolean,
) {
    /** Hizmet başlarken gösterilecek son bildirim. */
    @Volatile var current: Notification? = null
        private set

    fun start() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.vpn_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        scope.launch {
            vpn.status.collect { status ->
                when (status) {
                    is VpnStatus.Connected -> show(context.getString(R.string.vpn_notification_connected, status.server.name))
                    VpnStatus.Connecting -> show(context.getString(R.string.vpn_notification_connecting))
                    is VpnStatus.Error -> show(context.getString(R.string.vpn_notification_error))
                    VpnStatus.Off -> {
                        current = null
                        context.stopService(Intent(context, VpnKeepAliveService::class.java))
                        NotificationManagerCompat.from(context).cancel(ID)
                    }
                }
            }
        }
    }

    /** "VPN arka planda açık kalsın" ayarı değişince hizmet başlatılır ya da durdurulur (bildirim kalır). */
    fun onKeepAliveChanged(on: Boolean) {
        val notification = current ?: return
        if (on) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, VpnKeepAliveService::class.java)) }
        } else {
            context.stopService(Intent(context, VpnKeepAliveService::class.java))
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                try {
                    manager.notify(ID, notification)
                } catch (_: SecurityException) {
                }
            }
        }
    }

    private fun show(text: String) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_VPN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disconnect = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, VpnActionReceiver::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(context.getString(R.string.vpn_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.vpn_disconnect), disconnect)
            .build()
        current = notification
        if (keepAlive()) {
            // Hizmet zaten çalışıyorsa aynı kimlikle bildirim güncellenir; çalışmıyorsa başlatılır.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, VpnKeepAliveService::class.java)) }
                .onFailure { Diagnostics.log("arka plan hizmeti başlatılamadı: ${it.javaClass.simpleName}", "vpn") }
        }
        // Bildirim izni yoksa gösterilmez; VPN yine çalışır.
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(ID, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CHANNEL = "vpn"
        const val ID = 4207
        const val ACTION_DISCONNECT = "com.gtarayici.browser.vpn.DISCONNECT"
    }
}

/** VPN açıkken uygulamanın arka planda kapatılmamasını sağlayan ön plan hizmeti (yalnızca bildirim taşır). */
class VpnKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = applicationContext.components.vpnNotifier.current
        if (notification == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, VpnNotifier.ID, notification, type) }
            .onFailure {
                Diagnostics.log("arka plan hizmeti ön plana alınamadı: ${it.javaClass.simpleName}", "vpn")
                stopSelf()
            }
        return START_NOT_STICKY
    }
}

/** Bildirimdeki "Bağlantıyı kes" düğmesi. */
class VpnActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == VpnNotifier.ACTION_DISCONNECT) context.components.vpn.disconnect()
    }
}
