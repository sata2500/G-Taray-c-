package com.gtarayici.browser.downloads

import com.gtarayici.browser.R
import com.gtarayici.browser.ui.feedback.Message
import com.gtarayici.browser.ui.feedback.MessageAction
import com.gtarayici.browser.ui.feedback.MessageKind
import com.gtarayici.browser.ui.feedback.Messenger
import com.gtarayici.browser.ui.feedback.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow

/**
 * Hangi indirmelerin bu oturumda yeni tamamlandığını bulur. İlk görülen liste (geri yüklenen eski indirmeler)
 * duyurulmaz; sonradan doğrudan "tamamlandı" olarak eklenen kayıt (ör. YouTube indirmesi) yeniyse duyurulur.
 */
class DownloadCompletionTracker(private val clock: () -> Long = System::currentTimeMillis) {
    private var known: Map<String, DownloadState.Status>? = null

    fun newlyCompleted(downloads: Collection<DownloadState>): List<DownloadState> {
        val previous = known
        known = downloads.associate { it.id to it.status }
        if (previous == null) return emptyList()
        return downloads.filter { download ->
            if (download.status != DownloadState.Status.COMPLETED) return@filter false
            when (val before = previous[download.id]) {
                null -> clock() - download.createdTime < RECENT_MS
                else -> before != DownloadState.Status.COMPLETED
            }
        }
    }

    companion object {
        const val RECENT_MS = 60_000L
    }
}

/** Tamamlanan indirme için "İndirildi: dosya · Aç" mesajı gösterir. */
class DownloadCompletionNotifier(
    private val store: BrowserStore,
    private val messenger: Messenger,
    private val scope: CoroutineScope,
    private val open: (DownloadState) -> Boolean,
) {
    private val tracker = DownloadCompletionTracker()

    fun start() {
        scope.launch {
            store.flow().map { it.downloads }.distinctUntilChanged().collect { downloads ->
                tracker.newlyCompleted(downloads.values).forEach(::announce)
            }
        }
    }

    private fun announce(download: DownloadState) {
        val name = download.fileName ?: return
        messenger.show(
            Message(
                text = UiText.res(R.string.download_finished_message, name),
                kind = MessageKind.Success,
                action = MessageAction(UiText.res(R.string.action_open)) {
                    if (!open(download)) messenger.warning(UiText.res(R.string.download_open_failed))
                },
                key = "indirildi-${download.id}",
            ),
        )
    }
}
