package com.gtarayici.browser.sites

import org.junit.Assert.assertEquals
import org.junit.Test

class TextZoomTest {
    @Test
    fun stepsStayInRange() {
        assertEquals(110, TextZoom.step(100, up = true))
        assertEquals(90, TextZoom.step(100, up = false))
        assertEquals(TextZoom.MIN, TextZoom.step(TextZoom.MIN, up = false))
        assertEquals(TextZoom.MAX, TextZoom.step(TextZoom.MAX, up = true))
    }

    @Test
    fun factorIncludesSystemScale() {
        assertEquals(1.32f, TextZoom.factor(1.1f, 120), 0.0001f)
        assertEquals(1f, TextZoom.factor(1f, 100), 0.0001f)
    }
}
