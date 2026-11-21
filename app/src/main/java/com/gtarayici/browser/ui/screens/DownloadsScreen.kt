package com.gtarayici.browser.ui.screens

import com.gtarayici.browser.ui.feedback.UiText
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.downloads.DownloadedFiles
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.components.observe
import mozilla.components.browser.state.state.content.DownloadState

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

