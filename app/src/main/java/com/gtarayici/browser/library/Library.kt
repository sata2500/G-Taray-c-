package com.gtarayici.browser.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.gtarayici.browser.diagnostics.Diagnostics
import com.gtarayici.browser.downloads.DownloadedFiles
import com.gtarayici.browser.viewer.DocumentReader
import com.gtarayici.browser.viewer.FileKind
import com.gtarayici.browser.viewer.ReadingPositions
import com.gtarayici.browser.viewer.UriSource
import com.gtarayici.browser.viewer.ZipEntries
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow
import org.json.JSONArray
import org.json.JSONObject

/** Kitaplıktaki bir kitap. Dosya telefonda kalır; kitaplık yalnızca adresini, kapağını ve bilgilerini tutar. */
data class Book(
    val id: String,
    val uri: String,
    val fileName: String,
    val mime: String?,
    val title: String,
    val author: String?,
    val kind: FileKind,
    val pages: Int = 0,
    val added: Long = System.currentTimeMillis(),
    val opened: Long = 0,
) {
    fun source(context: Context) = UriSource(context, Uri.parse(uri), fileName, mime)
}

/**
 * Kitaplık: e-kitaplar (EPUB, FB2), çizgi romanlar (CBZ) ve kitap gibi uzun PDF'ler. Normal (gizli olmayan) sekmelerde
 * indirilen kitaplar kendiliğinden eklenir; telefondan da eklenebilir. Kısa PDF'ler (fatura, form…) belge sayılır ve
 * kitaplığa girmez.
 */
class Library(private val context: Context) {
    private val dir = File(context.filesDir, "library").apply { mkdirs() }
    private val covers = File(dir, "covers").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val lock = Mutex()
    private val positions = ReadingPositions(context)

    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    fun progress(book: Book): Float = positions.progress(book.uri)

    fun cover(book: Book): File = File(covers, "${book.id}.jpg")

    /** Kitap açıldı: "Okumaya devam et" sırası için. */
    suspend fun opened(book: Book) = update { list -> list.map { if (it.id == book.id) it.copy(opened = System.currentTimeMillis()) else it } }

    suspend fun remove(book: Book) {
        cover(book).delete()
        update { list -> list.filterNot { it.id == book.id } }
    }

    /**
     * Dosyayı kitaplığa ekler. [requireBook]: kısa PDF'ler eklenmez (indirmelerden kendiliğinden eklerken). Eklenen
     * kitabı ya da kitap değilse null döndürür.
     */
    suspend fun add(uri: Uri, fileName: String, mime: String?, requireBook: Boolean = false): Book? = withContext(Dispatchers.IO) {
        _books.value.firstOrNull { it.uri == uri.toString() }?.let { return@withContext it }
        val kind = FileKind.of(fileName, mime)
        if (!kind.isBookFormat && kind != FileKind.PDF) return@withContext null
        val id = UUID.randomUUID().toString()
        val source = UriSource(context, uri, fileName, mime)
        var title = fileName.substringBeforeLast('.').replace('_', ' ').trim()
        var author: String? = null
        var pages = 0
        val cover: Bitmap? = runCatching {
            when (kind) {
                FileKind.EPUB -> {
                    val doc = DocumentReader.epub(source.open().use { ZipEntries.read(it) { n -> !n.endsWith(".mp4") && !n.endsWith(".mp3") } })
                    doc.title?.let { title = it }
                    author = doc.author
                    pages = doc.chapters.size
                    (doc.coverPath ?: doc.resources.keys.firstOrNull { it.contains("cover", true) })?.let { doc.resources[it] }?.let(::decodeCover)
                }
                FileKind.FB2 -> {
                    val doc = DocumentReader.fb2(source.open().use { DocumentReader.text(it.readBytes()) })
                    doc.title?.let { title = it }
                    author = doc.author
                    pages = doc.chapters.size
                    doc.coverPath?.let { doc.resources[it] }?.let(::decodeCover)
                }
                FileKind.COMIC -> source.open().use { input ->
                    val images = ZipEntries.read(input, limit = 40L * 1024 * 1024) { n -> Regex("""\.(jpe?g|png|webp)$""", RegexOption.IGNORE_CASE).containsMatchIn(n) }
                    pages = images.size
                    images.toSortedMap().values.firstOrNull()?.let(::decodeCover)
                }
                FileKind.PDF -> source.descriptor()?.use { fd ->
                    PdfRenderer(fd).use { pdf ->
                        pages = pdf.pageCount
                        if (requireBook && pages < MIN_BOOK_PAGES) return@withContext null
                        pdf.openPage(0).use { page ->
                            val w = 400
                            val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(android.graphics.Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
                else -> null
            }
        }.onFailure { Diagnostics.log("kitap bilgileri okunamadı: ${it.javaClass.simpleName}", "kitaplık") }.getOrNull()
        val book = Book(id, uri.toString(), fileName, mime, title.ifBlank { fileName }, author, kind, pages)
        cover?.let { bitmap -> runCatching { cover(book).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } } }
        update { it + book }
        book
    }

    /** Telefondan seçilen dosya: kalıcı okuma izni alınır (uygulama yeniden açılınca da okunabilsin). */
    suspend fun import(uri: Uri): Book? {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "kitap"
        return add(uri, name, resolver.getType(uri))
    }

    /** Normal sekmelerde tamamlanan kitap indirmeleri kendiliğinden eklenir. */
    fun start(store: BrowserStore, scope: CoroutineScope) {
        scope.launch {
            store.flow()
                .map { state ->
                    state.downloads.values.filter {
                        it.status == DownloadState.Status.COMPLETED && !it.private &&
                            it.directoryPath?.startsWith("vault:") != true &&
                            FileKind.of(it.fileName.orEmpty(), it.contentType).let { k -> k.isBookFormat || k == FileKind.PDF }
                    }.map { it.id }.toSet()
                }
                .distinctUntilChanged()
                .collect { ids ->
                    ids.filter { it !in seen }.forEach { id ->
                        seen += id
                        val download = store.state.downloads[id] ?: return@forEach
                        val uri = withContext(Dispatchers.IO) { DownloadedFiles.uri(context, download.fileName, download.directoryPath) } ?: return@forEach
                        add(uri, download.fileName.orEmpty(), download.contentType, requireBook = true)
                    }
                }
        }
    }

    private val seen = HashSet<String>().apply { addAll(context.getSharedPreferences("gtarayici_library", Context.MODE_PRIVATE).getStringSet("seen", emptySet()).orEmpty()) }

    private suspend fun update(change: (List<Book>) -> List<Book>) = withContext(Dispatchers.IO) {
        lock.withLock {
            val updated = change(_books.value)
            _books.value = updated
            save(updated)
            context.getSharedPreferences("gtarayici_library", Context.MODE_PRIVATE).edit().putStringSet("seen", seen.toSet()).apply()
        }
    }

    private fun decodeCover(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 400) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun load(): List<Book> = runCatching {
        val array = JSONArray(indexFile.readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Book(
                id = o.getString("id"),
                uri = o.getString("uri"),
                fileName = o.getString("file"),
                mime = o.optString("mime").ifEmpty { null },
                title = o.getString("title"),
                author = o.optString("author").ifEmpty { null },
                kind = runCatching { FileKind.valueOf(o.getString("kind")) }.getOrDefault(FileKind.OTHER),
                pages = o.optInt("pages"),
                added = o.optLong("added"),
                opened = o.optLong("opened"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Book>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().put("id", it.id).put("uri", it.uri).put("file", it.fileName).put("mime", it.mime ?: "")
                    .put("title", it.title).put("author", it.author ?: "").put("kind", it.kind.name).put("pages", it.pages)
                    .put("added", it.added).put("opened", it.opened),
            )
        }
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(indexFile)
    }

    companion object {
        /** Bu sayfa sayısından kısa PDF'ler kitap değil belge sayılır. */
        const val MIN_BOOK_PAGES = 40
    }
}
