package com.gtarayici.browser.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow

/**
 * Normal (gizli olmayan) sekmelerde yüklenmesi tamamlanan sayfaları geçmişe kaydeder.
 * Gizli sekmeler hiçbir zaman kaydedilmez.
 */
class HistoryRecorder(
    private val store: BrowserStore,
    private val dao: HistoryDao,
    private val scope: CoroutineScope,
    private val profileIdFor: (contextId: String?) -> String?,
) {
    private data class Visit(val tabId: String, val profileId: String, val url: String, val title: String)

    private val lastRecorded = mutableMapOf<String, Visit>()

    fun start() {
        scope.launch {
            store.flow()
                .map { state ->
                    state.tabs
                        // Yalnızca yüklemesi tamamlanmış sayfalar: adres yazılınca sekmenin adresi yükleme
                        // başlamadan değişir; aksi halde yönlendirme kaynakları başlıksız kaydedilir.
                        .filter { !it.content.private && !it.content.loading && it.content.progress >= 100 }
                        .mapNotNull { tab ->
                            val profileId = profileIdFor(tab.contextId) ?: return@mapNotNull null
                            // Çevrilmiş sayfanın başlığı da çevrilir; geçmişe sayfanın özgün başlığı kalsın.
                            val title = if (tab.translationsState.isTranslated) "" else tab.content.title
                            Visit(tab.id, profileId, tab.content.url, title)
                        }
                        .filter { isRecordable(it.url) }
                }
                .distinctUntilChanged()
                .collect { visits -> visits.forEach { record(it) } }
        }
    }

    private fun record(visit: Visit) {
        val previous = lastRecorded[visit.tabId]
        if (previous == visit) return
        lastRecorded[visit.tabId] = visit
        scope.launch(Dispatchers.IO) {
            if (previous?.url == visit.url) {
                // Aynı sayfa: yalnızca başlık güncellendi.
                if (visit.title.isNotBlank()) dao.updateTitle(visit.profileId, visit.url, visit.title)
                return@launch
            }
            val existing = dao.find(visit.profileId, visit.url)
            // Sayfa kendi adresini yeniden yazınca (history.replaceState; ör. Vikipedi "Istanbul" → "İstanbul")
            // sekmenin başlığı sıfırlanır ama belge değişmediği için yeniden gönderilmez. Aynı sitedeysek önceki
            // başlık taşınır; gerçek başlık sonradan gelirse yukarıdaki güncelleme onu yazar.
            val carried = previous?.title?.takeIf { it.isNotBlank() && sameHost(previous.url, visit.url) }
            dao.upsert(
                HistoryEntry(
                    profileId = visit.profileId,
                    url = visit.url,
                    title = visit.title.ifBlank { existing?.title?.takeIf { it.isNotBlank() } ?: carried.orEmpty() },
                    lastVisited = System.currentTimeMillis(),
                    visitCount = (existing?.visitCount ?: 0) + 1,
                ),
            )
        }
    }

    private fun sameHost(a: String, b: String): Boolean {
        val ha = runCatching { java.net.URI(a).host }.getOrNull() ?: return false
        return ha == runCatching { java.net.URI(b).host }.getOrNull()
    }

    private fun isRecordable(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")
}
