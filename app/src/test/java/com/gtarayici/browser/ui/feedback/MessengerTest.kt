package com.gtarayici.browser.ui.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessengerTest {
    private val copied = UiText.Plain("Bağlantı kopyalandı")
    private val saved = UiText.Plain("Kaydedildi")

    @Test
    fun showsOneMessageAtATimeInOrder() {
        val m = Messenger()
        m.info(copied)
        m.success(saved)
        assertEquals(listOf(copied, saved), m.current.value.map { it.text })
        m.dismiss(m.current.value.first())
        assertEquals(listOf(saved), m.current.value.map { it.text })
    }

    @Test
    fun mergesDuplicatesWhileQueuedOrShown() {
        val m = Messenger()
        m.info(copied)
        m.info(copied)
        m.info(copied)
        assertEquals(1, m.current.value.size)
        m.dismiss(m.current.value.first())
        m.info(copied)
        assertEquals(1, m.current.value.size)
    }

    @Test
    fun errorsAndActionsAreLongAndIdsIncrease() {
        val m = Messenger()
        m.error(copied)
        m.info(saved, MessageAction(UiText.Plain("Geri al")) {})
        val (a, b) = m.current.value
        assertTrue(a.long)
        assertTrue(b.long)
        assertTrue(b.id > a.id)
        assertFalse(Message(saved).long)
    }

    @Test
    fun dismissByKey() {
        val m = Messenger()
        m.show(Message(copied, key = "undo-tab"))
        m.info(saved)
        m.dismissKey("undo-tab")
        assertEquals(listOf(saved), m.current.value.map { it.text })
    }
}
