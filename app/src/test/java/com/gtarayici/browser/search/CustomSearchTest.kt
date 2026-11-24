package com.gtarayici.browser.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSearchTest {
    @Test
    fun validatesPatterns() {
        assertTrue(CustomSearch.isValid("https://ornek.com/ara?q=%s"))
        assertTrue(CustomSearch.isValid(" https://search.example.org/?query=%s&lang=tr "))
        assertFalse(CustomSearch.isValid("https://ornek.com/ara?q="))
        assertFalse(CustomSearch.isValid("ornek.com/ara?q=%s"))
        assertFalse(CustomSearch.isValid("javascript:alert(%s)"))
        assertFalse(CustomSearch.isValid("https://ornek.com/ara q=%s"))
    }

    @Test
    fun labelsByHost() {
        assertEquals("ornek.com", CustomSearch.label("https://www.ornek.com/ara?q=%s"))
        assertEquals("", CustomSearch.label("geçersiz"))
    }
}
