package com.gtarayici.browser.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.extensions.ExtensionManager
import com.gtarayici.browser.extensions.displayName
import com.gtarayici.browser.ui.BrowserController
import com.gtarayici.browser.ui.components.observe
import com.gtarayici.browser.ui.components.OverlayScaffold
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.concept.engine.webextension.WebExtension

@Composable
fun ExtensionsScreen(controller: BrowserController, onBack: () -> Unit) {
    val components = LocalContext.current.components
    val extensions by components.extensions.installed.collectAsState()
    LaunchedEffect(Unit) { components.extensions.refresh() }

    OverlayScaffold(title = stringResource(R.string.extensions_title), onBack = onBack) { modifier ->
        LazyColumn(modifier) {
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.extensions_get_more)) },
                    supportingContent = { Text(stringResource(R.string.extensions_get_more_summary)) },
                    modifier = Modifier.clickable { controller.loadUrl(ExtensionManager.AMO_ANDROID_URL, inNewTab = true) },
                )
                HorizontalDivider()
            }
            if (extensions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.extensions_empty),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(extensions, key = { it.id }) { extension -> ExtensionRow(extension, controller) }
        }
    }
}

@Composable
private fun ExtensionRow(extension: WebExtension, controller: BrowserController) {
    val components = LocalContext.current.components
    var menu by remember { mutableStateOf(false) }
    val metadata = extension.getMetadata()
    ListItem(
        leadingContent = { Icon(Icons.Filled.Extension, null) },
        headlineContent = { Text(extension.displayName()) },
        supportingContent = {
            Text(
                listOfNotNull(
                    metadata?.version?.let { "v$it" },
                    stringResource(R.string.extension_builtin).takeIf { extension.id == ExtensionManager.UBLOCK_ID },
                ).joinToString(" · ") + (metadata?.description?.let { "\n$it" } ?: ""),
                maxLines = 3,
            )
        },
        trailingContent = {
            Switch(checked = extension.isEnabled(), onCheckedChange = { components.extensions.setEnabled(extension, it) })
        },
        modifier = Modifier.clickable { menu = true },
    )
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        metadata?.optionsPageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.extension_settings)) },
                onClick = {
                    menu = false
                    controller.loadUrl(url, inNewTab = true)
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.extension_private)) },
            trailingIcon = { Checkbox(checked = extension.isAllowedInPrivateBrowsing(), onCheckedChange = null) },
            onClick = {
                menu = false
                components.extensions.setAllowedInPrivate(extension, !extension.isAllowedInPrivateBrowsing())
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.extension_remove), color = MaterialTheme.colorScheme.error) },
            onClick = {
                menu = false
                components.extensions.uninstall(extension)
            },
        )
    }
}

/** Eklenti kurulum izinleri, hata ve bilgi istemleri. */
@Composable
fun ExtensionPrompts() {
    val context = LocalContext.current
    val components = context.components
    val request by components.store.observe { it.webExtensionPromptRequest }
    val consume = { components.store.dispatch(WebExtensionAction.ConsumePromptRequestWebExtensionAction) }

    when (val current = request) {
        null -> Unit
        is WebExtensionPromptRequest.AfterInstallation.Permissions.Required -> {
            if (components.extensions.isAutoApproved(current.extension.id)) return
            var allowPrivate by remember(current) { mutableStateOf(false) }
            val permissions = (current.permissions + current.origins).distinct()
            AlertDialog(
                onDismissRequest = {
                    current.onConfirm(PermissionPromptResponse(isPermissionsGranted = false))
                    consume()
                },
                title = { Text(stringResource(R.string.extension_install_title, current.extension.displayName())) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (permissions.isEmpty()) {
                            Text(stringResource(R.string.extension_no_permissions))
                        } else {
                            Text(stringResource(R.string.extension_permissions))
                            permissions.take(12).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                            if (permissions.size > 12) Text("• …", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { allowPrivate = !allowPrivate }.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = allowPrivate, onCheckedChange = { allowPrivate = it })
                            Text(stringResource(R.string.extension_allow_private))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        current.onConfirm(PermissionPromptResponse(isPermissionsGranted = true, isPrivateModeGranted = allowPrivate))
                        consume()
                        components.extensions.refresh()
                    }) { Text(stringResource(R.string.extension_add)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        current.onConfirm(PermissionPromptResponse(isPermissionsGranted = false))
                        consume()
                    }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
        is WebExtensionPromptRequest.AfterInstallation.Permissions.Optional -> {
            AlertDialog(
                onDismissRequest = { current.onConfirm(false); consume() },
                title = { Text(current.extension.displayName()) },
                text = {
                    Column {
                        Text(stringResource(R.string.extension_permissions))
                        (current.permissions + current.origins).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = { TextButton(onClick = { current.onConfirm(true); consume() }) { Text(stringResource(R.string.action_ok)) } },
                dismissButton = { TextButton(onClick = { current.onConfirm(false); consume() }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        is WebExtensionPromptRequest.AfterInstallation.PostInstallation -> LaunchedEffect(current) {
            if (current.extension.id != ExtensionManager.UBLOCK_ID) {
                Toast.makeText(context, context.getString(R.string.extension_installed, current.extension.displayName()), Toast.LENGTH_SHORT).show()
            }
            components.extensions.refresh()
            consume()
        }
        is WebExtensionPromptRequest.BeforeInstallation.InstallationFailed -> LaunchedEffect(current) {
            Toast.makeText(context, R.string.extension_install_failed, Toast.LENGTH_LONG).show()
            consume()
        }
        is WebExtensionPromptRequest.InstallationRequested -> LaunchedEffect(current) {
            components.extensions.install(current.url)
            consume()
        }
        else -> LaunchedEffect(current) { consume() }
    }
}

/** Eklenti simgesine (ör. uBlock Origin) dokununca açılan paneli alttan açılır sayfada gösterir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPopupSheet() {
    val components = LocalContext.current.components
    val popup by components.store.observe { state ->
        state.extensions.values.firstOrNull { it.popupSession != null }?.let { it.id to it.popupSession }
    }
    val (extensionId, session) = popup ?: return
    session ?: return
    val close = { components.store.dispatch(WebExtensionAction.UpdatePopupSessionAction(extensionId, popupSession = null)) }
    ModalBottomSheet(
        onDismissRequest = { close() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PopupEngineView(session, Modifier.fillMaxWidth().height(520.dp).navigationBarsPadding())
    }
}

@Composable
private fun PopupEngineView(session: EngineSession, modifier: Modifier) {
    val components = LocalContext.current.components
    AndroidView(
        factory = { context ->
            components.engine.createView(context).also { it.render(session) }.asView()
        },
        onRelease = { view -> (view as? EngineView)?.release() },
        modifier = modifier,
    )
}

/** Menüde gösterilen eklenti düğmesi simgesi. */
@Composable
fun ExtensionActionIcon(loadIcon: (suspend (Int) -> android.graphics.Bitmap?)?) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(loadIcon) { bitmap = runCatching { loadIcon?.invoke(64)?.asImageBitmap() }.getOrNull() }
    val current = bitmap
    if (current != null) Image(current, null, modifier = Modifier.size(24.dp)) else Icon(Icons.Filled.Extension, null)
}
