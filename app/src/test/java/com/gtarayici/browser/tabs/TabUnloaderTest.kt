package com.gtarayici.browser.tabs

import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.MediaSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.mediasession.MediaSession
import org.junit.Assert.assertEquals
import org.junit.Test

class TabUnloaderTest {
    private val minute = 60_000L
    private val now = 1_000L * minute

    @Test
    fun unloadsOnlyOldBackgroundTabsWithoutMedia() {
        val old = createTab("https://a.com", id = "old", lastAccess = now - 31 * minute, createdAt = now - 40 * minute)
        val fresh = createTab("https://b.com", id = "fresh", lastAccess = now - 5 * minute, createdAt = now - 40 * minute)
        val selected = createTab("https://c.com", id = "selected", lastAccess = now - 60 * minute, createdAt = now - 60 * minute)
        val media = createTab("https://d.com", id = "media", lastAccess = now - 60 * minute, createdAt = now - 60 * minute)
            .copy(
                mediaSessionState = MediaSessionState(
                    controller = object : MediaSession.Controller {
                        override fun pause() = Unit
                        override fun stop() = Unit
                        override fun play() = Unit
                        override fun seekTo(time: Double, fast: Boolean) = Unit
                        override fun seekForward() = Unit
                        override fun seekBackward() = Unit
                        override fun nextTrack() = Unit
                        override fun previousTrack() = Unit
                        override fun skipAd() = Unit
                        override fun muteAudio(mute: Boolean) = Unit
                    },
                    playbackState = MediaSession.PlaybackState.PAUSED,
                ),
            )
        val notLoaded = createTab("https://e.com", id = "notLoaded", lastAccess = now - 60 * minute, createdAt = now - 60 * minute)
        val state = BrowserState(tabs = listOf(old, fresh, selected, media, notLoaded), selectedTabId = "selected")

        val result = TabUnloader.candidates(state, now) { it.id != "notLoaded" }

        assertEquals(listOf("old"), result)
    }
}
