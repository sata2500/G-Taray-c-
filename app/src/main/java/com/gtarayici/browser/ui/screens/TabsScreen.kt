package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.graphics.Color
import com.gtarayici.browser.tabs.TabGroup
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.gtarayici.browser.components
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.ui.designsystem.AddFab
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import mozilla.components.browser.state.state.TabSessionState

@Composable
fun TabsScreen(
    tabs: List<TabSessionState>,
    selectedTabId: String?,
    initialPrivate: Boolean,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseAll: (private: Boolean) -> Unit,
    /** Birden çok sekmeyi tek seferde kapatır (tek "Geri al" ile geri gelir). */
    onCloseMany: (List<String>) -> Unit = { ids -> ids.forEach(onClose) },
    onNewTab: (private: Boolean) -> Unit,
    onNewTabInGroup: (groupId: String) -> Unit,
    onBack: () -> Unit,
    /** Gizli sekmeler kilitliyse bölümün içeriği (sayı, önizleme, adlar) gösterilmez. */
    privateLocked: Boolean = false,
    onUnlockPrivate: () -> Unit = {},
) {
    val tabGroups = LocalContext.current.components.tabGroups
    val allGroups by tabGroups.groups.collectAsState()
    var showPrivate by remember { mutableStateOf(initialPrivate) }
    val locked = showPrivate && privateLocked
    // Gizli bölüme geçince kilit kendiliğinden sorulur.
    LaunchedEffect(locked) { if (locked) onUnlockPrivate() }
    val visible = if (locked) emptyList() else tabs.filter { it.content.private == showPrivate }
    val visibleIds = visible.map { it.id }.toSet()
    // Bu profilde ve bu sekmede (normal/gizli) görünen sekmeleri olan gruplar
    val groups = allGroups.filter { group -> group.tabIds.any { it in visibleIds } }
    val grouped = groups.flatMap { it.tabIds }.toSet()
    val ungrouped = visible.filter { it.id !in grouped }
    var groupTarget by remember { mutableStateOf<TabSessionState?>(null) }
    var editGroup by remember { mutableStateOf<TabGroup?>(null) }
    // Arama ve seçim kipi
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<String>?>(null) }
    var groupPicked by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = picked != null || searching) {
        if (picked != null) picked = null else { searching = false; query = "" }
    }
    LaunchedEffect(showPrivate) { picked = null }
    val selectionMode = picked != null
    val tapCard: (String) -> Unit = { id ->
        val current = picked
        if (current != null) picked = if (id in current) current - id else current + id else onSelect(id)
    }
    val oldTabs = visible.filter { System.currentTimeMillis() - it.lastAccess > OLD_TAB_MS && it.id != selectedTabId && it.lastAccess > 0 }
    val filtered = if (query.isBlank()) null else visible.filter {
        com.gtarayici.browser.search.TextMatch.matches(query, it.content.title, it.content.url)
    }

    OverlayScaffold(
        title = stringResource(R.string.action_tabs),
        onBack = onBack,
        actions = {
            val chosen = picked
            if (chosen != null) {
                Text(
                    pluralStringResource(R.plurals.tabs_selected, chosen.size, chosen.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 4.dp),
                )
                IconButton(onClick = { picked = visible.map { it.id }.toSet() }) {
                    Icon(Icons.Filled.SelectAll, stringResource(R.string.tabs_select_all))
                }
                IconButton(onClick = { groupPicked = true }, enabled = chosen.isNotEmpty()) {
                    Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.tab_groups_add_to))
                }
                IconButton(onClick = { onCloseMany(chosen.toList()); picked = null }, enabled = chosen.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.tabs_close_selected))
                }
            } else if (visible.isNotEmpty()) {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(Icons.Filled.Search, stringResource(R.string.tabs_search))
                }
                Box {
                    IconButton(onClick = { overflow = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_menu)) }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.tabs_select)) }, onClick = { overflow = false; picked = emptySet() })
                        if (oldTabs.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(pluralStringResource(R.plurals.tabs_close_old, oldTabs.size, oldTabs.size)) },
                                onClick = { overflow = false; onCloseMany(oldTabs.map { it.id }) },
                            )
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.tabs_close_all)) }, onClick = { overflow = false; onCloseAll(showPrivate) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (!locked && !selectionMode) {
                AddFab(stringResource(if (showPrivate) R.string.menu_new_private_tab else R.string.menu_new_tab)) {
                    onNewTab(showPrivate)
                }
            }
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
                    text = {
                        if (privateLocked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.tabs_private))
                            }
                        } else {
                            Text("${stringResource(R.string.tabs_private)} (${tabs.count { it.content.private }})")
                        }
                    },
                )
            }
            if (searching && !locked) FilterField(query) { query = it }
            if (locked) {
                LockedPrivateTabs(onUnlockPrivate)
            } else if (filtered != null) {
                if (filtered.isEmpty()) {
                    EmptyState(stringResource(R.string.tabs_search_empty))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 165.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                    ) {
                        items(filtered, key = { it.id }) { tab ->
                            TabCard(
                                tab,
                                selected = tab.id == selectedTabId,
                                groupColor = null,
                                onSelect = tapCard,
                                onClose = onClose,
                                onAddToGroup = { groupTarget = tab },
                                onRemoveFromGroup = null,
                                checked = picked?.contains(tab.id),
                                onStartSelection = { picked = setOf(tab.id) },
                            )
                        }
                    }
                }
            } else if (visible.isEmpty()) {
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
                                    onSelect = tapCard,
                                    onClose = onClose,
                                    onAddToGroup = { groupTarget = tab },
                                    onRemoveFromGroup = { tabGroups.removeTab(tab.id) },
                                    checked = picked?.contains(tab.id),
                                    onStartSelection = { picked = setOf(tab.id) },
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
                            onSelect = tapCard,
                            onClose = onClose,
                            onAddToGroup = { groupTarget = tab },
                            onRemoveFromGroup = null,
                            checked = picked?.contains(tab.id),
                            onStartSelection = { picked = setOf(tab.id) },
                        )
                    }
                }
            }
        }
    }

    if (groupPicked) {
        val chosen = visible.filter { it.id in picked.orEmpty() }
        val first = chosen.firstOrNull()
        if (first == null) {
            groupPicked = false
        } else {
            AddToGroupDialog(
                groups = allGroups.filter { it.contextId == first.contextId },
                onPick = { group -> chosen.forEach { tabGroups.addTab(group.id, it.id) }; groupPicked = false; picked = null },
                onCreate = { name, color ->
                    val group = tabGroups.create(name, color, first)
                    chosen.drop(1).forEach { tabGroups.addTab(group.id, it.id) }
                    groupPicked = false
                    picked = null
                },
                onDismiss = { groupPicked = false },
            )
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

/** Kilitli gizli sekmeler: içerik yerine yalnızca kilit ve "Kilidi aç" düğmesi. */
@Composable
private fun LockedPrivateTabs(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.tabs_private_locked), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tabs_private_locked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onUnlock) { Text(stringResource(R.string.tabs_private_unlock)) }
    }
}

/** Bu süredir açılmamış sekmeler "eski" sayılır (7 gün). */
private const val OLD_TAB_MS = 7L * 24 * 60 * 60 * 1000
