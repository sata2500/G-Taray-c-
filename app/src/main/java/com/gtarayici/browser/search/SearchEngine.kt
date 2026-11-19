package com.gtarayici.browser.search

import androidx.core.net.toUri
import android.net.Uri
import mozilla.components.support.ktx.util.URLStringUtils

/** Gizlilik dostu seçenekler önce gelir; varsayılan DuckDuckGo'dur. */
enum class SearchEngine(val title: String, private val template: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    YANDEX("Yandex", "https://yandex.com.tr/search/?text=%s"),
    ;

    fun searchUrl(query: String): String = template.replace("%s", Uri.encode(query.trim()))
}

object UrlInput {
    /**
     * Kullanıcının yazdığı metni yüklenecek adrese dönüştürür: adres gibi görünüyorsa
     * (şema yoksa HTTPS ile) açılır, değilse seçili arama motorunda aranır.
     */
    fun toLoadableUrl(input: String, engine: SearchEngine): String {
        val text = input.trim()
        if (text.isEmpty()) return "about:blank"
        val lower = text.lowercase()
        return when {
            DIRECT_SCHEMES.any { lower.startsWith(it) } -> text
            text.contains(' ') || !URLStringUtils.isURLLike(text) -> engine.searchUrl(text)
            text.contains("://") -> text
            else -> "https://$text"
        }
    }

    /** Adres çubuğunda gösterilecek sade metin: şema ve "www." gizlenir. */
    fun displayUrl(url: String): String {
        if (url.isBlank() || url == "about:blank") return ""
        return url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
    }

    private val DIRECT_SCHEMES = listOf("about:", "view-source:", "data:", "moz-extension:")

    fun host(url: String): String =
        runCatching { url.toUri().host.orEmpty().removePrefix("www.") }.getOrDefault("")
}
