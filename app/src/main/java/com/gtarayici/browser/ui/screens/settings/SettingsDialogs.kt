package com.gtarayici.browser.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.diagnostics.Diagnostics
import com.gtarayici.browser.security.AppLock
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.settings.CacheSize
import com.gtarayici.browser.settings.DataSaverMode
import com.gtarayici.browser.network.DataSaverPolicy
import com.gtarayici.browser.network.LinkInfo
import com.gtarayici.browser.ui.designsystem.Spacing
import com.gtarayici.browser.ui.feedback.InputValidation
import com.gtarayici.browser.ui.feedback.UiText

/** Tek seçimli liste penceresi (arama motoru, tema, DNS sağlayıcısı). */
@Composable
fun <T> ChoiceDialog(
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
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = Spacing.md))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun themeLabel(mode: ThemeMode) = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
fun dataSaverLabel(mode: DataSaverMode) = stringResource(
    when (mode) {
        DataSaverMode.OFF -> R.string.data_saver_off
        DataSaverMode.AUTO -> R.string.data_saver_auto
        DataSaverMode.ON -> R.string.data_saver_on
    },
)

@Composable
fun cacheSizeLabel(size: CacheSize) =
    if (size == CacheSize.AUTO) stringResource(R.string.cache_size_auto)
    else if (size.megabytes >= 1024) "${size.megabytes / 1024} GB" else "${size.megabytes} MB"

@Composable
fun connectionLabel(link: LinkInfo?): String {
    if (link == null) return stringResource(R.string.data_saver_link_none)
    val parts = buildList {
        add(stringResource(if (link.metered) R.string.data_saver_link_metered else R.string.data_saver_link_unmetered))
        if (link.downstreamKbps in 1 until DataSaverPolicy.SLOW_KBPS) add(stringResource(R.string.data_saver_link_slow))
        if (link.systemSaver) add(stringResource(R.string.data_saver_link_system))
    }
    return parts.joinToString(" · ")
}

/** Özel DNS-over-HTTPS adresi; yazarken denetlenir. */
@Composable
fun DnsCustomDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    val problem = InputValidation.dohUrl(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dns_custom_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.settings_dns_custom_hint)) },
                isError = problem != null,
                supportingText = problem?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank() && problem == null, onClick = { onSave(value) }) {
                Text(stringResource(R.string.profile_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Tarama verilerini silme onayı: geri alınamaz bir işlem olduğu için onay istenir. */
@Composable
fun ClearDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_clear_data)) },
        text = { Text(stringResource(R.string.settings_clear_data_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Tanılama kaydının tamamı: seçilebilir metin, paylaş ve kopyala. */
@Composable
fun DiagnosticsLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val text = remember { Diagnostics.dump().ifBlank { resources.getString(R.string.diag_empty) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diag_log_title)) },
        text = {
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState(Int.MAX_VALUE)),
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
                com.gtarayici.browser.diagnostics.ReportActions.copy(context, "G-Tarayıcı tanılama", text)
                context.components.messenger.copied(UiText.res(R.string.diag_copied))
            }) { Text(stringResource(R.string.diag_copy)) }
        },
    )
}

/** Uygulama parolasını belirleme, değiştirme veya kaldırma. */
@Composable
fun PasswordDialog(onDismiss: () -> Unit) {
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
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
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
                            context.components.messenger.success(UiText.res(R.string.password_removed))
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
                    context.components.messenger.success(UiText.res(R.string.password_saved))
                    onDismiss()
                }
            }) { Text(stringResource(R.string.profile_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
