package com.gtarayici.browser.diagnostics

import com.gtarayici.browser.Components
import com.gtarayici.browser.vpn.VpnCoreState
import com.gtarayici.browser.vpn.VpnStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.lib.state.ext.flow

/**
 * Uygulamanın durum akışlarını izleyip önemli geçişleri tanılama kaydına yazar (VPN bağlantısı, indirme sonuçları).
 * Ziyaret edilen sayfalar kaydedilmez; indirmelerde yalnızca dosya türü yazılır.
 */
fun startDiagnosticsWatchers(components: Components) {
    val scope = components.applicationScope
    scope.launch {
        components.vpn.status.collect { status ->
            when (status) {
                VpnStatus.Off -> Diagnostics.log("kapalı", "vpn")
                VpnStatus.Connecting -> Diagnostics.log("bağlanıyor", "vpn")
                is VpnStatus.Connected -> Diagnostics.log("bağlandı (${status.server.protocol})", "vpn")
                is VpnStatus.Error -> Diagnostics.log("bağlanamadı: ${status.message}", "vpn")
            }
        }
    }
    scope.launch {
        components.vpnCore.state.collect { state ->
            if (state is VpnCoreState.Failed) Diagnostics.log("bileşen kurulamadı: ${state.message}", "vpn")
        }
    }
    scope.launch {
        val reported = mutableMapOf<String, DownloadState.Status>()
        components.store.flow()
            .map { state -> state.downloads.values.map { Triple(it.id, it.status, it.contentType) } }
            .distinctUntilChanged()
            .collect { downloads ->
                downloads.forEach { (id, status, type) ->
                    val previous = reported[id]
                    if (previous == status) return@forEach
                    reported[id] = status
                    // Açılışta geri yüklenen, zaten bitmiş indirmeler ilk görüldüklerinde yazılmaz
                    // (kayıtta her açılışta "tamamlandı (×3)" görünüyordu).
                    if (previous == null && status != DownloadState.Status.DOWNLOADING) return@forEach
                    when (status) {
                        DownloadState.Status.COMPLETED -> Diagnostics.log("tamamlandı (${type ?: "tür bilinmiyor"})", "indirme")
                        DownloadState.Status.FAILED -> Diagnostics.log("başarısız (${type ?: "tür bilinmiyor"})", "indirme")
                        DownloadState.Status.DOWNLOADING -> Diagnostics.log("başladı (${type ?: "tür bilinmiyor"})", "indirme")
                        else -> Unit
                    }
                }
            }
    }
}
