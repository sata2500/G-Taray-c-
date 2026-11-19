package com.gtarayici.browser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.ui.BrowserController
import com.gtarayici.browser.ui.Overlay
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp).verticalScroll(rememberScrollState())) {
            if (state.hasPage) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = { controller.goBack() }, enabled = state.canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                    IconButton(onClick = { controller.goForward() }, enabled = state.canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.action_forward))
                    }
                    IconButton(onClick = { controller.reloadOrStop() }) {
                        if (state.loading) Icon(Icons.Filled.Close, stringResource(R.string.action_stop))
                        else Icon(Icons.Filled.Refresh, stringResource(R.string.action_reload))
                    }
                    IconButton(onClick = { controller.toggleBookmark(state.bookmarked) }) {
                        if (state.bookmarked) {
                            Icon(Icons.Filled.Star, stringResource(R.string.menu_remove_bookmark), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Filled.StarBorder, stringResource(R.string.menu_add_bookmark))
                        }
                    }
                    IconButton(onClick = { controller.share() }) {
                        Icon(Icons.Filled.Share, stringResource(R.string.action_share))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }

            // Eklenti düğmeleri (ör. uBlock Origin paneli)
            if (state.extensionActions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.Start) {
                    state.extensionActions.forEach { (_, action) ->
                        IconButton(onClick = {
                            controller.menuOpen = false
                            action.onClick()
                        }) {
                            BadgedBox(badge = {
                                action.badgeText?.takeIf { it.isNotBlank() }?.let { Badge { Text(it) } }
                            }) { ExtensionActionIcon(action.loadIcon) }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }

            if (state.isYouTubeVideo) {
                MenuItem(Icons.Filled.OndemandVideo, stringResource(R.string.menu_download_video)) { controller.openYouTubeDownload() }
            }
            MenuItem(Icons.Filled.Add, stringResource(R.string.menu_new_tab)) { controller.newTab(private = false) }
            MenuItem(Icons.Filled.VisibilityOff, stringResource(R.string.menu_new_private_tab)) {
                controller.newTab(private = true)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem(Icons.Filled.Bookmarks, stringResource(R.string.menu_bookmarks)) { controller.open(Overlay.BOOKMARKS) }
            MenuItem(Icons.Filled.History, stringResource(R.string.menu_history)) { controller.open(Overlay.HISTORY) }
            MenuItem(Icons.Filled.Download, stringResource(R.string.menu_downloads)) { controller.open(Overlay.DOWNLOADS) }
            MenuItem(Icons.Filled.QrCodeScanner, stringResource(R.string.menu_scan_qr)) { controller.openQrScanner() }
            if (state.hasPage) {
                MenuItem(Icons.Filled.FindInPage, stringResource(R.string.menu_find_in_page)) { controller.openFindInPage() }
                if (state.canTranslate) {
                    MenuItem(Icons.Filled.Translate, stringResource(R.string.menu_translate)) { controller.openTranslate() }
                }
                if (state.readerActive) {
                    MenuItem(Icons.Filled.TextFields, stringResource(R.string.reader_settings)) { controller.openReaderSettings() }
                }
                MenuItem(Icons.Filled.InstallMobile, stringResource(R.string.menu_install_app)) { controller.openInstallDialog() }
                MenuItem(Icons.Filled.ContentCopy, stringResource(R.string.menu_copy_link)) { controller.copyLink() }
                ListItem(
                    leadingContent = { Icon(Icons.Filled.DesktopWindows, null) },
                    headlineContent = { Text(stringResource(R.string.menu_desktop_site)) },
                    trailingContent = {
                        Switch(checked = state.desktopMode, onCheckedChange = { controller.toggleDesktopMode() })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { controller.toggleDesktopMode() },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem(Icons.Filled.ManageAccounts, stringResource(R.string.menu_profiles)) { controller.open(Overlay.PROFILES) }
            MenuItem(Icons.Filled.Extension, stringResource(R.string.menu_extensions)) { controller.open(Overlay.EXTENSIONS) }
            MenuItem(Icons.Filled.VpnKey, stringResource(R.string.menu_vpn)) { controller.open(Overlay.VPN) }
            MenuItem(Icons.Filled.Settings, stringResource(R.string.menu_settings)) { controller.open(Overlay.SETTINGS) }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
