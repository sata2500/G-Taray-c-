package com.gtarayici.browser.engine

import com.gtarayici.browser.diagnostics.Diagnostics
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * PDF olarak kaydetme ve yazdırma sonuçlarını karşılar. Mozilla bileşenlerinde bu sonuç eylemleri bir ara katman
 * tarafından tüketilmezse durum deposu bilerek hata fırlatır (0.14.0'da "PDF olarak kaydet" bu yüzden çöküyordu).
 * Eylemler burada tüketilir, sonuç arayüze bildirilir.
 */
class PdfResultMiddleware(
    private val onResult: (success: Boolean) -> Unit,
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        when (action) {
            is EngineAction.SaveToPdfCompleteAction -> onResult(true)
            is EngineAction.SaveToPdfExceptionAction -> {
                Diagnostics.error("pdf", action.throwable)
                onResult(false)
            }
            is EngineAction.PrintContentCompletedAction -> Unit
            is EngineAction.PrintContentExceptionAction -> Diagnostics.error("yazdırma", action.throwable)
            else -> next(action)
        }
    }
}
