package com.gtarayici.browser.vpn

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gtarayici.browser.MainActivity
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hızlı ayarlar düğmesi (bildirim paneli): tarayıcının VPN'ini tek dokunuşla açar/kapatır. Açarken son kullanılan
 * sunucuya bağlanır; hiç sunucu yoksa VPN ekranı açılır. Yalnızca tarayıcının (ve web uygulamalarının) trafiğini etkiler.
 */
class VpnTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val listening = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        listening.launch { applicationContext.components.vpn.status.collect { update(it) } }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val vpn = applicationContext.components.vpn
        when (vpn.status.value) {
            VpnStatus.Off, is VpnStatus.Error -> {
                val server = vpn.activeServerId?.let { id -> vpn.servers.value.firstOrNull { it.id == id } }
                    ?: vpn.servers.value.firstOrNull()
                if (server == null) openVpnScreen() else vpn.connect(server)
            }
            else -> vpn.disconnect()
        }
    }

    private fun openVpnScreen() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_VPN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun update(status: VpnStatus) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_vpn)
        tile.label = getString(R.string.vpn_tile_label)
        tile.state = when (status) {
            is VpnStatus.Connected, VpnStatus.Connecting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (status) {
                is VpnStatus.Connected -> status.server.name
                VpnStatus.Connecting -> getString(R.string.vpn_notification_connecting)
                is VpnStatus.Error -> getString(R.string.vpn_tile_error)
                VpnStatus.Off -> getString(R.string.vpn_tile_off)
            }
        }
        tile.updateTile()
    }
}
