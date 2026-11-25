package com.gtarayici.browser.ui.screens

import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.clickable
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.qr.QrScannerScreen
import com.gtarayici.browser.ui.designsystem.AddFab
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.designsystem.SectionHeader
import com.gtarayici.browser.ui.feedback.ErrorArea
import com.gtarayici.browser.ui.feedback.HumanErrors
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.vpn.VpnCoreState
import com.gtarayici.browser.vpn.VpnServer
import com.gtarayici.browser.vpn.VpnStatus
import kotlinx.coroutines.launch

/** Sunucu eklemenin adımları. */
internal sealed interface AddStep {
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
            item { PrivateOnlyRow(vpn) }
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

/** "Yalnızca gizli sekmelerde": VPN normal sekmelere uygulanmaz. */
@Composable
private fun PrivateOnlyRow(vpn: com.gtarayici.browser.vpn.VpnManager) {
    val privateOnly by vpn.privateOnly.collectAsState()
    com.gtarayici.browser.ui.designsystem.ListRow(
        leading = { Icon(androidx.compose.material.icons.Icons.Rounded.VisibilityOff, null) },
        headline = { Text(stringResource(R.string.vpn_private_only)) },
        supporting = { Text(stringResource(R.string.vpn_private_only_summary)) },
        trailing = { androidx.compose.material3.Switch(checked = privateOnly, onCheckedChange = { vpn.setPrivateOnly(it) }) },
        modifier = Modifier.clickable { vpn.setPrivateOnly(!privateOnly) },
    )
}
