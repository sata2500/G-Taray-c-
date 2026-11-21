package com.gtarayici.browser.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyFilterTest {
    private fun mask(text: String) = PrivacyFilter.mask(text)

    @Test
    fun `adreslerde yalnızca alan adı kalır`() {
        assertEquals(
            "yüklenemedi: https://tr.wikipedia.org/…",
            mask("yüklenemedi: https://tr.wikipedia.org/wiki/Ankara?oldid=1#Tarih"),
        )
        assertEquals("https://ornek.com", mask("https://ornek.com/"))
        assertEquals("http://ornek.com/…", mask("http://kullanici:parola@ornek.com:8080/gizli?token=abc"))
    }

    @Test
    fun `VPN bağlantıları tamamen gizlenir`() {
        val link = "vless://1b2c3d4e-0000-4000-8000-123456789abc@sunucu.example.com:443?security=reality#Ev"
        val masked = mask("eklenemedi: $link")
        assertEquals("eklenemedi: vless://<gizli>", masked)
        assertEquals("ss://<gizli>", mask("ss://YWVzLTI1Ni1nY206cGFyb2xh@1.2.3.4:8388"))
    }

    @Test
    fun `e-posta, IP ve UUID maskelenir`() {
        assertEquals("kullanıcı <e-posta> giriş yaptı", mask("kullanıcı ali.veli@ornek.com.tr giriş yaptı"))
        assertEquals("failed to connect to /<ip> (port 443)", mask("failed to connect to /192.168.1.20 (port 443)"))
        assertEquals("sunucu <ip> yanıt vermedi", mask("sunucu 2001:db8:85a3:0:0:8a2e:370:7334 yanıt vermedi"))
        assertEquals("oturum <kimlik>", mask("oturum 123e4567-e89b-12d3-a456-426614174000"))
    }

    @Test
    fun `telefon numarası ve dosya yolu gizlenir`() {
        assertEquals("ara <numara>", mask("ara +90 532 123 45 67"))
        assertEquals("kaydedildi: <yol>", mask("kaydedildi: /storage/emulated/0/Download/Özgeçmiş.pdf"))
        assertEquals("<yol> silinemedi", mask("/data/user/0/com.gtarayici.browser/files/x.txt silinemedi"))
    }

    @Test
    fun `anahtar ve belirteç benzeri uzun diziler gizlenir`() {
        assertEquals("anahtar=<gizli>", mask("anahtar=ghp_a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8"))
    }

    @Test
    fun `sorunu anlamaya yarayan teknik metin bozulmaz`() {
        val technical = "SocketTimeoutException: timeout ← HTTP 503 · PiP=true · 1920x1080 · sürüm 0.8.0 (API 35)"
        assertEquals(technical, mask(technical))
        assertEquals("kod 12345 bekleniyor", mask("kod 12345 bekleniyor"))
        val stack = "at com.gtarayici.browser.vpn.VpnManager.connect(VpnManager.kt:131)"
        assertEquals(stack, mask(stack))
    }

    @Test
    fun `maskelenmiş metin yeniden maskelenince değişmez`() {
        val once = mask("https://ornek.com/a ali@ornek.com 10.0.0.1 vless://x@y:1")
        assertEquals(once, mask(once))
        assertFalse(once.contains("ali@"))
        assertTrue(once.contains("https://ornek.com/…"))
    }
}
