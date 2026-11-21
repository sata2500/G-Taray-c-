package com.gtarayici.browser.ui

import android.app.Activity
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import com.gtarayici.browser.ui.navigation.OverlayHost
import com.gtarayici.browser.ui.feedback.Hint
import com.gtarayici.browser.ui.feedback.HintRules
import com.gtarayici.browser.ui.feedback.Message
import com.gtarayici.browser.ui.feedback.MessageAction
import com.gtarayici.browser.ui.feedback.MessageHost
import com.gtarayici.browser.ui.feedback.UiText
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.security.AppLock
import com.gtarayici.browser.ui.components.BottomToolbar
import com.gtarayici.browser.ui.components.FindInPageBar
import com.gtarayici.browser.ui.components.MenuSheet
import com.gtarayici.browser.ui.components.MenuState
import com.gtarayici.browser.ui.components.ToolbarState
import com.gtarayici.browser.ui.components.VpnIndicator
import com.gtarayici.browser.ui.components.ReaderIndicator
import com.gtarayici.browser.ui.components.SiteInfoSheet
import com.gtarayici.browser.ui.components.ToolbarActions
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.gtarayici.browser.qr.QrScannerScreen
import com.gtarayici.browser.update.UpdateDialog
import com.gtarayici.browser.reader.ReaderControlsSheet
import com.gtarayici.browser.translate.TranslateOfferBar
import com.gtarayici.browser.translate.TranslateSheet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gtarayici.browser.ui.components.YouTubeDownloadSheet
import com.gtarayici.browser.ui.components.observe
import com.gtarayici.browser.ui.components.keepSizeDuringPip
import com.gtarayici.browser.ui.screens.BookmarksScreen
import com.gtarayici.browser.ui.screens.ReportScreen
import com.gtarayici.browser.ui.screens.DownloadsScreen
import com.gtarayici.browser.ui.screens.ExtensionPopupSheet
import com.gtarayici.browser.ui.screens.ExtensionPrompts
import com.gtarayici.browser.ui.screens.ExtensionsScreen
import com.gtarayici.browser.ui.screens.HistoryScreen
import com.gtarayici.browser.ui.screens.HomeScreen
import com.gtarayici.browser.ui.screens.OnboardingScreen
import com.gtarayici.browser.ui.screens.HomeSite
import com.gtarayici.browser.ui.screens.topSites
import com.gtarayici.browser.ui.screens.InstallAppDialog
import com.gtarayici.browser.ui.screens.LockScreen
import com.gtarayici.browser.ui.screens.ProfileSwitcherSheet
import com.gtarayici.browser.ui.screens.ProfilesScreen
import com.gtarayici.browser.ui.screens.SearchScreen
import com.gtarayici.browser.ui.screens.settings.SettingsScreen
import com.gtarayici.browser.ui.screens.TabsScreen
import com.gtarayici.browser.ui.screens.VpnScreen
import com.gtarayici.browser.ui.theme.GTarayiciTheme
import com.gtarayici.browser.ui.theme.isDarkTheme
import com.gtarayici.browser.vpn.VpnStatus
import com.gtarayici.browser.youtube.YouTubeDownloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SecurityInfo

/** Seçili sekmenin arayüzde ihtiyaç duyulan özeti. */
private data class TabSummary(
    val url: String,
    val contextId: String?,
    val private: Boolean,
    val loading: Boolean,
    val progress: Int,
    val secure: Boolean?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val desktopMode: Boolean,
    val hasManifest: Boolean,
    val readerable: Boolean,
    val readerActive: Boolean,
    /** Okuma görünümündeyken asıl sayfanın adresi */
    val readerUrl: String?,
    val offerTranslate: Boolean,
    val translated: Boolean,
)

@Composable
fun BrowserApp(
    controller: BrowserController,
    engineView: View,
    fullscreen: Boolean,
    pictureInPicture: Boolean = false,
    /** Yerel video PiP görünümü: küçük pencerede sayfanın üstünde yalnızca video gösterilir. */
    pipView: View? = null,
) {
    val components = LocalContext.current.components
    val store = components.store
    val themeMode by components.settings.theme.collectAsState()
    val searchEngine by components.settings.searchEngine.collectAsState()
    val profile by components.profiles.active.collectAsState()
    val profiles by components.profiles.profiles.collectAsState()
    val vpnStatus by components.vpn.status.collectAsState()
    val lockPrivate by components.settings.lockPrivateTabs.collectAsState()

    val selected by store.observe { state ->
        state.selectedTab?.let {
            TabSummary(
                url = it.content.url,
                contextId = it.contextId,
                private = it.content.private,
                loading = it.content.loading,
                progress = it.content.progress,
                secure = when {
                    // Okuma görünümü yerel bir eklenti sayfasıdır ve motor onu "güvensiz" sayar; gösterge, içeriğin
                    // çekildiği özgün adrese göre verilir (yoksa güvenli bir sitede uyarı üçgeni görünür).
                    it.readerState.active && it.readerState.activeUrl != null ->
                        it.readerState.activeUrl!!.startsWith("https://")
                    it.content.securityInfo is SecurityInfo.Secure -> true
                    it.content.securityInfo is SecurityInfo.Insecure -> false
                    else -> null
                },
                canGoBack = it.content.canGoBack,
                canGoForward = it.content.canGoForward,
                desktopMode = it.content.desktopMode,
                hasManifest = it.content.webAppManifest != null,
                readerable = it.readerState.readerable,
                readerActive = it.readerState.active,
                readerUrl = it.readerState.activeUrl,
                offerTranslate = it.translationsState.isOfferTranslate,
                translated = it.translationsState.isTranslated,
            )
        }
    }
    val allTabs by store.observe { it.tabs }
    val selectedTabId by store.observe { it.selectedTabId }
    val translationSupported by store.observe { it.translationEngine.isEngineSupported == true }
    val toolbarSwipe by components.settings.toolbarSwipeTabs.collectAsState()
    val hideToolbar by components.settings.hideToolbarOnScroll.collectAsState()
    // Kapatılan sekmeler: birkaç saniye "Geri al" çubuğu gösterilir.
    val undo by store.observe { it.undoHistory.tag to it.undoHistory.tabs.size }
    val messenger = components.messenger
    val closedOne = stringResource(R.string.tab_closed)
    val closedMany = pluralStringResource(R.plurals.tabs_closed, undo.second, undo.second)
    LaunchedEffect(undo) {
        // Önceki "Geri al" mesajı artık geçersiz: kaldırılır, gerekiyorsa güncel sayıyla yenisi gösterilir.
        messenger.dismissKey(UNDO_TABS_KEY)
        if (undo.second == 0) return@LaunchedEffect
        messenger.show(
            Message(
                UiText.Plain(if (undo.second == 1) closedOne else closedMany),
                action = MessageAction(UiText.res(R.string.action_undo)) { controller.undoCloseTabs() },
                key = UNDO_TABS_KEY,
            ),
        )
    }
    var dismissedOffer by remember { mutableStateOf<String?>(null) }
    val profileTabs = allTabs.filter { it.contextId == profile.contextId }
    val bookmarks by remember(profile.id) { components.database.bookmarks().all(profile.id) }.collectAsState(initial = emptyList())
    val history by remember(profile.id) { components.database.history().recent(profile.id, 500) }.collectAsState(initial = emptyList())
    val hiddenSites by controller.hiddenSites.collectAsState()
    // Hata sayfasında çeviri önerisi ve okuma simgesi gösterilmez (şablonun dili belirsiz, okunacak makale yok).
    val failedUrls by components.errorPages.failedUrls.collectAsState()
    val frequent = remember(history, hiddenSites, failedUrls) { topSites(history, hiddenSites, failed = failedUrls) }
    // Seçili sekme başka bir profile aitse bu profilde görünür sekme yok sayılır.
    val current = selected?.takeIf { it.contextId == profile.contextId }
        ?.let { if (it.url in failedUrls) it.copy(offerTranslate = false, readerable = false) else it }
    val private = current?.private ?: controller.privateMode
    val showHome = current == null || current.url == "about:blank"
    val pageUrl = current?.url?.takeUnless { showHome }
    val bookmarked by remember(pageUrl, profile.id) {
        pageUrl?.let { components.database.bookmarks().isBookmarked(profile.id, it) } ?: flowOf(false)
    }.collectAsState(initial = false)

    // Kilit: kilitli profil veya (ayar açıksa) gizli sekme görünürken kimlik doğrulama gerekir.
    val lockKey = when {
        profile.locked && !components.appLock.isUnlocked(AppLock.profileKey(profile.id)) -> AppLock.profileKey(profile.id)
        lockPrivate && private && current != null && !components.appLock.isUnlocked(AppLock.PRIVATE) -> AppLock.PRIVATE
        else -> null
    }

    // Bağlamsal ipuçları: özellik ilk kez anlamlı olduğunda bir kez (kilit ekranı açıkken hiç).
    val hints = components.hints
    val videoPageLoaded = pageUrl != null && current?.loading == false && YouTubeDownloader.isVideoPage(pageUrl)
    LaunchedEffect(videoPageLoaded, lockKey) {
        if (videoPageLoaded && lockKey == null) {
            delay(HINT_DELAY_MS)
            hints.offer(Hint.YouTubeVideo)
        }
    }
    val manyTabs = HintRules.manyTabs(profileTabs.size, toolbarSwipe)
    LaunchedEffect(manyTabs, lockKey) {
        if (manyTabs && lockKey == null) {
            delay(HINT_DELAY_MS)
            hints.offer(Hint.ManyTabs)
        }
    }

    val view = LocalView.current
    val darkChrome = private || isDarkTheme(themeMode)
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkChrome
            isAppearanceLightNavigationBars = !darkChrome
        }
    }

    GTarayiciTheme(mode = themeMode, private = private) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val chrome = if (fullscreen) Modifier else Modifier.systemBarsPadding()
            // Kaydırınca gizlenen adres çubuğu: çubuk sayfanın üstünde durur, motor görünümü tam boydur.
            val floatingBars = hideToolbar && !fullscreen
            val dynamic = controller.dynamicToolbar
            var toolbarHeight by remember { mutableIntStateOf(0) }
            val barsVisibleOnly = controller.findInPageOpen || showHome || pictureInPicture ||
                (current?.offerTranslate == true && current.translated.not() && dismissedOffer != pageUrl)
            SideEffect {
                dynamic.configure(enabled = floatingBars, heightPx = toolbarHeight)
                if (dynamic.locked != barsVisibleOnly) dynamic.locked = barsVisibleOnly
            }
            // Sayfa değişince veya yüklenmeye başlayınca çubuk yeniden görünür.
            LaunchedEffect(pageUrl, current?.loading, barsVisibleOnly) { dynamic.show() }
            val bars: @Composable () -> Unit = {
                if (controller.findInPageOpen && pageUrl != null) {
                    FindInPageBar(onClose = { controller.closeFindInPage() })
                }
                val tabId = selectedTabId
                if (tabId != null && pageUrl != null && current?.offerTranslate == true && current.translated.not() &&
                    dismissedOffer != pageUrl && !controller.translateOpen
                ) {
                    TranslateOfferBar(
                        tabId = tabId,
                        onOptions = { controller.openTranslate() },
                        onDismiss = { dismissedOffer = pageUrl },
                    )
                }
                Box(Modifier.onSizeChanged { toolbarHeight = it.height }) {
                BottomToolbar(
                    state = ToolbarState(
                        displayUrl = (current?.readerUrl?.takeIf { current.readerActive } ?: pageUrl)
                            ?.let(UrlInput::displayUrl).orEmpty(),
                        loading = current?.loading == true,
                        progress = current?.progress ?: 0,
                        secure = current?.secure,
                        tabCount = profileTabs.count { it.content.private == private },
                        private = private,
                        profile = profile.takeIf { profiles.size > 1 },
                        vpn = when (vpnStatus) {
                            is VpnStatus.Connected -> VpnIndicator.ON
                            is VpnStatus.Error -> VpnIndicator.ERROR
                            VpnStatus.Connecting -> VpnIndicator.ON
                            VpnStatus.Off -> VpnIndicator.OFF
                        },
                        reader = when {
                            current?.readerActive == true -> ReaderIndicator.ACTIVE
                            current?.readerable == true -> ReaderIndicator.AVAILABLE
                            else -> ReaderIndicator.NONE
                        },
                    ),
                    onReaderClick = { controller.toggleReader(current?.readerActive == true) },
                    actions = ToolbarActions(
                        onSwipeTab = if (toolbarSwipe && pageUrl != null) { delta -> controller.switchAdjacentTab(delta) } else null,
                        onNewTab = { controller.newTab(it) },
                        onCloseTab = { controller.closeCurrentTab() },
                        onCopyUrl = { controller.copyLink() },
                        onPasteAndGo = { controller.pasteAndGo() },
                        onSiteInfo = { controller.siteInfoOpen = true },
                    ),
                    onUrlClick = { controller.open(Overlay.SEARCH) },
                    onReloadOrStop = { controller.reloadOrStop() },
                    onTabsClick = { controller.open(Overlay.TABS) },
                    onMenuClick = { controller.menuOpen = true },
                    onProfileClick = { controller.profileSwitcherOpen = true },
                    onVpnClick = { controller.open(Overlay.VPN) },
                )
                }
            }
            // Tanıtım veya tam ekran bir pencere açıkken arkadaki tarayıcı arayüzü ekran okuyucuya görünmesin.
            val covered = controller.onboarding || controller.overlay != Overlay.NONE || lockKey != null
            Column(
                Modifier.fillMaxSize().then(chrome).imePadding()
                    .semantics { if (covered) hideFromAccessibility() },
            ) {
                Box(Modifier.weight(1f).clipToBounds()) {
                    // Motor görünümü her zaman yerinde kalır; aksi halde bağlı özellikler serbest bırakılır.
                    // PiP sırasında da gizlenmez: sayfa çizilmeye devam etmeli (YouTube oynatıcısı buna bağlı).
                    AndroidView(factory = { engineView }, modifier = Modifier.keepSizeDuringPip(pictureInPicture))
                    if (showHome && !pictureInPicture) {
                        Box(Modifier.padding(bottom = if (floatingBars) with(LocalDensity.current) { toolbarHeight.toDp() } else 0.dp)) {
                            HomeScreen(
                                private = private,
                                frequent = frequent,
                                bookmarks = bookmarks.map { HomeSite(it.url, it.title.ifBlank { UrlInput.host(it.url) }) },
                                onSearchClick = { controller.open(Overlay.SEARCH) },
                                onScanQr = { controller.openQrScanner() },
                                onOpen = { controller.loadUrl(it) },
                                onOpenInNewTab = { controller.loadUrl(it, inNewTab = true) },
                                onHideFrequent = { controller.hideFrequentSite(it.url) },
                                onRemoveBookmark = { controller.removeBookmark(it.url) },
                                onProtectionClick = { controller.open(Overlay.SETTINGS) },
                            )
                        }
                    }
                    if (floatingBars && !pictureInPicture) {
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .offset { IntOffset(0, dynamic.offset.roundToInt()) },
                        ) { bars() }
                    }
                }
                if (!fullscreen && !floatingBars) bars()
            }

            // Tam ekran sayfalar: tek, animasyonlu ve öngörülü geri hareketiyle uyumlu katman.
            OverlayHost(
                current = controller.overlay,
                none = Overlay.NONE,
                onBack = { controller.closeOverlay() },
                fullBleed = { it == Overlay.QR },
                // Kilit ekranı açıkken alttaki sayfa ekran okuyucuya görünmesin.
                modifier = Modifier.semantics { if (lockKey != null) hideFromAccessibility() },
            ) { page ->
                when (page) {
                    Overlay.NONE -> Unit
                    Overlay.SEARCH -> SearchScreen(
                        initialUrl = pageUrl.orEmpty(),
                        private = private,
                        engine = searchEngine,
                        onSubmit = { controller.submit(it) },
                        onOpenUrl = { controller.loadUrl(it) },
                        onDismiss = { controller.closeOverlay() },
                        onScanQr = { controller.openQrScanner() },
                    )
                    Overlay.QR -> QrScannerScreen(
                        onOpen = { controller.loadUrl(it) },
                        onSearch = { controller.submit(it) },
                        onBack = { controller.closeOverlay() },
                    )
                    Overlay.TABS -> TabsScreen(
                        tabs = profileTabs,
                        selectedTabId = selectedTabId,
                        initialPrivate = private,
                        onSelect = { controller.selectTab(it) },
                        onClose = { controller.closeTab(it) },
                        onCloseAll = { controller.closeAllTabs(it) },
                        onNewTab = { controller.newTab(it) },
                        onNewTabInGroup = { controller.newTabInGroup(it) },
                        onBack = { controller.closeOverlay() },
                    )
                    Overlay.HISTORY -> HistoryScreen(onOpen = { controller.loadUrl(it) }, onBack = { controller.closeOverlay() })
                    Overlay.BOOKMARKS -> BookmarksScreen(onOpen = { controller.loadUrl(it) }, onBack = { controller.closeOverlay() })
                    Overlay.DOWNLOADS -> DownloadsScreen(onBack = { controller.closeOverlay() })
                    Overlay.SETTINGS -> SettingsScreen(
                        onClearData = { controller.clearBrowsingData() },
                        onReport = { controller.openReport() },
                        onBack = { controller.closeOverlay() },
                    )
                    Overlay.PROFILES -> ProfilesScreen(controller, onBack = { controller.closeOverlay() })
                    Overlay.EXTENSIONS -> ExtensionsScreen(controller, onBack = { controller.closeOverlay() })
                    Overlay.VPN -> VpnScreen(onBack = { controller.closeOverlay() })
                    Overlay.REPORT -> controller.reportRequest?.let { request ->
                        ReportScreen(
                            request,
                            onOpenUrl = { controller.loadUrl(it, inNewTab = true) },
                            onBack = { controller.closeOverlay() },
                        )
                    }
                }
            }

            if (controller.onboarding && !pictureInPicture) {
                OnboardingScreen(onDone = { controller.finishOnboarding() })
            }

            // Önceki oturum çöktüyse bir kez sorulur (ayarlardan kapatılabilir; hiçbir şey kendiliğinden gönderilmez).
            var crashChecked by rememberSaveable { mutableStateOf(false) }
            val crashPromptOn by components.settings.crashPrompt.collectAsState()
            if (!crashChecked && crashPromptOn && lockKey == null && !controller.onboarding && !pictureInPicture &&
                remember { components.crashReporter.pending() != null }
            ) {
                AlertDialog(
                    onDismissRequest = { crashChecked = true },
                    icon = { Icon(Icons.Rounded.BugReport, null) },
                    title = { Text(stringResource(R.string.crash_prompt_title)) },
                    text = { Text(stringResource(R.string.crash_prompt_text)) },
                    confirmButton = {
                        TextButton(onClick = {
                            crashChecked = true
                            controller.openReport()
                        }) { Text(stringResource(R.string.crash_prompt_report)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            crashChecked = true
                            components.crashReporter.dismiss()
                        }) { Text(stringResource(R.string.crash_prompt_dismiss)) }
                    },
                )
            }

            if (lockKey != null && !pictureInPicture) {
                val title = if (lockKey == AppLock.PRIVATE) {
                    stringResource(R.string.lock_title_private)
                } else {
                    stringResource(R.string.lock_title_profile, profile.name)
                }
                // Kilit ekranı açılınca doğrulama istemi kendiliğinden gelir.
                LaunchedEffect(lockKey) { controller.unlock(lockKey, title) }
                // Surface: başlık ve metinler içerik rengini alır (önceden koyu zeminde siyah kalıyordu).
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.systemBarsPadding().imePadding()) { LockScreen(title, lockKey) { controller.unlock(lockKey, title) } }
                }
            }

            if (controller.menuOpen) {
                val extensionActions by store.observe { state ->
                    val tab = state.selectedTab
                    state.extensions.values
                        .filter { it.enabled && it.browserAction != null }
                        .map { ext ->
                            val base = ext.browserAction!!
                            val override = tab?.extensionState?.get(ext.id)?.browserAction
                            ext.id to (override?.let { base.copyWithOverride(it) } ?: base)
                        }
                }
                MenuSheet(
                    state = MenuState(
                        hasPage = pageUrl != null,
                        canGoBack = current?.canGoBack == true,
                        canGoForward = current?.canGoForward == true,
                        loading = current?.loading == true,
                        bookmarked = bookmarked,
                        desktopMode = current?.desktopMode == true,
                        isYouTubeVideo = pageUrl?.let(YouTubeDownloader::isVideoPage) == true,
                        installableWebApp = current?.hasManifest == true && components.webAppUseCases.isInstallable(),
                        extensionActions = extensionActions,
                        readerActive = current?.readerActive == true,
                        canTranslate = translationSupported && current?.readerActive != true,
                    ),
                    controller = controller,
                )
            }
            if (controller.profileSwitcherOpen) ProfileSwitcherSheet(controller)
            if (controller.installDialogOpen) InstallAppDialog(controller, installable = current?.hasManifest == true)
            controller.youtubeUrl?.let { url -> YouTubeDownloadSheet(url) { controller.youtubeUrl = null } }
            if (controller.translateOpen) {
                selectedTabId?.let { id -> TranslateSheet(id) { controller.translateOpen = false } }
            }
            if (controller.siteInfoOpen) {
                selectedTabId?.let { id -> SiteInfoSheet(id) { controller.siteInfoOpen = false } }
            }
            ReaderControlsSheet(controller.readerControls)
            UpdateDialog()
            ExtensionPrompts()
            ExtensionPopupSheet()


            if (!pictureInPicture) {
                MessageHost(
                    messenger,
                    Modifier.align(Alignment.BottomCenter).systemBarsPadding().imePadding().padding(bottom = 72.dp),
                )
            }

            // Küçük pencerede yalnızca video (Gecko'nun kopyaladığı PiP oturumu) gösterilir.
            if (pipView != null) {
                AndroidView(factory = { pipView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private const val UNDO_TABS_KEY = "geri-al-sekmeler"

/** Sayfa açıldıktan sonra ipucu gösterilmeden önceki bekleme (kullanıcı önce sayfayı görsün). */
private const val HINT_DELAY_MS = 2_500L
