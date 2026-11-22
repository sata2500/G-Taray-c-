package com.gtarayici.browser.ui

import com.gtarayici.browser.ui.components.PageDownloadsSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.reader.ReaderControlsSheet
import com.gtarayici.browser.search.SearchEngine
import com.gtarayici.browser.security.AppLock
import com.gtarayici.browser.translate.TranslateSheet
import com.gtarayici.browser.ui.screens.InstallAppDialog
import com.gtarayici.browser.ui.components.MenuSheet
import com.gtarayici.browser.ui.components.MenuState
import com.gtarayici.browser.ui.components.SiteInfoSheet
import com.gtarayici.browser.ui.components.YouTubeDownloadSheet
import com.gtarayici.browser.ui.components.observe
import com.gtarayici.browser.ui.feedback.Hint
import com.gtarayici.browser.ui.feedback.HintRules
import com.gtarayici.browser.ui.feedback.Message
import com.gtarayici.browser.ui.feedback.MessageAction
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.ui.screens.BookmarksScreen
import com.gtarayici.browser.ui.screens.DownloadsScreen
import com.gtarayici.browser.ui.screens.ExtensionPopupSheet
import com.gtarayici.browser.ui.screens.ExtensionPrompts
import com.gtarayici.browser.ui.screens.ExtensionsScreen
import com.gtarayici.browser.ui.screens.HistoryScreen
import com.gtarayici.browser.ui.screens.LockScreen
import com.gtarayici.browser.ui.screens.ProfileSwitcherSheet
import com.gtarayici.browser.ui.screens.ProfilesScreen
import com.gtarayici.browser.qr.QrScannerScreen
import com.gtarayici.browser.ui.screens.ReportScreen
import com.gtarayici.browser.ui.screens.SearchScreen
import com.gtarayici.browser.ui.screens.TabsScreen
import com.gtarayici.browser.ui.screens.VpnScreen
import com.gtarayici.browser.ui.screens.settings.SettingsScreen
import com.gtarayici.browser.update.UpdateDialog
import com.gtarayici.browser.youtube.YouTubeDownloader
import kotlinx.coroutines.delay
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState

/** [OverlayHost] içinde gösterilen tam ekran sayfa. */
@Composable
internal fun OverlayPage(
    page: Overlay,
    controller: BrowserController,
    pageUrl: String?,
    private: Boolean,
    searchEngine: SearchEngine,
    profileTabs: List<TabSessionState>,
    selectedTabId: String?,
) {
    val components = LocalContext.current.components
    val lockPrivate by components.settings.lockPrivateTabs.collectAsState()
    val privateLockTitle = stringResource(R.string.lock_title_private)
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
            openTabs = profileTabs.filter { it.content.private == private },
            currentTabId = selectedTabId,
            onSwitchTab = { controller.selectTab(it) },
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
            onCloseMany = { components.tabsUseCases.removeTabs(it) },
            onNewTab = { controller.newTab(it) },
            onNewTabInGroup = { controller.newTabInGroup(it) },
            onBack = { controller.closeOverlay() },
            privateLocked = lockPrivate && !components.appLock.isUnlocked(AppLock.PRIVATE),
            onUnlockPrivate = { controller.unlock(AppLock.PRIVATE, privateLockTitle) },
        )
        Overlay.HISTORY -> HistoryScreen(onOpen = { controller.loadUrl(it) }, onBack = { controller.closeOverlay() })
        Overlay.BOOKMARKS -> BookmarksScreen(onOpen = { controller.loadUrl(it) }, onBack = { controller.closeOverlay() })
        Overlay.DOWNLOADS -> DownloadsScreen(onBack = { controller.closeOverlay() })
        Overlay.SETTINGS -> SettingsScreen(
            onClearData = { controller.clearBrowsingData() },
            onPasswords = { controller.open(Overlay.PASSWORDS) },
            onReport = { controller.openReport() },
            onBack = { controller.closeOverlay() },
        )
        Overlay.PROFILES -> ProfilesScreen(controller, onBack = { controller.closeOverlay() })
        Overlay.EXTENSIONS -> ExtensionsScreen(controller, onBack = { controller.closeOverlay() })
        Overlay.VPN -> VpnScreen(onBack = { controller.closeOverlay() })
        Overlay.PASSWORDS -> com.gtarayici.browser.ui.screens.PasswordsScreen(
            onOpen = { controller.loadUrl(it, inNewTab = true) },
            onBack = { controller.closeOverlay() },
        )
        Overlay.REPORT -> controller.reportRequest?.let { request ->
            ReportScreen(
                request,
                onOpenUrl = { controller.loadUrl(it, inNewTab = true) },
                onBack = { controller.closeOverlay() },
            )
        }
    }
}

/** Kapatılan sekmeler: birkaç saniye "Geri al" mesajı gösterilir. */
@Composable
internal fun ClosedTabsUndo(controller: BrowserController) {
    val components = LocalContext.current.components
    val undo by components.store.observe { it.undoHistory.tag to it.undoHistory.tabs.size }
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
}

/** Bağlamsal ipuçları: özellik ilk kez anlamlı olduğunda bir kez (kilit ekranı açıkken hiç). */
@Composable
internal fun ContextualHints(pageUrl: String?, loading: Boolean, tabCount: Int, toolbarSwipe: Boolean, locked: Boolean) {
    val hints = LocalContext.current.components.hints
    val videoPageLoaded = pageUrl != null && !loading && YouTubeDownloader.isVideoPage(pageUrl)
    LaunchedEffect(videoPageLoaded, locked) {
        if (videoPageLoaded && !locked) {
            delay(HINT_DELAY_MS)
            hints.offer(Hint.YouTubeVideo)
        }
    }
    val manyTabs = HintRules.manyTabs(tabCount, toolbarSwipe)
    LaunchedEffect(manyTabs, locked) {
        if (manyTabs && !locked) {
            delay(HINT_DELAY_MS)
            hints.offer(Hint.ManyTabs)
        }
    }
}

/** Önceki oturum çöktüyse bir kez sorulur (ayarlardan kapatılabilir; hiçbir şey kendiliğinden gönderilmez). */
@Composable
internal fun CrashPrompt(controller: BrowserController, blocked: Boolean) {
    val components = LocalContext.current.components
    var crashChecked by rememberSaveable { mutableStateOf(false) }
    val crashPromptOn by components.settings.crashPrompt.collectAsState()
    if (crashChecked || !crashPromptOn || blocked || !remember { components.crashReporter.pending() != null }) return
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

/** Kilitli profil veya gizli sekme: kilit ekranı açılınca doğrulama istemi kendiliğinden gelir. */
@Composable
internal fun LockOverlay(lockKey: String, profileName: String, controller: BrowserController) {
    val title = if (lockKey == AppLock.PRIVATE) {
        stringResource(R.string.lock_title_private)
    } else {
        stringResource(R.string.lock_title_profile, profileName)
    }
    LaunchedEffect(lockKey) { controller.unlock(lockKey, title) }
    // Surface: başlık ve metinler içerik rengini alır (önceden koyu zeminde siyah kalıyordu).
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.systemBarsPadding().imePadding()) { LockScreen(title, lockKey) { controller.unlock(lockKey, title) } }
    }
}

/** Alttan açılan sayfalar ve pencereler: menü, profil seçici, yükleme, video indirme, çeviri, site bilgisi… */
@Composable
internal fun BrowserSheets(controller: BrowserController, menuState: () -> MenuState, installable: Boolean, selectedTabId: String?) {
    val components = LocalContext.current.components
    if (controller.menuOpen) {
        val extensionActions by components.store.observe { state ->
            val tab = state.selectedTab
            state.extensions.values
                .filter { it.enabled && it.browserAction != null }
                .map { ext ->
                    val base = ext.browserAction!!
                    val override = tab?.extensionState?.get(ext.id)?.browserAction
                    ext.id to (override?.let { base.copyWithOverride(it) } ?: base)
                }
        }
        MenuSheet(state = menuState().copy(extensionActions = extensionActions), controller = controller)
    }
    if (controller.profileSwitcherOpen) ProfileSwitcherSheet(controller)
    if (controller.installDialogOpen) InstallAppDialog(controller, installable = installable)
    controller.youtubeUrl?.let { url -> YouTubeDownloadSheet(url) { controller.youtubeUrl = null } }
    if (controller.translateOpen) {
        selectedTabId?.let { id -> TranslateSheet(id) { controller.translateOpen = false } }
    }
    if (controller.pageDownloadsOpen) {
        selectedTabId?.let { id ->
            PageDownloadsSheet(
                tabId = id,
                onDismiss = { controller.pageDownloadsOpen = false },
                onYouTube = {
                    controller.pageDownloadsOpen = false
                    controller.openYouTubeDownload()
                },
                onOpenDownloads = { controller.open(Overlay.DOWNLOADS) },
            )
        }
    }
    if (controller.siteInfoOpen) {
        selectedTabId?.let { id -> SiteInfoSheet(id) { controller.siteInfoOpen = false } }
    }
    ReaderControlsSheet(controller.readerControls)
    UpdateDialog()
    ExtensionPrompts()
    ExtensionPopupSheet()
}

private const val UNDO_TABS_KEY = "geri-al-sekmeler"

/** Sayfa açıldıktan sonra ipucu gösterilmeden önceki bekleme (kullanıcı önce sayfayı görsün). */
private const val HINT_DELAY_MS = 2_500L
