package com.gtarayici.browser.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.library.Book
import com.gtarayici.browser.ui.designsystem.AddFab
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.viewer.FileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Shelf(val label: Int) { ALL(R.string.library_all), EBOOKS(R.string.library_ebooks), PDF(R.string.library_pdf), COMICS(R.string.library_comics);

    fun matches(book: Book) = when (this) {
        ALL -> true
        EBOOKS -> book.kind == FileKind.EPUB || book.kind == FileKind.FB2
        PDF -> book.kind == FileKind.PDF
        COMICS -> book.kind == FileKind.COMIC
    }
}

/** Kitaplık: kapaklarıyla kitaplar, "Okumaya devam et" rafı, türe göre raflar. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (Book) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val library = context.components.library
    val books by library.books.collectAsState()
    val scope = rememberCoroutineScope()
    var shelf by remember { mutableStateOf(Shelf.ALL) }
    var actions by remember { mutableStateOf<Book?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            var added = 0
            uris.forEach { if (library.import(it) != null) added++ }
            if (uris.isNotEmpty()) {
                if (added > 0) context.components.messenger.success(UiText.plural(R.plurals.library_added, added, added))
                else context.components.messenger.warning(UiText.res(R.string.library_not_book))
            }
        }
    }
    val reading = books.filter { it.opened > 0 && library.progress(it) in 0.005f..0.98f }.sortedByDescending { it.opened }
    val shown = books.filter(shelf::matches).sortedByDescending { maxOf(it.opened, it.added) }

    OverlayScaffold(
        title = stringResource(R.string.library_title),
        onBack = onBack,
        floatingActionButton = {
            AddFab(stringResource(R.string.library_add)) {
                picker.launch(arrayOf("application/epub+zip", "application/pdf", "application/x-fictionbook+xml", "application/vnd.comicbook+zip", "application/zip", "application/octet-stream", "*/*"))
            }
        },
    ) { modifier ->
        if (books.isEmpty()) {
            EmptyState(stringResource(R.string.library_empty), modifier, icon = Icons.Rounded.MenuBook)
            return@OverlayScaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            modifier = modifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (reading.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(stringResource(R.string.library_continue), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(reading, key = { it.id }) { book -> ContinueCard(book, library.progress(book)) { onOpen(book) } }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Shelf.entries.forEach { s ->
                        val count = books.count(s::matches)
                        if (s == Shelf.ALL || count > 0) FilterChip(selected = shelf == s, onClick = { shelf = s }, label = { Text("${stringResource(s.label)} · $count") })
                    }
                }
            }
            items(shown, key = { it.id }) { book ->
                Column(Modifier.combinedClickable(onClick = { onOpen(book) }, onLongClick = { actions = book })) {
                    BookCover(book, Modifier.fillMaxWidth())
                    val progress = library.progress(book)
                    if (progress > 0.005f) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)))
                    }
                    Text(book.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    book.author?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
    actions?.let { book ->
        ModalBottomSheet(onDismissRequest = { actions = null }) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            ListItem(
                leadingContent = { Icon(Icons.Rounded.OpenInNew, null) },
                headlineContent = { Text(stringResource(R.string.vault_open_with)) },
                modifier = Modifier.combinedClickable(onClick = {
                    actions = null
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(book.uri), book.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                book.title,
                            ),
                        )
                    }
                }),
            )
            ListItem(
                leadingContent = { Icon(Icons.Rounded.Delete, null) },
                headlineContent = { Text(stringResource(R.string.library_remove)) },
                supportingContent = { Text(stringResource(R.string.library_remove_summary)) },
                modifier = Modifier.combinedClickable(onClick = {
                    actions = null
                    scope.launch { library.remove(book) }
                }),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ContinueCard(book: Book, progress: Float, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.width(280.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book, Modifier.width(64.dp))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                Text(stringResource(R.string.library_percent, (progress * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Kitap kapağı: kitabın kendi kapağı, yoksa başlıktan oluşturulmuş renkli bir kapak. */
@Composable
private fun BookCover(book: Book, modifier: Modifier) {
    val library = LocalContext.current.components.library
    val cover by produceState<Bitmap?>(null, book.id) {
        value = withContext(Dispatchers.IO) { library.cover(book).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    }
    Surface(shape = RoundedCornerShape(8.dp), shadowElevation = 3.dp, modifier = modifier.aspectRatio(2f / 3f)) {
        val image = cover
        if (image != null) {
            Image(image.asImageBitmap(), book.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val hue = (book.title.hashCode() and 0xFFFF) % 360
            val base = Color.hsv(hue.toFloat(), 0.45f, 0.55f)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(base, Color.hsv(hue.toFloat(), 0.55f, 0.35f)))).padding(10.dp)) {
                Text(
                    book.title,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center),
                )
                book.author?.let {
                    Text(it, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}
