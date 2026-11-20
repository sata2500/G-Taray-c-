package com.gtarayici.browser.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test
    fun matchesTurkishLettersCaseInsensitively() {
        assertTrue(TextMatch.matches("ista", "İstanbul - Vikipedi", "https://tr.wikipedia.org/wiki/%C4%B0stanbul"))
        assertTrue(TextMatch.matches("ISTA", "İstanbul", ""))
        assertTrue(TextMatch.matches("ıstanbul", "Istanbul", ""))
        assertTrue(TextMatch.matches("şeker", "", "https://a.com/%C5%9Feker"))
    }

    @Test
    fun matchesWithoutTurkishDiacritics() {
        assertTrue(TextMatch.matches("turkiye", "Türkiye - Vikipedi", ""))
        assertTrue(TextMatch.containsAny("cubuk", "Sayfayı kaydırınca çubuk iner"))
        // Son ünsüz yumuşaması: "çubuk" → "çubuğu", "kitap" → "kitabı"
        assertTrue(TextMatch.containsAny("cubuk", "Kaydırınca adres çubuğunu gizle"))
        assertTrue(TextMatch.containsAny("ÇUBUK", "adres çubuğu"))
        assertTrue(TextMatch.containsAny("kitap", "Bu kitabı oku"))
        assertFalse(TextMatch.containsAny("cubuz", "adres çubuğu"))
    }

    @Test
    fun matchesDecodedUrlAndRejectsOthers() {
        assertTrue(TextMatch.matches("wiki/istanbul", "", "https://tr.wikipedia.org/wiki/%C4%B0stanbul"))
        assertFalse(TextMatch.matches("ankara", "İstanbul", "https://tr.wikipedia.org/wiki/%C4%B0stanbul"))
        assertFalse(TextMatch.matches("  ", "İstanbul", ""))
    }
}
