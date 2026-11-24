package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.ui.designsystem.ListRow
import com.gtarayici.browser.vpn.OpenVpnConfig
import com.gtarayici.browser.vpn.VpnLabels
import com.gtarayici.browser.vpn.VpnServer

// VPN ekranında sunucu ekleme adımları: seçenekler, elle ekleme, abonelik, ön izleme.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSheet(
    onDismiss: () -> Unit,
    onPaste: () -> Unit,
    onQr: () -> Unit,
    onFile: () -> Unit,
    onSubscription: () -> Unit,
    onManual: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.vpn_add),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.vpn_add_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            AddOption(Icons.Filled.ContentPaste, R.string.vpn_add_paste, R.string.vpn_add_paste_summary, onPaste)
            AddOption(Icons.Filled.QrCodeScanner, R.string.vpn_add_qr, R.string.vpn_add_qr_summary, onQr)
            AddOption(Icons.Filled.FileOpen, R.string.vpn_add_file, R.string.vpn_add_file_summary, onFile)
            AddOption(Icons.Filled.Link, R.string.vpn_add_subscription, R.string.vpn_add_subscription_summary, onSubscription)
            AddOption(Icons.Filled.Keyboard, R.string.vpn_add_manual, R.string.vpn_add_manual_summary, onManual)
        }
    }
}

@Composable
internal fun AddOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, summary: Int, onClick: () -> Unit) {
    ListRow(
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headline = { Text(stringResource(title)) },
        supporting = { Text(stringResource(summary)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
internal fun ManualDialog(onDismiss: () -> Unit, onNext: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val problem = remember(text) { com.gtarayici.browser.ui.feedback.InputValidation.vpnLink(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vpn_add_manual)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.vpn_add_hint)) },
                minLines = 4,
                maxLines = 10,
                isError = problem != null,
                supportingText = problem?.let { { Text(stringResource(it)) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() && problem == null, onClick = { onNext(text) }) {
                Text(stringResource(R.string.vpn_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun SubscriptionDialog(onDismiss: () -> Unit, onFetch: (String, () -> Unit) -> Unit) {
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val valid = url.trim().let { it.startsWith("https://") || it.startsWith("http://") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.vpn_add_subscription)) },
        text = {
            Column {
                Text(stringResource(R.string.vpn_subscription_explain), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = {
                busy = true
                onFetch(url.trim()) { busy = false }
            }) { Text(stringResource(R.string.vpn_continue)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Bulunan sunucular: adları değiştirilebilir, OpenVPN için kullanıcı adı/parola istenir. */
@Composable
internal fun PreviewDialog(servers: List<VpnServer>, onDismiss: () -> Unit, onAdd: (List<VpnServer>, connect: Boolean) -> Unit) {
    val names = remember(servers) { mutableStateListOf(*servers.map { it.name }.toTypedArray()) }
    val openVpn = servers.singleOrNull()?.takeIf { it.isOpenVpn }
    val needsCredentials = openVpn != null && OpenVpnConfig.needsCredentials(openVpn.link)
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val ready = names.all { it.isNotBlank() } && (!needsCredentials || username.isNotBlank())
    val result = {
        servers.mapIndexed { i, server ->
            server.copy(
                name = names[i].trim(),
                username = if (needsCredentials) username.trim() else server.username,
                password = if (needsCredentials) password else server.password,
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (servers.size == 1) stringResource(R.string.vpn_found_one)
                else pluralStringResource(R.plurals.vpn_found_many, servers.size, servers.size),
            )
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.vpn_found_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                servers.forEachIndexed { i, server ->
                    OutlinedTextField(
                        value = names[i],
                        onValueChange = { names[i] = it },
                        singleLine = true,
                        label = { Text(VpnLabels.protocol(server.protocol)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (needsCredentials) {
                    Text(stringResource(R.string.vpn_credentials_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.vpn_username)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.vpn_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(enabled = ready, onClick = { onAdd(result(), true) }) { Text(stringResource(R.string.vpn_add_and_connect)) }
        },
        dismissButton = {
            TextButton(enabled = ready, onClick = { onAdd(result(), false) }) { Text(stringResource(R.string.vpn_add_only)) }
        },
    )
}
