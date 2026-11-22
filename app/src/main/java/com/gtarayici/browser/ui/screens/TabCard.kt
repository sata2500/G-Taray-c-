package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.gtarayici.browser.components
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.components.Favicon
import mozilla.components.browser.state.state.TabSessionState

@Composable
internal fun TabCard(
    tab: TabSessionState,
    selected: Boolean,
    groupColor: Color?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onAddToGroup: () -> Unit,
    onRemoveFromGroup: (() -> Unit)?,
    /** Seçim kipinde işaretli mi; null ise seçim kipi kapalıdır. */
    checked: Boolean? = null,
    onStartSelection: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    val url = tab.content.url
    val isBlank = url.isBlank() || url == "about:blank"
    val title = tab.content.title.ifBlank {
        UrlInput.displayUrl(url).ifBlank { stringResource(R.string.tab_untitled) }
    }
    val thumbnails = LocalContext.current.components.tabThumbnails
    val version by thumbnails.version.collectAsState()
    val thumbnail by produceState(thumbnails.cached(tab.id), tab.id, version, isBlank) {
        value = if (isBlank) null else thumbnails.load(tab.id, tab.content.private)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = when {
            selected -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            groupColor != null -> BorderStroke(2.dp, groupColor)
            else -> null
        },
        modifier = Modifier
            .padding(6.dp)
            .fillMaxWidth()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            // Yana kaydırınca sekme kapanır.
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 0.85f)
            }
            .draggable(
                enabled = checked == null,
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                onDragStopped = { velocity ->
                    val dismiss = abs(offset.value) > width * 0.35f || abs(velocity) > 2_500f
                    if (dismiss) {
                        val direction = if (offset.value + velocity * 0.1f < 0) -1 else 1
                        offset.animateTo(direction * width * 1.2f)
                        onClose(tab.id)
                    } else {
                        offset.animateTo(0f)
                    }
                },
            )
            .combinedClickable(
                onClick = { onSelect(tab.id) },
                onLongClick = { if (checked == null) menu = true },
            ),
    ) {
        // Uzun basınca: gruba ekle / gruptan çıkar
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.tabs_select)) }, onClick = { menu = false; onStartSelection() })
            DropdownMenuItem(text = { Text(stringResource(R.string.tab_groups_add_to)) }, onClick = { menu = false; onAddToGroup() })
            if (onRemoveFromGroup != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tab_groups_remove)) },
                    onClick = { menu = false; onRemoveFromGroup() },
                )
            }
        }
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Favicon(url, size = 18.dp, private = tab.content.private)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (checked != null) {
                androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = { onSelect(tab.id) })
            } else {
                IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), modifier = Modifier.size(18.dp))
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isBlank) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Favicon(url, size = 40.dp, private = tab.content.private)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            UrlInput.host(url),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
