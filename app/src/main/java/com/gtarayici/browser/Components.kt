package com.gtarayici.browser

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.gtarayici.browser.data.AppDatabase
import com.gtarayici.browser.data.HistoryRecorder
import com.gtarayici.browser.downloads.DownloadService
import com.gtarayici.browser.engine.GeckoPrefs
import com.gtarayici.browser.extensions.ExtensionManager
import com.gtarayici.browser.media.MediaSessionService
import com.gtarayici.browser.profiles.ProfileManager
import com.gtarayici.browser.security.AppLock
import com.gtarayici.browser.settings.AppSettings
import com.gtarayici.browser.thumbnails.TabThumbnails
import com.gtarayici.browser.vpn.VpnManager
import com.gtarayici.browser.youtube.YouTubeDownloader
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
import mozilla.components.feature.media.MediaSessionFeature
import mozilla.components.feature.prompts.PromptMiddleware
import mozilla.components.feature.prompts.file.FileUploadsDirCleaner
import mozilla.components.feature.pwa.ManifestStorage
import mozilla.components.feature.pwa.WebAppShortcutManager
import mozilla.components.feature.pwa.WebAppUseCases
import mozilla.components.feature.pwa.intent.WebAppIntentProcessor
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.session.middleware.LastAccessMiddleware
import mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage
import mozilla.components.feature.tabs.CustomTabsUseCases
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
    val profiles by lazy {
        ProfileManager(context, database, applicationScope, context.getString(R.string.profile_default_name))
    }
    val appLock by lazy { AppLock(context) }

    val runtime: GeckoRuntime by lazy {
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(true)
            .consoleOutput(BuildConfig.DEBUG)
            .extensionsProcessEnabled(true)
            // addons.mozilla.org sitesinden doğrudan eklenti kurulabilsin.
            .extensionsWebAPIEnabled(true)
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
            // Arka planda oynatma için sekme arka plandayken medya askıya alınmaz.
            suspendMediaWhenInactive = false
            applyDns(this)
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
    val customTabsUseCases by lazy { CustomTabsUseCases(store, sessionUseCases.loadUrl) }
    val downloadsUseCases by lazy { DownloadsUseCases(store, downloadFileUtils) }
    val contextMenuUseCases by lazy { ContextMenuUseCases(store) }

    val historyRecorder by lazy {
        HistoryRecorder(store, database.history(), applicationScope) { contextId -> profiles.byContextId(contextId)?.id }
    }

    val extensions by lazy { ExtensionManager(context, engine, store, settings, applicationScope) }

    val mediaSessionFeature by lazy { MediaSessionFeature(context, MediaSessionService::class.java, store) }

    // PWA (ana ekrana yüklenen web uygulamaları)
    val manifestStorage by lazy { ManifestStorage(context) }
    val webAppShortcutManager by lazy { WebAppShortcutManager(context, client, manifestStorage) }
    val webAppUseCases by lazy { WebAppUseCases(context, store, webAppShortcutManager) }
    val webAppIntentProcessor by lazy {
        WebAppIntentProcessor(store, customTabsUseCases.addWebApp, sessionUseCases.loadUrl, manifestStorage)
    }

    val vpn by lazy { VpnManager(context, applicationScope) }
    val webApkInstaller by lazy { com.gtarayici.browser.webapk.WebApkInstaller(context, this) }
    val youtube by lazy { YouTubeDownloader(context, this) }

    /** Ayar ekranındaki değişiklikleri çalışan motora anında uygular. */
    fun applyPrivacySettings() {
        engine.settings.trackingProtectionPolicy = trackingPolicy(settings.strictTracking.value)
        engine.settings.httpsOnlyMode = httpsMode(settings.httpsOnly.value)
        engine.settings.globalPrivacyControlEnabled = settings.globalPrivacyControl.value
        applyDns(engine.settings)
        applyGeckoPrefs()
    }

    /** Motor ayarlarında karşılığı olmayan Gecko tercihleri (çerez pencereleri vb.). */
    fun applyGeckoPrefs() {
        val cookieMode = if (settings.cookieBannerReject.value) COOKIE_BANNER_REJECT else COOKIE_BANNER_DISABLED
        GeckoPrefs.set("cookiebanners.service.mode", cookieMode)
        GeckoPrefs.set("cookiebanners.service.mode.privateBrowsing", cookieMode)
        // WebRTC'nin VPN/vekil sunucuyu atlayıp gerçek IP'yi sızdırmasını önler.
        GeckoPrefs.set("media.peerconnection.ice.proxy_only_if_behind_proxy", true)
        GeckoPrefs.set("media.peerconnection.ice.default_address_only", true)
        // Arka planda (ör. ekran kapalıyken) oynatma listesindeki sonraki video başlayabilsin.
        GeckoPrefs.set("media.block-autoplay-until-in-foreground", false)
    }

    private fun applyDns(target: mozilla.components.concept.engine.Settings) {
        val url = settings.dohUrl()
        if (url == null) {
            target.dohSettingsMode = Engine.DohSettingsMode.DEFAULT
        } else {
            target.dohProviderUrl = url
            target.dohSettingsMode =
                if (settings.dnsStrict.value) Engine.DohSettingsMode.MAX else Engine.DohSettingsMode.INCREASED
        }
    }

    private fun trackingPolicy(strict: Boolean) =
        if (strict) TrackingProtectionPolicy.strict() else TrackingProtectionPolicy.recommended()

    private fun httpsMode(enabled: Boolean) =
        if (enabled) Engine.HttpsOnlyMode.ENABLED else Engine.HttpsOnlyMode.DISABLED

    private companion object {
        const val COOKIE_BANNER_DISABLED = 0
        const val COOKIE_BANNER_REJECT = 1
    }
}
