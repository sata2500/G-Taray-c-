package com.gtarayici.browser.ui.screens

import androidx.core.net.toUri
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Password
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.gtarayici.browser.security.AppLock
import androidx.compose.material3.OutlinedTextField
import com.gtarayici.browser.settings.DnsProvider
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.gtarayici.browser.BuildConfig
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.search.SearchEngine
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.update.UpdateState
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun SettingsScreen(onClearData: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val components = context.components
    val settings = components.settings
    val engine by settings.searchEngine.collectAsState()
    val theme by settings.theme.collectAsState()
    val strict by settings.strictTracking.collectAsState()
    val httpsOnly by settings.httpsOnly.collectAsState()
    val gpc by settings.globalPrivacyControl.collectAsState()
    val cookieBanner by settings.cookieBannerReject.collectAsState()
    val dnsProvider by settings.dnsProvider.collectAsState()
    val dnsCustom by settings.dnsCustomUrl.collectAsState()
    val dnsStrict by settings.dnsStrict.collectAsState()
    val lockPrivate by settings.lockPrivateTabs.collectAsState()
    val securePrivate by settings.securePrivateTabs.collectAsState()
    val backgroundPlay by settings.backgroundPlay.collectAsState()
    val sponsorBlock by settings.sponsorBlock.collectAsState()
    val autoPip by settings.autoPictureInPicture.collectAsState()
    val autoUpdate by settings.autoUpdateCheck.collectAsState()
    val videoDownloadButton by settings.videoDownloadButton.collectAsState()
    val updateState by components.updater.state.collectAsState()
    var dnsDialog by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    var diagDialog by remember { mutableStateOf(false) }
    var dnsCustomDialog by remember { mutableStateOf(false) }

    var isDefault by remember { mutableStateOf(isDefaultBrowser(context)) }
    // Rol isteği penceresi startActivityForResult ile açılmazsa Android onu sessizce kapatır.
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultBrowser(context)
    }
    val requestDefaultBrowser = {
        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.getSystemService<RoleManager>() else null
        val canRequestRole = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
        if (canRequestRole) {
            roleLauncher.launch(roleManager!!.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
        } else {
            // Zaten varsayılansa veya rol isteği desteklenmiyorsa sistem ayarı açılır.
            runCatching { roleLauncher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                .onFailure { runCatching { roleLauncher.launch(Intent(Settings.ACTION_SETTINGS)) } }
        }
    }

    var engineDialog by remember { mutableStateOf(false) }
    var themeDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }

    OverlayScaffoldSettings(onBack) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.settings_general))
            ClickRow(Icons.Filled.Search, stringResource(R.string.settings_search_engine), engine.title) {
                engineDialog = true
            }
            ClickRow(Icons.Filled.Palette, stringResource(R.string.settings_theme), themeLabel(theme)) {
                themeDialog = true
            }
            ClickRow(
                Icons.Filled.OpenInBrowser,
                stringResource(R.string.settings_default_browser),
                stringResource(
                    if (isDefault) R.string.settings_default_browser_active else R.string.settings_default_browser_summary,
                ),
            ) { requestDefaultBrowser() }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_privacy))
            SwitchRow(
                stringResource(R.string.settings_strict_tracking),
                stringResource(R.string.settings_strict_tracking_summary),
                strict,
            ) {
                settings.setStrictTracking(it)
                components.applyPrivacySettings()
            }
            SwitchRow(
                stringResource(R.string.settings_https_only),
                stringResource(R.string.settings_https_only_summary),
                httpsOnly,
            ) {
                settings.setHttpsOnly(it)
                components.applyPrivacySettings()
            }
            SwitchRow(stringResource(R.string.settings_gpc), stringResource(R.string.settings_gpc_summary), gpc) {
                settings.setGlobalPrivacyControl(it)
                components.applyPrivacySettings()
            }
            SwitchRow(
                stringResource(R.string.settings_cookie_banner),
                stringResource(R.string.settings_cookie_banner_summary),
                cookieBanner,
            ) {
                settings.setCookieBannerReject(it)
                components.applyPrivacySettings()
            }
            ClickRow(
                Icons.Filled.Dns,
                stringResource(R.string.settings_dns),
                if (dnsProvider == DnsProvider.CUSTOM) dnsCustom.ifBlank { dnsProvider.title } else dnsProvider.title,
            ) { dnsDialog = true }
            if (dnsProvider != DnsProvider.OFF) {
                SwitchRow(
                    stringResource(R.string.settings_dns_strict),
                    stringResource(R.string.settings_dns_strict_summary),
                    dnsStrict,
                ) {
                    settings.setDnsStrict(it)
                    components.applyPrivacySettings()
                }
            }
            ClickRow(
                Icons.Filled.Password,
                stringResource(R.string.settings_app_password),
                stringResource(if (components.appLock.hasPassword) R.string.settings_app_password_on else R.string.settings_app_password_off),
            ) { passwordDialog = true }
            SwitchRow(
                stringResource(R.string.settings_lock_private),
                stringResource(R.string.settings_lock_private_summary),
                lockPrivate,
            ) { settings.setLockPrivateTabs(it) }
            SwitchRow(
                stringResource(R.string.settings_secure_private),
                stringResource(R.string.settings_secure_private_summary),
                securePrivate,
            ) { settings.setSecurePrivateTabs(it) }
            ClickRow(
                Icons.Filled.CleaningServices,
                stringResource(R.string.settings_clear_data),
                stringResource(R.string.settings_clear_data_summary),
            ) { clearDialog = true }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_media))
            SwitchRow(
                stringResource(R.string.settings_background_play),
                stringResource(R.string.settings_background_play_summary),
                backgroundPlay,
            ) { settings.setBackgroundPlay(it) }
            SwitchRow(
                stringResource(R.string.settings_sponsorblock),
                stringResource(R.string.settings_sponsorblock_summary),
                sponsorBlock,
            ) { settings.setSponsorBlock(it) }
            SwitchRow(
                stringResource(R.string.settings_auto_pip),
                stringResource(R.string.settings_auto_pip_summary),
                autoPip,
            ) { settings.setAutoPictureInPicture(it) }
            SwitchRow(
                stringResource(R.string.settings_video_download_button),
                stringResource(R.string.settings_video_download_button_summary),
                videoDownloadButton,
            ) { settings.setVideoDownloadButton(it) }
            ClickRow(
                Icons.Filled.BugReport,
                stringResource(R.string.diag_title),
                stringResource(R.string.diag_summary),
            ) { diagDialog = true }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_updates))
            ClickRow(
                Icons.Filled.SystemUpdate,
                stringResource(R.string.update_check),
                when (val state = updateState) {
                    UpdateState.Checking -> stringResource(R.string.update_checking)
                    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date, components.updater.currentVersion)
                    is UpdateState.Available -> stringResource(R.string.update_title, state.release.version)
                    is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.release.version)
                    else -> stringResource(R.string.update_check_summary, components.updater.currentVersion)
                },
            ) { components.updater.check() }
            SwitchRow(
                stringResource(R.string.update_auto),
                stringResource(R.string.update_auto_summary),
                autoUpdate,
            ) { settings.setAutoUpdateCheck(it) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_about))
            InfoRow(Icons.Filled.Info, stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
            InfoRow(
                Icons.Filled.Language,
                stringResource(R.string.settings_engine),
                "GeckoView ${org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION}",
            )
            InfoRow(Icons.Filled.Code, stringResource(R.string.settings_license), stringResource(R.string.settings_license_value))
        }
    }

    if (engineDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_search_engine),
            options = SearchEngine.entries.map { it to it.title },
            selected = engine,
            onSelect = { settings.setSearchEngine(it); engineDialog = false },
            onDismiss = { engineDialog = false },
        )
    }
    if (passwordDialog) PasswordDialog(onDismiss = { passwordDialog = false })
    if (diagDialog) DiagnosticsDialog(onDismiss = { diagDialog = false })
    if (dnsDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_dns),
            options = DnsProvider.entries.map { it to it.title },
            selected = dnsProvider,
            onSelect = {
                dnsDialog = false
                settings.setDnsProvider(it)
                if (it == DnsProvider.CUSTOM) dnsCustomDialog = true else components.applyPrivacySettings()
            },
            onDismiss = { dnsDialog = false },
        )
    }
    if (dnsCustomDialog) {
        var value by remember { mutableStateOf(dnsCustom) }
        AlertDialog(
            onDismissRequest = { dnsCustomDialog = false },
            title = { Text(stringResource(R.string.settings_dns_custom_title)) },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_dns_custom_hint)) },
                    isError = value.isNotBlank() && !value.trim().startsWith("https://"),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = value.trim().startsWith("https://"),
                    onClick = {
                        settings.setDnsCustomUrl(value)
                        components.applyPrivacySettings()
                        dnsCustomDialog = false
                    },
                ) { Text(stringResource(R.string.profile_save)) }
            },
            dismissButton = { TextButton(onClick = { dnsCustomDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = theme,
            onSelect = { settings.setTheme(it); themeDialog = false },
            onDismiss = { themeDialog = false },
        )
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_data)) },
            text = { Text(stringResource(R.string.settings_clear_data_confirm)) },
            confirmButton = {
                TextButton(onClick = { clearDialog = false; onClearData() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun isDefaultBrowser(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService<RoleManager>()?.let { return it.isRoleHeld(RoleManager.ROLE_BROWSER) }
    }
    val intent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
    val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return info?.activityInfo?.packageName == context.packageName
}

@Composable
private fun OverlayScaffoldSettings(onBack: () -> Unit, content: @Composable (Modifier) -> Unit) =
    com.gtarayici.browser.ui.components.OverlayScaffold(
        title = stringResource(R.string.menu_settings),
        onBack = onBack,
        content = content,
    )

/** Tanılama kaydı: resim içinde resim gibi cihaza bağlı adımların zaman çizelgesi; sorun bildirmek için paylaşılır. */
@Composable
private fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val text = remember {
        "G-Tarayıcı ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
            "GeckoView ${org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION}\n\n" +
            com.gtarayici.browser.diagnostics.Diagnostics.dump().ifBlank { resources.getString(R.string.diag_empty) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diag_title)) },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            }) { Text(stringResource(R.string.action_share)) }
        },
        dismissButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("G-Tarayıcı tanılama", text))
                Toast.makeText(context, R.string.diag_copied, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.diag_copy)) }
        },
    )
}

/** Uygulama parolasını belirleme, değiştirme veya kaldırma. */
@Composable
private fun PasswordDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lock = context.components.appLock
    val hasPassword = lock.hasPassword
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val field = @Composable { value: String, label: Int, onChange: (String) -> Unit ->
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); error = null },
            singleLine = true,
            label = { Text(stringResource(label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (hasPassword) R.string.password_change_title else R.string.password_set_title)) },
        text = {
            Column {
                if (hasPassword) field(current, R.string.password_current) { current = it }
                field(new, R.string.password_new) { new = it }
                field(repeat, R.string.password_repeat) { repeat = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (hasPassword) {
                    TextButton(onClick = {
                        if (lock.verifyPassword(current)) {
                            lock.removePassword()
                            Toast.makeText(context, R.string.password_removed, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            error = resources.getString(R.string.lock_wrong_password)
                        }
                    }) { Text(stringResource(R.string.password_remove), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    hasPassword && !lock.verifyPassword(current) -> resources.getString(R.string.lock_wrong_password)
                    new.length < AppLock.MIN_PASSWORD_LENGTH -> resources.getString(R.string.password_too_short)
                    new != repeat -> resources.getString(R.string.password_mismatch)
                    else -> null
                }
                if (error == null) {
                    lock.setPassword(new)
                    Toast.makeText(context, R.string.password_saved, Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { Text(stringResource(R.string.profile_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun themeLabel(mode: ThemeMode) = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
    )
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, onClick = { onSelect(value) })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
