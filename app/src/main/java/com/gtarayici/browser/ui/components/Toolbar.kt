package com.gtarayici.browser.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
)

/** Tek elle kullanım için ekranın altında duran adres çubuğu. */
@Composable
fun BottomToolbar(
    state: ToolbarState,
    onUrlClick: () -> Unit,
    onReloadOrStop: () -> Unit,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
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
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrlPill(state, onUrlClick, onReloadOrStop, Modifier.weight(1f))
                TabCounter(state.tabCount, onTabsClick)
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
    modifier: Modifier,
) {
    val isHome = state.displayUrl.isEmpty()
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, description, tint) = when {
            isHome -> Triple(Icons.Filled.Search, null, MaterialTheme.colorScheme.onSurfaceVariant)
            state.secure == false -> Triple(
                Icons.Filled.Warning,
                stringResource(R.string.insecure_connection),
                MaterialTheme.colorScheme.error,
            )
            else -> Triple(Icons.Filled.Lock, stringResource(R.string.secure_connection), MaterialTheme.colorScheme.primary)
        }
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
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
private fun TabCounter(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            Modifier
                .size(22.dp)
                .border(1.75.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 99) ":D" else count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
