package com.gtarayici.browser.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.downloads.manager.MediaKind
import com.gtarayici.browser.vault.VaultDataSource
import com.gtarayici.browser.vault.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Kasadaki dosyanın tam ekran görüntüleyicisi. Yana kaydırınca listedeki diğer dosyalara geçilir. Video ve ses
 * Media3 ile, görseller yakınlaştırılabilir, PDF sayfa sayfa, metinler doğrudan gösterilir; hiçbirinin şifresiz
 * kopyası diske yazılmaz.
 */
@Composable
internal fun VaultViewer(id: String, items: List<VaultItem>, onClose: () -> Unit, onActions: (VaultItem) -> Unit) {
    VaultBack(onClose)
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val start = items.indexOfFirst { it.id == id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { items.size }
    var chrome by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0, key = { items[it].id }) { page ->
            val item = items[page]
            val current = pager.currentPage == page
            when {
                item.kind == MediaKind.IMAGE -> ZoomableImage(item) { chrome = !chrome }
                item.kind == MediaKind.VIDEO || item.kind == MediaKind.AUDIO || item.kind == MediaKind.STREAM ->
                    if (current) MediaPage(item) else Placeholder(item)
                item.isPdf -> if (current) PdfPage(item) else Placeholder(item)
                item.isText -> TextPage(item)
                else -> OtherPage(item) { onActions(item) }
            }
        }
        val item = items.getOrNull(pager.currentPage)
        if (chrome && item != null) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = Color.White) }
                Text(
                    item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onActions(item) }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.vault_more), tint = Color.White) }
            }
        }
    }
}

@Composable
private fun Placeholder(item: VaultItem) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(kindIcon(item), null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(64.dp))
    }
}

/** İki parmakla yakınlaştırılan, çift dokunuşla büyütülen görsel. */
@Composable
private fun ZoomableImage(item: VaultItem, onTap: () -> Unit) {
    val vault = LocalContext.current.components.vault
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val maxSide = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }.coerceIn(1024, 4096)
        val bitmap by produceState<Bitmap?>(null, item.id) { value = vault.image(item.id, maxSide) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                image.asImageBitmap(),
                item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                        )
                    }
                    .pointerInput(item.id) {
                        // Yakınlaştırılmamışken tek parmak hareketi sayfayı kaydırsın diye yalnızca iki parmakla ya da
                        // yakınlaştırılmışken işlenir.
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}

/** Video ve ses: şifresi anlık çözülerek oynatılır (ileri sarma dahil). */
@OptIn(UnstableApi::class)
@Composable
private fun MediaPage(item: VaultItem) {
    val context = LocalContext.current
    val vault = context.components.vault
    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(
                ProgressiveMediaSource.Factory { VaultDataSource(vault) }
                    .createMediaSource(MediaItem.fromUri(VaultDataSource.uri(item.id))),
            )
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val artwork by produceState<Bitmap?>(null, item.id) {
        value = if (item.kind == MediaKind.AUDIO && item.hasThumbnail) vault.thumbnail(item.id) else null
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    if (item.kind == MediaKind.AUDIO) controllerShowTimeoutMs = 0
                }
            },
            update = { view ->
                artwork?.let { view.defaultArtwork = android.graphics.drawable.BitmapDrawable(context.resources, it) }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** PDF: sayfalar ekran genişliğinde, kaydırdıkça çizilir. */
@Composable
private fun PdfPage(item: VaultItem) {
    val vault = LocalContext.current.components.vault
    val renderer by produceState<PdfRenderer?>(null, item.id) {
        value = withContext(Dispatchers.IO) { runCatching { vault.openDescriptor(item.id)?.let(::PdfRenderer) }.getOrNull() }
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    val mutex = remember { Mutex() }
    val pdf = renderer
    if (pdf == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtMost(2000)
        LazyColumn(
            Modifier.fillMaxSize().background(Color(0xFF303030)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 64.dp, bottom = 24.dp),
        ) {
            items(pdf.pageCount) { index ->
                val page by produceState<Bitmap?>(null, index, width) {
                    value = withContext(Dispatchers.IO) {
                        mutex.withLock {
                            runCatching {
                                pdf.openPage(index).use { p ->
                                    val height = (width.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                        it.eraseColor(android.graphics.Color.WHITE)
                                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }.getOrNull()
                        }
                    }
                }
                val bitmap = page
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White))
                }
            }
        }
    }
}

@Composable
private fun TextPage(item: VaultItem) {
    val vault = LocalContext.current.components.vault
    val text by produceState<String?>(null, item.id) { value = vault.text(item.id) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        SelectionContainer {
            Text(
                text ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun OtherPage(item: VaultItem, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(kindIcon(item), null, tint = Color.White, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(item.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.vault_no_preview), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.vault_more)) }
    }
}
