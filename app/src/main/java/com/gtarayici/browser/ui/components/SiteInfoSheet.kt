package com.gtarayici.browser.ui.components

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = com.gtarayici.browser.ui.designsystem.rememberFullSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Başlık: sitenin simgesi, adı ve sayfa başlığı.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Favicon(url, size = 28.dp, private = current.content.private) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(host, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        current.content.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Durum kartları: bağlantı ve izleyici koruması yan yana.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                StatusCard(
                    icon = if (secure) Icons.Filled.Lock else Icons.Filled.Warning,
                    title = stringResource(if (secure) R.string.site_secure_short else R.string.site_insecure_short),
                    detail = stringResource(if (secure) R.string.site_secure_summary else R.string.site_insecure_summary),
                    good = secure,
                    bad = !secure,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatusCard(
                    icon = Icons.Filled.Shield,
                    title = stringResource(if (exception) R.string.site_protection_off_short else R.string.site_protection_short),
                    detail = when {
                        exception -> stringResource(R.string.site_tracking_off)
                        blocked > 0 -> pluralStringResource(R.plurals.site_trackers_blocked, blocked, blocked)
                        else -> stringResource(R.string.site_tracking_on)
                    },
                    good = !exception,
                    bad = false,
                    trailing = { Switch(checked = !exception, onCheckedChange = { on -> setProtection(components, tabId, on) }) },
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { setProtection(components, tabId, exception) },
                )
            }

            // Bu siteye özel ayarlar tek bir kartta.
            if (url.startsWith("http")) {
                Text(
                    stringResource(R.string.site_for_this_site),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                )
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(vertical = 6.dp)) { SiteControls(current) }
                }
            }

            // İşlemler: iki düğme yan yana.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
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
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Cookie, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.site_clear_short), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = {
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
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.site_reset_short), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                stringResource(R.string.site_clear_data_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** Durum kartı: simge, kısa başlık, açıklama; iyi durumda vurgulu, kötü durumda uyarı renginde. */
@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    good: Boolean,
    bad: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        bad -> colors.errorContainer
        good -> colors.primaryContainer
        else -> colors.surfaceContainerHigh
    }
    val content = when {
        bad -> colors.onErrorContainer
        good -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Surface(shape = RoundedCornerShape(20.dp), color = container, contentColor = content, modifier = modifier.clip(RoundedCornerShape(20.dp))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(if (trailing == null) 12.dp else 2.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bu sitede izleyici korumasını açar/kapatır ve sayfayı yeniler. */
private fun setProtection(components: com.gtarayici.browser.Components, tabId: String, enabled: Boolean) {
    val useCases = components.trackingProtectionUseCases
    if (enabled) useCases.removeException(tabId) else useCases.addException(tabId)
    components.sessionUseCases.reload(tabId)
}
