package com.gtarayici.browser.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.downloads.manager.MediaKind
import com.gtarayici.browser.security.AppLock
import com.gtarayici.browser.ui.designsystem.AddFab
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.vault.VaultItem
import kotlinx.coroutines.launch

/** Kasadaki dosyaların gruplanması (filtre çipleri). */
private enum class VaultFilter(val label: Int) {
    ALL(R.string.vault_filter_all),
    PHOTOS(R.string.vault_filter_photos),
    VIDEOS(R.string.vault_filter_videos),
    AUDIO(R.string.vault_filter_audio),
    BOOKS(R.string.vault_filter_books),
    DOCUMENTS(R.string.vault_filter_documents),
    OTHER(R.string.vault_filter_other);

    fun matches(item: VaultItem): Boolean = when (this) {
        ALL -> true
        PHOTOS -> item.kind == MediaKind.IMAGE
        VIDEOS -> item.kind == MediaKind.VIDEO || item.kind == MediaKind.STREAM
        AUDIO -> item.kind == MediaKind.AUDIO
        BOOKS -> com.gtarayici.browser.viewer.FileKind.of(item.name, item.mime).isBookFormat
        DOCUMENTS -> item.kind == MediaKind.DOCUMENT && !com.gtarayici.browser.viewer.FileKind.of(item.name, item.mime).isBookFormat
        OTHER -> item.kind !in setOf(MediaKind.IMAGE, MediaKind.VIDEO, MediaKind.STREAM, MediaKind.AUDIO, MediaKind.DOCUMENT)
    }
}

/**
 * Gizli kasa: şifreli dosyaların galerisi ve dosya yöneticisi. Açmak uygulama kilidine bağlıdır (gizli sekmelerle
 * aynı kilit). Fotoğraflar, videolar, sesler, PDF'ler ve metinler uygulamanın içinde, şifresi yalnızca bellekte
 * çözülerek açılır. [openId]: doğrudan açılacak dosya (ör. İndirilenler'den).
 */
@Composable
fun VaultScreen(openId: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val lock = components.appLock
    val canLock = remember { lock.canLock(context) }
    val title = stringResource(R.string.vault_title)
    if (canLock && !lock.isUnlocked(AppLock.PRIVATE)) {
        LaunchedEffect(Unit) { (context as? FragmentActivity)?.let { lock.authenticate(it, AppLock.PRIVATE, title) { } } }
        OverlayScaffold(title = title, onBack = onBack) { modifier ->
            Box(modifier) {
                LockScreen(title, AppLock.PRIVATE) {
                    (context as? FragmentActivity)?.let { lock.authenticate(it, AppLock.PRIVATE, title) { } }
                }
            }
        }
        return
    }
    VaultContent(openId, canLock, onBack)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultContent(openId: String?, canLock: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val vault = components.vault
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vault.load() }
    val items by vault.items.collectAsState()
    var filter by remember { mutableStateOf(VaultFilter.ALL) }
    var viewing by remember { mutableStateOf(openId) }
    var actionsFor by remember { mutableStateOf<VaultItem?>(null) }
    var importing by remember { mutableStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing += uris.size
        scope.launch {
            var added = 0
            uris.forEach { uri ->
                runCatching {
                    val resolver = context.contentResolver
                    val name = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment ?: "dosya"
                    resolver.openInputStream(uri)?.use { vault.importStream(it, name, resolver.getType(uri)) }
                }.onSuccess { if (it != null) added++ }
                importing--
            }
            components.messenger.success(UiText.plural(R.plurals.vault_added, added, added))
        }
    }

    val shown = items.filter(filter::matches).sortedByDescending { it.added }
    viewing?.let { id ->
        val list = shown.takeIf { list -> list.any { it.id == id } } ?: items
        val sources = list.map { com.gtarayici.browser.viewer.VaultSource(vault, it) }
        com.gtarayici.browser.ui.viewer.FileViewer(
            sources,
            list.indexOfFirst { it.id == id }.coerceAtLeast(0),
            onClose = { viewing = null },
            onActions = { source -> (source as? com.gtarayici.browser.viewer.VaultSource)?.let { actionsFor = it.item } },
        )
        actionsFor?.let { item -> VaultActions(item, onDismiss = { actionsFor = null }, onDeleted = { viewing = null }) }
        return
    }

    OverlayScaffold(
        title = stringResource(R.string.vault_title),
        onBack = onBack,
        floatingActionButton = { AddFab(stringResource(R.string.vault_add)) { picker.launch(arrayOf("*/*")) } },
    ) { modifier ->
        Column(modifier) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VaultFilter.entries.forEach { f ->
                    val count = items.count(f::matches)
                    if (f == VaultFilter.ALL || count > 0) {
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${stringResource(f.label)} · $count") })
                    }
                }
            }
            if (shown.isEmpty()) {
                EmptyState(
                    stringResource(if (importing > 0) R.string.vault_importing else R.string.vault_empty),
                    Modifier.weight(1f),
                    icon = Icons.Rounded.Lock,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(
                                if (canLock) R.string.vault_info else R.string.vault_info_no_lock,
                                Formatter.formatShortFileSize(context, items.sumOf { it.size }),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    items(shown, key = { it.id }) { item ->
                        VaultTile(
                            item,
                            Modifier.combinedClickable(onClick = { viewing = item.id }, onLongClick = { actionsFor = item }),
                        )
                    }
                }
            }
        }
    }
    actionsFor?.let { item -> VaultActions(item, onDismiss = { actionsFor = null }, onDeleted = {}) }
}

@Composable
private fun VaultTile(item: VaultItem, modifier: Modifier) {
    val vault = LocalContext.current.components.vault
    val thumb by produceState<Bitmap?>(null, item.id, item.hasThumbnail) {
        value = if (item.hasThumbnail) vault.thumbnail(item.id) else null
    }
    Column(modifier.clip(RoundedCornerShape(12.dp))) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                val bitmap = thumb
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(kindIcon(item), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                }
                if (item.kind == MediaKind.VIDEO && item.durationMs > 0) {
                    Text(
                        DateUtils.formatElapsedTime(item.durationMs / 1000),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (item.kind == MediaKind.VIDEO && bitmap != null) {
                    Icon(Icons.Rounded.PlayCircle, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(32.dp))
                }
            }
        }
        if (item.kind != MediaKind.IMAGE) {
            Text(
                item.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
            )
        }
    }
}

internal fun kindIcon(item: VaultItem): ImageVector = when (item.kind) {
    MediaKind.IMAGE -> Icons.Rounded.Image
    MediaKind.VIDEO, MediaKind.STREAM -> Icons.Rounded.Movie
    MediaKind.AUDIO -> Icons.Rounded.Audiotrack
    MediaKind.DOCUMENT -> Icons.Rounded.Description
    MediaKind.ARCHIVE -> Icons.Rounded.Archive
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

/** Bir dosyanın işlemleri: başka uygulamada aç, paylaş, dışa aktar, yeniden adlandır, sil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultActions(item: VaultItem, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val vault = components.vault
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }

    when (confirm) {
        "export" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.vault_export)) },
            text = { Text(stringResource(R.string.vault_export_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    onDismiss()
                    scope.launch {
                        runCatching { vault.export(item.id) }
                            .onSuccess { components.messenger.success(UiText.res(R.string.vault_exported, it)) }
                            .onFailure { components.messenger.error(UiText.res(R.string.vault_export_failed)) }
                    }
                }) { Text(stringResource(R.string.vault_export)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text(stringResource(R.string.vault_delete_question)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    onDismiss()
                    onDeleted()
                    scope.launch { vault.delete(item.id) }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        "open", "share" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (confirm == "open") R.string.vault_open_with else R.string.action_share)) },
            text = { Text(stringResource(R.string.vault_external_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    val share = confirm == "share"
                    confirm = null
                    onDismiss()
                    val uri = vault.uri(item.id)
                    val intent = if (share) {
                        Intent(Intent.ACTION_SEND).setType(item.mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri)
                    } else {
                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime ?: "*/*")
                    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(intent, item.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        .onFailure { components.messenger.warning(UiText.res(R.string.download_open_failed)) }
                }) { Text(stringResource(R.string.vault_continue)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.vault_rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    renaming = false
                    onDismiss()
                    scope.launch { vault.rename(item.id, name) }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirm != null || renaming) return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                listOfNotNull(
                    Formatter.formatShortFileSize(context, item.size),
                    item.width.takeIf { it > 0 }?.let { "${item.width}×${item.height}" },
                    item.durationMs.takeIf { it > 0 }?.let { DateUtils.formatElapsedTime(it / 1000) },
                    DateUtils.getRelativeTimeSpanString(item.added).toString(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            ActionRow(Icons.Rounded.OpenInNew, R.string.vault_open_with) { confirm = "open" }
            ActionRow(Icons.Rounded.Share, R.string.action_share) { confirm = "share" }
            ActionRow(Icons.Rounded.SaveAlt, R.string.vault_export) { confirm = "export" }
            ActionRow(Icons.Rounded.Edit, R.string.vault_rename) { renaming = true }
            ActionRow(Icons.Rounded.Delete, R.string.action_delete) { confirm = "delete" }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: Int, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(stringResource(label)) },
        modifier = Modifier.combinedClickableCompat(onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.then(Modifier.combinedClickable(onClick = onClick))

/** Görüntüleyiciden geri tuşu: kasa listesine dönülür. */
@Composable
internal fun VaultBack(onClose: () -> Unit) = BackHandler(onBack = onClose)
