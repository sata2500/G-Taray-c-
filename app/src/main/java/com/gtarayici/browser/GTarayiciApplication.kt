package com.gtarayici.browser

import androidx.core.content.edit
import android.app.Application
import android.content.Context
import android.os.Looper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
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

        // Hata ayıklama sürümünde ana iş parçacığında disk/ağ erişimi ve sızıntılar günlüğe yazılır (logcat: StrictMode).
        // Açılıştan sonra (ilk kare çizildikten sonra) açılır: açılıştaki ihlallerin neredeyse tamamı Gecko/Mozilla
        // kitaplıklarından gelir ve günlüğe yazmak açılışı ~1,2 sn yavaşlatıp ölçümleri bozuyordu. İlk "boşta" anı
        // etkinlik açılmadan önce gelebildiği için süreli gecikme kullanılır.
        if (BuildConfig.DEBUG) {
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                android.os.StrictMode.setThreadPolicy(
                    android.os.StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build(),
                )
                android.os.StrictMode.setVmPolicy(
                    android.os.StrictMode.VmPolicy.Builder()
                        .detectLeakedClosableObjects()
                        .detectLeakedRegistrationObjects()
                        .detectActivityLeaks()
                        .penaltyLog()
                        .build(),
                )
            }, STRICT_MODE_DELAY_MS)
        }

        // Tanılama kaydı ve çökme yakalayıcı her şeyden önce kurulur ki açılıştaki sorunlar da kaydedilsin.
        com.gtarayici.browser.diagnostics.Diagnostics.init(
            java.io.File(filesDir, "diagnostics/log.txt"),
            enabled = { components.settings.diagnosticsLog.value },
        )
        components.crashReporter.install()
        com.gtarayici.browser.diagnostics.Diagnostics.log("uygulama açıldı · ${BuildConfig.VERSION_NAME}", "uygulama")

        components.engine.warmUp()
        components.applyGeckoPrefs()
        components.dataSaver.start()
        components.siteSettingsApplier.start()
        com.gtarayici.browser.media.BackgroundAutoplay(
            components.store, components.permissionStorage, components.dataSaver, components.applicationScope,
        ).start(this)
        components.applicationScope.launch { components.settings.theme.collect { components.applyColorScheme() } }
        initExtensions()
        components.vpn.restore()
        components.vpnNotifier.start()
        com.gtarayici.browser.media.BackgroundPlayback(this, components.store, components.settings, components.applicationScope).start()
        reloadFailedPagesOnVpnConnect()
        components.mediaSessionFeature.start()
        components.historyRecorder.start()
        com.gtarayici.browser.diagnostics.startDiagnosticsWatchers(components)
        com.gtarayici.browser.diagnostics.HealthWatchers.start(this, components.store, components.applicationScope)
        com.gtarayici.browser.ui.feedback.startNetworkMonitor(
            this,
            com.gtarayici.browser.ui.feedback.NetworkStatusReporter(components.messenger, components.applicationScope),
        )
        com.gtarayici.browser.downloads.DownloadCompletionNotifier(components.store, components.messenger, components.applicationScope) {
            components.downloadFileUtils.openFile(fileName = it.fileName, directoryPath = it.directoryPath, contentType = it.contentType)
        }.start()
        restoreTabs()
        // Açılışı geciktirmemek için acil olmayan işler ilk ekran çizildikten sonra (ana iş parçacığı boşken) yapılır.
        Looper.myQueue().addIdleHandler {
            components.downloadsUseCases.restoreDownloads()
            // Önceki oturumda yarım kalan indirmeler "başarısız" olarak gösterilir; "Yeniden dene" ile sürdürülür.
            components.applicationScope.launch {
                kotlinx.coroutines.delay(1_500)
                components.downloadQueue.markInterrupted()
            }
            // Önceki oturumda yarım kalan, kasaya gidecek indirmelerin şifresiz geçici parçaları silinir.
            components.applicationScope.launch(Dispatchers.IO) { components.vault.cleanIncoming() }
            components.applicationScope.launch(Dispatchers.IO) {
                components.manifestStorage.warmUpScopes(System.currentTimeMillis())
            }
            allowMediaSiteAutoplay()
            com.gtarayici.browser.ui.AppShortcuts.publish(this)
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
        components.extensions.onDarkReaderFailed = {
            components.messenger.error(com.gtarayici.browser.ui.feedback.UiText.res(R.string.dark_reader_failed))
        }
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
            components.extensions.onHelperReady = { components.resourceScanner.attach(it) }
            components.extensions.start()
        } catch (e: UnsupportedOperationException) {
            Logger.error("Eklenti desteği başlatılamadı", e)
        }
    }

    /** VPN (yeniden) bağlanınca, bağlantı hatası gösteren sekmeler kendiliğinden yenilenir. */
    private fun reloadFailedPagesOnVpnConnect() {
        components.applicationScope.launch {
            components.vpn.status
                .map { it is com.gtarayici.browser.vpn.VpnStatus.Connected }
                .distinctUntilChanged()
                .collect { connected ->
                    if (!connected) return@collect
                    val failed = components.errorPages.failedUrls.value
                    (components.store.state.tabs + components.store.state.customTabs)
                        .filter { it.content.url in failed && it.engineState.engineSession != null }
                        .forEach { components.sessionUseCases.reload(it.id) }
                }
        }
    }

    private fun restoreTabs() {
        components.applicationScope.launch {
            // Sekmeler, reklam engelleyici yüklenince ve (açık bırakıldıysa) VPN bağlanınca geri yüklenir; böylece ilk
            // sayfa reklamlarla ya da "vekil bağlantısı reddedildi" hatasıyla açılmaz. Beklemenin üst sınırı vardır.
            val started = android.os.SystemClock.uptimeMillis()
            components.extensions.awaitLoaded(EXTENSIONS_WAIT_MS)
            if (components.vpn.status.value == com.gtarayici.browser.vpn.VpnStatus.Connecting) {
                kotlinx.coroutines.withTimeoutOrNull(VPN_WAIT_MS) {
                    components.vpn.status.first { it != com.gtarayici.browser.vpn.VpnStatus.Connecting }
                }
            }
            val waited = android.os.SystemClock.uptimeMillis() - started
            if (waited > 300) com.gtarayici.browser.diagnostics.Diagnostics.log("sekmeler $waited ms beklendi (eklentiler/VPN)", "açılış")
            try {
                components.tabsUseCases.restore(components.sessionStorage)
            } finally {
                components.tabsRestored.value = true
            }
            components.tabGroups.start()
            components.sessionStorage
                .autoSave(components.store)
                .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
                .whenGoingToBackground()
                .whenSessionsChange()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Telefonun yazı ölçeği değişirse sayfalardaki yazı boyutu da güncellenir.
        if (isMainProcess()) components.siteSettingsApplier.onConfigurationChanged()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (isMainProcess()) {
            components.store.dispatch(SystemAction.LowMemoryAction(level))
            components.icons.onTrimMemory(level)
        }
    }
}

private const val EXTENSIONS_WAIT_MS = 3_000L
private const val VPN_WAIT_MS = 8_000L

private val MEDIA_SITES = listOf(
    "https://www.youtube.com",
    "https://m.youtube.com",
    "https://music.youtube.com",
)

val Context.components: Components
    get() = (applicationContext as GTarayiciApplication).components

/** Hata ayıklama sürümünde StrictMode, açılış ölçümünü etkilemesin diye bu kadar sonra açılır. */
private const val STRICT_MODE_DELAY_MS = 5_000L
