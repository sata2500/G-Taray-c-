package com.gtarayici.browser.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.downloads.manager.DownloadNames
import com.gtarayici.browser.downloads.manager.MediaKind
import com.gtarayici.browser.downloads.manager.NewDownload
import com.gtarayici.browser.downloads.manager.PageResource
import com.gtarayici.browser.downloads.manager.PageScan
import com.gtarayici.browser.downloads.manager.RequestContext
import com.gtarayici.browser.downloads.manager.SegmentedDownloader
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.designsystem.LoadingState
import com.gtarayici.browser.ui.designsystem.rememberFullSheetState
import com.gtarayici.browser.ui.feedback.MessageAction
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.youtube.YouTubeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.findTabOrCustomTab

/** Bir grupta varsayılan olarak gösterilen en fazla öğe (görseller çok olabilir). */
private const val COLLAPSED_LIMIT = 6

/**
 * "Sayfadaki indirilebilirler": sayfadaki video, ses, akış, belge, arşiv, uygulama ve görselleri bulur, türüne göre
 * gruplar; seçilenler çok bağlantılı indirme yöneticisiyle indirilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageDownloadsSheet(
    tabId: String,
    onDismiss: () -> Unit,
    onYouTube: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val context = LocalContext.current
    val components = context.components
    val tab = components.store.state.findTabOrCustomTab(tabId)
    val pageUrl = tab?.content?.url.orEmpty()
    val isYouTube = YouTubeDownloader.isVideoPage(pageUrl)
    var scan by remember { mutableStateOf<PageScan?>(null) }
    var loading by remember { mutableStateOf(true) }
    val sizes = remember { mutableStateMapOf<String, Long>() }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val expanded = remember { mutableStateMapOf<MediaKind, Boolean>() }

    LaunchedEffect(tabId) {
        val session = tab?.engineState?.engineSession
        scan = session?.let { components.resourceScanner.scan(it) } ?: PageScan(emptyList(), tab?.content?.title.orEmpty(), false)
        loading = false
        scan?.resources?.singleOrNull { it.kind == MediaKind.VIDEO || it.kind == MediaKind.STREAM }?.let { selected[it.url] = true }
        // Boyutlar sunucuya sorulur (görseller hariç, en fazla 40 öğe, aynı anda 4 istek).
        val downloader = SegmentedDownloader(components.downloadQueue.clientFor(tab?.content?.private == true), retries = 0)
        val gate = Semaphore(4)
        val ctx = RequestContext(referer = pageUrl, private = tab?.content?.private == true)
        withContext(Dispatchers.IO) {
            scan?.resources.orEmpty().filter { it.kind != MediaKind.IMAGE && it.kind != MediaKind.STREAM }.take(40).map { resource ->
                async {
                    gate.withPermit {
                        runCatching { downloader.probe(resource.url, ctx.copy(referer = resource.referer ?: pageUrl)) }.getOrNull()?.total?.takeIf { it > 0 }?.let { sizes[resource.url] = it }
                    }
                }
            }.awaitAll()
        }
    }

    val chosen = scan?.resources.orEmpty().filter { selected[it.url] == true }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFullSheetState()) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(R.string.pd_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                UrlInput.host(pageUrl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (isYouTube) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.OndemandVideo, null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.pd_youtube_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.pd_youtube_body), style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton(onClick = onYouTube) { Text(stringResource(R.string.pd_youtube_action)) }
                    }
                }
            }
            val result = scan
            when {
                loading -> LoadingState(stringResource(R.string.pd_scanning))
                result == null || result.resources.isEmpty() -> EmptyNotice(result?.hasStreamingVideo == true && !isYouTube)
                else -> {
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 560.dp)) {
                        if (result.hasStreamingVideo && result.groups[MediaKind.VIDEO].isNullOrEmpty() && result.groups[MediaKind.STREAM].isNullOrEmpty() && !isYouTube) {
                            item { StreamingNotice() }
                        }
                        result.groups.forEach { (kind, all) ->
                            val showAll = expanded[kind] == true || all.size <= COLLAPSED_LIMIT + 1
                            val shown = if (showAll) all else all.take(COLLAPSED_LIMIT)
                            item(key = "h-$kind") {
                                GroupHeader(
                                    kind = kind,
                                    count = all.size,
                                    allSelected = all.all { selected[it.url] == true },
                                    onToggleAll = { on -> all.forEach { selected[it.url] = on } },
                                )
                            }
                            items(shown, key = { "${kind.name}-${it.url}" }) { resource ->
                                ResourceRow(
                                    resource = resource,
                                    title = displayName(resource, result.title),
                                    size = sizes[resource.url],
                                    checked = selected[resource.url] == true,
                                    onToggle = { selected[resource.url] = !(selected[resource.url] ?: false) },
                                )
                            }
                            if (!showAll) {
                                item(key = "more-$kind") {
                                    TextButton(onClick = { expanded[kind] = true }, modifier = Modifier.padding(start = 56.dp)) {
                                        Text(stringResource(R.string.pd_show_all, all.size))
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val total = chosen.sumOf { sizes[it.url] ?: 0 }
                        Text(
                            if (chosen.isEmpty()) stringResource(R.string.pd_choose)
                            else listOfNotNull(
                                pluralStringResource(R.plurals.pd_selected, chosen.size, chosen.size),
                                total.takeIf { it > 0 }?.let { "~" + Formatter.formatShortFileSize(context, it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            enabled = chosen.isNotEmpty(),
                            onClick = {
                                val ctx = RequestContext(referer = pageUrl, private = tab?.content?.private == true)
                                chosen.forEach { resource ->
                                    components.downloadQueue.enqueue(
                                        NewDownload(
                                            url = resource.url,
                                            suggestedName = suggestedName(resource, result.title),
                                            kind = resource.kind,
                                            // Gömülü oynatıcıdaki dosya, oynatıcının adresiyle istenir.
                                            context = ctx.copy(referer = resource.referer ?: pageUrl),
                                        ),
                                    )
                                }
                                components.messenger.success(
                                    UiText.plural(R.plurals.pd_started, chosen.size, chosen.size),
                                    MessageAction(UiText.res(R.string.menu_downloads), onOpenDownloads),
                                )
                                onDismiss()
                            },
                        ) {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pd_download))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GroupHeader(kind: MediaKind, count: Int, allSelected: Boolean, onToggleAll: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(kind), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            "${stringResource(kindLabel(kind))} · $count",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        TextButton(onClick = { onToggleAll(!allSelected) }) {
            Text(stringResource(if (allSelected) R.string.pd_select_none else R.string.pd_select_all))
        }
    }
}

@Composable
private fun ResourceRow(resource: PageResource, title: String, size: Long?, checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(start = 4.dp, end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                DownloadNames.extension(resource.url).uppercase().takeIf { it.isNotEmpty() },
                if (resource.width > 0 && resource.height > 0) "${resource.width}×${resource.height}" else null,
                resource.durationSeconds.takeIf { it > 0 }?.let(::duration),
                size?.let { Formatter.formatShortFileSize(context, it) },
                UrlInput.host(resource.url).takeIf { it.isNotEmpty() },
            )
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyNotice(streaming: Boolean) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.pd_empty), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (streaming) R.string.pd_streaming else R.string.pd_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StreamingNotice() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.pd_streaming),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun duration(seconds: Int): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

/** Listede gösterilen ad: bağlantı metni, yoksa dosya adı. */
private fun displayName(resource: PageResource, pageTitle: String): String =
    suggestedName(resource, pageTitle) ?: DownloadNames.fromUrl(resource.url)

/** İndirilecek dosyaya önerilen ad: videolarda sayfa başlığı, bağlantılarda bağlantı metni (dosya adına benziyorsa). */
internal fun suggestedName(resource: PageResource, pageTitle: String): String? {
    val label = resource.label?.trim().orEmpty()
    return when {
        label.contains('.') && label.length <= 120 -> label
        (resource.kind == MediaKind.VIDEO || resource.kind == MediaKind.STREAM || resource.kind == MediaKind.AUDIO) && pageTitle.isNotBlank() -> pageTitle
        label.isNotEmpty() && resource.kind != MediaKind.IMAGE -> label
        else -> null
    }
}

private fun kindIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.VIDEO -> Icons.Rounded.Movie
    MediaKind.STREAM -> Icons.Rounded.LiveTv
    MediaKind.AUDIO -> Icons.Rounded.AudioFile
    MediaKind.DOCUMENT -> Icons.Rounded.Article
    MediaKind.ARCHIVE -> Icons.Rounded.Folder
    MediaKind.APP -> Icons.Rounded.Apps
    MediaKind.IMAGE -> Icons.Rounded.Image
    MediaKind.OTHER -> Icons.Rounded.InsertDriveFile
}

private fun kindLabel(kind: MediaKind): Int = when (kind) {
    MediaKind.VIDEO -> R.string.pd_kind_video
    MediaKind.STREAM -> R.string.pd_kind_stream
    MediaKind.AUDIO -> R.string.pd_kind_audio
    MediaKind.DOCUMENT -> R.string.pd_kind_document
    MediaKind.ARCHIVE -> R.string.pd_kind_archive
    MediaKind.APP -> R.string.pd_kind_app
    MediaKind.IMAGE -> R.string.pd_kind_image
    MediaKind.OTHER -> R.string.pd_kind_other
}
