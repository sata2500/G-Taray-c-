package com.gtarayici.browser.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.ui.designsystem.ErrorNotice
import com.gtarayici.browser.ui.designsystem.ListRow
import com.gtarayici.browser.ui.feedback.ErrorArea
import com.gtarayici.browser.ui.feedback.HumanErrors
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.vpn.VpnCoreState
import com.gtarayici.browser.vpn.VpnLabels
import com.gtarayici.browser.vpn.VpnServer
import com.gtarayici.browser.vpn.VpnStatus
import com.gtarayici.browser.vpn.VpnTestResult

// VPN ekranının kartları ve satırları: bağlantı kartı, sunucu satırı, yardım, VPN bileşeni.

/** Büyük aç/kapa düğmesi, bağlı sunucu ve çıkış bilgisi. */
@Composable
internal fun ConnectionCard(
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
internal fun testSummary(test: VpnTestResult): String = listOfNotNull(
    listOfNotNull(VpnLabels.flag(test.country).ifBlank { null }, VpnLabels.country(test.country) ?: test.country)
        .joinToString(" ").ifBlank { null },
    stringResource(R.string.vpn_latency, test.latencyMs.toInt()),
    test.ip,
).joinToString(" · ")

@Composable
internal fun ServerRow(
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
internal fun HelpCard(open: Boolean, onToggle: () -> Unit) {
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


/** VPN çekirdekleri ayrı paket olarak gelir; kurulu değilse bu kart gösterilir. */
@Composable
internal fun VpnCoreCard(state: VpnCoreState, onInstall: () -> Unit) {
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
