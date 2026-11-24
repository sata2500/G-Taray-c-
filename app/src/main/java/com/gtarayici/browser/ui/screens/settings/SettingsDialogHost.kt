package com.gtarayici.browser.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.search.SearchEngine
import com.gtarayici.browser.settings.CacheSize
import com.gtarayici.browser.settings.DataSaverMode
import com.gtarayici.browser.settings.DnsProvider
import com.gtarayici.browser.settings.ThemeMode

// Ayarlar ekranında açılan pencereler (seçim, DNS, parola, önbellek…).

@Composable
internal fun SettingsDialogs(
    dialog: SettingsDialog?,
    onDismiss: () -> Unit,
    open: (SettingsDialog) -> Unit,
    onClearData: () -> Unit,
) {
    val components = LocalContext.current.components
    val settings = components.settings
    when (dialog) {
        null -> Unit
        SettingsDialog.ENGINE -> ChoiceDialog(
            title = stringResource(R.string.settings_search_engine),
            options = SearchEngine.entries.map { it to if (it == SearchEngine.CUSTOM) stringResource(R.string.search_engine_custom) else it.title },
            selected = settings.searchEngine.collectAsState().value,
            onSelect = {
                if (it == SearchEngine.CUSTOM) open(SettingsDialog.CUSTOM_ENGINE) else { settings.setSearchEngine(it); onDismiss() }
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.CUSTOM_ENGINE -> CustomSearchDialog(
            initial = settings.customSearchUrl.collectAsState().value,
            onSave = {
                settings.setCustomSearchUrl(it)
                settings.setSearchEngine(SearchEngine.CUSTOM)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = settings.theme.collectAsState().value,
            onSelect = { settings.setTheme(it); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.DNS -> ChoiceDialog(
            title = stringResource(R.string.settings_dns),
            options = DnsProvider.entries.map { it to it.title },
            selected = settings.dnsProvider.collectAsState().value,
            onSelect = {
                settings.setDnsProvider(it)
                if (it == DnsProvider.CUSTOM) open(SettingsDialog.DNS_CUSTOM) else { components.applyPrivacySettings(); onDismiss() }
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.DNS_CUSTOM -> DnsCustomDialog(
            initial = settings.dnsCustomUrl.collectAsState().value,
            onSave = {
                settings.setDnsCustomUrl(it)
                components.applyPrivacySettings()
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.PASSWORD -> PasswordDialog(onDismiss)
        SettingsDialog.CLEAR_DATA -> ClearDataDialog(onConfirm = { onDismiss(); onClearData() }, onDismiss = onDismiss)
        SettingsDialog.LOG -> DiagnosticsLogDialog(onDismiss)
        SettingsDialog.DATA_SAVER -> ChoiceDialog(
            title = stringResource(R.string.settings_data_saver),
            options = DataSaverMode.entries.map { it to dataSaverLabel(it) },
            selected = settings.dataSaver.collectAsState().value,
            onSelect = { settings.setDataSaver(it); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.TEXT_ZOOM -> ChoiceDialog(
            title = stringResource(R.string.settings_text_zoom),
            options = com.gtarayici.browser.sites.TextZoom.CHOICES.map { it to "%$it" },
            selected = settings.textZoom.collectAsState().value,
            onSelect = { settings.setTextZoom(it); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.SITES -> SitePrefsDialog(onDismiss)
        SettingsDialog.CACHE_SIZE -> ChoiceDialog(
            title = stringResource(R.string.settings_cache_size),
            options = CacheSize.entries.map { it to cacheSizeLabel(it) },
            selected = settings.cacheSize.collectAsState().value,
            onSelect = { settings.setCacheSize(it); onDismiss() },
            onDismiss = onDismiss,
        )
    }
}
