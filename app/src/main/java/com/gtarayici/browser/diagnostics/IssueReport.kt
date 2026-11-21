package com.gtarayici.browser.diagnostics

import androidx.annotation.StringRes
import com.gtarayici.browser.R
import java.net.URLEncoder

/** Sorun bildiriminin türü; GitHub başlığındaki önek ve formdaki seçenek. */
enum class IssueCategory(val tag: String, @StringRes val label: Int) {
    Crash("Çökme", R.string.report_cat_crash),
    Page("Sayfa", R.string.report_cat_page),
    Video("Video", R.string.report_cat_video),
    Download("İndirme", R.string.report_cat_download),
    Vpn("VPN", R.string.report_cat_vpn),
    Extensions("Eklenti", R.string.report_cat_extensions),
    Other("Diğer", R.string.report_cat_other),
    Idea("Öneri", R.string.report_cat_idea),
}

/**
 * Sorun bildiriminin içeriği. Metin, GitHub "yeni sorun" sayfasına önceden doldurulmuş olarak açılır veya dosya
 * olarak paylaşılır. Kullanıcının açıklaması dışındaki her şey (site, kayıt, çökme raporu) maskelenmiştir.
 */
data class IssueReport(
    val category: IssueCategory,
    val description: String,
    val environment: String,
    val site: String? = null,
    val log: String = "",
    val crash: String? = null,
    val hasScreenshot: Boolean = false,
) {
    val title: String
        get() {
            val summary = description.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(TITLE_LIMIT)
            return "[${category.tag}] " + (summary ?: DEFAULT_TITLE)
        }

    /** Raporun tamamı (Markdown). [includeLog] false ise kayıt yerine panoya kopyalandığı notu yazılır. */
    fun body(includeLog: Boolean = true): String = buildString {
        appendLine("### Ne oldu?")
        appendLine(description.trim().ifBlank { "_(açıklama yazılmadı)_" })
        appendLine()
        appendLine("### Ortam")
        environment.lines().filter { it.isNotBlank() }.forEach { appendLine("- $it") }
        site?.let { appendLine("- Site: ${PrivacyFilter.mask(it)}") }
        if (hasScreenshot) appendLine("- Ekran görüntüsü: ayrıca eklendi")
        crash?.let {
            appendLine()
            appendLine("### Çökme raporu")
            appendLine("```")
            appendLine(it.trim())
            appendLine("```")
        }
        if (log.isNotBlank()) {
            appendLine()
            appendLine("### Tanılama kaydı")
            if (includeLog) {
                appendLine("<details><summary>Son ${log.lines().size} satır (kişisel bilgiler maskelendi)</summary>")
                appendLine()
                appendLine("```")
                appendLine(log.trim())
                appendLine("```")
                appendLine("</details>")
            } else {
                appendLine("Kayıt uzun olduğu için panoya kopyalandı; lütfen buraya yapıştırın.")
            }
        }
    }.trimEnd() + "\n"

    /**
     * GitHub'da önceden doldurulmuş "yeni sorun" adresi. Tarayıcıların adres sınırını aşmamak için gövde
     * [MAX_URL_LENGTH] karakteri geçerse kayıt adrese konmaz; [GitHubLink.clipboard] doldurulur.
     */
    fun gitHubLink(repoUrl: String = REPO_ISSUES_URL): GitHubLink {
        val complete = body(includeLog = true)
        val full = link(repoUrl, complete)
        if (full.length <= MAX_URL_LENGTH) return GitHubLink(full, clipboard = null)
        val withoutLog = body(includeLog = log.isBlank())
        val url = fitted(repoUrl, withoutLog)
        // Kayıt çıkarıldıysa ve gövde sığdıysa yalnızca kayıt, gövde de kısaltıldıysa raporun tamamı panoya gider.
        val truncated = url.length < link(repoUrl, withoutLog).length
        return GitHubLink(url, clipboard = if (truncated || log.isBlank()) complete else log.trim())
    }

    private fun link(repoUrl: String, body: String) =
        "$repoUrl?title=${encode(title)}&body=${encode(body)}"

    /** Gövde hâlâ uzunsa (ör. çok uzun açıklama) metin kısaltılır; kodlanmış karakterler yarıda bölünmez. */
    private fun fitted(repoUrl: String, body: String): String {
        var url = link(repoUrl, body)
        var keep = body.length
        while (url.length > MAX_URL_LENGTH && keep > 200) {
            keep = (keep * 0.85).toInt()
            url = link(repoUrl, body.take(keep) + "\n\n…(kısaltıldı; tamamı panoya kopyalandı)\n")
        }
        return url
    }

    /** [clipboard]: adrese sığmayan ve kullanıcının GitHub sayfasına yapıştıracağı metin (yoksa null). */
    data class GitHubLink(val url: String, val clipboard: String?)

    companion object {
        const val REPO_ISSUES_URL = "https://github.com/sata2500/G-Taray-c-/issues/new"
        const val MAX_URL_LENGTH = 7_500
        private const val TITLE_LIMIT = 80
        private const val DEFAULT_TITLE = "Sorun bildirimi"

        private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    }
}
