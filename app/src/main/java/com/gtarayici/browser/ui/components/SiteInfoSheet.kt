package com.gtarayici.browser.ui.components

import com.gtarayici.browser.ui.feedback.UiText
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
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = com.gtarayici.browser.ui.designsystem.rememberFullSheetState()) {
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

            ListRow(
                leading = {
                    Icon(
                        if (secure) Icons.Filled.Lock else Icons.Filled.Warning,
                        null,
                        tint = if (secure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                },
                headline = { Text(stringResource(if (secure) R.string.site_secure else R.string.site_insecure)) },
                supporting = {
                    Text(stringResource(if (secure) R.string.site_secure_summary else R.string.site_insecure_summary))
                },
            )

            ListRow(
                leading = {
                    Icon(Icons.Filled.Shield, null, tint = if (exception) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                },
                headline = { Text(stringResource(R.string.site_tracking_protection)) },
                supporting = {
                    Text(
                        when {
                            exception -> stringResource(R.string.site_tracking_off)
                            blocked > 0 -> pluralStringResource(R.plurals.site_trackers_blocked, blocked, blocked)
                            else -> stringResource(R.string.site_tracking_on)
                        },
                    )
                },
                trailing = {
                    Switch(
                        checked = !exception,
                        onCheckedChange = { on -> setProtection(components, tabId, on) },
                    )
                },
                modifier = Modifier.clickable { setProtection(components, tabId, exception) },
            )

            if (url.startsWith("http")) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.site_for_this_site),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                SiteControls(current)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListRow(
                leading = { Icon(Icons.Filled.Cookie, null) },
                headline = { Text(stringResource(R.string.site_clear_data)) },
                supporting = { Text(stringResource(R.string.site_clear_data_summary)) },
                modifier = Modifier.clickable {
                    components.engine.clearData(
                        Engine.BrowsingData.allSiteData(),
                        host = host,
                        onSuccess = {
                            context.components.messenger.success(UiText.res(R.string.site_data_cleared))
                            components.sessionUseCases.reload(tabId)
                        },
                    )
                    onDismiss()
                },
            )
            ListRow(
                leading = { Icon(Icons.Filled.RestartAlt, null) },
                headline = { Text(stringResource(R.string.site_reset_permissions)) },
                supporting = { Text(stringResource(R.string.site_reset_permissions_summary)) },
                modifier = Modifier.clickable {
                    scope.launch {
                        val origin = url.getOrigin() ?: return@launch
                        val storage = components.permissionStorage
                        val private = current.content.private
                        storage.findSitePermissionsBy(origin, includeTemporary = true, private = private)
                            ?.let { storage.remove(it, private) }
                        context.components.messenger.success(UiText.res(R.string.site_permissions_reset))
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
