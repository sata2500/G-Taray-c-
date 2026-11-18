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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.gtarayici.browser.components
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.components.BottomToolbar
import com.gtarayici.browser.ui.components.MenuSheet
import com.gtarayici.browser.ui.components.MenuState
import com.gtarayici.browser.ui.components.ToolbarState
import com.gtarayici.browser.ui.components.observe
import com.gtarayici.browser.ui.screens.BookmarksScreen
import com.gtarayici.browser.ui.screens.DownloadsScreen
import com.gtarayici.browser.ui.screens.HistoryScreen
import com.gtarayici.browser.ui.screens.HomeScreen
import com.gtarayici.browser.ui.screens.SearchScreen
import com.gtarayici.browser.ui.screens.SettingsScreen
import com.gtarayici.browser.ui.screens.TabsScreen
import com.gtarayici.browser.ui.theme.GTarayiciTheme
import com.gtarayici.browser.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.flowOf
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SecurityInfo

/** Seçili sekmenin arayüzde ihtiyaç duyulan özeti. */
private data class TabSummary(
    val url: String,
    val private: Boolean,
    val loading: Boolean,
    val progress: Int,
    val secure: Boolean?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val desktopMode: Boolean,
)

@Composable
fun BrowserApp(controller: BrowserController, engineView: View, fullscreen: Boolean) {
    val components = LocalContext.current.components
    val store = components.store
    val themeMode by components.settings.theme.collectAsState()
    val searchEngine by components.settings.searchEngine.collectAsState()

    val tab by store.observe { state ->
        state.selectedTab?.let {
            TabSummary(
                url = it.content.url,
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
            )
        }
    }
    val allTabs by store.observe { it.tabs }
    val selectedTabId by store.observe { it.selectedTabId }
    val bookmarks by remember { components.database.bookmarks().all() }.collectAsState(initial = emptyList())

    val current = tab
    val private = current?.private ?: controller.privateMode
    val showHome = current == null || current.url == "about:blank"
    val pageUrl = current?.url?.takeUnless { showHome }
    val bookmarked by remember(pageUrl) {
        pageUrl?.let { components.database.bookmarks().isBookmarked(it) } ?: flowOf(false)
    }.collectAsState(initial = false)

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
                    AndroidView(factory = { engineView }, modifier = Modifier.fillMaxSize())
                    if (showHome) {
                        HomeScreen(
                            private = private,
                            shortcuts = bookmarks,
                            onSearchClick = { controller.open(Overlay.SEARCH) },
                            onOpen = { controller.loadUrl(it) },
                        )
                    }
                }
                if (!fullscreen) {
                    BottomToolbar(
                        state = ToolbarState(
                            displayUrl = pageUrl?.let(UrlInput::displayUrl).orEmpty(),
                            loading = current?.loading == true,
                            progress = current?.progress ?: 0,
                            secure = current?.secure,
                            tabCount = allTabs.count { it.content.private == private },
                            private = private,
                        ),
                        onUrlClick = { controller.open(Overlay.SEARCH) },
                        onReloadOrStop = { controller.reloadOrStop() },
                        onTabsClick = { controller.open(Overlay.TABS) },
                        onMenuClick = { controller.menuOpen = true },
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
                        tabs = allTabs,
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
            }

            if (controller.menuOpen) {
                MenuSheet(
                    state = MenuState(
                        hasPage = pageUrl != null,
                        canGoBack = current?.canGoBack == true,
                        canGoForward = current?.canGoForward == true,
                        loading = current?.loading == true,
                        bookmarked = bookmarked,
                        desktopMode = current?.desktopMode == true,
                    ),
                    controller = controller,
                )
            }

            BackHandler(enabled = controller.overlay != Overlay.NONE) { controller.closeOverlay() }
        }
    }
}
