package com.gtarayici.browser.network

import com.gtarayici.browser.settings.CacheSize
import com.gtarayici.browser.settings.DataSaverMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSaverPolicyTest {
    private val wifi = LinkInfo(metered = false, downstreamKbps = 50_000, systemSaver = false)
    private val mobile = LinkInfo(metered = true, downstreamKbps = 20_000, systemSaver = false)
    private val slowWifi = LinkInfo(metered = false, downstreamKbps = 600, systemSaver = false)
    private val unknownSpeed = LinkInfo(metered = false, downstreamKbps = 0, systemSaver = false)

    @Test
    fun autoFollowsConnection() {
        assertFalse(DataSaverPolicy.active(DataSaverMode.AUTO, wifi))
        assertTrue(DataSaverPolicy.active(DataSaverMode.AUTO, mobile))
        assertTrue(DataSaverPolicy.active(DataSaverMode.AUTO, slowWifi))
        assertTrue(DataSaverPolicy.active(DataSaverMode.AUTO, wifi.copy(systemSaver = true)))
        assertFalse(DataSaverPolicy.active(DataSaverMode.AUTO, unknownSpeed))
        assertFalse(DataSaverPolicy.active(DataSaverMode.AUTO, null))
    }

    @Test
    fun manualModesIgnoreConnection() {
        assertTrue(DataSaverPolicy.active(DataSaverMode.ON, wifi))
        assertFalse(DataSaverPolicy.active(DataSaverMode.OFF, mobile))
    }

    @Test
    fun cacheIsCappedByFreeSpace() {
        val gb = 1024L * 1024 * 1024
        assertNull(DataSaverPolicy.cacheCapacityKb(CacheSize.AUTO, 100 * gb))
        assertEquals(512 * 1024, DataSaverPolicy.cacheCapacityKb(CacheSize.MB_512, 100 * gb))
        // 2 GB boş alan: en fazla onda biri (~205 MB).
        assertEquals((2 * gb / 10 / 1024).toInt(), DataSaverPolicy.cacheCapacityKb(CacheSize.GB_1, 2 * gb))
        // Telefon neredeyse dolu: en az 50 MB.
        assertEquals(50 * 1024, DataSaverPolicy.cacheCapacityKb(CacheSize.GB_2, 100L * 1024 * 1024))
    }
}
