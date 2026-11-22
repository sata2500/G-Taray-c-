package com.gtarayici.browser.passwords

import com.gtarayici.browser.ui.screens.normalizeSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginMatchingTest {
    @Test
    fun hostAndDomain() {
        assertEquals("giris.example.com", LoginMatching.hostOf("https://Giris.Example.com:443/yol"))
        assertTrue(LoginMatching.belongsTo("giris.example.com", "example.com"))
        assertTrue(LoginMatching.belongsTo("example.com", "example.com"))
        // Benzer ama başka site: "badexample.com" example.com'a ait değildir.
        assertFalse(LoginMatching.belongsTo("badexample.com", "example.com"))
        assertFalse(LoginMatching.belongsTo("", "example.com"))
    }

    @Test
    fun sameAccountIgnoresDefaultPortAndCase() {
        assertTrue(LoginMatching.sameAccount("https://Example.com:443", "ali", "https://example.com", "ali"))
        assertFalse(LoginMatching.sameAccount("https://example.com", "ali", "http://example.com", "ali"))
        assertFalse(LoginMatching.sameAccount("https://example.com", "ali", "https://example.com", "veli"))
    }

    @Test
    fun manualSiteBecomesOrigin() {
        assertEquals("https://example.com", normalizeSite(" example.com "))
        assertEquals("http://intranet.local:8080", normalizeSite("http://intranet.local:8080/login"))
        assertEquals("example.com", LoginMatching.displayHost("https://www.example.com"))
    }
}
