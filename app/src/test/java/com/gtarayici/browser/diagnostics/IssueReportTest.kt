package com.gtarayici.browser.diagnostics

import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueReportTest {
    private val environment = "G-Tarayıcı 0.8.0 (debug)\nAndroid 15 (API 35) · vivo V2244\nGeckoView 160.0"

    private fun report(description: String = "Video tam ekranda donuyor", log: String = "", crash: String? = null) =
        IssueReport(IssueCategory.Video, description, environment, site = "https://www.youtube.com/watch?v=abc", log = log, crash = crash)

    private fun decodedBody(url: String) = URLDecoder.decode(url.substringAfter("&body="), "UTF-8")

    @Test
    fun `başlık kategori öneki ve açıklamanın ilk satırıdır`() {
        assertEquals("[Video] Video tam ekranda donuyor", report().title)
        assertEquals("[Video] Sorun bildirimi", report(description = "  ").title)
        assertEquals(80 + "[Video] ".length, report(description = "a".repeat(200)).title.length)
    }

    @Test
    fun `gövde ortamı, maskelenmiş siteyi ve kaydı içerir`() {
        val body = report(log = "2026-11-21 05:00:00.000 [genel] açıldı").body()
        assertTrue(body.contains("### Ne oldu?\nVideo tam ekranda donuyor"))
        assertTrue(body.contains("- Android 15 (API 35) · vivo V2244"))
        assertTrue(body.contains("- Site: https://www.youtube.com/…"))
        assertFalse(body.contains("watch?v=abc"))
        assertTrue(body.contains("```\n2026-11-21 05:00:00.000 [genel] açıldı\n```"))
    }

    @Test
    fun `çökme raporu ayrı bölümde`() {
        val body = report(crash = "java.lang.IllegalStateException: test").body()
        assertTrue(body.contains("### Çökme raporu\n```\njava.lang.IllegalStateException: test\n```"))
    }

    @Test
    fun `kısa rapor GitHub adresine tamamen sığar`() {
        val link = report(log = "satır").gitHubLink()
        assertTrue(link.url.startsWith(IssueReport.REPO_ISSUES_URL + "?title="))
        assertNull(link.clipboard)
        assertTrue(decodedBody(link.url).contains("satır"))
        assertFalse(link.url.contains("+"))
    }

    @Test
    fun `uzun kayıt adrese konmaz, panoya kopyalanır`() {
        val longLog = (1..400).joinToString("\n") { "2026-11-21 05:00:00.000 [genel] olay $it" }
        val link = report(log = longLog).gitHubLink()
        assertTrue(link.url.length <= IssueReport.MAX_URL_LENGTH)
        assertEquals(longLog, link.clipboard)
        val body = decodedBody(link.url)
        assertTrue(body.contains("panoya kopyalandı"))
        assertTrue(body.contains("Video tam ekranda donuyor"))
    }

    @Test
    fun `çok uzun açıklama kısaltılır, raporun tamamı panoya gider`() {
        val report = report(description = "ğüşiöç ".repeat(3000))
        val link = report.gitHubLink()
        assertTrue(link.url.length <= IssueReport.MAX_URL_LENGTH)
        assertEquals(report.body(), link.clipboard)
        // Kodlanmış karakterler yarıda bölünmez: adres sorunsuz çözülür.
        assertTrue(decodedBody(link.url).contains("kısaltıldı"))
    }

    @Test
    fun `çökme kaydı dosyaya maskelenmiş yazılır ve bir kez sorulur`() {
        val dir = Files.createTempDirectory("cokme").toFile()
        val reporter = CrashReporter(File(dir, "crash.txt")) { environment }
        assertNull(reporter.pending())
        val error = IllegalStateException("bağlanılamadı: https://ornek.com/gizli/yol", RuntimeException("192.168.1.5"))
        reporter.record("main", error, now = 0)
        val text = reporter.pending()!!
        assertTrue(text.contains("İş parçacığı: main"))
        assertTrue(text.contains("java.lang.IllegalStateException: bağlanılamadı: https://ornek.com/…"))
        assertTrue(text.contains("<ip>"))
        assertFalse(text.contains("gizli/yol"))
        assertTrue(text.contains("G-Tarayıcı 0.8.0"))
        reporter.dismiss()
        assertNull(reporter.pending())
        dir.deleteRecursively()
    }
}
