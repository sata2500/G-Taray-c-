package com.gtarayici.browser.ui.screens

import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.components.EmptyState
import com.gtarayici.browser.ui.components.Favicon
import com.gtarayici.browser.ui.components.OverlayScaffold
import com.gtarayici.browser.ui.components.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.content.DownloadState

@Composable
fun HistoryScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = context.components.database.history()
    val entries by remember { dao.recent() }.collectAsState(initial = emptyList())
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
            filter.isBlank() || it.title.contains(filter, true) || it.url.contains(filter, true)
        }
        Column(modifier) {
            if (entries.isNotEmpty()) FilterField(filter) { filter = it }
            if (visible.isEmpty()) {
                EmptyState(stringResource(R.string.history_empty))
            } else {
                LazyColumn {
                    items(visible, key = { it.url }) { entry ->
                        ListItem(
                            leadingContent = { Favicon(entry.url) },
                            headlineContent = {
                                Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    "${UrlInput.host(entry.url)} · " +
                                        DateUtils.getRelativeTimeSpanString(entry.lastVisited),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { scope.launch(Dispatchers.IO) { dao.delete(entry.url) } }) {
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

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch(Dispatchers.IO) { dao.clear() }
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
    val bookmarks by remember { dao.all() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("") }

    OverlayScaffold(title = stringResource(R.string.menu_bookmarks), onBack = onBack) { modifier ->
        val visible = bookmarks.filter {
            filter.isBlank() || it.title.contains(filter, true) || it.url.contains(filter, true)
        }
        Column(modifier) {
            if (bookmarks.isNotEmpty()) FilterField(filter) { filter = it }
            if (visible.isEmpty()) {
                EmptyState(stringResource(R.string.bookmarks_empty))
            } else {
                LazyColumn {
                    items(visible, key = { it.id }) { bookmark ->
                        ListItem(
                            leadingContent = { Favicon(bookmark.url) },
                            headlineContent = { Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(UrlInput.displayUrl(bookmark.url), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                IconButton(onClick = { scope.launch(Dispatchers.IO) { dao.deleteByUrl(bookmark.url) } }) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                                }
                            },
                            modifier = Modifier.clickable { onOpen(bookmark.url) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val downloads by components.store.observe { state ->
        state.downloads.values.filter { !it.private }.sortedByDescending { it.createdTime }
    }

    OverlayScaffold(title = stringResource(R.string.menu_downloads), onBack = onBack) { modifier ->
        if (downloads.isEmpty()) {
            EmptyState(stringResource(R.string.downloads_empty), modifier)
        } else {
            LazyColumn(modifier) {
                items(downloads, key = { it.id }) { download ->
                    val done = download.status == DownloadState.Status.COMPLETED
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.InsertDriveFile, null) },
                        headlineContent = {
                            Text(download.fileName ?: download.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Column {
                                Text(downloadStatusText(download), maxLines = 1)
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
                        trailingContent = {
                            IconButton(onClick = { components.downloadsUseCases.removeDownload(download.id) }) {
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
                                Toast.makeText(context, R.string.download_open_failed, Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun downloadStatusText(download: DownloadState): String {
    val context = LocalContext.current
    val size = download.contentLength?.let { Formatter.formatShortFileSize(context, it) }
    val status = when (download.status) {
        DownloadState.Status.COMPLETED -> stringResource(R.string.download_completed)
        DownloadState.Status.FAILED -> stringResource(R.string.download_failed)
        DownloadState.Status.CANCELLED -> stringResource(R.string.download_cancelled)
        DownloadState.Status.PAUSED -> stringResource(R.string.download_paused)
        else -> stringResource(R.string.download_in_progress)
    }
    return listOfNotNull(status, size, UrlInput.host(download.url).ifBlank { null }).joinToString(" · ")
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
