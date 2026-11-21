package com.gtarayici.browser.downloads

import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.state.content.DownloadState.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCompletionTrackerTest {
    private var now = 1_000_000L
    private val tracker = DownloadCompletionTracker { now }

    private fun download(id: String, status: Status, createdTime: Long = now) =
        DownloadState(
            url = "https://ornek.com/$id",
            fileName = "$id.pdf",
            id = id,
            status = status,
            createdTime = createdTime,
            directoryPath = "/indirilenler",
        )

    @Test
    fun `ilk liste duyurulmaz`() {
        assertTrue(tracker.newlyCompleted(listOf(download("eski", Status.COMPLETED))).isEmpty())
    }

    @Test
    fun `inen dosya tamamlanınca bir kez duyurulur`() {
        tracker.newlyCompleted(emptyList())
        tracker.newlyCompleted(listOf(download("a", Status.DOWNLOADING)))
        assertEquals(listOf("a"), tracker.newlyCompleted(listOf(download("a", Status.COMPLETED))).map { it.id })
        assertTrue(tracker.newlyCompleted(listOf(download("a", Status.COMPLETED))).isEmpty())
    }

    @Test
    fun `doğrudan tamamlandı eklenen yeni kayıt duyurulur, eski kayıt duyurulmaz`() {
        tracker.newlyCompleted(emptyList())
        val fresh = download("youtube", Status.COMPLETED)
        val restored = download("geri-yuklenen", Status.COMPLETED, createdTime = now - DownloadCompletionTracker.RECENT_MS - 1)
        assertEquals(listOf("youtube"), tracker.newlyCompleted(listOf(fresh, restored)).map { it.id })
    }

    @Test
    fun `başarısız indirme duyurulmaz`() {
        tracker.newlyCompleted(listOf(download("b", Status.DOWNLOADING)))
        assertTrue(tracker.newlyCompleted(listOf(download("b", Status.FAILED))).isEmpty())
    }
}
