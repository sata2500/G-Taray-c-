package com.gtarayici.browser.engine

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfResultMiddlewareTest {
    @Test
    fun consumesPdfResultsSoTheStoreDoesNotThrow() {
        val results = mutableListOf<Boolean>()
        val forwarded = mutableListOf<BrowserAction>()
        val middleware = PdfResultMiddleware { results += it }
        val next: (BrowserAction) -> Unit = { forwarded += it }
        val store = BrowserStore()

        middleware.invoke(store, next, EngineAction.SaveToPdfCompleteAction("t"))
        middleware.invoke(store, next, EngineAction.SaveToPdfExceptionAction("t", RuntimeException("x")))
        middleware.invoke(store, next, EngineAction.PrintContentCompletedAction("t"))
        assertEquals(listOf(true, false), results)
        assertTrue("PDF sonuçları depoya iletilmemeli", forwarded.isEmpty())

        val other = TabListAction.AddTabAction(createTab("https://ornek.com"))
        middleware.invoke(store, next, other)
        assertEquals(listOf<BrowserAction>(other), forwarded)
    }

    @Test
    fun realStoreSurvivesPdfComplete() {
        val results = mutableListOf<Boolean>()
        val store = BrowserStore(middleware = listOf(PdfResultMiddleware { results += it }))
        store.dispatch(EngineAction.SaveToPdfCompleteAction("t"))
        // Depo eylemi kendi iş parçacığında işleyebilir; sonucun gelmesini kısa süre bekle.
        val deadline = System.currentTimeMillis() + 2_000
        while (results.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(listOf(true), results)
    }
}
