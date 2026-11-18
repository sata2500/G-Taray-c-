package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.gtarayici.browser.ui.components.EmptyState
import com.gtarayici.browser.ui.components.Favicon
import com.gtarayici.browser.ui.components.OverlayScaffold
import mozilla.components.browser.state.state.TabSessionState

@Composable
fun TabsScreen(
    tabs: List<TabSessionState>,
    selectedTabId: String?,
    initialPrivate: Boolean,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseAll: (private: Boolean) -> Unit,
    onNewTab: (private: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var showPrivate by remember { mutableStateOf(initialPrivate) }
    val visible = tabs.filter { it.content.private == showPrivate }

    OverlayScaffold(
        title = stringResource(R.string.action_tabs),
        onBack = onBack,
        actions = {
            if (visible.isNotEmpty()) {
                TextButton(onClick = { onCloseAll(showPrivate) }) { Text(stringResource(R.string.tabs_close_all)) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNewTab(showPrivate) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(if (showPrivate) R.string.menu_new_private_tab else R.string.menu_new_tab)) },
            )
        },
    ) { modifier ->
        Column(modifier) {
            PrimaryTabRow(selectedTabIndex = if (showPrivate) 1 else 0) {
                Tab(
                    selected = !showPrivate,
                    onClick = { showPrivate = false },
                    text = { Text("${stringResource(R.string.tabs_normal)} (${tabs.count { !it.content.private }})") },
                )
                Tab(
                    selected = showPrivate,
                    onClick = { showPrivate = true },
                    text = { Text("${stringResource(R.string.tabs_private)} (${tabs.count { it.content.private }})") },
                )
            }
            if (visible.isEmpty()) {
                EmptyState(stringResource(R.string.tabs_empty))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 165.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                ) {
                    items(visible, key = { it.id }) { tab ->
                        TabCard(tab, selected = tab.id == selectedTabId, onSelect = onSelect, onClose = onClose)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: TabSessionState,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
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
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.padding(6.dp).fillMaxWidth().clickable { onSelect(tab.id) },
    ) {
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
            IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_close), modifier = Modifier.size(18.dp))
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
