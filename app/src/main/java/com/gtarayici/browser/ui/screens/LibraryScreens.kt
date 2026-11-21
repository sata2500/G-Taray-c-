package com.gtarayici.browser.ui.screens

import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.ui.feedback.InputValidation
import android.text.format.DateUtils
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
import com.gtarayici.browser.data.HistoryEntry
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.gtarayici.browser.downloads.DownloadedFiles
import com.gtarayici.browser.search.TextMatch
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.components.Favicon
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.components.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.content.DownloadState

@Composable
fun HistoryScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = context.components.database.history()
    val profileId = context.components.profiles.active.collectAsState().value.id
    val entries by remember(profileId) { dao.recent(profileId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    OverlayScaffold(
        title = stringResource(R.string.menu_history),
        onBack = onBack,
        actions = {
            if (entries.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.DeleteSweep, stringResource(R.string.history_clear_all))
                }
            }
        },
    ) { modifier ->
        val visible = entries.filter {
            filter.isBlank() || TextMatch.matches(filter, it.title, it.url)
        }
        Column(modifier) {
            if (entries.isNotEmpty()) FilterField(filter) { filter = it }
            if (visible.isEmpty()) {
                EmptyState(
                    stringResource(if (filter.isBlank()) R.string.history_empty else R.string.search_no_results),
                    icon = if (filter.isBlank()) Icons.Rounded.History else Icons.Rounded.SearchOff,
                )
            } else {
                LazyColumn {
                    historyGroups(visible).forEach { (label, group) ->
                        item(key = "day-$label") { DayHeader(stringResource(label)) }
                        items(group, key = { it.url }) { entry ->
                        ListRow(
                            leading = { Favicon(entry.url) },
                            headline = {
                                Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supporting = {
                                Text(
                                    "${UrlInput.host(entry.url)} · " +
                                        DateUtils.formatDateTime(context, entry.lastVisited, DateUtils.FORMAT_SHOW_TIME),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailing = {
                                IconButton(onClick = {
                                    val app = context.components
                                    app.applicationScope.launch(Dispatchers.IO) { dao.delete(profileId, entry.url) }
                                    app.messenger.undoable(UiText.res(R.string.history_entry_deleted), UNDO_HISTORY_KEY) {
                                        app.applicationScope.launch(Dispatchers.IO) { dao.upsert(entry) }
                                    }
                                }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.action_delete))
                                }
                            },
                            modifier = Modifier.clickable { onOpen(entry.url) },
                        )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch(Dispatchers.IO) { dao.clear(profileId) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

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

/** Geçmişi "Bugün / Dün / Bu hafta / Bu ay / Daha eski" gruplarına ayırır (sıra korunur). */
internal fun historyGroups(entries: List<HistoryEntry>, now: Long = System.currentTimeMillis()): List<Pair<Int, List<HistoryEntry>>> {
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val today = calendar.timeInMillis
    val day = 24L * 60 * 60 * 1000
    fun bucket(time: Long) = when {
        time >= today -> R.string.history_today
        time >= today - day -> R.string.history_yesterday
        time >= today - 6 * day -> R.string.history_this_week
        time >= today - 29 * day -> R.string.history_this_month
        else -> R.string.history_older
    }
    return entries.groupBy { bucket(it.lastVisited) }.toList()
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val downloads by components.store.observe { state ->
        state.downloads.values.filter { !it.private }.sortedByDescending { it.createdTime }
    }
    var removing by remember { mutableStateOf<DownloadState?>(null) }
    removing?.let { download ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(download.fileName.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text(stringResource(R.string.download_delete_question)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    val deleted = DownloadedFiles.delete(context, download.fileName, download.directoryPath)
                    components.downloadsUseCases.removeDownload(download.id)
                    if (!deleted) context.components.messenger.error(UiText.res(R.string.download_delete_failed))
                }) { Text(stringResource(R.string.download_delete_file), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    removing = null
                    components.downloadsUseCases.removeDownload(download.id)
                }) { Text(stringResource(R.string.download_remove_from_list)) }
            },
        )
    }

    OverlayScaffold(title = stringResource(R.string.menu_downloads), onBack = onBack) { modifier ->
        if (downloads.isEmpty()) {
            EmptyState(stringResource(R.string.downloads_empty), modifier, icon = Icons.Rounded.Download)
        } else {
            LazyColumn(modifier) {
                items(downloads, key = { it.id }) { download ->
                    val done = download.status == DownloadState.Status.COMPLETED
                    // Kalan süre: indirme boyunca aynı tahminci kullanılır (bayt sayısı değiştikçe güncellenir).
                    val estimator = remember(download.id) { com.gtarayici.browser.ui.feedback.ProgressEstimator() }
                    val remaining = remember(download.currentBytesCopied, download.status) {
                        val total = download.contentLength
                        if (download.status == DownloadState.Status.DOWNLOADING && total != null) {
                            estimator.update(download.currentBytesCopied, total)
                        } else {
                            null
                        }
                    }
                    ListRow(
                        leading = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                        headline = {
                            Text(download.fileName ?: download.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supporting = {
                            Column {
                                Text(downloadStatusText(download, remaining), maxLines = 2)
                                if (download.status == DownloadState.Status.DOWNLOADING) {
                                    val progress = download.progress
                                    if (progress != null) {
                                        LinearProgressIndicator(
                                            progress = { progress / 100f },
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        )
                                    } else {
                                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                                    }
                                }
                            }
                        },
                        trailing = {
                            IconButton(onClick = {
                                // Tamamlanan indirmede dosyanın da silinip silinmeyeceği sorulur; diğerleri yalnızca listeden çıkar.
                                if (done) removing = download else components.downloadsUseCases.removeDownload(download.id)
                            }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.action_delete))
                            }
                        },
                        modifier = Modifier.clickable(enabled = done) {
                            val opened = components.downloadFileUtils.openFile(
                                fileName = download.fileName,
                                directoryPath = download.directoryPath,
                                contentType = download.contentType,
                            )
                            if (!opened) {
                                context.components.messenger.warning(UiText.res(R.string.download_open_failed))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun downloadStatusText(download: DownloadState, remainingMs: Long? = null): String {
    val context = LocalContext.current
    val total = download.contentLength
    val size = when {
        total == null -> null
        download.status == DownloadState.Status.DOWNLOADING -> stringResource(
            R.string.progress_bytes,
            Formatter.formatShortFileSize(context, download.currentBytesCopied),
            Formatter.formatShortFileSize(context, total),
        )
        else -> Formatter.formatShortFileSize(context, total)
    }
    val left = remainingMs?.let { com.gtarayici.browser.ui.feedback.ProgressEstimator.remainingText(it).resolve(context) }
    val status = when (download.status) {
        DownloadState.Status.COMPLETED -> stringResource(R.string.download_completed)
        DownloadState.Status.FAILED -> stringResource(R.string.download_failed)
        DownloadState.Status.CANCELLED -> stringResource(R.string.download_cancelled)
        DownloadState.Status.PAUSED -> stringResource(R.string.download_paused)
        else -> stringResource(R.string.download_in_progress)
    }
    return listOfNotNull(status, size, left, UrlInput.host(download.url).ifBlank { null }).joinToString(" · ")
}

@Composable
private fun FilterField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        placeholder = { Text(stringResource(R.string.search_in_list)) },
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private const val UNDO_HISTORY_KEY = "geri-al-gecmis"
private const val UNDO_BOOKMARK_KEY = "geri-al-yer-imi"
