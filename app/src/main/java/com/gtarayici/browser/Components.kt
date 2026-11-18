package com.gtarayici.browser

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.gtarayici.browser.data.AppDatabase
import com.gtarayici.browser.data.HistoryRecorder
import com.gtarayici.browser.downloads.DownloadService
import com.gtarayici.browser.settings.AppSettings
import com.gtarayici.browser.thumbnails.TabThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.thumbnails.ThumbnailsMiddleware
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession.TrackingProtectionPolicy
import mozilla.components.concept.engine.mediaquery.PreferredColorScheme
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.contextmenu.ContextMenuUseCases
import mozilla.components.feature.downloads.DefaultFileSizeFormatter
import mozilla.components.feature.downloads.DownloadEstimator
import mozilla.components.feature.downloads.DownloadMiddleware
import mozilla.components.feature.downloads.DownloadsUseCases
import mozilla.components.feature.downloads.FileSizeFormatter
import mozilla.components.feature.prompts.PromptMiddleware
import mozilla.components.feature.prompts.file.FileUploadsDirCleaner
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.session.middleware.LastAccessMiddleware
import mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.utils.DefaultDateTimeProvider
import mozilla.components.support.utils.DefaultDownloadFileUtils
import mozilla.components.support.utils.DownloadFileUtils
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Uygulama genelinde tek örnek olarak yaşayan bileşenler (motor, durum deposu, kullanım senaryoları).
 */
class Components(private val context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val settings by lazy { AppSettings(context) }
    val database by lazy { AppDatabase.create(context) }

    private val runtime: GeckoRuntime by lazy {
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(true)
            .consoleOutput(BuildConfig.DEBUG)
            .build()
        GeckoRuntime.create(context, runtimeSettings)
    }

    val engineSettings by lazy {
        DefaultSettings().apply {
            trackingProtectionPolicy = trackingPolicy(settings.strictTracking.value)
            httpsOnlyMode = httpsMode(settings.httpsOnly.value)
            globalPrivacyControlEnabled = settings.globalPrivacyControl.value
            queryParameterStripping = true
            queryParameterStrippingPrivateBrowsing = true
            preferredColorScheme = PreferredColorScheme.System
            supportMultipleWindows = true
            javaScriptCanOpenWindowsAutomatically = false
            remoteDebuggingEnabled = BuildConfig.DEBUG
        }
    }

    val engine: Engine by lazy { GeckoEngine(context, engineSettings, runtime) }

    val client: Client by lazy { GeckoViewFetchClient(context, runtime) }

    val icons by lazy { BrowserIcons(context, client) }

    val notificationsDelegate by lazy { NotificationsDelegate(NotificationManagerCompat.from(context)) }

    val downloadFileUtils: DownloadFileUtils by lazy { DefaultDownloadFileUtils(context = context) }
    val fileSizeFormatter: FileSizeFormatter by lazy { DefaultFileSizeFormatter(context) }
    val downloadEstimator by lazy { DownloadEstimator(dateTimeProvider = DefaultDateTimeProvider()) }

    val fileUploadsDirCleaner by lazy { FileUploadsDirCleaner(applicationScope) { context.cacheDir } }

    val permissionStorage by lazy { OnDiskSitePermissionsStorage(context, coroutineScope = applicationScope) }

    val thumbnailStorage by lazy { ThumbnailStorage(context) }
    val tabThumbnails by lazy { TabThumbnails(thumbnailStorage, applicationScope) }

    val store by lazy {
        BrowserStore(
            middleware = listOf(
                DownloadMiddleware(
                    applicationContext = context,
                    downloadServiceClass = DownloadService::class.java,
                    deleteFileFromStorage = { false },
                    downloadFileUtils = downloadFileUtils,
                ),
                // Sıra önemli: önizleme önbelleği, diske kaydeden Mozilla katmanından önce gelir.
                tabThumbnails.middleware,
                ThumbnailsMiddleware(thumbnailStorage),
                LastAccessMiddleware(),
                PromptMiddleware(),
            ) + EngineMiddleware.create(engine),
        )
    }

    val sessionStorage by lazy { SessionStorage(context, engine, applicationScope = applicationScope) }

    val sessionUseCases by lazy { SessionUseCases(store) }
    val tabsUseCases by lazy { TabsUseCases(store) }
    val downloadsUseCases by lazy { DownloadsUseCases(store, downloadFileUtils) }
    val contextMenuUseCases by lazy { ContextMenuUseCases(store) }

    val historyRecorder by lazy { HistoryRecorder(store, database.history(), applicationScope) }

    /** Ayar ekranındaki değişiklikleri çalışan motora anında uygular. */
    fun applyPrivacySettings() {
        engine.settings.trackingProtectionPolicy = trackingPolicy(settings.strictTracking.value)
        engine.settings.httpsOnlyMode = httpsMode(settings.httpsOnly.value)
        engine.settings.globalPrivacyControlEnabled = settings.globalPrivacyControl.value
    }

    private fun trackingPolicy(strict: Boolean) =
        if (strict) TrackingProtectionPolicy.strict() else TrackingProtectionPolicy.recommended()

    private fun httpsMode(enabled: Boolean) =
        if (enabled) Engine.HttpsOnlyMode.ENABLED else Engine.HttpsOnlyMode.DISABLED
}
