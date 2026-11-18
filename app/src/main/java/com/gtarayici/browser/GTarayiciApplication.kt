package com.gtarayici.browser

import android.app.Application
import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.webextensions.WebExtensionSupport

class GTarayiciApplication : Application() {
    val components by lazy { Components(this) }

    override fun onCreate() {
        super.onCreate()
        // GeckoView içerik süreçleri de bu sınıfı başlatır; yalnızca ana süreçte kurulum yapılır.
        if (!isMainProcess()) return

        components.engine.warmUp()
        components.applyGeckoPrefs()
        initExtensions()
        components.vpn.restore()
        components.mediaSessionFeature.start()
        components.historyRecorder.start()
        components.downloadsUseCases.restoreDownloads()
        restoreTabs()
        components.applicationScope.launch(Dispatchers.IO) {
            components.manifestStorage.warmUpScopes(System.currentTimeMillis())
        }
    }

    private fun initExtensions() {
        try {
            WebExtensionSupport.initialize(
                runtime = components.engine,
                store = components.store,
                openPopupInTab = false,
                onNewTabOverride = { _, engineSession, url, selected, isPrivate ->
                    components.tabsUseCases.addTab(
                        url,
                        selectTab = selected,
                        engineSession = engineSession,
                        private = isPrivate,
                        contextId = components.profiles.activeProfile.contextId,
                    )
                },
                onCloseTabOverride = { _, sessionId -> components.tabsUseCases.removeTab(sessionId) },
                onSelectTabOverride = { _, sessionId -> components.tabsUseCases.selectTab(sessionId) },
                onExtensionsLoaded = { components.extensions.refresh() },
            )
            components.extensions.start()
        } catch (e: UnsupportedOperationException) {
            Logger.error("Eklenti desteği başlatılamadı", e)
        }
    }

    private fun restoreTabs() {
        components.applicationScope.launch {
            components.tabsUseCases.restore(components.sessionStorage)
            components.sessionStorage
                .autoSave(components.store)
                .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
                .whenGoingToBackground()
                .whenSessionsChange()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (isMainProcess()) {
            components.store.dispatch(SystemAction.LowMemoryAction(level))
            components.icons.onTrimMemory(level)
        }
    }
}

val Context.components: Components
    get() = (applicationContext as GTarayiciApplication).components
