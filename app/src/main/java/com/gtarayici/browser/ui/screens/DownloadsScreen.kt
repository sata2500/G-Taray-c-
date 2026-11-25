package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.pluralStringResource
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

/** İndirilenler. [private]: gizli sekmelerdeyken yalnızca gizli sekmelerde indirilenler gösterilir (ve tersi). */
@Composable
fun DownloadsScreen(private: Boolean = false, onBack: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val downloads by components.store.observe { state ->
        state.downloads.values.filter { it.private == private }.sortedByDescending { it.createdTime }
    }
    val youtubeProgress by com.gtarayici.browser.youtube.YouTubeDownloads.progress.collectAsState()
    var removing by remember { mutableStateOf<DownloadState?>(null) }
    val queue = components.downloadQueue
    val tasks by queue.tasks.collectAsState()
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

    OverlayScaffold(
        title = stringResource(if (private) R.string.downloads_private_title else R.string.menu_downloads),
        onBack = onBack,
    ) { modifier ->
        if (downloads.isEmpty()) {
            EmptyState(
                stringResource(if (private) R.string.downloads_private_empty else R.string.downloads_empty),
                modifier,
                icon = Icons.Rounded.Download,
            )
        } else {
            // Sürenler üstte, bitenler altta.
            val active = downloads.filter { it.status.isActive() }
            val finished = downloads.filterNot { it.status.isActive() }
            LazyColumn(modifier) {
                if (private) {
                    item {
                        Text(
                            stringResource(R.string.downloads_private_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                if (active.isNotEmpty()) {
                    item { com.gtarayici.browser.ui.designsystem.SectionHeader(stringResource(R.string.downloads_section_active)) }
                }
                val sections = listOf(active, finished)
                sections.forEachIndexed { index, section ->
                if (index == 1 && active.isNotEmpty() && finished.isNotEmpty()) {
                    item { com.gtarayici.browser.ui.designsystem.SectionHeader(stringResource(R.string.downloads_section_done)) }
                }
                items(section, key = { it.id }) { download ->
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
                                val task = tasks.firstOrNull { it.id == download.id }
                                val live = task?.takeIf { it.status == com.gtarayici.browser.downloads.manager.TaskStatus.RUNNING }
                                val failure = task?.error?.takeIf { download.status == DownloadState.Status.FAILED }
                                Text(
                                    listOfNotNull(
                                        downloadStatusText(download, live?.remainingMs ?: remaining),
                                        live?.speed?.takeIf { it > 0 }?.let {
                                            stringResource(R.string.dm_speed, android.text.format.Formatter.formatShortFileSize(context, it))
                                        },
                                        live?.connections?.takeIf { it > 1 }?.let { pluralStringResource(R.plurals.dm_connections, it, it) },
                                    ).joinToString(" · "),
                                    maxLines = 2,
                                )
                                // Neden indirilemediği ve ne yapılabileceği.
                                failure?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                }
                                if (download.status == DownloadState.Status.DOWNLOADING) {
                                    // Parça sayısıyla ilerleyen akışlar (HLS) ve YouTube indirmeleri kendi ilerlemesini verir.
                                    val progress = live?.progress ?: youtubeProgress[download.id] ?: download.progress?.let { it / 100f }
                                    if (progress != null) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        )
                                    } else {
                                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                                    }
                                }
                            }
                        },
                        trailing = {
                            Row {
                                val owned = queue.owns(download.id)
                                val youtube = com.gtarayici.browser.youtube.YouTubeDownloads.owns(download.id)
                                when {
                                    youtube && download.status == DownloadState.Status.FAILED -> IconButton(onClick = {
                                        components.messenger.info(UiText.res(R.string.downloads_youtube_retry))
                                    }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.dm_retry)) }
                                    owned && download.status == DownloadState.Status.DOWNLOADING -> IconButton(onClick = { queue.pause(download.id) }) {
                                        Icon(Icons.Rounded.Pause, stringResource(R.string.dm_pause))
                                    }
                                    owned && download.status == DownloadState.Status.PAUSED -> IconButton(onClick = { queue.resume(download.id) }) {
                                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.dm_resume))
                                    }
                                    download.status == DownloadState.Status.FAILED || download.status == DownloadState.Status.CANCELLED ->
                                        IconButton(onClick = { queue.retryFromState(download) }) {
                                            Icon(Icons.Rounded.Refresh, stringResource(R.string.dm_retry))
                                        }
                                }
                                IconButton(onClick = {
                                    when {
                                        // Tamamlanan indirmede dosyanın da silinip silinmeyeceği sorulur.
                                        done -> removing = download
                                        // Süren indirme iptal edilir, yarım dosya silinir.
                                        owned -> queue.cancel(download.id)
                                        youtube && download.status == DownloadState.Status.DOWNLOADING ->
                                            com.gtarayici.browser.youtube.YouTubeDownloads.cancel(context, download.id)
                                        else -> components.downloadsUseCases.removeDownload(download.id)
                                    }
                                }) {
                                    Icon(Icons.Filled.Close, stringResource(if (done) R.string.action_delete else R.string.dm_cancel))
                                }
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


/** Süren (henüz bitmemiş) indirme mi? */
private fun DownloadState.Status.isActive(): Boolean =
    this == DownloadState.Status.DOWNLOADING || this == DownloadState.Status.PAUSED || this == DownloadState.Status.INITIATED
