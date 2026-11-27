package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.EnhancedEncryption
import kotlinx.coroutines.launch
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
fun DownloadsScreen(
    private: Boolean = false,
    onOpenVault: (itemId: String?) -> Unit = {},
    /** Dosyaları uygulamanın görüntüleyicisinde açar (yana kaydırınca diğer indirilenlere geçilir). */
    onOpenFiles: (List<com.gtarayici.browser.viewer.FileSource>, Int) -> Unit = { _, _ -> },
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val components = context.components
    val downloads by components.store.observe { state ->
        state.downloads.values.filter { it.private == private }.sortedByDescending { it.createdTime }
    }
    val youtubeProgress by com.gtarayici.browser.youtube.YouTubeDownloads.progress.collectAsState()
    var removing by remember { mutableStateOf<DownloadState?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
                    val vaultItem = download.directoryPath?.takeIf { it.startsWith(com.gtarayici.browser.vault.Vault.DOWNLOAD_PREFIX) }
                    val deleted = if (vaultItem != null) {
                        scope.launch { components.vault.delete(vaultItem.removePrefix(com.gtarayici.browser.vault.Vault.DOWNLOAD_PREFIX)) }
                        true
                    } else {
                        DownloadedFiles.delete(context, download.fileName, download.directoryPath)
                    }
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

    val encrypt by components.settings.encryptPrivateDownloads.collectAsState()
    OverlayScaffold(
        title = stringResource(if (private) R.string.downloads_private_title else R.string.menu_downloads),
        onBack = onBack,
        actions = {
            if (private) {
                IconButton(onClick = { onOpenVault(null) }) {
                    Icon(Icons.Rounded.EnhancedEncryption, stringResource(R.string.vault_title))
                }
            }
        },
    ) { modifier ->
        if (downloads.isEmpty()) {
            Column(modifier) {
                if (private) EncryptRow(encrypt) { components.settings.setEncryptPrivateDownloads(it) }
                EmptyState(
                    stringResource(if (private) R.string.downloads_private_empty else R.string.downloads_empty),
                    Modifier.weight(1f),
                    icon = Icons.Rounded.Download,
                )
            }
        } else {
            // Sürenler üstte, bitenler altta.
            val active = downloads.filter { it.status.isActive() }
            val finished = downloads.filterNot { it.status.isActive() }
            LazyColumn(modifier) {
                if (private) {
                    item { EncryptRow(encrypt) { components.settings.setEncryptPrivateDownloads(it) } }
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
                    val vaultId = download.directoryPath?.takeIf { it.startsWith(com.gtarayici.browser.vault.Vault.DOWNLOAD_PREFIX) }
                        ?.removePrefix(com.gtarayici.browser.vault.Vault.DOWNLOAD_PREFIX)
                    ListRow(
                        leading = {
                            // Dosya türüne göre simge, renkli küçük bir kutuda.
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Icon(
                                        if (vaultId != null) Icons.Rounded.EnhancedEncryption else typeIcon(download.fileName.orEmpty(), download.contentType),
                                        null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        },
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
                            if (vaultId != null) {
                                onOpenVault(vaultId)
                                return@clickable
                            }
                            // Uygulamanın açabildiği dosyalar kendi görüntüleyicisinde açılır.
                            if (com.gtarayici.browser.viewer.FileKind.of(download.fileName.orEmpty(), download.contentType) != com.gtarayici.browser.viewer.FileKind.OTHER) {
                                scope.launch {
                                    val viewable = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        downloads.filter {
                                            it.status == DownloadState.Status.COMPLETED && it.directoryPath?.startsWith(com.gtarayici.browser.vault.Vault.DOWNLOAD_PREFIX) != true &&
                                                com.gtarayici.browser.viewer.FileKind.of(it.fileName.orEmpty(), it.contentType) != com.gtarayici.browser.viewer.FileKind.OTHER
                                        }.mapNotNull { d ->
                                            DownloadedFiles.uri(context, d.fileName, d.directoryPath)?.let { uri ->
                                                d.id to com.gtarayici.browser.viewer.UriSource(context, uri, d.fileName.orEmpty(), d.contentType, d.contentLength ?: -1)
                                            }
                                        }
                                    }
                                    val index = viewable.indexOfFirst { it.first == download.id }
                                    if (index >= 0) onOpenFiles(viewable.map { it.second }, index)
                                    else context.components.messenger.warning(UiText.res(R.string.download_open_failed))
                                }
                                return@clickable
                            }
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


/** Dosya türünün simgesi. */
private fun typeIcon(name: String, mime: String?): androidx.compose.ui.graphics.vector.ImageVector {
    val r = androidx.compose.material.icons.Icons.Rounded
    return when (com.gtarayici.browser.viewer.FileKind.of(name, mime)) {
        com.gtarayici.browser.viewer.FileKind.IMAGE -> r.Image
        com.gtarayici.browser.viewer.FileKind.VIDEO -> r.Movie
        com.gtarayici.browser.viewer.FileKind.AUDIO -> r.Audiotrack
        com.gtarayici.browser.viewer.FileKind.PDF -> r.PictureAsPdf
        com.gtarayici.browser.viewer.FileKind.EPUB, com.gtarayici.browser.viewer.FileKind.FB2, com.gtarayici.browser.viewer.FileKind.COMIC -> r.MenuBook
        com.gtarayici.browser.viewer.FileKind.SHEET -> r.TableChart
        com.gtarayici.browser.viewer.FileKind.SLIDES -> r.Slideshow
        com.gtarayici.browser.viewer.FileKind.WORD, com.gtarayici.browser.viewer.FileKind.TEXT -> r.Description
        com.gtarayici.browser.viewer.FileKind.OTHER -> when {
            name.endsWith(".apk", true) -> r.Android
            Regex("""\.(zip|rar|7z|tar|gz)$""", RegexOption.IGNORE_CASE).containsMatchIn(name) -> r.FolderZip
            else -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }
}

/** Gizli indirmeler ekranında: "Gizli indirmeleri şifrele" anahtarı. */
@Composable
private fun EncryptRow(on: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(
        leading = { Icon(Icons.Rounded.EnhancedEncryption, null) },
        headline = { Text(stringResource(R.string.settings_encrypt_private_downloads)) },
        supporting = { Text(stringResource(R.string.settings_encrypt_private_downloads_summary)) },
        trailing = { androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!on) },
    )
}

/** Süren (henüz bitmemiş) indirme mi? */
private fun DownloadState.Status.isActive(): Boolean =
    this == DownloadState.Status.DOWNLOADING || this == DownloadState.Status.PAUSED || this == DownloadState.Status.INITIATED
