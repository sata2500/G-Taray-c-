package com.gtarayici.browser.ui.screens

import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.ui.feedback.InputValidation
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.res.pluralStringResource
import com.gtarayici.browser.data.Bookmark
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.search.TextMatch
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.components.Favicon
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BookmarksScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = context.components.database.bookmarks()
    val profileId = context.components.profiles.active.collectAsState().value.id
    val bookmarks by remember(profileId) { dao.all(profileId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var renamingFolder by remember { mutableStateOf<String?>(null) }
    val folders = bookmarks.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
    BackHandler(enabled = folder.isNotEmpty()) { folder = "" }

    OverlayScaffold(
        title = folder.ifEmpty { stringResource(R.string.menu_bookmarks) },
        onBack = { if (folder.isNotEmpty()) folder = "" else onBack() },
        actions = {
            if (folder.isNotEmpty()) {
                IconButton(onClick = { renamingFolder = folder }) { Icon(Icons.Filled.Edit, stringResource(R.string.bookmark_rename_folder)) }
            }
        },
    ) { modifier ->
        val searching = filter.isNotBlank()
        val visible = bookmarks.filter {
            if (searching) TextMatch.matches(filter, it.title, it.url) else it.folder == folder
        }
        Column(modifier) {
            if (bookmarks.isNotEmpty() && folder.isEmpty()) FilterField(filter) { filter = it }
            LazyColumn {
                if (!searching && folder.isEmpty()) {
                    items(folders, key = { "folder-$it" }) { name ->
                        ListRow(
                            leading = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                            headline = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supporting = {
                                val count = bookmarks.count { it.folder == name }
                                Text(pluralStringResource(R.plurals.bookmark_count, count, count))
                            },
                            modifier = Modifier.clickable { folder = name },
                        )
                    }
                }
                items(visible, key = { it.id }) { bookmark ->
                    BookmarkRow(
                        bookmark,
                        onOpen = { onOpen(bookmark.url) },
                        onEdit = { editing = bookmark },
                        onDelete = {
                            val app = context.components
                            app.applicationScope.launch(Dispatchers.IO) { dao.deleteById(bookmark.id) }
                            app.messenger.undoable(UiText.res(R.string.bookmark_removed), UNDO_BOOKMARK_KEY) {
                                app.applicationScope.launch(Dispatchers.IO) { dao.upsert(bookmark) }
                            }
                        },
                    )
                }
            }
            if (visible.isEmpty() && (searching || folders.isEmpty() || folder.isNotEmpty())) {
                EmptyState(
                    stringResource(if (searching) R.string.search_no_results else R.string.bookmarks_empty),
                    icon = if (searching) Icons.Rounded.SearchOff else Icons.Rounded.BookmarkBorder,
                )
            }
        }
    }

    editing?.let { bookmark ->
        BookmarkEditDialog(
            bookmark = bookmark,
            folders = folders,
            onSave = { updated ->
                editing = null
                scope.launch(Dispatchers.IO) { runCatching { dao.upsert(updated) } }
            },
            onDismiss = { editing = null },
        )
    }
    renamingFolder?.let { old ->
        var name by remember(old) { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renamingFolder = null },
            title = { Text(stringResource(R.string.bookmark_rename_folder)) },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.bookmark_rename_folder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = name.trim()
                    renamingFolder = null
                    folder = target
                    scope.launch(Dispatchers.IO) { dao.renameFolder(profileId, old, target) }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renamingFolder = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun BookmarkRow(bookmark: Bookmark, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListRow(
        leading = { Favicon(bookmark.url) },
        headline = { Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supporting = { Text(UrlInput.displayUrl(bookmark.url), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_menu)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bookmark_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

@Composable
private fun BookmarkEditDialog(bookmark: Bookmark, folders: List<String>, onSave: (Bookmark) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(bookmark.title) }
    var url by remember { mutableStateOf(bookmark.url) }
    var folder by remember { mutableStateOf(bookmark.folder) }
    var folderMenu by remember { mutableStateOf(false) }
    val urlProblem = InputValidation.bookmarkUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmark_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.bookmark_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.bookmark_url)) },
                    singleLine = true,
                    isError = urlProblem != null,
                    supportingText = urlProblem?.let { { Text(stringResource(it)) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text(stringResource(R.string.bookmark_folder)) },
                        placeholder = { Text(stringResource(R.string.bookmark_folder_none)) },
                        singleLine = true,
                        trailingIcon = {
                            if (folders.isNotEmpty()) {
                                IconButton(onClick = { folderMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.bookmark_folder_none)) }, onClick = { folder = ""; folderMenu = false })
                        folders.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { folder = name; folderMenu = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = urlProblem == null,
                onClick = {
                    val normalized = InputValidation.normalizeBookmarkUrl(url)
                    onSave(bookmark.copy(title = title.trim().ifBlank { normalized }, url = normalized, folder = folder.trim()))
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}


private const val UNDO_BOOKMARK_KEY = "geri-al-yer-imi"
