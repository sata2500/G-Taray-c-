package com.gtarayici.browser.ui.screens

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
            AddFab(stringResource(if (showPrivate) R.string.menu_new_private_tab else R.string.menu_new_tab)) {
                onNewTab(showPrivate)
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

