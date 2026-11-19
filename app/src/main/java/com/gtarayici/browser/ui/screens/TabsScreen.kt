package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import com.gtarayici.browser.tabs.TabGroup
import com.gtarayici.browser.tabs.TabGroups
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
    onNewTabInGroup: (groupId: String) -> Unit,
    onBack: () -> Unit,
) {
    val tabGroups = LocalContext.current.components.tabGroups
    val allGroups by tabGroups.groups.collectAsState()
    var showPrivate by remember { mutableStateOf(initialPrivate) }
    val visible = tabs.filter { it.content.private == showPrivate }
    val visibleIds = visible.map { it.id }.toSet()
    // Bu profilde ve bu sekmede (normal/gizli) görünen sekmeleri olan gruplar
    val groups = allGroups.filter { group -> group.tabIds.any { it in visibleIds } }
    val grouped = groups.flatMap { it.tabIds }.toSet()
    val ungrouped = visible.filter { it.id !in grouped }
    var groupTarget by remember { mutableStateOf<TabSessionState?>(null) }
    var editGroup by remember { mutableStateOf<TabGroup?>(null) }

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
                val byId = visible.associateBy { it.id }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 165.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                ) {
                    groups.forEach { group ->
                        val members = group.tabIds.mapNotNull { byId[it] }
                        item(key = "group-" + group.id, span = { GridItemSpan(maxLineSpan) }) {
                            GroupHeader(
                                group = group,
                                count = members.size,
                                onToggle = { tabGroups.toggleCollapsed(group.id) },
                                onEdit = { editGroup = group },
                                onNewTab = { onNewTabInGroup(group.id) },
                                onUngroup = { tabGroups.ungroup(group.id) },
                                onCloseGroup = { members.forEach { onClose(it.id) } },
                            )
                        }
                        if (!group.collapsed) {
                            items(members, key = { it.id }) { tab ->
                                TabCard(
                                    tab,
                                    selected = tab.id == selectedTabId,
                                    groupColor = Color(group.color),
                                    onSelect = onSelect,
                                    onClose = onClose,
                                    onAddToGroup = { groupTarget = tab },
                                    onRemoveFromGroup = { tabGroups.removeTab(tab.id) },
                                )
                            }
                        }
                    }
                    if (groups.isNotEmpty() && ungrouped.isNotEmpty()) {
                        item(key = "ungrouped", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.tab_groups_other),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(ungrouped, key = { it.id }) { tab ->
                        TabCard(
                            tab,
                            selected = tab.id == selectedTabId,
                            groupColor = null,
                            onSelect = onSelect,
                            onClose = onClose,
                            onAddToGroup = { groupTarget = tab },
                            onRemoveFromGroup = null,
                        )
                    }
                }
            }
        }
    }

    groupTarget?.let { tab ->
        val candidates = allGroups.filter { it.contextId == tab.contextId && tab.id !in it.tabIds }
        AddToGroupDialog(
            groups = candidates,
            onPick = { tabGroups.addTab(it.id, tab.id); groupTarget = null },
            onCreate = { name, color -> tabGroups.create(name, color, tab); groupTarget = null },
            onDismiss = { groupTarget = null },
        )
    }
    editGroup?.let { group ->
        GroupEditorDialog(
            title = stringResource(R.string.tab_groups_edit),
            initialName = group.name,
            initialColor = group.color,
            onSave = { name, color ->
                tabGroups.rename(group.id, name)
                tabGroups.setColor(group.id, color)
                editGroup = null
            },
            onDismiss = { editGroup = null },
        )
    }
}

@Composable
private fun GroupHeader(
    group: TabGroup,
    count: Int,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onNewTab: () -> Unit,
    onUngroup: () -> Unit,
    onCloseGroup: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(group.color).copy(alpha = 0.16f))
            .clickable(onClick = onToggle)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(group.color)))
        Spacer(Modifier.width(10.dp))
        Text(
            group.name.ifBlank { stringResource(R.string.tab_groups_unnamed) },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            "  ·  $count",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            if (group.collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
            contentDescription = stringResource(if (group.collapsed) R.string.tab_groups_expand else R.string.tab_groups_collapse),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_menu)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.tab_groups_new_tab)) }, onClick = { menu = false; onNewTab() })
                DropdownMenuItem(text = { Text(stringResource(R.string.tab_groups_edit)) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(stringResource(R.string.tab_groups_ungroup)) }, onClick = { menu = false; onUngroup() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tab_groups_close), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onCloseGroup() },
                )
            }
        }
    }
}

@Composable
private fun AddToGroupDialog(
    groups: List<TabGroup>,
    onPick: (TabGroup) -> Unit,
    onCreate: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(groups.isEmpty()) }
    if (creating) {
        GroupEditorDialog(
            title = stringResource(R.string.tab_groups_new),
            initialName = "",
            initialColor = TabGroups.COLORS[groups.size % TabGroups.COLORS.size],
            onSave = onCreate,
            onDismiss = onDismiss,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_groups_add_to)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                groups.forEach { group ->
                    ListItem(
                        leadingContent = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(group.color))) },
                        headlineContent = { Text(group.name.ifBlank { stringResource(R.string.tab_groups_unnamed) }) },
                        supportingContent = { Text(pluralStringResource(R.plurals.tab_count, group.tabIds.size, group.tabIds.size)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(group) },
                    )
                }
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Add, null) },
                    headlineContent = { Text(stringResource(R.string.tab_groups_new)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { creating = true },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupEditorDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tab_groups_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabGroups.COLORS.forEach { option ->
                        Box(
                            Modifier
                                .padding(bottom = 10.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(option))
                                .border(
                                    width = if (option == color) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = option },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TabCard(
    tab: TabSessionState,
    selected: Boolean,
    groupColor: Color?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onAddToGroup: () -> Unit,
    onRemoveFromGroup: (() -> Unit)?,
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
                onLongClick = { menu = true },
            ),
    ) {
        // Uzun basınca: gruba ekle / gruptan çıkar
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
