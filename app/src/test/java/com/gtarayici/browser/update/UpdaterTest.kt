package com.gtarayici.browser.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updater.isNewer("0.5.0", "0.4.5"))
        assertTrue(Updater.isNewer("0.10.0", "0.9.9"))
        assertTrue(Updater.isNewer("1.0", "0.99.99"))
        assertFalse(Updater.isNewer("0.5.0", "0.5.0"))
        assertFalse(Updater.isNewer("0.4.9", "0.5.0"))
        assertFalse(Updater.isNewer("0.5", "0.5.0"))
    }

    private val release = JSONObject(
        """
        {"tag_name":"v0.5.0","body":"Notlar","assets":[
          {"name":"G-Tarayici-0.5.0-armeabi-v7a.apk","size":100,"browser_download_url":"https://example.org/v7a.apk"},
          {"name":"G-Tarayici-0.5.0-arm64-v8a.apk","size":200,"browser_download_url":"https://example.org/arm64.apk"},
          {"name":"SHA256SUMS.txt","size":1,"browser_download_url":"https://example.org/sums"}
        ]}
        """.trimIndent(),
    )

    @Test
    fun picksApkForDeviceAbi() {
        val parsed = Updater.parseRelease(release, "arm64-v8a")!!
        assertEquals("0.5.0", parsed.version)
        assertEquals("https://example.org/arm64.apk", parsed.apkUrl)
        assertEquals(200L, parsed.apkSize)
        assertEquals("Notlar", parsed.notes)
        assertEquals("https://example.org/v7a.apk", Updater.parseRelease(release, "armeabi-v7a")!!.apkUrl)
    }

    @Test
    fun noApkForAbi() {
        assertNull(Updater.parseRelease(release, "x86_64"))
    }
}
