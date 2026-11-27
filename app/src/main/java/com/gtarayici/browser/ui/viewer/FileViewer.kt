package com.gtarayici.browser.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gtarayici.browser.R
import com.gtarayici.browser.viewer.Block
import com.gtarayici.browser.viewer.Document
import com.gtarayici.browser.viewer.DocumentReader
import com.gtarayici.browser.viewer.FileKind
import com.gtarayici.browser.viewer.FileSource
import com.gtarayici.browser.viewer.ReadingPositions
import com.gtarayici.browser.viewer.ZipEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Uygulamanın dosya görüntüleyicisi: görseller (hareketli GIF/WebP, HEIC, AVIF dahil), video ve ses, PDF, e-kitaplar
 * (EPUB, FB2), çizgi romanlar (CBZ), Word/OpenDocument metinleri, sunular, tablolar (XLSX, ODS, CSV) ve metin dosyaları.
 * Yana kaydırınca listedeki diğer dosyaya geçilir (video, PDF ve kitaplarda kaydırma içeriğe ayrılmıştır).
 */
@Composable
fun FileViewer(sources: List<FileSource>, start: Int, onClose: () -> Unit, onActions: ((FileSource) -> Unit)? = null) {
    BackHandler(onBack = onClose)
    if (sources.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, sources.lastIndex)) { sources.size }
    var chrome by remember { mutableStateOf(true) }
    val current = sources.getOrNull(pager.currentPage)
    // İçeriği kendi hareketlerini kullanan sayfalarda (video, PDF, kitap) yana kaydırma sayfa değiştirmez.
    val swipe = current?.kind in setOf(FileKind.IMAGE, FileKind.OTHER, FileKind.AUDIO)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = swipe, beyondViewportPageCount = 0, key = { sources[it].key }) { page ->
            val source = sources[page]
            val active = pager.currentPage == page
            val toggle = { chrome = !chrome }
            when (source.kind) {
                FileKind.IMAGE -> ImagePage(source, onTap = toggle, onDismiss = onClose)
                FileKind.VIDEO, FileKind.AUDIO -> if (active) MediaPlayer(source, chrome, toggle) else Loading()
                FileKind.PDF -> if (active) PdfPage(source, chrome, toggle) else Loading()
                FileKind.EPUB, FileKind.FB2 -> if (active) BookReader(source, onClose) else Loading()
                FileKind.COMIC -> if (active) ComicPage(source, toggle) else Loading()
                FileKind.WORD, FileKind.SLIDES, FileKind.SHEET, FileKind.TEXT -> if (active) DocumentPage(source) else Loading()
                FileKind.OTHER -> OtherPage(source) { onActions?.invoke(source) }
            }
        }
        // Kitap okuyucunun kendi üst çubuğu var.
        if (current != null && current.kind != FileKind.EPUB && current.kind != FileKind.FB2) {
            AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
                TopBar(current.name, sources.size.takeIf { it > 1 }?.let { "${pager.currentPage + 1} / $it" }, onClose, onActions?.let { { it(current) } })
            }
        }
    }
}

@Composable
internal fun TopBar(title: String, subtitle: String?, onClose: () -> Unit, onMore: (() -> Unit)?, extra: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall) }
        }
        extra()
        if (onMore != null) IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.vault_more), tint = Color.White) }
    }
}

@Composable
internal fun Loading(dark: Boolean = true) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = if (dark) Color.White else MaterialTheme.colorScheme.primary)
    }
}

// --- Görsel --------------------------------------------------------------------------------

/**
 * Görsel: Android'in görsel çözücüsüyle (hareketli GIF/WebP/AVIF, HEIC, DNG). İki parmakla ya da çift dokunuşla
 * yakınlaştırılır; yakınlaştırılmışken sürüklenir; yakınlaştırılmamışken aşağı/yukarı çekmek görüntüleyiciyi kapatır.
 */
@Composable
private fun ImagePage(source: FileSource, onTap: () -> Unit, onDismiss: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val maxSide = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }.coerceIn(1080, 4096)
        val drawable by produceState<Result<Drawable>?>(null, source.key) {
            value = withContext(Dispatchers.IO) { runCatching { decode(source, maxSide) } }
        }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var pull by remember { mutableFloatStateOf(0f) }
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        fun clamp(o: Offset) = Offset(
            o.x.coerceIn(-(scale - 1) * width / 2, (scale - 1) * width / 2),
            o.y.coerceIn(-(scale - 1) * height / 2, (scale - 1) * height / 2),
        )
        when (val result = drawable) {
            null -> CircularProgressIndicator(color = Color.White)
            else -> result.onFailure {
                Text(stringResource(R.string.viewer_image_failed), color = Color.White)
            }.onSuccess { image ->
                DisposableEffect(image) {
                    (image as? AnimatedImageDrawable)?.start()
                    onDispose { (image as? AnimatedImageDrawable)?.stop() }
                }
                AndroidView(
                    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                    update = { it.setImageDrawable(image) },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y + pull
                            alpha = 1f - (abs(pull) / height).coerceIn(0f, 0.6f)
                        },
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(source.key) {
                            detectTapGestures(
                                onTap = { onTap() },
                                onDoubleTap = { at ->
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                        offset = clamp((Offset(width / 2, height / 2) - at) * (scale - 1))
                                    }
                                },
                            )
                        }
                        .pointerInput(source.key) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var vertical = 0f
                                var horizontal = 0f
                                var pulling = false
                                do {
                                    val event = awaitPointerEvent()
                                    val pointers = event.changes.count { it.pressed }
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (pointers > 1 || scale > 1f) {
                                        scale = (scale * zoom).coerceIn(1f, 8f)
                                        offset = if (scale == 1f) Offset.Zero else clamp(offset + pan)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } else if (pointers == 1) {
                                        vertical += pan.y
                                        horizontal += pan.x
                                        if (!pulling && abs(vertical) > viewConfiguration.touchSlop && abs(vertical) > abs(horizontal) * 1.5f) pulling = true
                                        if (pulling) {
                                            pull = vertical
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                if (pulling && abs(pull) > height * 0.18f) onDismiss() else pull = 0f
                            }
                        },
                )
            }
        }
    }
}

/** Görseli ekrana yetecek çözünürlükte çözer (çok büyük görseller küçültülerek). */
private fun decode(source: FileSource, maxSide: Int): Drawable {
    val bytes = source.open().use { input -> input.readBytes() }
    val decoderSource = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
    return runCatching {
        ImageDecoder.decodeDrawable(decoderSource) { decoder, info, _ ->
            val largest = maxOf(info.size.width, info.size.height)
            if (largest > maxSide) decoder.setTargetSampleSize((largest / maxSide).coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrElse {
        // Çözücünün tanımadığı biçimler için eski çözücü (ör. bazı BMP/ICO).
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw it
        android.graphics.drawable.BitmapDrawable(null, bitmap)
    }
}

// --- PDF -----------------------------------------------------------------------------------

/** PDF: iki parmakla yakınlaştırma, sayfa göstergesi, sayfaya git, gece modu, kaldığın sayfa. */
@Composable
private fun PdfPage(source: FileSource, chrome: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val positions = remember { ReadingPositions(context) }
    val renderer by produceState<Result<PdfRenderer>?>(null, source.key) {
        value = withContext(Dispatchers.IO) { runCatching { PdfRenderer(source.descriptor() ?: error("Dosya açılamadı")) } }
    }
    DisposableEffect(renderer) { onDispose { renderer?.getOrNull()?.let { runCatching { it.close() } } } }
    val pdf = renderer?.getOrNull()
    if (renderer == null) return Loading()
    if (pdf == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.viewer_pdf_failed), color = Color.White) }
        return
    }
    val mutex = remember { Mutex() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = positions.get(source.key).toInt().coerceIn(0, pdf.pageCount - 1))
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var night by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.collect {
            positions.set(source.key, it.toLong())
            positions.setProgress(source.key, (it + 1f) / pdf.pageCount)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(if (night) Color.Black else Color(0xFF2B2B2B))) {
        val width = constraints.maxWidth.toFloat()
        // Sayfalar ekran genişliğinin 1,5 katı çözünürlükte çizilir (yakınlaştırınca da net kalır).
        val renderWidth = (constraints.maxWidth * 1.5f).roundToInt().coerceAtMost(2400)
        LazyColumn(
            state = list,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                offsetX = (offsetX + event.calculatePan().x).coerceIn(-(scale - 1) * width / 2, (scale - 1) * width / 2)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (scale > 1f) {
                                val pan = event.calculatePan()
                                offsetX = (offsetX + pan.x).coerceIn(-(scale - 1) * width / 2, (scale - 1) * width / 2)
                            }
                        } while (event.changes.any { it.pressed })
                        if (scale < 1.02f) { scale = 1f; offsetX = 0f }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() }, onDoubleTap = { if (scale > 1f) { scale = 1f; offsetX = 0f } else scale = 2f })
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 72.dp, bottom = 48.dp, start = 6.dp, end = 6.dp),
        ) {
            items(pdf.pageCount) { index ->
                val page by produceState<Bitmap?>(null, index, renderWidth) {
                    value = withContext(Dispatchers.IO) {
                        mutex.withLock {
                            runCatching {
                                pdf.openPage(index).use { p ->
                                    val h = (renderWidth.toFloat() * p.height / p.width).roundToInt().coerceAtLeast(1)
                                    Bitmap.createBitmap(renderWidth, h, Bitmap.Config.ARGB_8888).also {
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
                    Image(
                        bitmap.asImageBitmap(),
                        stringResource(R.string.viewer_page, index + 1),
                        contentScale = ContentScale.FillWidth,
                        colorFilter = if (night) INVERT else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(if (night) Color.Black else Color.White))
                }
            }
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = { goTo = true }, shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.7f)) {
                    Text(
                        stringResource(R.string.viewer_page_of, list.firstVisibleItemIndex + 1, pdf.pageCount),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                Surface(onClick = { night = !night }, shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.7f)) {
                    Icon(Icons.Rounded.DarkMode, stringResource(R.string.viewer_night), tint = if (night) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.padding(10.dp))
                }
            }
        }
    }
    if (goTo) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { goTo = false },
            title = { Text(stringResource(R.string.viewer_go_to_page)) },
            text = {
                OutlinedTextField(
                    text,
                    { text = it.filter(Char::isDigit).take(6) },
                    singleLine = true,
                    label = { Text("1 – ${pdf.pageCount}") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    goTo = false
                    text.toIntOrNull()?.let { n -> scope.launch { list.scrollToItem((n - 1).coerceIn(0, pdf.pageCount - 1)) } }
                }) { Text(stringResource(R.string.viewer_go)) }
            },
            dismissButton = { TextButton(onClick = { goTo = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private val INVERT = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),
)

// --- Belgeler ------------------------------------------------------------------------------

/** Word/OpenDocument, sunu, tablo ve metin dosyaları. */
@Composable
private fun DocumentPage(source: FileSource) {
    val doc by produceState<Result<Document>?>(null, source.key) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                when (source.kind) {
                    FileKind.WORD -> DocumentReader.word(source.open().use { ZipEntries.read(it, keep = ::officeEntry) })
                    FileKind.SLIDES -> DocumentReader.slides(source.open().use { ZipEntries.read(it, keep = ::officeEntry) })
                    FileKind.SHEET -> if (source.name.endsWith(".csv", true) || source.name.endsWith(".tsv", true)) {
                        DocumentReader.csv(source.open().use { String(readUpTo(it, 8 * 1024 * 1024)) })
                    } else {
                        DocumentReader.sheet(source.open().use { ZipEntries.read(it, keep = ::officeEntry) })
                    }
                    else -> {
                        val text = source.open().use { String(readUpTo(it, 2 * 1024 * 1024), Charsets.UTF_8) }
                        Document(null, null, listOf(com.gtarayici.browser.viewer.Chapter(null, listOf(Block.Preformatted(text)))))
                    }
                }
            }
        }
    }
    val result = doc ?: return Loading(dark = false)
    val document = result.getOrNull()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (document == null || document.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.viewer_document_failed)) }
            return@Surface
        }
        var tab by remember { mutableIntStateOf(0) }
        val tabs = document.chapters.size > 1 && document.chapters.all { it.blocks.singleOrNull() is Block.Table }
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp)) {
            if (tabs) {
                androidx.compose.material3.ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    document.chapters.forEachIndexed { i, chapter -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(chapter.title ?: "${i + 1}") }) }
                }
            }
            val chapters = if (tabs) listOf(document.chapters[tab]) else document.chapters
            val single = chapters.singleOrNull()?.blocks?.singleOrNull()
            when {
                single is Block.Table -> TableView(single)
                single is Block.Preformatted -> SelectionContainer {
                    Text(
                        single.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (source.kind == FileKind.TEXT && looksLikeCode(source.name)) FontFamily.Monospace else FontFamily.Default),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                    chapters.forEach { chapter ->
                        if (chapters.size > 1 && chapter.title != null) {
                            item {
                                Text(chapter.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                            }
                        }
                        itemsIndexed(chapter.blocks) { _, block -> BlockView(block, ReaderStyle.default(), document) }
                    }
                }
            }
        }
    }
}

/** Akışın başından en fazla [limit] bayt. */
private fun readUpTo(input: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private fun officeEntry(name: String) = name.endsWith(".xml") && !name.contains("/_rels/") && !name.startsWith("docProps/")
private fun looksLikeCode(name: String) = name.substringAfterLast('.').lowercase() in setOf("json", "xml", "log", "kt", "java", "py", "js", "css", "sh", "sql", "yaml", "yml", "ini", "conf", "ovpn")

/** Tablo: başlık satırı vurgulu, satır numaralı, yatay ve dikey kaydırmalı. */
@Composable
private fun TableView(table: Block.Table) {
    val horizontal = rememberScrollState()
    val columns = table.rows.maxOfOrNull { it.size } ?: 0
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(table.rows) { index, row ->
            Row(
                Modifier
                    .horizontalScroll(horizontal)
                    .background(if (index == 0) MaterialTheme.colorScheme.surfaceContainerHigh else if (index % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent),
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp).padding(8.dp),
                )
                for (c in 0 until columns) {
                    Text(
                        row.getOrNull(c).orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(140.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// --- Çizgi roman ---------------------------------------------------------------------------

@Composable
private fun ComicPage(source: FileSource, onTap: () -> Unit) {
    val pages by produceState<Result<List<ByteArray>>?>(null, source.key) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                source.open().use { ZipEntries.read(it, limit = 300L * 1024 * 1024) { n -> Regex("""\.(jpe?g|png|webp|gif|avif|bmp)$""", RegexOption.IGNORE_CASE).containsMatchIn(n) } }
                    .toSortedMap(naturalOrder()).values.toList()
            }
        }
    }
    val result = pages ?: return Loading()
    val list = result.getOrNull().orEmpty()
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.viewer_document_failed), color = Color.White) }
        return
    }
    val context = LocalContext.current
    val positions = remember { ReadingPositions(context) }
    val pager = rememberPagerState(initialPage = positions.get(source.key).toInt().coerceIn(0, list.lastIndex)) { list.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect {
            positions.set(source.key, it.toLong())
            positions.setProgress(source.key, (it + 1f) / list.size)
        }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }, contentAlignment = Alignment.Center) {
                val target = constraints.maxWidth.coerceAtLeast(720)
                val bitmap by produceState<Bitmap?>(null, i) {
                    value = withContext(Dispatchers.IO) {
                        val bytes = list[i]
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }
                bitmap?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) } ?: CircularProgressIndicator(color = Color.White)
            }
        }
        Text(
            "${pager.currentPage + 1} / ${list.size}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun OtherPage(source: FileSource, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, tint = Color.White, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(source.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.vault_no_preview), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onOpen) { Text(stringResource(R.string.vault_more)) }
    }
}
