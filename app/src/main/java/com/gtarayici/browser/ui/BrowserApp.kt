package com.gtarayici.browser.ui

import android.app.Activity
import android.view.View
import androidx.activity.compose.BackHandler
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
import com.gtarayici.browser.ui.components.YouTubeDownloadSheet
import com.gtarayici.browser.ui.components.observe
import com.gtarayici.browser.ui.components.keepSizeDuringPip
import com.gtarayici.browser.ui.screens.BookmarksScreen
import com.gtarayici.browser.ui.screens.DownloadsScreen
import com.gtarayici.browser.ui.screens.ExtensionPopupSheet
import com.gtarayici.browser.ui.screens.ExtensionPrompts
import com.gtarayici.browser.ui.screens.ExtensionsScreen
import com.gtarayici.browser.ui.screens.HistoryScreen
import com.gtarayici.browser.ui.screens.HomeScreen
import com.gtarayici.browser.ui.screens.InstallAppDialog
import com.gtarayici.browser.ui.screens.LockScreen
import com.gtarayici.browser.ui.screens.ProfileSwitcherSheet
import com.gtarayici.browser.ui.screens.ProfilesScreen
import com.gtarayici.browser.ui.screens.SearchScreen
import com.gtarayici.browser.ui.screens.SettingsScreen
import com.gtarayici.browser.ui.screens.TabsScreen
import com.gtarayici.browser.ui.screens.VpnScreen
import com.gtarayici.browser.ui.theme.GTarayiciTheme
import com.gtarayici.browser.ui.theme.isDarkTheme
import com.gtarayici.browser.vpn.VpnStatus
import com.gtarayici.browser.youtube.YouTubeDownloader
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
)

@Composable
fun BrowserApp(
    controller: BrowserController,
    engineView: View,
    fullscreen: Boolean,
    pictureInPicture: Boolean = false,
    /** Yerel video PiP görünümü: küçük pencerede sayfanın üstünde yalnızca video gösterilir. */
    pipView: View? = null,
    nativePip: Boolean = false,
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
                secure = when (it.content.securityInfo) {
                    is SecurityInfo.Secure -> true
                    is SecurityInfo.Insecure -> false
                    else -> null
                },
                canGoBack = it.content.canGoBack,
                canGoForward = it.content.canGoForward,
                desktopMode = it.content.desktopMode,
                hasManifest = it.content.webAppManifest != null,
            )
        }
    }
    val allTabs by store.observe { it.tabs }
    val selectedTabId by store.observe { it.selectedTabId }
    val profileTabs = allTabs.filter { it.contextId == profile.contextId }
    val bookmarks by remember(profile.id) { components.database.bookmarks().all(profile.id) }.collectAsState(initial = emptyList())

    // Seçili sekme başka bir profile aitse bu profilde görünür sekme yok sayılır.
    val current = selected?.takeIf { it.contextId == profile.contextId }
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
            Column(Modifier.fillMaxSize().then(chrome).imePadding()) {
                Box(Modifier.weight(1f)) {
                    // Motor görünümü her zaman yerinde kalır; aksi halde bağlı özellikler serbest bırakılır.
                    // PiP sırasında da gizlenmez: sayfa çizilmeye devam etmeli (YouTube oynatıcısı buna bağlı).
                    AndroidView(factory = { engineView }, modifier = Modifier.keepSizeDuringPip(pictureInPicture))
                    if (showHome && !pictureInPicture) {
                        HomeScreen(
                            private = private,
                            shortcuts = bookmarks,
                            onSearchClick = { controller.open(Overlay.SEARCH) },
                            onOpen = { controller.loadUrl(it) },
                        )
                    }
                }
                if (!fullscreen) {
                    if (controller.findInPageOpen && pageUrl != null) {
                        FindInPageBar(onClose = { controller.closeFindInPage() })
                    }
                    BottomToolbar(
                        state = ToolbarState(
                            displayUrl = pageUrl?.let(UrlInput::displayUrl).orEmpty(),
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

            val overlayModifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding()
                .imePadding()
            when (controller.overlay) {
                Overlay.NONE -> Unit
                Overlay.SEARCH -> Box(overlayModifier) {
                    SearchScreen(
                        initialUrl = pageUrl.orEmpty(),
                        private = private,
                        engine = searchEngine,
                        onSubmit = { controller.submit(it) },
                        onOpenUrl = { controller.loadUrl(it) },
                        onDismiss = { controller.closeOverlay() },
                    )
                }
                Overlay.TABS -> Box(overlayModifier) {
                    TabsScreen(
                        tabs = profileTabs,
                        selectedTabId = selectedTabId,
                        initialPrivate = private,
                        onSelect = { controller.selectTab(it) },
                        onClose = { controller.closeTab(it) },
                        onCloseAll = { controller.closeAllTabs(it) },
                        onNewTab = { controller.newTab(it) },
                        onBack = { controller.closeOverlay() },
                    )
                }
                Overlay.HISTORY -> Box(overlayModifier) {
                    HistoryScreen(onOpen = { controller.loadUrl(it) }, onBack = { controller.closeOverlay() })
                }
                Overlay.BOOKMARKS -> Box(overlayModifier) {
                    BookmarksScreen(onOpen = { controller.loadUrl(it) }, onBack = { controller.closeOverlay() })
                }
                Overlay.DOWNLOADS -> Box(overlayModifier) {
                    DownloadsScreen(onBack = { controller.closeOverlay() })
                }
                Overlay.SETTINGS -> Box(overlayModifier) {
                    SettingsScreen(
                        onClearData = { controller.clearBrowsingData() },
                        onBack = { controller.closeOverlay() },
                    )
                }
                Overlay.PROFILES -> Box(overlayModifier) {
                    ProfilesScreen(controller, onBack = { controller.closeOverlay() })
                }
                Overlay.EXTENSIONS -> Box(overlayModifier) {
                    ExtensionsScreen(controller, onBack = { controller.closeOverlay() })
                }
                Overlay.VPN -> Box(overlayModifier) {
                    VpnScreen(onBack = { controller.closeOverlay() })
                }
            }

            if (lockKey != null && !pictureInPicture) {
                val title = if (lockKey == AppLock.PRIVATE) {
                    stringResource(R.string.lock_title_private)
                } else {
                    stringResource(R.string.lock_title_profile, profile.name)
                }
                // Kilit ekranı açılınca doğrulama istemi kendiliğinden gelir.
                LaunchedEffect(lockKey) { controller.unlock(lockKey, title) }
                Box(overlayModifier) { LockScreen(title, lockKey) { controller.unlock(lockKey, title) } }
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
                    ),
                    controller = controller,
                )
            }
            if (controller.profileSwitcherOpen) ProfileSwitcherSheet(controller)
            if (controller.installDialogOpen) InstallAppDialog(controller, installable = current?.hasManifest == true)
            controller.youtubeUrl?.let { url -> YouTubeDownloadSheet(url) { controller.youtubeUrl = null } }
            ExtensionPrompts()
            ExtensionPopupSheet()

            BackHandler(enabled = controller.overlay != Overlay.NONE) { controller.closeOverlay() }

            // Küçük pencerede yalnızca video (Gecko'nun kopyaladığı PiP oturumu) gösterilir.
            if (pipView != null) {
                AndroidView(factory = { pipView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
