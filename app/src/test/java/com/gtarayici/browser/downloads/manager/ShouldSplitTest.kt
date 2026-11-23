package com.gtarayici.browser.downloads.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldSplitTest {
    private fun probe(mime: String?, total: Long = 50_000_000, ranges: Boolean = true) =
        Probe(total = total, rangesSupported = ranges, mime = mime, fileName = null, finalUrl = "https://ornek.com/x")

    @Test
    fun generatedPdfIsNeverReplacedByThePageHtml() {
        // "PDF olarak kaydet": adres sayfanın kendisi, sunucu HTML döndürüyor.
        assertFalse(DownloadQueue.shouldSplit("application/pdf", probe("text/html; charset=utf-8"), 8))
        assertFalse(DownloadQueue.shouldSplit("application/pdf", probe(null), 8))
    }

    @Test
    fun sameTypeLargeFilesAreSplit() {
        assertTrue(DownloadQueue.shouldSplit("video/mp4", probe("video/mp4"), 8))
        assertTrue(DownloadQueue.shouldSplit("application/pdf", probe("Application/PDF"), 8))
        assertTrue(DownloadQueue.shouldSplit(null, probe("application/zip"), 8))
        assertTrue(DownloadQueue.shouldSplit("application/octet-stream", probe("application/zip"), 8))
    }

    @Test
    fun smallOrUnrangedFilesUseTheReadyConnection() {
        assertFalse(DownloadQueue.shouldSplit("video/mp4", probe("video/mp4", total = 1_000), 8))
        assertFalse(DownloadQueue.shouldSplit("video/mp4", probe("video/mp4", ranges = false), 8))
        assertFalse(DownloadQueue.shouldSplit("video/mp4", probe("video/mp4"), 1))
    }
}
