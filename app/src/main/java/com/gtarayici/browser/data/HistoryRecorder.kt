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
) {
    private data class Visit(val tabId: String, val url: String, val title: String)

    private val lastRecorded = mutableMapOf<String, Visit>()

    fun start() {
        scope.launch {
            store.flow()
                .map { state ->
                    state.tabs
                        .filter { !it.content.private && !it.content.loading }
                        .map { Visit(it.id, it.content.url, it.content.title) }
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
                if (visit.title.isNotBlank()) dao.updateTitle(visit.url, visit.title)
                return@launch
            }
            val existing = dao.find(visit.url)
            dao.upsert(
                HistoryEntry(
                    url = visit.url,
                    title = visit.title.ifBlank { existing?.title.orEmpty() },
                    lastVisited = System.currentTimeMillis(),
                    visitCount = (existing?.visitCount ?: 0) + 1,
                ),
            )
        }
    }

    private fun isRecordable(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")
}
