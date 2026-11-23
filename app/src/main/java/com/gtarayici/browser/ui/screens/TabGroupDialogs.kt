package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import com.gtarayici.browser.tabs.TabGroup
import com.gtarayici.browser.tabs.TabGroups
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.gtarayici.browser.ui.components.colorOption

/* Sekme gruplarının başlığı ve pencereleri (gruba ekle, grup oluştur/düzenle). */

@Composable
internal fun GroupHeader(
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
        // Ad ve sayı kalan genişliği tek başına kullanır; düğmeler her zaman sağ kenarda durur.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
        }
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
internal fun AddToGroupDialog(
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
                    ListRow(
                        leading = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(group.color))) },
                        headline = { Text(group.name.ifBlank { stringResource(R.string.tab_groups_unnamed) }) },
                        supporting = { Text(pluralStringResource(R.plurals.tab_count, group.tabIds.size, group.tabIds.size)) },
                        modifier = Modifier.clickable { onPick(group) },
                    )
                }
                ListRow(
                    leading = { Icon(Icons.Filled.Add, null) },
                    headline = { Text(stringResource(R.string.tab_groups_new)) },
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
internal fun GroupEditorDialog(
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
                com.gtarayici.browser.ui.designsystem.ColorPicker(
                    colors = TabGroups.COLORS.map { Color(it) },
                    selected = TabGroups.COLORS.indexOf(color),
                    onSelect = { color = TabGroups.COLORS[it] },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

