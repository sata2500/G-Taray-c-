package com.gtarayici.browser.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {
    private val content = JSONObject().put("bookmarks", org.json.JSONArray().put(JSONObject().put("url", "https://ornek.com").put("title", "Örnek ğüşıöç")))

    @Test
    fun plainRoundTrip() {
        val text = BackupCodec.encode(content, null)
        assertFalse(BackupCodec.isEncrypted(text))
        assertEquals(content.toString(), BackupCodec.decode(text, null).toString())
    }

    @Test
    fun encryptedRoundTripAndHidesContent() {
        val text = BackupCodec.encode(content, "çok gizli parola")
        assertTrue(BackupCodec.isEncrypted(text))
        assertFalse("İçerik açıkta kalmamalı", text.contains("ornek.com"))
        assertEquals(content.toString(), BackupCodec.decode(text, "çok gizli parola").toString())
    }

    @Test
    fun wrongOrMissingPassword() {
        val text = BackupCodec.encode(content, "dogru-parola")
        expect(BackupException.Reason.WRONG_PASSWORD) { BackupCodec.decode(text, "yanlis-parola") }
        expect(BackupException.Reason.PASSWORD_REQUIRED) { BackupCodec.decode(text, null) }
    }

    @Test
    fun rejectsForeignAndNewerFiles() {
        expect(BackupException.Reason.NOT_A_BACKUP) { BackupCodec.decode("{\"a\":1}", null) }
        expect(BackupException.Reason.NOT_A_BACKUP) { BackupCodec.decode("<html>", null) }
        val newer = JSONObject(BackupCodec.encode(content, null)).put("version", BackupCodec.VERSION + 1).toString()
        expect(BackupException.Reason.NEWER_VERSION) { BackupCodec.decode(newer, null) }
    }

    @Test
    fun settingsKeepTheirTypes() {
        val values = mapOf("a" to true, "b" to 7, "c" to 7L, "d" to 1.5f, "e" to "metin", "f" to setOf("x", "y"))
        val back = BackupManager.settingsFrom(JSONObject(BackupManager.settingsJson(values).toString()))
        assertEquals(values, back)
    }

    private fun expect(reason: BackupException.Reason, block: () -> Unit) {
        try {
            block()
            fail("$reason bekleniyordu")
        } catch (e: BackupException) {
            assertEquals(reason, e.reason)
        }
    }
}
