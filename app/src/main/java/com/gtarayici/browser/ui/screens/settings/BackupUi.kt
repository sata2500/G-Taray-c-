package com.gtarayici.browser.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.backup.BackupCodec
import com.gtarayici.browser.backup.BackupException
import com.gtarayici.browser.backup.BackupOptions
import com.gtarayici.browser.backup.RestoreSummary
import com.gtarayici.browser.components
import com.gtarayici.browser.ui.feedback.UiText
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Ayarlar → Genel → Yedekleme satırları ve pencereleri. */
class BackupUi(
    val items: List<SettingItem>,
    val dialogs: @Composable () -> Unit,
)

@Composable
fun rememberBackupUi(): BackupUi {
    val context = LocalContext.current
    val components = context.components
    val manager = components.backup
    val messenger = components.messenger
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<BackupOptions?>(null) }
    var askOptions by remember { mutableStateOf(false) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    var wrongPassword by remember { mutableStateOf(false) }

    fun fail(error: Throwable) {
        val reason = (error as? BackupException)?.reason
        messenger.error(
            UiText.res(
                when (reason) {
                    BackupException.Reason.NOT_A_BACKUP -> R.string.backup_err_not_backup
                    BackupException.Reason.NEWER_VERSION -> R.string.backup_err_newer
                    else -> R.string.backup_err_io
                },
            ),
        )
    }

    fun restore(text: String, password: String?) {
        scope.launch {
            runCatching { manager.restore(text, password) }
                .onSuccess { summary ->
                    restoreText = null
                    messenger.success(UiText.Plain(summaryText(context, summary)))
                }
                .onFailure { error ->
                    if ((error as? BackupException)?.reason == BackupException.Reason.WRONG_PASSWORD) wrongPassword = true else {
                        restoreText = null
                        fail(error)
                    }
                }
        }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val chosen = options
        if (uri == null || chosen == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { manager.write(uri, chosen) }
                .onSuccess { messenger.success(UiText.res(if (chosen.password.isNullOrEmpty()) R.string.backup_done else R.string.backup_done_encrypted)) }
                .onFailure(::fail)
            options = null
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = manager.read(uri)
                text to BackupCodec.isEncrypted(text)
            }.onSuccess { (text, encrypted) ->
                if (encrypted) {
                    wrongPassword = false
                    restoreText = text
                } else {
                    restore(text, null)
                }
            }.onFailure(::fail)
        }
    }
    val importHtml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { manager.importBookmarksHtml(uri) }
                .onSuccess { messenger.success(UiText.plural(R.plurals.backup_bookmarks_imported, it, it)) }
                .onFailure(::fail)
        }
    }
    val exportHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { manager.exportBookmarksHtml(uri) }
                .onSuccess { messenger.success(UiText.plural(R.plurals.backup_bookmarks_exported, it, it)) }
                .onFailure(::fail)
        }
    }

    val items = listOf(
        SettingItem.Action(stringResource(R.string.backup_create), stringResource(R.string.backup_create_summary), Icons.Rounded.Backup) {
            askOptions = true
        },
        SettingItem.Action(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_summary), Icons.Rounded.Restore) {
            open.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
        },
        SettingItem.Action(stringResource(R.string.backup_import_html), stringResource(R.string.backup_import_html_summary), Icons.Rounded.BookmarkAdd) {
            importHtml.launch(arrayOf("text/html", "*/*"))
        },
        SettingItem.Action(stringResource(R.string.backup_export_html), stringResource(R.string.backup_export_html_summary), Icons.Rounded.Bookmarks) {
            exportHtml.launch("G-Tarayici-yer-imleri-${LocalDate.now()}.html")
        },
    )

    val dialogs: @Composable () -> Unit = {
        if (askOptions) {
            BackupOptionsDialog(
                onConfirm = { chosen ->
                    askOptions = false
                    options = chosen
                    create.launch("G-Tarayici-yedek-${LocalDate.now()}.json")
                },
                onDismiss = { askOptions = false },
            )
        }
        restoreText?.let { text ->
            RestorePasswordDialog(
                wrong = wrongPassword,
                onConfirm = { password -> wrongPassword = false; restore(text, password) },
                onDismiss = { restoreText = null },
            )
        }
    }
    return BackupUi(items, dialogs)
}

private fun summaryText(context: android.content.Context, s: RestoreSummary): String {
    val r = context.resources
    val parts = buildList {
        if (s.bookmarks > 0) add(r.getQuantityString(R.plurals.backup_n_bookmarks, s.bookmarks, s.bookmarks))
        if (s.passwords > 0) add(r.getQuantityString(R.plurals.backup_n_passwords, s.passwords, s.passwords))
        if (s.history > 0) add(r.getQuantityString(R.plurals.backup_n_history, s.history, s.history))
        if (s.profiles > 0) add(r.getQuantityString(R.plurals.backup_n_profiles, s.profiles, s.profiles))
        if (s.vpnServers > 0) add(r.getQuantityString(R.plurals.backup_n_vpn, s.vpnServers, s.vpnServers))
    }
    val added = if (parts.isEmpty()) context.getString(R.string.backup_restored_nothing_new) else parts.joinToString(", ")
    return context.getString(if (s.settings) R.string.backup_restored_with_settings else R.string.backup_restored, added)
}

@Composable
private fun BackupOptionsDialog(onConfirm: (BackupOptions) -> Unit, onDismiss: () -> Unit) {
    var history by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val mismatch = password.isNotEmpty() && repeat.isNotEmpty() && password != repeat
    val ok = password.isEmpty() || (password.length >= MIN_PASSWORD && password == repeat)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_contents), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = history, onCheckedChange = { history = it })
                    Text(stringResource(R.string.backup_include_history))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.backup_password)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (password.isNotEmpty() && password.length < MIN_PASSWORD) R.string.backup_password_short else R.string.backup_password_hint,
                            ),
                        )
                    },
                    isError = password.isNotEmpty() && password.length < MIN_PASSWORD,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (password.isNotEmpty()) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.backup_password_repeat)) },
                        isError = mismatch,
                        supportingText = if (mismatch) ({ Text(stringResource(R.string.backup_password_mismatch)) }) else null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(BackupOptions(history, password.ifEmpty { null })) }, enabled = ok) {
                Text(stringResource(R.string.backup_choose_file))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RestorePasswordDialog(wrong: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text(stringResource(R.string.backup_password)) },
                isError = wrong,
                supportingText = { Text(stringResource(if (wrong) R.string.backup_err_wrong_password else R.string.backup_password_needed)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty()) { Text(stringResource(R.string.backup_restore_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val MIN_PASSWORD = 8

