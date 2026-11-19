package com.gtarayici.browser.ui

import com.gtarayici.browser.R
import com.gtarayici.browser.data.HistoryEntry
import com.gtarayici.browser.ui.screens.historyGroups
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryGroupsTest {
    @Test
    fun groupsByDayKeepingOrder() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 19, 15, 0, 0) }.timeInMillis
        val hour = 60 * 60 * 1000L
        val entries = listOf(
            HistoryEntry("p", "https://a", "", now - hour),
            HistoryEntry("p", "https://b", "", now - 20 * hour),
            HistoryEntry("p", "https://c", "", now - 3 * 24 * hour),
            HistoryEntry("p", "https://d", "", now - 10 * 24 * hour),
            HistoryEntry("p", "https://e", "", now - 60 * 24 * hour),
        )
        val groups = historyGroups(entries, now)
        assertEquals(
            listOf(R.string.history_today, R.string.history_yesterday, R.string.history_this_week, R.string.history_this_month, R.string.history_older),
            groups.map { it.first },
        )
        assertEquals(listOf("https://a"), groups.first().second.map { it.url })
    }
}
