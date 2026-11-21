package com.gtarayici.browser.ui.screens

import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.ui.feedback.ErrorArea
import com.gtarayici.browser.ui.feedback.HumanErrors
import com.gtarayici.browser.ui.designsystem.ErrorNotice
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gtarayici.browser.vpn.VpnCoreState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.ui.designsystem.AddFab
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.vpn.OpenVpnConfig
import com.gtarayici.browser.vpn.VpnStatus
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch

@Composable
fun VpnScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val vpn = context.components.vpn
    val servers by vpn.servers.collectAsState()
    val status by vpn.status.collectAsState()
    LaunchedEffect(status is VpnStatus.Error) {
        if (status is VpnStatus.Error) context.components.hints.offer(com.gtarayici.browser.ui.feedback.Hint.VpnError)
    }
    val log by vpn.log.collectAsState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    // Dosyadan gelen ve kullanıcı adı/parola isteyen OpenVPN yapılandırması, ekleme penceresinde açılır.
    var pendingText by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf(vpn.activeServerId) }
    val core = context.components.vpnCore
    val coreState by core.state.collectAsState()
    // Bileşen kurulum ekranından dönülünce durumu yenile.
    LifecycleResumeEffect(Unit) {
        core.refresh()
        onPauseOrDispose { }
    }
    var helpOpen by remember { mutableStateOf(servers.isEmpty()) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull().orEmpty()
        if (OpenVpnConfig.looksLike(text) && OpenVpnConfig.needsCredentials(text)) {
            pendingText = text
            adding = true
            return@rememberLauncherForActivityResult
        }
        vpn.addServer(text)
            .onSuccess { selectedId = it.id; helpOpen = false }
            .onFailure { context.components.messenger.error(HumanErrors.message(ErrorArea.VpnLink, it)) }
    }

    OverlayScaffold(
        title = stringResource(R.string.vpn_title),
        onBack = onBack,
        floatingActionButton = {
            AddFab(stringResource(R.string.vpn_add)) { adding = true }
        },
    ) { modifier ->
        LazyColumn(modifier) {
            if (coreState !is VpnCoreState.Ready) {
                item { VpnCoreCard(coreState, onInstall = { core.install() }) }
            }
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
                                    is VpnStatus.Error -> stringResource(R.string.err_lead_vpn_connect)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        (status as? VpnStatus.Error)?.let { failed ->
                            Spacer(Modifier.height(12.dp))
                            val area = ErrorArea.VpnConnect
                            ErrorNotice(
                                message = UiText.res(HumanErrors.advice(area, HumanErrors.classify(area, failed.message))),
                                detail = failed.message,
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
                                            { resources.getString(R.string.vpn_test_result, it.ip ?: "?", it.country ?: "?", it.latencyMs.toInt()) },
                                            { HumanErrors.message(ErrorArea.VpnTest, it).resolve(context) },
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { helpOpen = !helpOpen },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Help, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.vpn_help_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(if (helpOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                        if (helpOpen) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.vpn_help_body), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
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
                ListRow(
                    leading = { RadioButton(selected = isSelected, onClick = null) },
                    headline = { Text(server.name) },
                    supporting = { Text(server.protocol) },
                    trailing = {
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
        var text by remember { mutableStateOf(pendingText) }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val isOpenVpn = OpenVpnConfig.looksLike(text)
        // Yazarken denetim: tanınmayan veya bozuk bağlantı hemen alanın altında söylenir.
        val linkProblem = remember(text) { com.gtarayici.browser.ui.feedback.InputValidation.vpnLink(text) }
        val close = { adding = false; pendingText = "" }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.vpn_add)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = null },
                        placeholder = { Text(stringResource(R.string.vpn_add_hint)) },
                        minLines = 4,
                        maxLines = if (isOpenVpn) 4 else 10,
                        isError = error != null || linkProblem != null,
                        supportingText = (error ?: linkProblem?.let { stringResource(it) })?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isOpenVpn) {
                        Text(
                            stringResource(R.string.vpn_openvpn_detected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        if (OpenVpnConfig.needsCredentials(text)) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it; error = null },
                                singleLine = true,
                                label = { Text(stringResource(R.string.vpn_username)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; error = null },
                                singleLine = true,
                                label = { Text(stringResource(R.string.vpn_password)) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                            text = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        }) {
                            Icon(Icons.Filled.ContentPaste, null)
                            Text(stringResource(R.string.vpn_paste), modifier = Modifier.padding(start = 8.dp))
                        }
                        TextButton(onClick = {
                            close()
                            importFile.launch(arrayOf("*/*"))
                        }) {
                            Icon(Icons.Filled.FileOpen, null)
                            Text(stringResource(R.string.vpn_import_file), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank() && linkProblem == null, onClick = {
                    if (isOpenVpn && OpenVpnConfig.needsCredentials(text) && username.isBlank()) {
                        error = resources.getString(R.string.vpn_credentials_required)
                        return@TextButton
                    }
                    vpn.addServer(text, username, password)
                        .onSuccess { added ->
                            selectedId = added.id
                            close()
                        }
                        .onFailure { error = HumanErrors.message(ErrorArea.VpnLink, it).resolve(context) }
                }) { Text(stringResource(R.string.profile_save)) }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** VPN çekirdekleri ayrı paket olarak gelir; kurulu değilse bu kart gösterilir. */
@Composable
private fun VpnCoreCard(state: VpnCoreState, onInstall: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.vpn_core_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.vpn_core_body), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            when (state) {
                is VpnCoreState.Downloading -> {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.vpn_core_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                }
                VpnCoreState.Installing -> Text(stringResource(R.string.vpn_core_installing), style = MaterialTheme.typography.bodySmall)
                else -> {
                    if (state is VpnCoreState.Failed) {
                        ErrorNotice(
                            message = HumanErrors.message(ErrorArea.VpnCore, state.message),
                            detail = state.message,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    FilledTonalButton(onClick = onInstall) { Text(stringResource(R.string.vpn_core_install)) }
                }
            }
        }
    }
}
