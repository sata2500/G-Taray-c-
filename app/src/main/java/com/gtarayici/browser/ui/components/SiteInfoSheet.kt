package com.gtarayici.browser.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.search.UrlInput
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.SecurityInfo
import mozilla.components.concept.engine.Engine
import mozilla.components.support.ktx.kotlin.getOrigin

/**
 * Adres çubuğundaki kilit simgesine dokununca açılan site bilgisi: bağlantının güvenliği, engellenen
 * izleyiciler, bu sitede izleyici korumasını kapatma (site bozuk görünüyorsa), site verilerini ve
 * izinlerini sıfırlama.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoSheet(tabId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val scope = rememberCoroutineScope()
    val tab by components.store.observe { it.findTab(tabId) }
    val current = tab ?: return
    val url = current.content.url
    val host = UrlInput.host(url)
    val secure = current.content.securityInfo is SecurityInfo.Secure
    val protection = current.trackingProtection
    val blocked = protection.blockedTrackers.size
    val exception = protection.ignoredOnTrackingProtection

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Favicon(url, size = 32.dp, private = current.content.private)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(host, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        current.content.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            ListItem(
                leadingContent = {
                    Icon(
                        if (secure) Icons.Filled.Lock else Icons.Filled.Warning,
                        null,
                        tint = if (secure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                },
                headlineContent = { Text(stringResource(if (secure) R.string.site_secure else R.string.site_insecure)) },
                supportingContent = {
                    Text(stringResource(if (secure) R.string.site_secure_summary else R.string.site_insecure_summary))
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListItem(
                leadingContent = {
                    Icon(Icons.Filled.Shield, null, tint = if (exception) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                },
                headlineContent = { Text(stringResource(R.string.site_tracking_protection)) },
                supportingContent = {
                    Text(
                        when {
                            exception -> stringResource(R.string.site_tracking_off)
                            blocked > 0 -> pluralStringResource(R.plurals.site_trackers_blocked, blocked, blocked)
                            else -> stringResource(R.string.site_tracking_on)
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = !exception,
                        onCheckedChange = { on -> setProtection(components, tabId, on) },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { setProtection(components, tabId, exception) },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                leadingContent = { Icon(Icons.Filled.Cookie, null) },
                headlineContent = { Text(stringResource(R.string.site_clear_data)) },
                supportingContent = { Text(stringResource(R.string.site_clear_data_summary)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    components.engine.clearData(
                        Engine.BrowsingData.allSiteData(),
                        host = host,
                        onSuccess = {
                            Toast.makeText(context, R.string.site_data_cleared, Toast.LENGTH_SHORT).show()
                            components.sessionUseCases.reload(tabId)
                        },
                    )
                    onDismiss()
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.RestartAlt, null) },
                headlineContent = { Text(stringResource(R.string.site_reset_permissions)) },
                supportingContent = { Text(stringResource(R.string.site_reset_permissions_summary)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    scope.launch {
                        val origin = url.getOrigin() ?: return@launch
                        val storage = components.permissionStorage
                        val private = current.content.private
                        storage.findSitePermissionsBy(origin, includeTemporary = true, private = private)
                            ?.let { storage.remove(it, private) }
                        Toast.makeText(context, R.string.site_permissions_reset, Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
            )
        }
    }
}

/** Bu sitede izleyici korumasını açar/kapatır ve sayfayı yeniler. */
private fun setProtection(components: com.gtarayici.browser.Components, tabId: String, enabled: Boolean) {
    val useCases = components.trackingProtectionUseCases
    if (enabled) useCases.removeException(tabId) else useCases.addException(tabId)
    components.sessionUseCases.reload(tabId)
}
