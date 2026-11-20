package com.gtarayici.browser.tabs

import com.gtarayici.browser.settings.AppSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore

/**
 * Uzun süredir açılmayan sekmeleri bellekten boşaltır ("uyutur"). Sekme listede kalır; dokununca
 * kaldığı yerden yeniden yüklenir. Çok sekme açıkken RAM ve pil tasarrufu sağlar.
 * Seçili sekme ve medya oynatan/duraklatılmış sekmeler hiçbir zaman uyutulmaz.
 */
class TabUnloader(
    private val store: BrowserStore,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                if (settings.unloadInactiveTabs.value) {
                    candidates(store.state, System.currentTimeMillis()).forEach {
                        store.dispatch(EngineAction.SuspendEngineSessionAction(it))
                    }
                }
            }
        }
    }

    companion object {
        private val CHECK_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)
        val INACTIVE_AFTER_MS = TimeUnit.MINUTES.toMillis(30)

        /** Uyutulacak sekmelerin kimlikleri. [isLoaded]: sekmenin motor oturumu bellekte mi (testte değiştirilebilir). */
        internal fun candidates(
            state: BrowserState,
            now: Long,
            isLoaded: (TabSessionState) -> Boolean = { it.engineState.engineSession != null },
        ): List<String> = state.tabs
            .filter { tab ->
                tab.id != state.selectedTabId &&
                    isLoaded(tab) &&
                    tab.mediaSessionState == null &&
                    !tab.content.loading &&
                    now - maxOf(tab.lastAccess, tab.createdAt) > INACTIVE_AFTER_MS
            }
            .map { it.id }
    }
}
