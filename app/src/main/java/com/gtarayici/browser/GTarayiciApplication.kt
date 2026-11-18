package com.gtarayici.browser

import android.app.Application
import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.support.ktx.android.content.isMainProcess

class GTarayiciApplication : Application() {
    val components by lazy { Components(this) }

    override fun onCreate() {
        super.onCreate()
        // GeckoView içerik süreçleri de bu sınıfı başlatır; yalnızca ana süreçte kurulum yapılır.
        if (!isMainProcess()) return

        components.engine.warmUp()
        components.historyRecorder.start()
        components.downloadsUseCases.restoreDownloads()
        restoreTabs()
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
