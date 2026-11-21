package com.gtarayici.browser.ui.feedback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.gtarayici.browser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * İnternet bağlantısı kesilince ve geri gelince ortak mesaj kanalında haber verir. Kısa kopmalar (VPN açılıp
 * kapanırken ağ değişimi gibi) [graceMs] boyunca beklenir; o sürede bağlantı dönerse hiçbir şey gösterilmez.
 */
class NetworkStatusReporter(
    private val messenger: Messenger,
    private val scope: CoroutineScope,
    private val graceMs: Long = GRACE_MS,
) {
    private var pendingLoss: Job? = null
    private var announcedLoss = false

    fun onOnline() {
        pendingLoss?.cancel()
        pendingLoss = null
        if (!announcedLoss) return
        announcedLoss = false
        messenger.dismissKey(OFFLINE_KEY)
        messenger.show(Message(UiText.res(R.string.network_back), kind = MessageKind.Success, key = ONLINE_KEY))
    }

    fun onOffline() {
        if (announcedLoss || pendingLoss?.isActive == true) return
        pendingLoss = scope.launch {
            delay(graceMs)
            announcedLoss = true
            messenger.dismissKey(ONLINE_KEY)
            messenger.show(
                Message(UiText.res(R.string.network_lost), kind = MessageKind.Warning, long = true, key = OFFLINE_KEY),
            )
        }
    }

    companion object {
        const val GRACE_MS = 2_000L
        internal const val OFFLINE_KEY = "ag-yok"
        internal const val ONLINE_KEY = "ag-var"
    }
}

/** Varsayılan ağı izleyip [NetworkStatusReporter]'a bildirir. Uygulama süresince bir kez başlatılır. */
fun startNetworkMonitor(context: Context, reporter: NetworkStatusReporter) {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
    runCatching {
        // Geri çağrılar ana iş parçacığında gelir; bildirici durumu tek iş parçacığından değişir.
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) reporter.onOnline()
            }

            override fun onLost(network: Network) {
                // Ağ değişiminde yeni varsayılan ağ bekleme süresi içinde doğrulanırsa mesaj gösterilmez.
                reporter.onOffline()
            }

            override fun onUnavailable() = reporter.onOffline()
        }, main)
    }
}
