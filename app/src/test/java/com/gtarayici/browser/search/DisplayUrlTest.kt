package com.gtarayici.browser.search

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayUrlTest {
    @Test
    fun decodesUtf8PathForDisplay() {
        assertEquals("tr.wikipedia.org/wiki/İstanbul", UrlInput.displayUrl("https://tr.wikipedia.org/wiki/%C4%B0stanbul"))
        assertEquals("example.com/çay?q=şeker", UrlInput.displayUrl("https://www.example.com/%C3%A7ay?q=%C5%9Feker"))
    }

    @Test
    fun keepsMeaningfulAndUnsafeCharactersEncoded() {
        // Ayraçlar, boşluk ve yön değiştiren görünmez karakterler kodlu kalır.
        assertEquals("a.com/x%2Fy%3Fz%20w", UrlInput.displayUrl("https://a.com/x%2Fy%3Fz%20w"))
        assertEquals("a.com/%E2%80%AEtxt", UrlInput.displayUrl("https://a.com/%E2%80%AEtxt"))
    }

    @Test
    fun leavesInvalidSequencesAndHostAlone() {
        assertEquals("a.com/%C4", UrlInput.displayUrl("https://a.com/%C4"))
        assertEquals("a.com/%zz", UrlInput.displayUrl("https://a.com/%zz"))
        assertEquals("xn--e1afmkfd.com", UrlInput.displayUrl("https://xn--e1afmkfd.com/"))
    }
}
