package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.ui.BrowserController
import com.gtarayici.browser.ui.Overlay
import com.gtarayici.browser.ui.designsystem.AppShape
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.designsystem.SectionHeader
import com.gtarayici.browser.ui.designsystem.Spacing
import com.gtarayici.browser.ui.screens.settings.SettingsPage

/** Rehberdeki bir özellik: ne işe yaradığı tek cümleyle ve (varsa) onu açan düğme. */
private class GuideItem(val icon: ImageVector, val title: Int, val text: Int, val open: (() -> Unit)? = null)

private class GuideSection(val title: Int, val items: List<GuideItem>)

/**
 * "Neler yapabilirsin?": uygulamanın göze hemen çarpmayan özelliklerinin kısa listesi. Her madde tek cümle; "Aç"
 * ilgili ekranı açar. Menü → Tüm seçenekler ve Ayarlar → Hakkında'dan açılır.
 */
@Composable
fun GuideScreen(controller: BrowserController, onBack: () -> Unit) {
    val sections = listOf(
        GuideSection(
            R.string.guide_section_browse,
            listOf(
                GuideItem(Icons.Rounded.Swipe, R.string.guide_swipe_title, R.string.guide_swipe_text),
                GuideItem(Icons.Rounded.TouchApp, R.string.guide_longpress_title, R.string.guide_longpress_text),
                GuideItem(Icons.Rounded.Search, R.string.guide_shortcuts_title, R.string.guide_shortcuts_text) { controller.open(Overlay.SEARCH) },
                GuideItem(Icons.Rounded.Tab, R.string.guide_tabs_title, R.string.guide_tabs_text) { controller.open(Overlay.TABS) },
            ),
        ),
        GuideSection(
            R.string.guide_section_privacy,
            listOf(
                GuideItem(Icons.Rounded.Shield, R.string.guide_blocking_title, R.string.guide_blocking_text) { controller.openSettings(SettingsPage.PRIVACY) },
                GuideItem(Icons.Rounded.Lock, R.string.guide_private_title, R.string.guide_private_text) { controller.openSettings(SettingsPage.PRIVACY) },
                GuideItem(Icons.Rounded.Key, R.string.guide_passwords_title, R.string.guide_passwords_text) { controller.open(Overlay.PASSWORDS) },
                GuideItem(Icons.Rounded.VpnKey, R.string.guide_vpn_title, R.string.guide_vpn_text) { controller.open(Overlay.VPN) },
                GuideItem(Icons.Rounded.ManageAccounts, R.string.guide_profiles_title, R.string.guide_profiles_text) { controller.open(Overlay.PROFILES) },
            ),
        ),
        GuideSection(
            R.string.guide_section_media,
            listOf(
                GuideItem(Icons.Rounded.Headphones, R.string.guide_background_title, R.string.guide_background_text) { controller.openSettings(SettingsPage.MEDIA) },
                GuideItem(Icons.Rounded.Download, R.string.guide_download_title, R.string.guide_download_text) { controller.open(Overlay.DOWNLOADS) },
                GuideItem(Icons.Rounded.AutoStories, R.string.guide_reading_title, R.string.guide_reading_text) { controller.open(Overlay.READING) },
                GuideItem(Icons.Rounded.Translate, R.string.guide_translate_title, R.string.guide_translate_text),
            ),
        ),
        GuideSection(
            R.string.guide_section_sites,
            listOf(
                GuideItem(Icons.Rounded.Tune, R.string.guide_site_title, R.string.guide_site_text),
                GuideItem(Icons.Rounded.DataSaverOn, R.string.guide_data_title, R.string.guide_data_text) { controller.openSettings(SettingsPage.DATA) },
                GuideItem(Icons.Rounded.InstallMobile, R.string.guide_webapp_title, R.string.guide_webapp_text),
            ),
        ),
        GuideSection(
            R.string.guide_section_tools,
            listOf(
                GuideItem(Icons.Rounded.TouchApp, R.string.guide_quick_title, R.string.guide_quick_text),
                GuideItem(Icons.Rounded.Backup, R.string.guide_backup_title, R.string.guide_backup_text) { controller.openSettings(SettingsPage.GENERAL) },
                GuideItem(Icons.Rounded.BugReport, R.string.guide_report_title, R.string.guide_report_text) { controller.openReport() },
            ),
        ),
    )

    OverlayScaffold(title = stringResource(R.string.guide_title), onBack = onBack) { modifier ->
        LazyColumn(modifier.fillMaxWidth()) {
            item {
                Text(
                    stringResource(R.string.guide_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screen + Spacing.xs, vertical = Spacing.sm),
                )
            }
            sections.forEach { section ->
                item { SectionHeader(stringResource(section.title)) }
                items(section.items) { GuideCard(it) }
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }
}

@Composable
private fun GuideCard(item: GuideItem) {
    Surface(
        shape = AppShape.md,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xxs),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.Top) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(22.dp))
            Column(Modifier.weight(1f).padding(start = Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(item.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.open?.let { open ->
                TextButton(onClick = open) { Text(stringResource(R.string.guide_open)) }
            }
        }
    }
}
