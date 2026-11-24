package com.gtarayici.browser.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.gtarayici.browser.R
import com.gtarayici.browser.ui.designsystem.NavRow
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.designsystem.SectionCard
import com.gtarayici.browser.ui.designsystem.Spacing

/** Ayarlar ana sayfasındaki kategoriler. */
enum class SettingsPage(val icon: ImageVector, val title: Int, val summary: Int) {
    GENERAL(Icons.Rounded.Tune, R.string.settings_general, R.string.settings_page_general_summary),
    APPEARANCE(Icons.Rounded.Palette, R.string.settings_appearance, R.string.settings_page_appearance_summary),
    PRIVACY(Icons.Rounded.Shield, R.string.settings_privacy, R.string.settings_page_privacy_summary),
    MEDIA(Icons.Rounded.PlayCircle, R.string.settings_media, R.string.settings_page_media_summary),
    DATA(Icons.Rounded.DataSaverOn, R.string.settings_data_saver_page, R.string.settings_page_data_summary),
    DIAGNOSTICS(Icons.Rounded.BugReport, R.string.diag_settings_title, R.string.diag_settings_summary),
    ABOUT(Icons.Rounded.Info, R.string.settings_about, R.string.settings_page_about_summary),
}

/** Açık pencere: her seferinde en fazla biri. */
internal enum class SettingsDialog { ENGINE, CUSTOM_ENGINE, THEME, DNS, DNS_CUSTOM, PASSWORD, CLEAR_DATA, LOG, DATA_SAVER, CACHE_SIZE, TEXT_ZOOM, SITES }

@Composable
fun SettingsScreen(
    onClearData: () -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit,
    onPasswords: () -> Unit = {},
    onGuide: () -> Unit = {},
    startPage: SettingsPage? = null,
) {
    var page by rememberSaveable { mutableStateOf(startPage) }
    var query by rememberSaveable { mutableStateOf("") }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    BackHandler(enabled = page != null || query.isNotEmpty()) {
        if (query.isNotEmpty()) query = "" else page = null
    }
    val backup = rememberBackupUi()
    val pages = settingsPages(open = { dialog = it }, onReport = onReport, onPasswords = onPasswords, onGuide = onGuide, backup = backup)

    OverlayScaffold(
        title = page?.let { stringResource(it.title) } ?: stringResource(R.string.menu_settings),
        onBack = { if (page != null) page = null else onBack() },
    ) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            val current = page
            when {
                current != null -> SettingSections(pages.getValue(current))
                query.isNotBlank() -> {
                    SettingsSearchField(query) { query = it }
                    SettingsSearchResults(query, pages.map { (entry, sections) -> stringResource(entry.title) to sections })
                }
                else -> {
                    SettingsSearchField(query) { query = it }
                    Spacer(Modifier.height(Spacing.sm))
                    SectionCard(
                        SettingsPage.entries.map { entry ->
                            { NavRow(stringResource(entry.title), stringResource(entry.summary), entry.icon) { page = entry } }
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    SettingsDialogs(dialog, onDismiss = { dialog = null }, open = { dialog = it }, onClearData = onClearData)
    backup.dialogs()
}
