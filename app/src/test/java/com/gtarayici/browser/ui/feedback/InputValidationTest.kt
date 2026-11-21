package com.gtarayici.browser.ui.feedback

import com.gtarayici.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputValidationTest {

    @Test
    fun `DNS adresi`() {
        assertNull(InputValidation.dohUrl(""))
        assertNull(InputValidation.dohUrl("https://dns.quad9.net/dns-query"))
        assertNull(InputValidation.dohUrl("  https://dns.nextdns.io/abc123  "))
        assertEquals(R.string.validation_doh_https, InputValidation.dohUrl("http://dns.example.com/dns-query"))
        assertEquals(R.string.validation_doh_https, InputValidation.dohUrl("dns.example.com"))
        assertEquals(R.string.validation_doh_host, InputValidation.dohUrl("https://"))
        assertEquals(R.string.validation_doh_host, InputValidation.dohUrl("https://localhost/dns-query"))
        assertEquals(R.string.validation_no_spaces, InputValidation.dohUrl("https://dns example.com"))
    }

    @Test
    fun `yer imi adresi`() {
        assertEquals(R.string.validation_url_empty, InputValidation.bookmarkUrl("  "))
        assertNull(InputValidation.bookmarkUrl("https://tr.wikipedia.org/wiki/Ankara"))
        assertNull(InputValidation.bookmarkUrl("ornek.com"))
        assertNull(InputValidation.bookmarkUrl("about:blank"))
        assertNull(InputValidation.bookmarkUrl("http://localhost:8080"))
        assertEquals(R.string.validation_no_spaces, InputValidation.bookmarkUrl("ornek com"))
        assertEquals(R.string.validation_url_invalid, InputValidation.bookmarkUrl("ornek"))
        assertEquals(R.string.validation_url_invalid, InputValidation.bookmarkUrl("https://"))
    }

    @Test
    fun `yer imi adresine şema eklenir`() {
        assertEquals("https://ornek.com", InputValidation.normalizeBookmarkUrl(" ornek.com "))
        assertEquals("http://ornek.com", InputValidation.normalizeBookmarkUrl("http://ornek.com"))
        assertEquals("about:blank", InputValidation.normalizeBookmarkUrl("about:blank"))
    }

    @Test
    fun `VPN bağlantısı`() {
        assertNull(InputValidation.vpnLink(""))
        assertNull(
            InputValidation.vpnLink("trojan://parola@sunucu.example.com:443?sni=sunucu.example.com#Deneme"),
        )
        assertEquals(R.string.validation_vpn_unknown, InputValidation.vpnLink("merhaba dünya"))
        assertEquals(R.string.validation_vpn_unknown, InputValidation.vpnLink("ftp://sunucu.example.com"))
        assertEquals(R.string.validation_vpn_broken, InputValidation.vpnLink("vless://"))
    }
}
