package com.gtarayici.browser.downloads.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadGuardsTest {
    @Test
    fun pageHtmlIsNotSavedAsTheRequestedFile() {
        assertTrue(DownloadQueue.servesPageInsteadOf("application/pdf", "text/html; charset=utf-8"))
        assertTrue(DownloadQueue.servesPageInsteadOf("video/mp4", "application/xhtml+xml"))
        assertFalse(DownloadQueue.servesPageInsteadOf("application/pdf", "application/pdf"))
        assertFalse(DownloadQueue.servesPageInsteadOf(null, "text/html"))
        assertFalse(DownloadQueue.servesPageInsteadOf("text/html", "text/html"))
        assertFalse(DownloadQueue.servesPageInsteadOf("application/octet-stream", "text/html"))
    }

    @Test
    fun pdfHeaderCheck() {
        val pdf = "%PDF-1.7\n".toByteArray()
        val html = "<!DOCTYPE html>".toByteArray()
        assertTrue(DownloadQueue.looksLikePdf(pdf, pdf.size))
        assertFalse(DownloadQueue.looksLikePdf(html, html.size))
        assertFalse(DownloadQueue.looksLikePdf(pdf, 3))
    }
}
