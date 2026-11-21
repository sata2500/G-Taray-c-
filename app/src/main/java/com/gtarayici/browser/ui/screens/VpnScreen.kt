package com.gtarayici.browser.ui.screens

import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.qr.QrScannerScreen
import com.gtarayici.browser.ui.designsystem.AddFab
import com.gtarayici.browser.ui.designsystem.ErrorNotice
import com.gtarayici.browser.ui.designsystem.ListRow
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.designsystem.SectionHeader
import com.gtarayici.browser.ui.feedback.ErrorArea
import com.gtarayici.browser.ui.feedback.HumanErrors
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.vpn.OpenVpnConfig
import com.gtarayici.browser.vpn.VpnCoreState
import com.gtarayici.browser.vpn.VpnLabels
import com.gtarayici.browser.vpn.VpnServer
import com.gtarayici.browser.vpn.VpnStatus
import com.gtarayici.browser.vpn.VpnTestResult
import kotlinx.coroutines.launch

/** Sunucu eklemenin adımları. */
private sealed interface AddStep {
    data object Choose : AddStep
    data object Manual : AddStep
    data object Subscription : AddStep
    data object Qr : AddStep
    /** Bulunan sunucular eklenmeden önce gösterilir; adları burada değiştirilebilir. */
    data class Preview(val servers: List<VpnServer>) : AddStep
}

/**
 * Tarayıcı VPN'i. Sunucular listelenir, dokunulan sunucuya bağlanılır; adlar kullanıcı tarafından verilebilir.
 * Ekleme teknik bilgi gerektirmesin diye panodan, QR koddan, dosyadan veya abonelik adresinden yapılabilir;
 * eklenmeden önce bulunanlar gösterilir.
 */
@Composable
fun VpnScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val vpn = components.vpn
    val messenger = components.messenger
    val servers by vpn.servers.collectAsState()
    val status by vpn.status.collectAsState()
    val tests by vpn.lastTests.collectAsState()
    val log by vpn.log.collectAsState()
    val core = components.vpnCore
    val coreState by core.state.collectAsState()
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<AddStep?>(null) }
    var renaming by remember { mutableStateOf<VpnServer?>(null) }
    var showLog by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(servers.isEmpty()) }
    var testing by remember { mutableStateOf(false) }

    LaunchedEffect(status is VpnStatus.Error) {
        if (status is VpnStatus.Error) components.hints.offer(com.gtarayici.browser.ui.feedback.Hint.VpnError)
    }
    // Bileşen kurulum ekranından dönülünce durumu yenile.
    LifecycleResumeEffect(Unit) {
        core.refresh()
        onPauseOrDispose { }
    }

    /** Metni çözümleyip ön izlemeye geçer; anlaşılmazsa insan diliyle söyler. */
    val preview: (String) -> Unit = { text ->
        vpn.parse(text)
            .onSuccess { step = AddStep.Preview(it) }
            .onFailure {
                step = AddStep.Choose
                messenger.error(HumanErrors.message(ErrorArea.VpnLink, it))
            }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull().orEmpty()
        preview(text)
    }

    val active = servers.firstOrNull { it.id == vpn.activeServerId } ?: servers.firstOrNull()

    OverlayScaffold(
        title = stringResource(R.string.vpn_title),
        onBack = onBack,
        floatingActionButton = { AddFab(stringResource(R.string.vpn_add)) { step = AddStep.Choose } },
    ) { modifier ->
        LazyColumn(modifier) {
            if (coreState !is VpnCoreState.Ready) {
                item { VpnCoreCard(coreState, onInstall = { core.install() }) }
            }
            item {
                ConnectionCard(
                    status = status,
                    server = (status as? VpnStatus.Connected)?.server ?: active,
                    test = ((status as? VpnStatus.Connected)?.server ?: active)?.let { tests[it.id] },
                    testing = testing,
                    hasServers = servers.isNotEmpty(),
                    onToggle = {
                        when (status) {
                            VpnStatus.Off, is VpnStatus.Error -> active?.let(vpn::connect) ?: run { step = AddStep.Choose }
                            else -> vpn.disconnect()
                        }
                    },
                    onTest = {
                        testing = true
                        scope.launch {
                            vpn.testActive()
                                .onFailure { messenger.error(HumanErrors.message(ErrorArea.VpnTest, it)) }
                            testing = false
                        }
                    },
                    onAdd = { step = AddStep.Choose },
                )
            }
            if (servers.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.vpn_my_servers), Modifier.padding(top = 8.dp)) }
                items(servers, key = { it.id }) { server ->
                    val connected = (status as? VpnStatus.Connected)?.server?.id == server.id
                    ServerRow(
                        server = server,
                        selected = server.id == active?.id,
                        connected = connected,
                        connecting = status is VpnStatus.Connecting && server.id == vpn.activeServerId,
                        test = tests[server.id],
                        onConnect = { if (!connected) vpn.connect(server) },
                        onRename = { renaming = server },
                        onDelete = {
                            val index = servers.indexOf(server)
                            vpn.removeServer(server)
                            messenger.undoable(UiText.res(R.string.vpn_server_deleted, server.name), key = "vpn-${server.id}") {
                                vpn.restore(server, index)
                            }
                        },
                    )
                }
            }
            item {
                HelpCard(open = helpOpen, onToggle = { helpOpen = !helpOpen })
                Text(
                    stringResource(R.string.vpn_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (log.isNotEmpty()) {
                    TextButton(onClick = { showLog = !showLog }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Filled.Terminal, null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.vpn_logs), modifier = Modifier.padding(start = 8.dp))
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
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    when (val current = step) {
        null -> Unit
        AddStep.Choose -> AddSheet(
            onDismiss = { step = null },
            onPaste = {
                val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (text.isBlank()) {
                    messenger.info(UiText.res(R.string.vpn_clipboard_empty))
                } else {
                    preview(text)
                }
            },
            onQr = { step = AddStep.Qr },
            onFile = {
                step = null
                importFile.launch(arrayOf("*/*"))
            },
            onSubscription = { step = AddStep.Subscription },
            onManual = { step = AddStep.Manual },
        )
        AddStep.Qr -> Box(Modifier.fillMaxSize()) {
            QrScannerScreen(
                onOpen = {},
                onSearch = {},
                onBack = { step = AddStep.Choose },
                onRawResult = { preview(it) },
            )
        }
        AddStep.Manual -> ManualDialog(onDismiss = { step = null }, onNext = { preview(it) })
        AddStep.Subscription -> SubscriptionDialog(
            onDismiss = { step = null },
            onFetch = { url, done ->
                scope.launch {
                    vpn.fetchSubscription(url)
                        .onSuccess { preview(it) }
                        .onFailure { messenger.error(HumanErrors.message(ErrorArea.VpnLink, it)) }
                    done()
                }
            },
        )
        is AddStep.Preview -> PreviewDialog(
            servers = current.servers,
            onDismiss = { step = null },
            onAdd = { finalServers, connect ->
                vpn.add(finalServers)
                step = null
                helpOpen = false
                messenger.success(
                    if (finalServers.size == 1) UiText.res(R.string.vpn_added_one, finalServers.first().name)
                    else UiText.plural(R.plurals.vpn_added_many, finalServers.size, finalServers.size),
                )
                if (connect) vpn.connect(finalServers.first())
            },
        )
    }

    renaming?.let { server ->
        var name by remember(server.id) { mutableStateOf(server.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.vpn_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.vpn_server_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vpn.rename(server, name); renaming = null }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Büyük aç/kapa düğmesi, bağlı sunucu ve çıkış bilgisi. */
@Composable
private fun ConnectionCard(
    status: VpnStatus,
    server: VpnServer?,
    test: VpnTestResult?,
    testing: Boolean,
    hasServers: Boolean,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val on = status is VpnStatus.Connected
    val busy = status is VpnStatus.Connecting
    val error = status is VpnStatus.Error
    val buttonColor by animateColorAsState(
        when {
            on -> colors.primary
            error -> colors.error
            busy -> colors.secondaryContainer
            else -> colors.surfaceContainerHighest
        },
        label = "vpn-button",
    )
    val iconColor = when {
        on -> colors.onPrimary
        error -> colors.onError
        else -> colors.onSurfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (on) colors.primaryContainer else colors.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val label = stringResource(if (on || busy) R.string.vpn_disconnect else R.string.vpn_connect)
            Surface(
                onClick = if (hasServers || on || busy) onToggle else onAdd,
                shape = CircleShape,
                color = buttonColor,
                modifier = Modifier.size(104.dp).semantics { contentDescription = label },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 4.dp)
                    } else {
                        Icon(Icons.Filled.PowerSettingsNew, null, tint = iconColor, modifier = Modifier.size(52.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                when (status) {
                    VpnStatus.Off -> stringResource(R.string.vpn_state_off)
                    VpnStatus.Connecting -> stringResource(R.string.vpn_connecting)
                    is VpnStatus.Connected -> stringResource(R.string.vpn_state_on)
                    is VpnStatus.Error -> stringResource(R.string.err_lead_vpn_connect)
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            when {
                server != null -> Text(
                    "${server.name} · ${VpnLabels.protocol(server.protocol)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                !hasServers -> Text(
                    stringResource(R.string.vpn_no_servers_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (on && test != null) {
                Text(
                    testSummary(test),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            (status as? VpnStatus.Error)?.let { failed ->
                Spacer(Modifier.height(12.dp))
                val area = ErrorArea.VpnConnect
                ErrorNotice(
                    message = UiText.res(HumanErrors.advice(area, HumanErrors.classify(area, failed.message))),
                    detail = failed.message,
                )
                Text(
                    stringResource(R.string.vpn_error_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (on) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onTest, enabled = !testing) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Speed, null, modifier = Modifier.size(18.dp))
                    }
                    Text(stringResource(R.string.vpn_test), modifier = Modifier.padding(start = 8.dp))
                }
            } else if (!hasServers) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAdd) { Text(stringResource(R.string.vpn_add)) }
            }
        }
    }
}

/** "🇩🇪 Almanya · 45 ms · 203.0.113.7" */
@Composable
private fun testSummary(test: VpnTestResult): String = listOfNotNull(
    listOfNotNull(VpnLabels.flag(test.country).ifBlank { null }, VpnLabels.country(test.country) ?: test.country)
        .joinToString(" ").ifBlank { null },
    stringResource(R.string.vpn_latency, test.latencyMs.toInt()),
    test.ip,
).joinToString(" · ")

@Composable
private fun ServerRow(
    server: VpnServer,
    selected: Boolean,
    connected: Boolean,
    connecting: Boolean,
    test: VpnTestResult?,
    onConnect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListRow(
        leading = {
            if (connecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else RadioButton(selected = selected, onClick = null)
        },
        headline = { Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supporting = {
            val parts = listOfNotNull(
                if (connected) stringResource(R.string.vpn_state_on) else null,
                VpnLabels.protocol(server.protocol),
                test?.let { listOfNotNull(VpnLabels.flag(it.country).ifBlank { null }, VpnLabels.country(it.country)).joinToString(" ").ifBlank { null } },
                test?.let { stringResource(R.string.vpn_latency, it.latencyMs.toInt()) },
            )
            Text(
                parts.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_menu)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!connected) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.vpn_connect)) },
                            leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, null) },
                            onClick = { menu = false; onConnect() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vpn_rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onConnect),
    )
}

@Composable
private fun HelpCard(open: Boolean, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onToggle),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Help, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.vpn_help_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.vpn_help_simple), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(
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
private fun AddOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, summary: Int, onClick: () -> Unit) {
    ListRow(
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headline = { Text(stringResource(title)) },
        supporting = { Text(stringResource(summary)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ManualDialog(onDismiss: () -> Unit, onNext: (String) -> Unit) {
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
private fun SubscriptionDialog(onDismiss: () -> Unit, onFetch: (String, () -> Unit) -> Unit) {
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
private fun PreviewDialog(servers: List<VpnServer>, onDismiss: () -> Unit, onAdd: (List<VpnServer>, connect: Boolean) -> Unit) {
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

/** VPN çekirdekleri ayrı paket olarak gelir; kurulu değilse bu kart gösterilir. */
@Composable
private fun VpnCoreCard(state: VpnCoreState, onInstall: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VpnKey, null)
                Text(stringResource(R.string.vpn_core_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
            }
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
                        ErrorNotice(message = HumanErrors.message(ErrorArea.VpnCore, state.message), detail = state.message)
                        Spacer(Modifier.height(8.dp))
                    }
                    FilledTonalButton(onClick = onInstall) { Text(stringResource(R.string.vpn_core_install)) }
                }
            }
        }
    }
}
