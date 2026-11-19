package com.gtarayici.browser

import androidx.core.content.edit
import android.app.Application
import android.content.Context
import android.os.Looper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.concept.engine.permission.SitePermissions
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
        restoreTabs()
        // Açılışı geciktirmemek için acil olmayan işler ilk ekran çizildikten sonra (ana iş parçacığı boşken) yapılır.
        Looper.myQueue().addIdleHandler {
            components.downloadsUseCases.restoreDownloads()
            components.applicationScope.launch(Dispatchers.IO) {
                components.manifestStorage.warmUpScopes(System.currentTimeMillis())
            }
            allowMediaSiteAutoplay()
            components.tabUnloader.start()
            false
        }
    }

    /**
     * Oynatma listelerinde sonraki videonun ekran kapalıyken de başlayabilmesi için YouTube'da
     * sesli otomatik oynatmaya izin verilir (diğer sitelerde engelli kalır). Bir kez yapılır.
     */
    private fun allowMediaSiteAutoplay() {
        val prefs = getSharedPreferences("gtarayici_state", MODE_PRIVATE)
        if (prefs.getBoolean("media_autoplay_allowed", false)) return
        components.applicationScope.launch {
            MEDIA_SITES.forEach { origin ->
                val existing = components.permissionStorage.findSitePermissionsBy(origin, includeTemporary = false, private = false)
                if (existing == null) {
                    components.permissionStorage.save(
                        SitePermissions(
                            origin = origin,
                            autoplayAudible = SitePermissions.AutoplayStatus.ALLOWED,
                            autoplayInaudible = SitePermissions.AutoplayStatus.ALLOWED,
                        ),
                        request = null,
                        private = false,
                    )
                }
            }
            prefs.edit { putBoolean("media_autoplay_allowed", true) }
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
            components.tabGroups.start()
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

private val MEDIA_SITES = listOf(
    "https://www.youtube.com",
    "https://m.youtube.com",
    "https://music.youtube.com",
)

val Context.components: Components
    get() = (applicationContext as GTarayiciApplication).components
