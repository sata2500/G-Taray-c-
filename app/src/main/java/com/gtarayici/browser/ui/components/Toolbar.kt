package com.gtarayici.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.VpnKey
import com.gtarayici.browser.data.Profile
import com.gtarayici.browser.ui.screens.ProfileAvatar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtarayici.browser.R

data class ToolbarState(
    val displayUrl: String,
    val loading: Boolean,
    val progress: Int,
    val secure: Boolean?,
    val tabCount: Int,
    val private: Boolean,
    val profile: Profile? = null,
    val vpn: VpnIndicator = VpnIndicator.OFF,
    val reader: ReaderIndicator = ReaderIndicator.NONE,
)

/** Okuma görünümü düğmesi: sayfa okunabilir değilse gizli, okuma görünümündeyken vurgulu. */
enum class ReaderIndicator { NONE, AVAILABLE, ACTIVE }

enum class VpnIndicator { OFF, ON, ERROR }

/** Tek elle kullanım için ekranın altında duran adres çubuğu. */
@Composable
fun BottomToolbar(
    state: ToolbarState,
    onUrlClick: () -> Unit,
    onReloadOrStop: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onVpnClick: () -> Unit = {},
    onReaderClick: () -> Unit = {},
    actions: ToolbarActions = ToolbarActions(),
) {
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val shift by animateFloatAsState(drag, label = "toolbar-swipe")
    val swipe = actions.onSwipeTab
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            if (state.loading) {
                LinearProgressIndicator(
                    progress = { (state.progress.coerceIn(5, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    drawStopIndicator = {},
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    // Araç çubuğunu yana kaydırmak komşu sekmeye geçer.
                    .then(
                        if (swipe == null) Modifier else Modifier.pointerInput(swipe) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (abs(drag) > threshold) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        swipe(if (drag < 0) 1 else -1)
                                    }
                                    drag = 0f
                                },
                                onDragCancel = { drag = 0f },
                            ) { change, amount ->
                                change.consume()
                                drag = (drag + amount).coerceIn(-threshold * 2, threshold * 2)
                            }
                        },
                    )
                    .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.profile?.let { profile ->
                    // Rozet yalnızca baş harfi gösterir; ekran okuyucu profilin adını ve düğmenin işini okusun.
                    val label = "${stringResource(R.string.profile_switch)}: ${profile.name}"
                    IconButton(onClick = onProfileClick, modifier = Modifier.semantics { contentDescription = label }) {
                        Box(Modifier.clearAndSetSemantics { }) { ProfileAvatar(profile, 28.dp) }
                    }
                }
                UrlPill(
                    state,
                    onUrlClick,
                    onReloadOrStop,
                    onReaderClick,
                    actions,
                    Modifier.weight(1f).graphicsLayer {
                        translationX = shift * 0.4f
                        alpha = 1f - (abs(shift) / (threshold * 4)).coerceAtMost(0.4f)
                    },
                )
                if (state.vpn != VpnIndicator.OFF) {
                    IconButton(onClick = onVpnClick) {
                        Icon(
                            Icons.Filled.VpnKey,
                            contentDescription = stringResource(R.string.menu_vpn),
                            tint = if (state.vpn == VpnIndicator.ON) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TabCounter(state.tabCount, state.displayUrl.isNotEmpty(), onTabsClick, actions)
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
                }
            }
        }
    }
}

@Composable
private fun UrlPill(
    state: ToolbarState,
    onClick: () -> Unit,
    onReloadOrStop: () -> Unit,
    onReaderClick: () -> Unit,
    actions: ToolbarActions,
    modifier: Modifier,
) {
    val isHome = state.displayUrl.isEmpty()
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
            )
            .padding(start = if (isHome) 14.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Uzun basınca: bağlantıyı kopyala / panodakini yapıştırıp git
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!isHome) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_copy_link)) },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    onClick = { menu = false; actions.onCopyUrl() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.toolbar_paste_and_go)) },
                leadingIcon = { Icon(Icons.Filled.ContentPaste, null) },
                onClick = { menu = false; actions.onPasteAndGo() },
            )
        }
        val (icon, description, tint) = when {
            isHome -> Triple(Icons.Filled.Search, null, MaterialTheme.colorScheme.onSurfaceVariant)
            state.secure == false -> Triple(
                Icons.Filled.Warning,
                stringResource(R.string.insecure_connection),
                MaterialTheme.colorScheme.error,
            )
            else -> Triple(Icons.Filled.Lock, stringResource(R.string.secure_connection), MaterialTheme.colorScheme.primary)
        }
        if (isHome) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        } else {
            // Kilit simgesi: site bilgisi (bağlantı, izleyici koruması, izinler)
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = actions.onSiteInfo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = stringResource(R.string.site_info), tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = if (isHome) {
                stringResource(if (state.private) R.string.private_search_hint else R.string.search_hint)
            } else {
                state.displayUrl
            },
            color = if (isHome) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (!isHome && state.reader != ReaderIndicator.NONE) {
            IconButton(onClick = onReaderClick) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = stringResource(
                        if (state.reader == ReaderIndicator.ACTIVE) R.string.reader_close else R.string.reader_open,
                    ),
                    tint = if (state.reader == ReaderIndicator.ACTIVE) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!isHome) {
            IconButton(onClick = onReloadOrStop) {
                Icon(
                    if (state.loading) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = stringResource(if (state.loading) R.string.action_stop else R.string.action_reload),
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.width(14.dp))
        }
    }
}

@Composable
private fun TabCounter(count: Int, hasPage: Boolean, onClick: () -> Unit, actions: ToolbarActions) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
                onClickLabel = stringResource(R.string.action_tabs),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(1.75.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Sayaç bir simgenin parçasıdır ve kutusu sabit boyutludur: rakam sistem yazı boyutuyla büyümez
            // (büyük yazıda iki basamak kutudan taşıyordu). Ekran okuyucu sayıyı yine okur.
            val digits = with(LocalDensity.current) { 11.dp.toSp() }
            Text(
                text = if (count > 99) ":D" else count.toString(),
                fontSize = digits,
                lineHeight = digits,
                fontWeight = FontWeight.Bold,
            )
        }
        // Uzun basınca: yeni sekme, yeni gizli sekme, bu sekmeyi kapat
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_new_tab)) },
                leadingIcon = { Icon(Icons.Filled.Add, null) },
                onClick = { menu = false; actions.onNewTab(false) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_new_private_tab)) },
                leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                onClick = { menu = false; actions.onNewTab(true) },
            )
            if (hasPage) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.toolbar_close_tab)) },
                    leadingIcon = { Icon(Icons.Filled.Close, null) },
                    onClick = { menu = false; actions.onCloseTab() },
                )
            }
        }
    }
}

/** Araç çubuğundaki hareket ve uzun basma işlemleri. */
data class ToolbarActions(
    /** Komşu sekmeye geç (+1 sonraki, -1 önceki); null ise kaydırma kapalıdır. */
    val onSwipeTab: ((Int) -> Unit)? = null,
    val onNewTab: (private: Boolean) -> Unit = {},
    val onCloseTab: () -> Unit = {},
    val onCopyUrl: () -> Unit = {},
    val onPasteAndGo: () -> Unit = {},
    val onSiteInfo: () -> Unit = {},
)
