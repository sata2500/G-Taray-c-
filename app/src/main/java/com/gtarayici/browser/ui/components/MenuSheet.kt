package com.gtarayici.browser.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.ui.BrowserController
import com.gtarayici.browser.ui.Overlay
import com.gtarayici.browser.ui.designsystem.AppShape
import com.gtarayici.browser.ui.designsystem.NavRow
import com.gtarayici.browser.ui.designsystem.RowIconStyle
import com.gtarayici.browser.ui.designsystem.SectionCard
import com.gtarayici.browser.ui.designsystem.Size
import com.gtarayici.browser.ui.designsystem.Spacing
import com.gtarayici.browser.ui.designsystem.SwitchRow
import com.gtarayici.browser.ui.designsystem.Tile
import com.gtarayici.browser.ui.designsystem.TileGrid
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(state: MenuState, controller: BrowserController) {
    ModalBottomSheet(
        onDismissRequest = { controller.menuOpen = false },
        sheetState = com.gtarayici.browser.ui.designsystem.rememberFullSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(bottom = Spacing.lg).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (state.hasPage) PageActions(state, controller)
            if (state.extensionActions.isNotEmpty()) ExtensionActions(state.extensionActions, controller)

            // Sık kullanılan sayfalar: tek dokunuşla, kaydırmadan
            val tiles = listOf(
                MenuTileSpec(Icons.Rounded.Add, R.string.menu_tile_new_tab) { controller.newTab(private = false) },
                MenuTileSpec(Icons.Rounded.VisibilityOff, R.string.menu_tile_private_tab) { controller.newTab(private = true) },
                MenuTileSpec(Icons.Rounded.Bookmarks, R.string.menu_bookmarks) { controller.open(Overlay.BOOKMARKS) },
                MenuTileSpec(Icons.Rounded.History, R.string.menu_history) { controller.open(Overlay.HISTORY) },
                MenuTileSpec(Icons.Rounded.Download, R.string.menu_downloads) { controller.open(Overlay.DOWNLOADS) },
                MenuTileSpec(Icons.Rounded.Extension, R.string.menu_extensions) { controller.open(Overlay.EXTENSIONS) },
                MenuTileSpec(Icons.Rounded.VpnKey, R.string.menu_vpn) { controller.open(Overlay.VPN) },
                MenuTileSpec(Icons.Rounded.Settings, R.string.menu_settings) { controller.open(Overlay.SETTINGS) },
            )
            TileGrid(columns = 4, count = tiles.size, modifier = Modifier.padding(horizontal = Spacing.sm)) { index, modifier ->
                val tile = tiles[index]
                Tile(stringResource(tile.label), modifier, icon = tile.icon, onClick = tile.onClick)
            }

            if (state.hasPage) {
                SectionCard(
                    listOf(
                        if (state.isYouTubeVideo) {
                            { MenuRow(Icons.Rounded.OndemandVideo, stringResource(R.string.menu_download_video)) { controller.openYouTubeDownload() } }
                        } else null,
                        {
                            MenuRow(Icons.Rounded.Download, stringResource(R.string.menu_page_downloads)) {
                                controller.menuOpen = false
                                controller.pageDownloadsOpen = true
                            }
                        },
                        if (state.canTranslate) {
                            { MenuRow(Icons.Rounded.Translate, stringResource(R.string.menu_translate)) { controller.openTranslate() } }
                        } else null,
                        if (state.readerActive) {
                            { MenuRow(Icons.Rounded.TextFields, stringResource(R.string.reader_settings)) { controller.openReaderSettings() } }
                        } else null,
                        { MenuRow(Icons.Rounded.FindInPage, stringResource(R.string.menu_find_in_page)) { controller.openFindInPage() } },
                        { MenuRow(Icons.Rounded.InstallMobile, stringResource(R.string.menu_install_app)) { controller.openInstallDialog() } },
                        { MenuRow(Icons.Rounded.ContentCopy, stringResource(R.string.menu_copy_link)) { controller.copyLink() } },
                        {
                            SwitchRow(
                                title = stringResource(R.string.menu_desktop_site),
                                checked = state.desktopMode,
                                icon = Icons.Rounded.DesktopWindows,
                                iconStyle = RowIconStyle.Plain,
                            ) { controller.toggleDesktopMode() }
                        },
                    ),
                )
            }
            SectionCard(
                listOf(
                    { MenuRow(Icons.Rounded.QrCodeScanner, stringResource(R.string.menu_scan_qr)) { controller.openQrScanner() } },
                    { MenuRow(Icons.Rounded.ManageAccounts, stringResource(R.string.menu_profiles)) { controller.open(Overlay.PROFILES) } },
                    { MenuRow(Icons.Rounded.BugReport, stringResource(R.string.menu_report)) { controller.openReport() } },
                ),
            )
        }
    }
}

private class MenuTileSpec(val icon: ImageVector, val label: Int, val onClick: () -> Unit)

/** Geri / ileri / yenile / yer imi / paylaş: eşit aralıklı ton düğmeleri. */
@Composable
private fun PageActions(state: MenuState, controller: BrowserController) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ActionButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), enabled = state.canGoBack) { controller.goBack() }
        ActionButton(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.action_forward), enabled = state.canGoForward) { controller.goForward() }
        if (state.loading) {
            ActionButton(Icons.Rounded.Close, stringResource(R.string.action_stop)) { controller.reloadOrStop() }
        } else {
            ActionButton(Icons.Rounded.Refresh, stringResource(R.string.action_reload)) { controller.reloadOrStop() }
        }
        ActionButton(
            if (state.bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            stringResource(if (state.bookmarked) R.string.menu_remove_bookmark else R.string.menu_add_bookmark),
            selected = state.bookmarked,
        ) { controller.toggleBookmark(state.bookmarked) }
        ActionButton(Icons.Rounded.Share, stringResource(R.string.action_share)) { controller.share() }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = AppShape.md,
        colors = if (selected) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        },
        modifier = Modifier.size(width = 56.dp, height = Size.touchTarget),
    ) {
        Icon(icon, label)
    }
}


/** Eklenti düğmeleri (ör. uBlock Origin paneli): simge + rozet. */
@Composable
private fun ExtensionActions(actions: List<Pair<String, Action>>, controller: BrowserController) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        actions.forEach { (_, action) ->
            Surface(
                onClick = {
                    controller.menuOpen = false
                    action.onClick()
                },
                shape = AppShape.full,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                // Simge tek başına ekran okuyucuya bir şey söylemez; eklentinin düğme başlığı okunur.
                modifier = Modifier.semantics { action.title?.let { contentDescription = it } },
            ) {
                Row(
                    Modifier.heightIn(min = Size.touchTarget).padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BadgedBox(badge = {
                        action.badgeText?.takeIf { it.isNotBlank() }?.let { Badge { Text(it) } }
                    }) { ExtensionActionIcon(action.loadIcon) }
                    action.title?.let {
                        Spacer(Modifier.width(Spacing.md))
                        Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Bölüm kartındaki menü satırı (düz simgeli, sağda ok simgesi yok). */
@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavRow(title = label, icon = icon, showChevron = false, iconStyle = RowIconStyle.Plain, onClick = onClick)
}
