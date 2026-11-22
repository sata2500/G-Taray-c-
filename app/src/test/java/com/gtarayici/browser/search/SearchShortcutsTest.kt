package com.gtarayici.browser.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchShortcutsTest {
    @Test
    fun resolvesKeywordAndQuery() {
        val (site, query) = SearchShortcuts.resolve("YT kedi videoları")!!
        assertEquals("YouTube", site.title)
        assertEquals("kedi videoları", query)
        assertEquals("https://m.youtube.com/results?search_query=kedi%20videolar%C4%B1", site.url(query))
    }

    @Test
    fun ignoresNonShortcuts() {
        assertNull(SearchShortcuts.resolve("yt"))
        assertNull(SearchShortcuts.resolve("yt   "))
        assertNull(SearchShortcuts.resolve("hava durumu"))
        assertNull(SearchShortcuts.resolve("example.com"))
    }

    @Test
    fun parsesOpenSearchSuggestions() {
        val json = """["kedi",["kedi","kedi maması","Kedi Maması","kedi oyunları"," "]]"""
        assertEquals(listOf("kedi maması", "Kedi Maması", "kedi oyunları"), SearchSuggestions.parse(json, "kedi", 4))
        assertEquals(listOf("kedi maması"), SearchSuggestions.parse(json, "kedi", 1))
    }
}
