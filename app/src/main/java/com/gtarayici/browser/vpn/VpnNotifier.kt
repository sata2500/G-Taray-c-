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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * VPN açıkken sabit bir bildirim gösterir: hangi sunucuya bağlı olduğu ve "Bağlantıyı kes" düğmesi. Yüklü web
 * uygulamalarında adres çubuğu ve menü olmadığından VPN buradan kapatılabilir; diğer uygulamalardayken de görünür.
 */
class VpnNotifier(private val context: Context, private val vpn: VpnManager, private val scope: CoroutineScope) {

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
                    VpnStatus.Off -> NotificationManagerCompat.from(context).cancel(ID)
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

/** Bildirimdeki "Bağlantıyı kes" düğmesi. */
class VpnActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == VpnNotifier.ACTION_DISCONNECT) context.components.vpn.disconnect()
    }
}
