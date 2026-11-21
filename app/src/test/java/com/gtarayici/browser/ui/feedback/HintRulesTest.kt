package com.gtarayici.browser.ui.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HintRulesTest {

    private class FakeStore(override var enabled: Boolean = true) : HintStore {
        val seen = mutableSetOf<Hint>()
        override fun isSeen(hint: Hint) = hint in seen
        override fun markSeen(hint: Hint) { seen += hint }
    }

    private var now = 1_000_000L
    private val store = FakeStore()
    private val rules = HintRules(store) { now }

    @Test
    fun `her ipucu yalnızca bir kez gösterilir`() {
        assertTrue(rules.consume(Hint.YouTubeVideo))
        now += HintRules.GAP_MS * 10
        assertFalse(rules.consume(Hint.YouTubeVideo))
        assertEquals(setOf(Hint.YouTubeVideo), store.seen)
    }

    @Test
    fun `aynı anda en fazla bir ipucu`() {
        assertTrue(rules.consume(Hint.YouTubeVideo))
        now += HintRules.GAP_MS - 1
        assertFalse(rules.consume(Hint.ManyTabs))
        // Reddedilen ipucu "görüldü" sayılmaz, süre dolunca gösterilebilir.
        assertFalse(store.isSeen(Hint.ManyTabs))
        now += 1
        assertTrue(rules.consume(Hint.ManyTabs))
    }

    @Test
    fun `ayarlardan kapatılınca hiç gösterilmez`() {
        store.enabled = false
        Hint.entries.forEach { assertFalse(rules.canShow(it)) }
        assertFalse(rules.consume(Hint.VpnError))
        assertTrue(store.seen.isEmpty())
        store.enabled = true
        assertTrue(rules.consume(Hint.VpnError))
    }

    @Test
    fun `daha önce görülen ipucu yeni oturumda da gösterilmez`() {
        store.seen += Hint.ManyTabs
        val nextSession = HintRules(store) { now }
        assertFalse(nextSession.consume(Hint.ManyTabs))
        assertTrue(nextSession.consume(Hint.VpnError))
    }

    @Test
    fun `çok sekme koşulu`() {
        assertFalse(HintRules.manyTabs(HintRules.MANY_TABS - 1, toolbarSwipeEnabled = true))
        assertTrue(HintRules.manyTabs(HintRules.MANY_TABS, toolbarSwipeEnabled = true))
        assertFalse(HintRules.manyTabs(20, toolbarSwipeEnabled = false))
    }

    @Test
    fun `ipucu mesaj kanalında tek anahtarla gösterilir`() {
        val messenger = Messenger()
        val hints = Hints(rules, messenger)
        hints.offer(Hint.YouTubeVideo)
        hints.offer(Hint.YouTubeVideo)
        val shown = messenger.current.value.single()
        assertEquals(MessageKind.Hint, shown.kind)
        assertEquals(UiText.res(Hint.YouTubeVideo.text), shown.text)
        assertTrue(shown.long)
    }
}
