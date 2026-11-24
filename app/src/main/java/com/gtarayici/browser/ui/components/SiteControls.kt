package com.gtarayici.browser.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.sites.TextZoom
import com.gtarayici.browser.ui.designsystem.ListRow
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.support.ktx.kotlin.getOrigin

/** Site bilgisi penceresindeki "Bu site için" bölümü: yazı boyutu, masaüstü görünümü ve site izinleri. */
@Composable
fun SiteControls(tab: TabSessionState) {
    val components = LocalContext.current.components
    val url = tab.content.url
    val sites by components.siteSettings.sites.collectAsState()
    val global by components.settings.textZoom.collectAsState()
    val site = sites[com.gtarayici.browser.sites.SiteSettings.key(url)]
    val zoom = site?.zoom ?: global

    ListRow(
        leading = { Icon(Icons.Rounded.TextFields, null) },
        headline = { Text(stringResource(R.string.site_text_size)) },
        supporting = {
            Text(
                if (site?.zoom != null) stringResource(R.string.site_text_size_custom) else stringResource(R.string.site_text_size_default),
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { components.siteSettings.setZoom(url, TextZoom.step(zoom, up = false)) }, enabled = zoom > TextZoom.MIN) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.site_text_smaller))
                }
                val label = "%$zoom"
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = label },
                )
                FilledTonalIconButton(onClick = { components.siteSettings.setZoom(url, TextZoom.step(zoom, up = true)) }, enabled = zoom < TextZoom.MAX) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.site_text_larger))
                }
            }
        },
    )
    if (site?.zoom != null) {
        TextButton(onClick = { components.siteSettings.setZoom(url, null) }, modifier = Modifier.padding(start = 56.dp)) {
            Text(stringResource(R.string.site_text_size_reset, global))
        }
    }

    val desktop = tab.content.desktopMode
    val toggleDesktop = {
        components.siteSettings.setDesktop(url, !desktop)
        components.sessionUseCases.requestDesktopSite(!desktop, tab.id)
    }
    ListRow(
        leading = { Icon(Icons.Rounded.DesktopWindows, null) },
        headline = { Text(stringResource(R.string.site_desktop)) },
        supporting = { Text(stringResource(R.string.site_desktop_summary)) },
        trailing = { Switch(checked = desktop, onCheckedChange = { toggleDesktop() }) },
        modifier = Modifier.clickable { toggleDesktop() },
    )

    // Küçük pencere (resim içinde resim): müzik sitelerinde varsayılan olarak kapalı, yalnızca arka planda çalınır.
    val pipOn = components.siteSettings.pipAllowed(url)
    ListRow(
        leading = { Icon(Icons.Rounded.PictureInPictureAlt, null) },
        headline = { Text(stringResource(R.string.site_pip)) },
        supporting = { Text(stringResource(if (pipOn) R.string.site_pip_on else R.string.site_pip_off)) },
        trailing = { Switch(checked = pipOn, onCheckedChange = { components.siteSettings.setPip(url, it) }) },
        modifier = Modifier.clickable { components.siteSettings.setPip(url, !pipOn) },
    )

    SitePermissionRows(tab)
}

/** Bu sitenin izinleri: kamera, mikrofon, konum, bildirim (karar verilmişse) ve otomatik oynatma. */
@Composable
private fun SitePermissionRows(tab: TabSessionState) {
    val components = LocalContext.current.components
    val scope = rememberCoroutineScope()
    val origin = tab.content.url.getOrigin() ?: return
    val private = tab.content.private
    var permissions by remember(origin) { mutableStateOf<SitePermissions?>(null) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(origin, version) {
        permissions = components.permissionStorage.findSitePermissionsBy(origin, includeTemporary = false, private = private)
    }

    fun change(update: (SitePermissions) -> SitePermissions) {
        scope.launch {
            val storage = components.permissionStorage
            val current = permissions
            if (current == null) {
                storage.save(update(SitePermissions(origin = origin, savedAt = System.currentTimeMillis())), request = null, private = private)
            } else {
                storage.update(update(current), private)
            }
            version++
        }
    }

    val p = permissions
    PermissionRow(Icons.Rounded.Camera, R.string.site_perm_camera, p?.camera) { allow -> change { it.copy(camera = status(allow)) } }
    PermissionRow(Icons.Rounded.Mic, R.string.site_perm_microphone, p?.microphone) { allow -> change { it.copy(microphone = status(allow)) } }
    PermissionRow(Icons.Rounded.LocationOn, R.string.site_perm_location, p?.location) { allow -> change { it.copy(location = status(allow)) } }
    PermissionRow(Icons.Rounded.Notifications, R.string.site_perm_notification, p?.notification) { allow -> change { it.copy(notification = status(allow)) } }

    // Otomatik oynatma her zaman gösterilir: varsayılan olarak sesli oynatma engellidir.
    val autoplay = p?.autoplayAudible == SitePermissions.AutoplayStatus.ALLOWED
    ListRow(
        leading = { Icon(Icons.Rounded.PlayCircle, null) },
        headline = { Text(stringResource(R.string.site_perm_autoplay)) },
        supporting = { Text(stringResource(if (autoplay) R.string.site_perm_allowed else R.string.site_perm_autoplay_blocked)) },
        trailing = {
            Switch(checked = autoplay, onCheckedChange = { allow ->
                change {
                    val s = if (allow) SitePermissions.AutoplayStatus.ALLOWED else SitePermissions.AutoplayStatus.BLOCKED
                    it.copy(autoplayAudible = s, autoplayInaudible = if (allow) s else it.autoplayInaudible)
                }
            })
        },
    )
}

/** Yalnızca site bu izni daha önce istemişse (izin verilmiş veya engellenmişse) gösterilir. */
@Composable
private fun PermissionRow(icon: ImageVector, title: Int, status: SitePermissions.Status?, onChange: (Boolean) -> Unit) {
    if (status == null || status == SitePermissions.Status.NO_DECISION) return
    val allowed = status == SitePermissions.Status.ALLOWED
    ListRow(
        leading = { Icon(icon, null) },
        headline = { Text(stringResource(title)) },
        supporting = { Text(stringResource(if (allowed) R.string.site_perm_allowed else R.string.site_perm_blocked)) },
        trailing = { Switch(checked = allowed, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!allowed) },
    )
}

private fun status(allow: Boolean) = if (allow) SitePermissions.Status.ALLOWED else SitePermissions.Status.BLOCKED
