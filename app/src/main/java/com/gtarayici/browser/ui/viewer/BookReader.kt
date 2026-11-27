package com.gtarayici.browser.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.core.content.edit
import com.gtarayici.browser.R
import com.gtarayici.browser.viewer.Block
import com.gtarayici.browser.viewer.Document
import com.gtarayici.browser.viewer.DocumentReader
import com.gtarayici.browser.viewer.FileKind
import com.gtarayici.browser.viewer.FileSource
import com.gtarayici.browser.viewer.ReadingPositions
import com.gtarayici.browser.viewer.Span
import com.gtarayici.browser.viewer.ZipEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Okuma teması. */
enum class ReaderTheme(val background: Color, val text: Color, val label: Int) {
    LIGHT(Color(0xFFFFFFFF), Color(0xFF1F1F1F), R.string.reader_theme_light),
    SEPIA(Color(0xFFF6EEDC), Color(0xFF4A3B28), R.string.reader_theme_sepia),
    DARK(Color(0xFF1E1F22), Color(0xFFDADADA), R.string.reader_theme_dark),
    BLACK(Color(0xFF000000), Color(0xFFC8C8C8), R.string.reader_theme_black),
}

/** Okuma görünümü ayarları (tüm kitaplar için ortak, saklanır). */
data class ReaderStyle(
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.6f,
    val serif: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.SEPIA,
    val margin: Int = 20,
    val justify: Boolean = true,
) {
    companion object {
        /** Belgeler için uygulamanın kendi renkleriyle varsayılan görünüm. */
        fun default() = ReaderStyle(fontSize = 16f, lineHeight = 1.5f, serif = false, theme = ReaderTheme.LIGHT, justify = false)

        private fun prefs(context: Context) = context.getSharedPreferences("gtarayici_book_reader", Context.MODE_PRIVATE)

        fun load(context: Context): ReaderStyle = prefs(context).let {
            ReaderStyle(
                fontSize = it.getFloat("size", 18f),
                lineHeight = it.getFloat("line", 1.6f),
                serif = it.getBoolean("serif", true),
                theme = runCatching { ReaderTheme.valueOf(it.getString("theme", "SEPIA")!!) }.getOrDefault(ReaderTheme.SEPIA),
                margin = it.getInt("margin", 20),
                justify = it.getBoolean("justify", true),
            )
        }

        fun save(context: Context, style: ReaderStyle) = prefs(context).edit {
            putFloat("size", style.fontSize)
            putFloat("line", style.lineHeight)
            putBoolean("serif", style.serif)
            putString("theme", style.theme.name)
            putInt("margin", style.margin)
            putBoolean("justify", style.justify)
        }
    }
}

/** Kitap okuyucu (EPUB, FB2): kaydırmalı okuma, içindekiler, okuma ayarları, kaldığın yer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookReader(source: FileSource, onClose: () -> Unit) {
    val context = LocalContext.current
    val positions = remember { ReadingPositions(context) }
    var style by remember { mutableStateOf(ReaderStyle.load(context)) }
    val doc by produceState<Result<Document>?>(null, source.key) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (source.kind == FileKind.FB2) DocumentReader.fb2(source.open().use { DocumentReader.text(it.readBytes()) })
                else DocumentReader.epub(source.open().use { ZipEntries.read(it) })
            }
        }
    }
    val result = doc ?: return Box(Modifier.fillMaxSize().background(style.theme.background)) { Loading(dark = style.theme.ordinal >= 2) }
    val document = result.getOrNull()
    if (document == null || document.isEmpty) {
        Box(Modifier.fillMaxSize().background(style.theme.background), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.viewer_book_failed), color = style.theme.text, modifier = Modifier.padding(32.dp), textAlign = TextAlign.Center)
        }
        return
    }
    // Bölüm başlıkları ve bloklar tek bir listede.
    val items = remember(document) {
        buildList {
            document.chapters.forEachIndexed { index, chapter ->
                if (index > 0) add(index to null)
                chapter.blocks.forEach { add(index to it) }
            }
        }
    }
    val chapterStarts = remember(items) { items.indices.filter { i -> i == 0 || items[i].first != items[i - 1].first } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = positions.get(source.key).toInt().coerceIn(0, items.lastIndex))
    var chrome by remember { mutableStateOf(false) }
    var toc by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.collect {
            positions.set(source.key, it.toLong())
            positions.setProgress(source.key, ((it + 1f) / items.size).coerceIn(0f, 1f))
        }
    }
    val currentChapter = items.getOrNull(list.firstVisibleItemIndex)?.first ?: 0

    Box(Modifier.fillMaxSize().background(style.theme.background)) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }) },
            contentPadding = PaddingValues(start = style.margin.dp, end = style.margin.dp, top = 72.dp, bottom = 96.dp),
        ) {
            itemsIndexed(items) { _, (chapter, block) ->
                if (block == null) {
                    // Bölüm geçişi: ince bir ayraç.
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("❦", color = style.theme.text.copy(alpha = 0.4f), fontSize = 20.sp)
                    }
                } else {
                    BlockView(block, style, document)
                }
            }
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
            Surface(color = style.theme.background.copy(alpha = 0.96f), shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = style.theme.text) }
                    Column(Modifier.weight(1f)) {
                        Text(document.title ?: source.name, color = style.theme.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        document.author?.let { Text(it, color = style.theme.text.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    }
                    IconButton(onClick = { toc = true }) { Icon(Icons.AutoMirrored.Rounded.List, stringResource(R.string.reader_toc), tint = style.theme.text) }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.TextFields, stringResource(R.string.book_settings), tint = style.theme.text) }
                }
            }
        }
        // Alt bilgi: bölüm ve ilerleme (her zaman ince, dokununca kaydırıcıyla).
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(style.theme.background.copy(alpha = if (chrome) 0.96f else 0f)).navigationBarsPadding()) {
            if (chrome) {
                Slider(
                    value = list.firstVisibleItemIndex.toFloat() / items.lastIndex.coerceAtLeast(1),
                    onValueChange = { v -> scope.launch { list.scrollToItem((v * items.lastIndex).toInt()) } },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    document.chapters.getOrNull(currentChapter)?.title.orEmpty(),
                    color = style.theme.text.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("%${((list.firstVisibleItemIndex + 1) * 100 / items.size.coerceAtLeast(1))}", color = style.theme.text.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (toc) {
        ModalBottomSheet(onDismissRequest = { toc = false }) {
            Text(stringResource(R.string.reader_toc), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                itemsIndexed(document.chapters) { index, chapter ->
                    ListItem(
                        headlineContent = { Text(chapter.title ?: stringResource(R.string.reader_chapter, index + 1), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable {
                            toc = false
                            chrome = false
                            scope.launch { list.scrollToItem(chapterStarts.getOrElse(index) { 0 }) }
                        }.then(if (index == currentChapter) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (settings) {
        ModalBottomSheet(onDismissRequest = { settings = false }) {
            ReaderSettings(style) {
                style = it
                ReaderStyle.save(context, it)
            }
        }
    }
}

@Composable
private fun ReaderSettings(style: ReaderStyle, onChange: (ReaderStyle) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.book_settings), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReaderTheme.entries.forEach { theme ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(48.dp).background(theme.background, CircleShape)
                            .border(BorderStroke(if (theme == style.theme) 3.dp else 1.dp, if (theme == style.theme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
                            .clickable { onChange(style.copy(theme = theme)) },
                        contentAlignment = Alignment.Center,
                    ) { Text("Aa", color = theme.text, fontFamily = FontFamily.Serif) }
                    Text(stringResource(theme.label), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Text(stringResource(R.string.reader_font_size, style.fontSize.toInt()), style = MaterialTheme.typography.labelLarge)
        Slider(value = style.fontSize, onValueChange = { onChange(style.copy(fontSize = it)) }, valueRange = 13f..32f, steps = 18)
        Text(stringResource(R.string.reader_line_height), style = MaterialTheme.typography.labelLarge)
        Slider(value = style.lineHeight, onValueChange = { onChange(style.copy(lineHeight = it)) }, valueRange = 1.2f..2.2f, steps = 9)
        Text(stringResource(R.string.reader_margin), style = MaterialTheme.typography.labelLarge)
        Slider(value = style.margin.toFloat(), onValueChange = { onChange(style.copy(margin = it.toInt())) }, valueRange = 8f..48f, steps = 9)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = style.serif, onClick = { onChange(style.copy(serif = true)) }, label = { Text(stringResource(R.string.book_font_serif), fontFamily = FontFamily.Serif) })
            FilterChip(selected = !style.serif, onClick = { onChange(style.copy(serif = false)) }, label = { Text(stringResource(R.string.book_font_sans)) })
            FilterChip(selected = style.justify, onClick = { onChange(style.copy(justify = !style.justify)) }, label = { Text(stringResource(R.string.reader_justify)) })
        }
    }
}

/** Bir içerik bloğunu okuma ayarlarına göre çizer (kitaplar ve belgeler). */
@Composable
internal fun BlockView(block: Block, style: ReaderStyle, document: Document) {
    val useAppColors = style == ReaderStyle.default()
    val textColor = if (useAppColors) MaterialTheme.colorScheme.onSurface else style.theme.text
    val family = if (style.serif) FontFamily.Serif else FontFamily.Default
    val body = TextStyle(fontSize = style.fontSize.sp, lineHeight = style.lineHeight.em, fontFamily = family, color = textColor)
    when (block) {
        is Block.Heading -> Text(
            annotated(block.spans),
            style = body.copy(
                fontSize = (style.fontSize * when (block.level) { 1 -> 1.6f; 2 -> 1.4f; 3 -> 1.25f; else -> 1.1f }).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 1.3.em,
            ),
            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
        )
        is Block.Paragraph -> {
            val text = annotated(block.spans)
            when {
                block.quote -> Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.width(3.dp).heightIn(min = 24.dp).background(textColor.copy(alpha = 0.3f)))
                    Text(text, style = body.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(start = 12.dp))
                }
                block.listItem -> Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•", style = body, modifier = Modifier.width(20.dp))
                    Text(text, style = body)
                }
                else -> Text(
                    text,
                    style = body.copy(textAlign = if (style.justify) TextAlign.Justify else TextAlign.Start),
                    modifier = Modifier.padding(vertical = (style.fontSize * 0.35f).dp),
                )
            }
        }
        is Block.Preformatted -> Text(
            block.text,
            style = body.copy(fontFamily = FontFamily.Monospace, fontSize = (style.fontSize * 0.85f).sp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).background(textColor.copy(alpha = 0.06f), RoundedCornerShape(8.dp)).padding(12.dp),
        )
        is Block.Picture -> {
            val bytes = document.resources[block.path]
            val bitmap by produceState<Bitmap?>(null, block.path) {
                value = bytes?.let { data ->
                    withContext(Dispatchers.Default) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= 1080) sample *= 2
                        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }
            }
            bitmap?.let {
                Image(it.asImageBitmap(), block.alt, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(max = 900.dp))
            }
        }
        Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 16.dp), color = textColor.copy(alpha = 0.2f))
        is Block.Table -> Column(Modifier.padding(vertical = 8.dp).border(1.dp, textColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))) {
            block.rows.take(200).forEachIndexed { i, row ->
                Row(Modifier.fillMaxWidth().background(if (i == 0) textColor.copy(alpha = 0.06f) else Color.Transparent)) {
                    row.forEach { cell ->
                        Text(cell, style = body.copy(fontSize = (style.fontSize * 0.8f).sp, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal), modifier = Modifier.weight(1f).padding(6.dp))
                    }
                }
            }
        }
    }
}

private fun annotated(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        if (span.bold || span.italic) {
            withStyle(SpanStyle(fontWeight = if (span.bold) FontWeight.Bold else null, fontStyle = if (span.italic) FontStyle.Italic else null)) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}
