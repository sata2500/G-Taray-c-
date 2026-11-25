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
    val icon: ImageVector,
    val label: Int,
    val highlighted: Boolean = false,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Tarayıcı menüsü: üstte sayfa kartı (site + gezinme), altında iki simge ızgarası ("Bu sayfa", "Uygulama").
 * Her ızgaranın ilk iki satırı görünür; "Daha fazla" geri kalanını aynı yerde açar. Uzun listeler ve ikinci bir
 * menü sayfası yoktur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(state: MenuState, controller: BrowserController) {
    var expanded by remember { mutableStateOf(false) }
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

            val page = if (state.hasPage) pageItems(state, controller) else emptyList()
            val app = appItems(controller)
            val pageVisible = page.take(GRID_COLUMNS * 2)
            val appVisible = app.take(GRID_COLUMNS * 2)
            val hasMore = page.size > pageVisible.size || app.size > appVisible.size

            if (page.isNotEmpty()) MenuGrid(pageVisible)
            AnimatedVisibility(expanded && page.size > pageVisible.size, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                MenuGrid(page.drop(pageVisible.size))
            }
            if (page.isNotEmpty()) GridDivider()
            MenuGrid(appVisible)
            AnimatedVisibility(expanded && app.size > appVisible.size, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                MenuGrid(app.drop(appVisible.size))
            }
            if (hasMore) MoreToggle(expanded) { expanded = !expanded }
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
        modifier = Modifier.size(52.dp),
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

/** Bu sayfaya ait işlemler: o an işe yarayanlar (video, çeviri, okuma ayarları) önde. */
@Composable
private fun pageItems(state: MenuState, controller: BrowserController): List<MenuItem> = buildList {
    if (state.isYouTubeVideo) add(MenuItem(Icons.Rounded.OndemandVideo, R.string.menu_tile_download_video, highlighted = true) { controller.openYouTubeDownload() })
    if (state.canTranslate) add(MenuItem(Icons.Rounded.Translate, R.string.menu_tile_translate, highlighted = true) { controller.openTranslate() })
    if (state.readerActive) add(MenuItem(Icons.Rounded.TextFields, R.string.menu_tile_reader_settings) { controller.openReaderSettings() })
    add(MenuItem(Icons.Rounded.FindInPage, R.string.menu_tile_find) { controller.openFindInPage() })
    add(MenuItem(Icons.Rounded.DesktopWindows, R.string.menu_tile_desktop, active = state.desktopMode) { controller.toggleDesktopMode() })
    if (!state.readerActive) add(MenuItem(Icons.Rounded.BookmarkAdd, R.string.menu_tile_save_later) { controller.reading.saveForLater() })
    add(MenuItem(Icons.Rounded.FileDownload, R.string.menu_tile_page_downloads) {
        controller.menuOpen = false
        controller.pageDownloadsOpen = true
    })
    if (!state.readerActive) add(MenuItem(Icons.Rounded.RecordVoiceOver, R.string.menu_tile_read_aloud) { controller.reading.readAloud() })
    add(MenuItem(Icons.Rounded.ContentCopy, R.string.menu_tile_copy_link) { controller.copyLink() })
    add(MenuItem(Icons.Rounded.Screenshot, R.string.menu_tile_screenshot) { controller.reading.captureFullPage() })
    add(MenuItem(Icons.Rounded.PictureAsPdf, R.string.menu_tile_pdf) { controller.reading.saveAsPdf() })
    add(MenuItem(Icons.Rounded.InstallMobile, R.string.menu_tile_install) { controller.openInstallDialog() })
}

/** Uygulamanın bölümleri; en sık kullanılanlar ilk iki satırda. */
private fun appItems(controller: BrowserController): List<MenuItem> = listOf(
    MenuItem(Icons.Rounded.Add, R.string.menu_tile_new_tab) { controller.newTab(private = false) },
    MenuItem(Icons.Rounded.VisibilityOff, R.string.menu_tile_private_tab) { controller.newTab(private = true) },
    MenuItem(Icons.Rounded.Bookmarks, R.string.menu_bookmarks) { controller.open(Overlay.BOOKMARKS) },
    MenuItem(Icons.Rounded.History, R.string.menu_history) { controller.open(Overlay.HISTORY) },
    MenuItem(Icons.Rounded.Download, R.string.menu_downloads) { controller.open(Overlay.DOWNLOADS) },
    MenuItem(Icons.Rounded.AutoStories, R.string.menu_tile_reading) { controller.open(Overlay.READING) },
    MenuItem(Icons.Rounded.VpnKey, R.string.menu_vpn) { controller.open(Overlay.VPN) },
    MenuItem(Icons.Rounded.Settings, R.string.menu_settings) { controller.open(Overlay.SETTINGS) },
    MenuItem(Icons.Rounded.Extension, R.string.menu_extensions) { controller.open(Overlay.EXTENSIONS) },
    MenuItem(Icons.Rounded.ManageAccounts, R.string.menu_profiles) { controller.open(Overlay.PROFILES) },
    MenuItem(Icons.Rounded.QrCodeScanner, R.string.menu_tile_qr) { controller.openQrScanner() },
    MenuItem(Icons.Rounded.Lightbulb, R.string.menu_tile_guide) { controller.open(Overlay.GUIDE) },
    MenuItem(Icons.Rounded.BugReport, R.string.menu_tile_report) { controller.openReport() },
)

@Composable
private fun MenuGrid(items: List<MenuItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items.chunked(GRID_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item -> MenuTile(item, Modifier.weight(1f)) }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MenuTile(item: MenuItem, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        item.active -> colors.primary to colors.onPrimary
        item.highlighted -> colors.primaryContainer to colors.onPrimaryContainer
        else -> colors.surfaceContainerHighest to colors.onSurface
    }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = item.onClick)
            .semantics {
                role = Role.Button
                if (item.active) selected = true
            }
            .padding(vertical = Spacing.sm, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = container, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = content, modifier = Modifier.size(24.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(item.label),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 32.dp),
        )
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

@Composable
private fun MoreToggle(expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (expanded) R.string.menu_less else R.string.menu_more),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private const val GRID_COLUMNS = 4
