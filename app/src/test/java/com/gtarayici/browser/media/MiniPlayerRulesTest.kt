package com.gtarayici.browser.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerRulesTest {
    private fun show(
        enabled: Boolean = true,
        blocked: Boolean = false,
        hasTarget: Boolean = true,
        siteAllowed: Boolean = true,
        suppressed: Boolean = false,
        playing: Boolean = true,
        paused: Boolean = false,
        showing: Boolean = false,
    ) = MiniPlayerRules.shouldShow(enabled, blocked, hasTarget, siteAllowed, suppressed, playing, paused, showing)

    @Test fun opensForPlayingVideo() = assertTrue(show())

    @Test fun respectsSettingAndBlocks() {
        assertFalse(show(enabled = false))
        assertFalse(show(blocked = true))
        assertFalse(show(hasTarget = false))
        assertFalse(show(siteAllowed = false))
        assertFalse(show(suppressed = true))
    }

    @Test fun pausedVideoStaysOnlyIfAlreadyShowing() {
        assertFalse(show(playing = false, paused = true))
        assertTrue(show(playing = false, paused = true, showing = true))
        assertFalse(show(playing = false, paused = false, showing = true))
    }
}
