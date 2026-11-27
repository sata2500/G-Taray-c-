package com.gtarayici.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.collectAsState
import com.gtarayici.browser.components
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.BrowserController
import com.gtarayici.browser.ui.Overlay
import com.gtarayici.browser.ui.designsystem.Spacing
import com.gtarayici.browser.ui.screens.ExtensionActionIcon
import mozilla.components.concept.engine.webextension.Action

data class MenuState(
    val hasPage: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val loading: Boolean,
    val bookmarked: Boolean,
    val desktopMode: Boolean,
    val isYouTubeVideo: Boolean,
    val installableWebApp: Boolean,
    val readerActive: Boolean = false,
    val canTranslate: Boolean = false,
    val extensionActions: List<Pair<String, Action>>,
    val url: String = "",
    val title: String = "",
    val private: Boolean = false,
)

/** Menüdeki bir kutucuk. [highlighted]: o an öne çıkan (ör. videoyu indir); [active]: açık bir anahtar (masaüstü). */
private class MenuItem(
    val id: String,
    val icon: ImageVector,
    val label: Int,
    val page: Boolean,
    val highlighted: Boolean = false,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Tarayıcı menüsü: üstte sayfa kartı (site + gezinme), altında tek simge ızgarası. Izgarada o an işe yarayanlar
 * (videoyu indir, çevir) ve kullanıcının seçtiği öğeler durur; varsayılan olarak en sık kullanılanlar. Geri kalan her
 * şey "Daha fazla"da. "Menüyü düzenle" ya da bir simgeye uzun basmak öğeyi ana ızgaraya ekler/çıkarır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(state: MenuState, controller: BrowserController) {
    val components = androidx.compose.ui.platform.LocalContext.current.components
    val pinsText by components.settings.menuPins.collectAsState()
    val pins = remember(pinsText) { pinsText.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    val all = (if (state.hasPage) pageItems(state, controller) else emptyList()) + appItems(controller)
    val contextual = all.filter { it.highlighted }
    val pinned = pins.mapNotNull { id -> all.firstOrNull { it.id == id && !it.highlighted } }
    val rest = all.filter { !it.highlighted && it.id !in pins }

    fun togglePin(item: MenuItem) {
        val updated = if (item.id in pins) pins - item.id else pins + item.id
        components.settings.setMenuPins(updated)
        if (!editing) {
            components.messenger.info(
                com.gtarayici.browser.ui.feedback.UiText.res(if (item.id in pins) R.string.menu_unpinned else R.string.menu_pinned),
            )
        }
    }
    val tap: (MenuItem) -> Unit = { item -> if (editing) togglePin(item) else item.onClick() }

    ModalBottomSheet(
        onDismissRequest = { controller.menuOpen = false },
        sheetState = com.gtarayici.browser.ui.designsystem.rememberFullSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (state.hasPage) PageCard(state, controller)

            MenuGrid(contextual + pinned, editing, pinnedIds = pins, onTap = tap, onLongPress = ::togglePin)

            AnimatedVisibility(expanded || editing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    GridDivider()
                    val restPage = rest.filter { it.page }
                    val restApp = rest.filter { !it.page }
                    if (restPage.isNotEmpty()) {
                        GroupLabel(R.string.menu_group_page)
                        MenuGrid(restPage, editing, pinnedIds = pins, onTap = tap, onLongPress = ::togglePin)
                    }
                    if (restApp.isNotEmpty()) {
                        GroupLabel(R.string.menu_group_app)
                        MenuGrid(restApp, editing, pinnedIds = pins, onTap = tap, onLongPress = ::togglePin)
                    }
                    if (editing) {
                        Text(
                            stringResource(R.string.menu_edit_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally)) {
                if (editing) {
                    Pill(R.string.menu_edit_reset, null) { components.settings.setMenuPins(com.gtarayici.browser.settings.DEFAULT_MENU_PINS.split(',')) }
                    Pill(R.string.menu_edit_done, null, primary = true) { editing = false }
                } else {
                    Pill(if (expanded) R.string.menu_less else R.string.menu_more, if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore) {
                        expanded = !expanded
                    }
                    if (expanded) Pill(R.string.menu_edit, Icons.Rounded.Edit) { editing = true }
                }
            }
        }
    }
}

/** Sitenin kartı: simge, başlık, adres, eklenti rozetleri ve gezinme düğmeleri. */
@Composable
private fun PageCard(state: MenuState, controller: BrowserController) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Favicon(state.url, size = 28.dp, private = state.private)
                Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                    Text(
                        state.title.ifBlank { UrlInput.host(state.url) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        UrlInput.host(state.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                state.extensionActions.forEach { (_, action) -> ExtensionBadge(action, controller) }
                IconButton(onClick = { controller.menuOpen = false }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                NavButton(Icons.AutoMirrored.Rounded.ArrowBack, R.string.action_back, enabled = state.canGoBack) { controller.goBack() }
                NavButton(Icons.AutoMirrored.Rounded.ArrowForward, R.string.action_forward, enabled = state.canGoForward) { controller.goForward() }
                if (state.loading) {
                    NavButton(Icons.Rounded.Close, R.string.action_stop) { controller.reloadOrStop() }
                } else {
                    NavButton(Icons.Rounded.Refresh, R.string.action_reload) { controller.reloadOrStop() }
                }
                NavButton(
                    if (state.bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (state.bookmarked) R.string.menu_remove_bookmark else R.string.menu_add_bookmark,
                    tint = if (state.bookmarked) MaterialTheme.colorScheme.primary else null,
                ) { controller.toggleBookmark(state.bookmarked) }
                NavButton(Icons.Rounded.Share, R.string.action_share) { controller.share() }
            }
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, label: Int, enabled: Boolean = true, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = tint?.let { IconButtonDefaults.iconButtonColors(contentColor = it) } ?: IconButtonDefaults.iconButtonColors(),
        modifier = Modifier.size(48.dp),
    ) { Icon(icon, stringResource(label)) }
}

/** Eklenti düğmesi (ör. uBlock Origin): yalnızca simge ve sayaç rozeti; dokununca eklentinin paneli açılır. */
@Composable
private fun ExtensionBadge(action: Action, controller: BrowserController) {
    IconButton(
        onClick = {
            controller.menuOpen = false
            action.onClick()
        },
        modifier = Modifier.semantics { action.title?.let { contentDescription = it } },
    ) {
        BadgedBox(badge = {
            action.badgeText?.takeIf { it.isNotBlank() }?.let { Badge { Text(it) } }
        }) { ExtensionActionIcon(action.loadIcon) }
    }
}

/** Bu sayfaya ait işlemler. Videoyu indir ve çevir yalnızca işe yaradığında vardır ve hep en öndedir. */
private fun pageItems(state: MenuState, controller: BrowserController): List<MenuItem> = buildList {
    if (state.isYouTubeVideo) add(MenuItem("video", Icons.Rounded.OndemandVideo, R.string.menu_tile_download_video, page = true, highlighted = true) { controller.openYouTubeDownload() })
    if (state.canTranslate) add(MenuItem("translate", Icons.Rounded.Translate, R.string.menu_tile_translate, page = true, highlighted = true) { controller.openTranslate() })
    if (state.readerActive) add(MenuItem("reader", Icons.Rounded.TextFields, R.string.menu_tile_reader_settings, page = true, highlighted = true) { controller.openReaderSettings() })
    add(MenuItem("find", Icons.Rounded.FindInPage, R.string.menu_tile_find, page = true) { controller.openFindInPage() })
    add(MenuItem("desktop", Icons.Rounded.DesktopWindows, R.string.menu_tile_desktop, page = true, active = state.desktopMode) { controller.toggleDesktopMode() })
    add(MenuItem("page_files", Icons.Rounded.FileDownload, R.string.menu_tile_page_downloads, page = true) {
        controller.menuOpen = false
        controller.pageDownloadsOpen = true
    })
    if (!state.readerActive) add(MenuItem("save_later", Icons.Rounded.BookmarkAdd, R.string.menu_tile_save_later, page = true) { controller.reading.saveForLater() })
    if (!state.readerActive) add(MenuItem("read_aloud", Icons.Rounded.RecordVoiceOver, R.string.menu_tile_read_aloud, page = true) { controller.reading.readAloud() })
    add(MenuItem("copy_link", Icons.Rounded.ContentCopy, R.string.menu_tile_copy_link, page = true) { controller.copyLink() })
    add(MenuItem("screenshot", Icons.Rounded.Screenshot, R.string.menu_tile_screenshot, page = true) { controller.reading.captureFullPage() })
    add(MenuItem("pdf", Icons.Rounded.PictureAsPdf, R.string.menu_tile_pdf, page = true) { controller.reading.saveAsPdf() })
    add(MenuItem("install", Icons.Rounded.InstallMobile, R.string.menu_tile_install, page = true) { controller.openInstallDialog() })
}

/** Uygulamanın bölümleri. */
private fun appItems(controller: BrowserController): List<MenuItem> = listOf(
    MenuItem("bookmarks", Icons.Rounded.Bookmarks, R.string.menu_bookmarks, page = false) { controller.open(Overlay.BOOKMARKS) },
    MenuItem("history", Icons.Rounded.History, R.string.menu_history, page = false) { controller.open(Overlay.HISTORY) },
    MenuItem("downloads", Icons.Rounded.Download, R.string.menu_downloads, page = false) { controller.open(Overlay.DOWNLOADS) },
    MenuItem("settings", Icons.Rounded.Settings, R.string.menu_settings, page = false) { controller.open(Overlay.SETTINGS) },
    MenuItem("reading", Icons.Rounded.AutoStories, R.string.menu_tile_reading, page = false) { controller.open(Overlay.READING) },
    MenuItem("vpn", Icons.Rounded.VpnKey, R.string.menu_vpn, page = false) { controller.open(Overlay.VPN) },
    MenuItem("new_tab", Icons.Rounded.Add, R.string.menu_tile_new_tab, page = false) { controller.newTab(private = false) },
    MenuItem("private_tab", Icons.Rounded.VisibilityOff, R.string.menu_tile_private_tab, page = false) { controller.newTab(private = true) },
    MenuItem("extensions", Icons.Rounded.Extension, R.string.menu_extensions, page = false) { controller.open(Overlay.EXTENSIONS) },
    MenuItem("profiles", Icons.Rounded.ManageAccounts, R.string.menu_profiles, page = false) { controller.open(Overlay.PROFILES) },
    MenuItem("qr", Icons.Rounded.QrCodeScanner, R.string.menu_tile_qr, page = false) { controller.openQrScanner() },
    MenuItem("library", Icons.Rounded.LocalLibrary, R.string.menu_tile_library, page = false) { controller.open(Overlay.LIBRARY) },
    MenuItem("vault", Icons.Rounded.EnhancedEncryption, R.string.menu_tile_vault, page = false) { controller.openVault() },
    MenuItem("guide", Icons.Rounded.Lightbulb, R.string.menu_tile_guide, page = false) { controller.open(Overlay.GUIDE) },
    MenuItem("report", Icons.Rounded.BugReport, R.string.menu_tile_report, page = false) { controller.openReport() },
)

@Composable
private fun MenuGrid(
    items: List<MenuItem>,
    editing: Boolean,
    pinnedIds: List<String>,
    onTap: (MenuItem) -> Unit,
    onLongPress: (MenuItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.chunked(GRID_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    // Düzenlerken bağlama göre çıkan öğeler (videoyu indir, çevir) değiştirilemez.
                    val mark = when {
                        !editing || item.highlighted -> null
                        item.id in pinnedIds -> false
                        else -> true
                    }
                    MenuTile(item, Modifier.weight(1f), mark, onTap = { onTap(item) }, onLongPress = { if (!item.highlighted) onLongPress(item) })
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** [addMark]: düzenlemede köşedeki işaret (true: ekle "+", false: çıkar "−", null: yok). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MenuTile(item: MenuItem, modifier: Modifier, addMark: Boolean?, onTap: () -> Unit, onLongPress: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        item.active -> colors.primary to colors.onPrimary
        item.highlighted -> colors.primaryContainer to colors.onPrimaryContainer
        else -> colors.surfaceContainerHighest to colors.onSurface
    }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .semantics {
                role = Role.Button
                if (item.active) selected = true
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            // Sade, sıkı kutucuk: yatay hap biçimli simge alanı (Material 3 gezinme çubuğu gibi), küçük etiket.
            Surface(shape = RoundedCornerShape(16.dp), color = container, modifier = Modifier.size(width = 56.dp, height = 40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = content, modifier = Modifier.size(22.dp)) }
            }
            if (addMark != null) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (addMark) colors.primary else colors.error,
                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
                ) {
                    Icon(
                        if (addMark) Icons.Rounded.Add else Icons.Rounded.Remove,
                        contentDescription = stringResource(if (addMark) R.string.menu_pin else R.string.menu_unpin),
                        tint = if (addMark) colors.onPrimary else colors.onError,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(item.label),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 28.dp),
        )
    }
}

@Composable
private fun GroupLabel(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
    )
}

@Composable
private fun Pill(text: Int, icon: ImageVector?, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(text), style = MaterialTheme.typography.labelLarge)
            if (icon != null) {
                Spacer(Modifier.width(4.dp))
                Icon(icon, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun GridDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .height(1.dp)
            .clip(RoundedCornerShape(1.dp)),
    ) {
        Surface(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
    }
}

private const val GRID_COLUMNS = 4
