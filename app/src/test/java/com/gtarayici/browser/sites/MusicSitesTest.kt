package com.gtarayici.browser.sites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSitesTest {
    @Test
    fun musicSitesAreRecognised() {
        assertTrue(MusicSites.contains(SiteSettings.key("https://music.youtube.com/watch?v=x")))
        assertTrue(MusicSites.contains(SiteSettings.key("https://open.spotify.com/track/1")))
        assertTrue(MusicSites.contains(SiteSettings.key("https://www.deezer.com/tr/")))
        assertTrue(MusicSites.contains(SiteSettings.key("https://artist.bandcamp.com/album/a")))
    }

    @Test
    fun videoSitesKeepPip() {
        assertFalse(MusicSites.contains(SiteSettings.key("https://m.youtube.com/watch?v=x")))
        assertFalse(MusicSites.contains(SiteSettings.key("https://www.youtube.com/")))
        assertFalse(MusicSites.contains(SiteSettings.key("https://notdeezer.com/")))
        assertEquals("music.youtube.com", SiteSettings.key("https://music.youtube.com/"))
    }
}
