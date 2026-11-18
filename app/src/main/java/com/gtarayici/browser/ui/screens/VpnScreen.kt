package com.gtarayici.browser.ui.screens

import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.ui.components.OverlayScaffold
import com.gtarayici.browser.vpn.VpnStatus
import kotlinx.coroutines.launch

@Composable
fun VpnScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vpn = context.components.vpn
    val servers by vpn.servers.collectAsState()
    val status by vpn.status.collectAsState()
    val log by vpn.log.collectAsState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf(vpn.activeServerId) }

    OverlayScaffold(
        title = stringResource(R.string.vpn_title),
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.vpn_add)) },
            )
        },
    ) { modifier ->
        LazyColumn(modifier) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (status) {
                            is VpnStatus.Connected -> MaterialTheme.colorScheme.primaryContainer
                            is VpnStatus.Error -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VpnKey, null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                when (val s = status) {
                                    VpnStatus.Off -> stringResource(R.string.vpn_off)
                                    VpnStatus.Connecting -> stringResource(R.string.vpn_connecting)
                                    is VpnStatus.Connected -> stringResource(R.string.vpn_connected, s.server.name)
                                    is VpnStatus.Error -> stringResource(R.string.vpn_error, s.message)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (status is VpnStatus.Error) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.vpn_error_blocked), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row {
                            val selected = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()
                            if (status is VpnStatus.Off) {
                                FilledTonalButton(onClick = { selected?.let(vpn::connect) }, enabled = selected != null) {
                                    Text(stringResource(R.string.vpn_connect))
                                }
                            } else {
                                FilledTonalButton(onClick = { vpn.disconnect(); testResult = null }) {
                                    Text(stringResource(R.string.vpn_disconnect))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            if (status is VpnStatus.Connected) {
                                OutlinedButton(onClick = {
                                    testResult = "…"
                                    scope.launch {
                                        testResult = vpn.test().fold(
                                            { context.getString(R.string.vpn_test_result, it.ip ?: "?", it.country ?: "?", it.latencyMs.toInt()) },
                                            { context.getString(R.string.vpn_test_failed, it.message ?: "") },
                                        )
                                    }
                                }) { Text(stringResource(R.string.vpn_test)) }
                            }
                        }
                        testResult?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (log.isNotEmpty()) {
                            TextButton(onClick = { showLog = !showLog }) { Text(stringResource(R.string.vpn_logs)) }
                        }
                    }
                }
                Text(
                    stringResource(R.string.vpn_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            if (showLog) {
                item {
                    SelectionContainer {
                        Text(
                            log.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
            if (servers.isEmpty()) {
                item {
                    Text(stringResource(R.string.vpn_empty), modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(servers, key = { it.id }) { server ->
                val isSelected = server.id == (selectedId ?: servers.firstOrNull()?.id)
                ListItem(
                    leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                    headlineContent = { Text(server.name) },
                    supportingContent = { Text(server.protocol) },
                    trailingContent = {
                        IconButton(onClick = { vpn.removeServer(server) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                    },
                    modifier = Modifier.clickable {
                        selectedId = server.id
                        if (status !is VpnStatus.Off) vpn.connect(server)
                    },
                )
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (adding) {
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.vpn_add)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = null },
                        placeholder = { Text(stringResource(R.string.vpn_add_hint)) },
                        minLines = 4,
                        maxLines = 10,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                        text = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    }) {
                        Icon(Icons.Filled.ContentPaste, null)
                        Text(stringResource(R.string.vpn_paste), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vpn.addServer(text)
                        .onSuccess { added ->
                            selectedId = added.id
                            adding = false
                        }
                        .onFailure { error = context.getString(R.string.vpn_invalid, it.message ?: "") }
                }) { Text(stringResource(R.string.profile_save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
