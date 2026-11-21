package com.gtarayici.browser.ui

import com.gtarayici.browser.data.HistoryEntry
import com.gtarayici.browser.ui.screens.topSites
import org.junit.Assert.assertEquals
import org.junit.Test

class TopSitesTest {
    private fun visit(url: String, count: Int, at: Long) = HistoryEntry("p", url, "", at, count)

    @Test
    fun groupsByHostAndSortsByVisits() {
        val sites = topSites(
            listOf(
                visit("https://www.youtube.com/watch?v=1", 3, 10),
                visit("https://www.youtube.com/watch?v=2", 4, 20),
                visit("https://tr.wikipedia.org/wiki/A", 2, 30),
                visit("https://tek.example.com/", 1, 40),
                visit("about:blank", 9, 50),
            ),
            hidden = emptySet(),
        )
        assertEquals(listOf("https://www.youtube.com/", "https://tr.wikipedia.org/"), sites.map { it.url })
        assertEquals("Youtube", sites.first().title)
    }

    @Test
    fun skipsHiddenHosts() {
        val sites = topSites(listOf(visit("https://www.youtube.com/", 5, 1)), hidden = setOf("youtube.com"))
        assertEquals(emptyList<Any>(), sites)
    }

    @Test
    fun skipsFailedUrls() {
        val sites = topSites(
            listOf(visit("https://yok.example.com/", 5, 1), visit("https://var.example.com/", 3, 2)),
            hidden = emptySet(),
            failed = setOf("https://yok.example.com/"),
        )
        assertEquals(listOf("https://var.example.com/"), sites.map { it.url })
    }

    @Test
    fun disambiguatesSameShortName() {
        val sites = topSites(
            listOf(
                visit("https://tr.wikipedia.org/", 5, 1),
                visit("https://en.m.wikipedia.org/", 3, 2),
                visit("https://www.youtube.com/", 2, 3),
            ),
            hidden = emptySet(),
        )
        assertEquals(listOf("Wikipedia (tr)", "Wikipedia (en)", "Youtube"), sites.map { it.title })
    }
}
