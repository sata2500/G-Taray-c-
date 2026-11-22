package com.gtarayici.browser.search

import java.net.URLEncoder

/** Adres çubuğunda "yt kedi" gibi kısayolla doğrudan bir sitede arama. */
data class SearchShortcut(val keyword: String, val title: String, private val template: String) {
    fun url(query: String): String = template.replace("%s", URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20"))
}

object SearchShortcuts {
    val ALL = listOf(
        SearchShortcut("yt", "YouTube", "https://m.youtube.com/results?search_query=%s"),
        SearchShortcut("w", "Vikipedi", "https://tr.m.wikipedia.org/w/index.php?search=%s"),
        SearchShortcut("wen", "Wikipedia (İngilizce)", "https://en.m.wikipedia.org/w/index.php?search=%s"),
        SearchShortcut("g", "Google", "https://www.google.com/search?q=%s"),
        SearchShortcut("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        SearchShortcut("b", "Bing", "https://www.bing.com/search?q=%s"),
        SearchShortcut("y", "Yandex", "https://yandex.com.tr/search/?text=%s"),
        SearchShortcut("map", "Haritalar", "https://www.openstreetmap.org/search?query=%s"),
        SearchShortcut("ç", "Çeviri", "https://translate.google.com/?sl=auto&tl=tr&text=%s"),
        SearchShortcut("gh", "GitHub", "https://github.com/search?q=%s"),
        SearchShortcut("r", "Reddit", "https://www.reddit.com/search/?q=%s"),
        SearchShortcut("eksi", "Ekşi Sözlük", "https://eksisozluk.com/?q=%s"),
    )

    /** "yt kedi videoları" → (YouTube, "kedi videoları"); kısayol değilse null. Büyük/küçük harf fark etmez. */
    fun resolve(input: String): Pair<SearchShortcut, String>? {
        val text = input.trimStart()
        val space = text.indexOf(' ')
        if (space <= 0) return null
        val keyword = text.substring(0, space).lowercase(java.util.Locale.forLanguageTag("tr"))
        val query = text.substring(space + 1).trim()
        if (query.isEmpty()) return null
        val shortcut = ALL.firstOrNull { it.keyword == keyword } ?: return null
        return shortcut to query
    }
}
