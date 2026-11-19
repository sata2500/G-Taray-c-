package com.gtarayici.browser.tabs

import org.junit.Assert.assertEquals
import org.junit.Test

class TabGroupsTest {
    @Test
    fun serializationRoundTrip() {
        val groups = listOf(
            TabGroup("a", "İş", TabGroups.COLORS[0], null, listOf("t1", "t2"), collapsed = true),
            TabGroup("b", "Okul \"notlar\"", TabGroups.COLORS[3], "profile-2", listOf("t3")),
        )
        assertEquals(groups, TabGroups.parse(TabGroups.serialize(groups)))
    }
}
