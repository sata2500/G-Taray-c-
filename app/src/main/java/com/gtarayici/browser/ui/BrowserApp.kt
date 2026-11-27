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
import androidx.compose.ui.Modifier
import com.gtarayici.browser.ui.navigation.OverlayHost
import com.gtarayici.browser.ui.feedback.MessageHost
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.gtarayici.browser.components
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.security.AppLock
import com.gtarayici.browser.ui.components.BottomToolbar
import com.gtarayici.browser.ui.components.FindInPageBar
import com.gtarayici.browser.ui.components.MenuState
import com.gtarayici.browser.ui.components.ToolbarState
import com.gtarayici.browser.ui.components.VpnIndicator
import com.gtarayici.browser.ui.components.ReaderIndicator
import com.gtarayici.browser.ui.components.ToolbarActions
import com.gtarayici.browser.ui.components.SwipeTab
import com.gtarayici.browser.ui.components.TabSwipeLayer
import com.gtarayici.browser.ui.components.TabSwipeState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.gtarayici.browser.translate.TranslateOfferBar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gtarayici.browser.ui.components.observe
import com.gtarayici.browser.ui.components.keepSizeDuringPip
import com.gtarayici.browser.ui.screens.HomeScreen
import com.gtarayici.browser.ui.screens.OnboardingScreen
import com.gtarayici.browser.ui.screens.HomeSite
import com.gtarayici.browser.ui.screens.topSites
import com.gtarayici.browser.ui.theme.GTarayiciTheme
import com.gtarayici.browser.ui.theme.isDarkTheme
import com.gtarayici.browser.vpn.VpnStatus
import com.gtarayici.browser.youtube.YouTubeDownloader
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    val crashed: Boolean = false,
)

@Composable
fun BrowserApp(
    controller: BrowserController,
    engineView: View,
    fullscreen: Boolean,
    pictureInPicture: Boolean = false,
    /** Yerel video PiP görünümü: küçük pencerede sayfanın üstünde yalnızca video gösterilir. */
    pipView: View? = null,
    /** Uygulama içi küçük pencere (başka sekmeye ya da bir menüye geçince video burada sürer). */
    miniPlayer: com.gtarayici.browser.media.InAppMiniPlayer? = null,
) {
    val context = LocalContext.current
    val components = context.components
    val store = components.store
    val themeMode by components.settings.theme.collectAsState()
    val searchEngine by components.settings.searchEngine.collectAsState()
    val profile by components.profiles.active.collectAsState()
    val profiles by components.profiles.profiles.collectAsState()
    val vpnStatus by components.vpn.status.collectAsState()
    val vpnPrivateOnly by components.vpn.privateOnly.collectAsState()
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
                crashed = it.engineState.crashed,
                translated = it.translationsState.isTranslated,
            )
        }
    }
    val allTabs by store.observe { it.tabs }
    val selectedTabId by store.observe { it.selectedTabId }
    val translationSupported by store.observe { it.translationEngine.isEngineSupported == true }
    val toolbarSwipe by components.settings.toolbarSwipeTabs.collectAsState()
    val hideToolbar by components.settings.hideToolbarOnScroll.collectAsState()
    val tabsRestored by components.tabsRestored.collectAsState()
    val messenger = components.messenger
    ClosedTabsUndo(controller)
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

    ContextualHints(
        pageUrl = pageUrl,
        loading = current?.loading != false,
        tabCount = profileTabs.size,
        toolbarSwipe = toolbarSwipe,
        locked = lockKey != null,
    )

    // Araç çubuğunu kaydırınca sayfa parmakla birlikte kayar, komşu sekme yandan gelir.
    val swipeScope = rememberCoroutineScope()
    val latestTabs by rememberUpdatedState(profileTabs.filter { it.content.private == private })
    val latestSelected by rememberUpdatedState(selectedTabId)
    val tabSwipe = remember(controller) {
        TabSwipeState(
            scope = swipeScope,
            thumbnails = components.tabThumbnails,
            neighbors = {
                val tabs = latestTabs
                val index = tabs.indexOfFirst { it.id == latestSelected }
                fun at(i: Int) = tabs.getOrNull(i)?.let { SwipeTab(it.id, it.content.title, it.content.url, it.content.private) }
                if (index < 0) Triple(null, null, null) else Triple(at(index - 1), at(index), at(index + 1))
            },
            engineView = { controller.dynamicToolbar.engineView },
            select = { components.tabsUseCases.selectTab(it) },
        )
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
                com.gtarayici.browser.ui.components.LoginPickerBar(controller.loginPicker)
                com.gtarayici.browser.ui.components.SpeakerBar(components.speaker)
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
                        // VPN yalnızca gizli sekmelerdeyse normal sekmede "kapalı" görünür.
                        vpn = if (vpnPrivateOnly && !private) VpnIndicator.OFF else when (vpnStatus) {
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
                        tabSwipe = tabSwipe.takeIf { toolbarSwipe && pageUrl != null && !pictureInPicture },
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
                Box(Modifier.weight(1f).clipToBounds().onSizeChanged { tabSwipe.width = it.width.toFloat() }) {
                    // Motor görünümü her zaman yerinde kalır; aksi halde bağlı özellikler serbest bırakılır.
                    // PiP sırasında da gizlenmez: sayfa çizilmeye devam etmeli (YouTube oynatıcısı buna bağlı).
                    AndroidView(factory = { engineView }, modifier = Modifier.keepSizeDuringPip(pictureInPicture))
                    // Açılışta sekmeler geri yüklenirken (eklentiler/VPN beklenirken) ana sayfa bir anlığına görünmesin.
                    if (showHome && !pictureInPicture && tabsRestored) {
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
                                onOpenBook = { book ->
                                    components.applicationScope.launch { components.library.opened(book) }
                                    controller.openViewer(listOf(book.source(context)))
                                },
                            )
                        }
                    }
                    if (current?.crashed == true && !pictureInPicture) {
                        com.gtarayici.browser.ui.components.CrashedTabNotice(
                            onRestore = { selectedTabId?.let { components.sessionUseCases.crashRecovery.invoke(listOf(it)) } },
                        )
                    }
                    if (!pictureInPicture) TabSwipeLayer(tabSwipe)
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
                OverlayPage(page, controller, pageUrl, private, searchEngine, profileTabs, selectedTabId)
            }

            if (controller.onboarding && !pictureInPicture) {
                OnboardingScreen(
                    onDone = { controller.finishOnboarding() },
                    onGuide = {
                        controller.finishOnboarding()
                        controller.open(Overlay.GUIDE)
                    },
                )
            }

            CrashPrompt(controller, blocked = lockKey != null || controller.onboarding || pictureInPicture)

            if (lockKey != null && !pictureInPicture) LockOverlay(lockKey, profile.name, controller)

            BrowserSheets(
                controller,
                menuState = {
                    MenuState(
                        hasPage = pageUrl != null,
                        canGoBack = current?.canGoBack == true,
                        canGoForward = current?.canGoForward == true,
                        loading = current?.loading == true,
                        bookmarked = bookmarked,
                        desktopMode = current?.desktopMode == true,
                        isYouTubeVideo = pageUrl?.let(YouTubeDownloader::isVideoPage) == true,
                        hasVideo = components.store.state.selectedTab?.mediaSessionState != null,
                        installableWebApp = current?.hasManifest == true && components.webAppUseCases.isInstallable(),
                        extensionActions = emptyList(),
                        readerActive = current?.readerActive == true,
                        canTranslate = translationSupported && current?.readerActive != true,
                        url = pageUrl.orEmpty(),
                        title = components.store.state.selectedTab?.content?.title.orEmpty(),
                        private = private,
                    )
                },
                installable = current?.hasManifest == true,
                selectedTabId = selectedTabId,
            )

            if (!pictureInPicture) {
                MessageHost(
                    messenger,
                    // Yüzen "Yeni sekme/Ekle" düğmesi olan sayfalarda mesaj düğmenin üstünde durur (M3: snackbar FAB'ı örtmez).
                    Modifier.align(Alignment.BottomCenter).systemBarsPadding().imePadding().padding(
                        bottom = if (controller.overlay in PAGES_WITH_FAB) 88.dp else 72.dp,
                    ),
                )
            }

            if (miniPlayer != null && !pictureInPicture && lockKey == null && !controller.onboarding) {
                com.gtarayici.browser.ui.components.MiniPlayerWindow(miniPlayer) { tabId ->
                    controller.closeOverlay()
                    components.tabsUseCases.selectTab(tabId)
                }
            }

            // Küçük pencerede yalnızca video (Gecko'nun kopyaladığı PiP oturumu) gösterilir.
            if (pipView != null) {
                AndroidView(factory = { pipView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Yüzen eylem düğmesi (AddFab) olan tam ekran sayfalar. */
private val PAGES_WITH_FAB = setOf(Overlay.TABS, Overlay.VPN, Overlay.PROFILES)
