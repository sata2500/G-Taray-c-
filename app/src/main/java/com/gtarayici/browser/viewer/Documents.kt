package com.gtarayici.browser.viewer

import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.ZipInputStream

/** Bir belgenin gösterilebilir öğesi. Satır içi biçim: [Span] listesi. */
sealed interface Block {
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class Paragraph(val spans: List<Span>, val quote: Boolean = false, val listItem: Boolean = false) : Block
    data class Preformatted(val text: String) : Block
    /** [path]: belgenin içindeki görsel (zip içindeki yol). */
    data class Picture(val path: String, val alt: String?) : Block
    data object Rule : Block
    data class Table(val rows: List<List<String>>) : Block
}

data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false)

/** Bölüm (EPUB'da bir dosya, FB2'de bir kısım, sunuda bir slayt…). */
data class Chapter(val title: String?, val blocks: List<Block>)

/** Açılmış bir belge ya da kitap. [resources]: görseller (yol → bayt). */
data class Document(
    val title: String?,
    val author: String?,
    val chapters: List<Chapter>,
    val resources: Map<String, ByteArray> = emptyMap(),
    val coverPath: String? = null,
) {
    val isEmpty: Boolean get() = chapters.all { it.blocks.isEmpty() }
}

/** Uygulamanın kendisinin açabildiği dosya türleri. */
enum class FileKind { IMAGE, VIDEO, AUDIO, PDF, EPUB, FB2, COMIC, WORD, SHEET, SLIDES, TEXT, OTHER;

    /** Kitaplıkta kitap olarak gösterilen türler (PDF'ler sayfa sayısına göre ayrıca değerlendirilir). */
    val isBookFormat: Boolean get() = this == EPUB || this == FB2 || this == COMIC

    companion object {
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "heic", "heif", "dng", "ico", "wbmp")
        private val VIDEO_EXT = setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp", "ts", "m2ts", "mts", "flv", "avi", "mpg", "mpeg", "ogv")
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "amr", "mid", "midi", "weba", "mka", "m4b")
        private val TEXT_EXT = setOf("txt", "md", "csv", "tsv", "json", "xml", "srt", "vtt", "ass", "ssa", "log", "ini", "conf", "ovpn", "yaml", "yml", "html", "htm", "js", "css", "kt", "java", "py", "sh", "sql")

        fun of(name: String, mime: String? = null): FileKind {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val type = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return when {
                ext == "pdf" || type == "application/pdf" -> PDF
                ext == "epub" || type == "application/epub+zip" -> EPUB
                ext == "fb2" -> FB2
                ext == "cbz" -> COMIC
                ext in setOf("docx", "odt") -> WORD
                ext in setOf("xlsx", "ods") -> SHEET
                ext in setOf("pptx", "odp") -> SLIDES
                ext in IMAGE_EXT || (type.startsWith("image/") && type != "image/svg+xml") -> IMAGE
                ext in VIDEO_EXT || type.startsWith("video/") -> VIDEO
                ext in AUDIO_EXT || type.startsWith("audio/") -> AUDIO
                ext in TEXT_EXT || type.startsWith("text/") || type == "application/json" -> if (ext == "csv" || ext == "tsv") SHEET else TEXT
                else -> OTHER
            }
        }
    }
}

/**
 * Hoşgörülü işaretleme tarayıcısı (XHTML, FB2, Office XML). Katı XML ayrıştırıcıları bozuk ya da DTD'li belgelerde
 * (EPUB'larda sık) hata verir ve DTD'yi ağdan indirmeye kalkabilir; bu tarayıcı yalnızca etiketleri ve metni ayırır.
 */
object Markup {
    sealed interface Token {
        data class Open(val name: String, val attrs: Map<String, String>, val selfClosing: Boolean) : Token
        data class Close(val name: String) : Token
        data class Text(val text: String) : Token
    }

    private val ATTR = Regex("""([\w:.-]+)\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""")

    fun tokens(source: String): Sequence<Token> = sequence {
        var i = 0
        val n = source.length
        while (i < n) {
            val lt = source.indexOf('<', i)
            if (lt < 0) {
                yield(Token.Text(decode(source.substring(i))))
                break
            }
            if (lt > i) yield(Token.Text(decode(source.substring(i, lt))))
            when {
                source.startsWith("<!--", lt) -> i = source.indexOf("-->", lt).let { if (it < 0) n else it + 3 }
                source.startsWith("<![CDATA[", lt) -> {
                    val end = source.indexOf("]]>", lt).let { if (it < 0) n else it }
                    yield(Token.Text(source.substring(lt + 9, end)))
                    i = (end + 3).coerceAtMost(n)
                }
                source.startsWith("<!", lt) || source.startsWith("<?", lt) -> i = source.indexOf('>', lt).let { if (it < 0) n else it + 1 }
                else -> {
                    val gt = source.indexOf('>', lt).let { if (it < 0) n else it }
                    val body = source.substring(lt + 1, gt).trim()
                    i = gt + 1
                    if (body.startsWith("/")) {
                        yield(Token.Close(localName(body.substring(1).trim())))
                    } else if (body.isNotEmpty()) {
                        val nameEnd = body.indexOfFirst { it.isWhitespace() || it == '/' }.let { if (it < 0) body.length else it }
                        val name = localName(body.substring(0, nameEnd))
                        val attrs = ATTR.findAll(body.substring(nameEnd)).associate { m ->
                            localName(m.groupValues[1]) to decode(m.groupValues[3].ifEmpty { m.groupValues[4].ifEmpty { m.groupValues[5] } })
                        }
                        val self = body.endsWith("/") || name in VOID
                        yield(Token.Open(name, attrs, self))
                        // Betik ve stil içerikleri atlanır.
                        if (!self && (name == "script" || name == "style")) {
                            val close = source.indexOf("</$name", i, ignoreCase = true)
                            i = if (close < 0) n else source.indexOf('>', close).let { if (it < 0) n else it + 1 }
                        }
                    }
                }
            }
        }
    }

    private val VOID = setOf("br", "img", "hr", "meta", "link", "input", "col", "area", "base", "wbr", "source", "image")

    /** "w:p" → "p", "xhtml:img" → "img"; küçük harf. */
    private fun localName(name: String) = name.substringAfterLast(':').lowercase(Locale.ROOT)

    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0", "shy" to "",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "laquo" to "«", "raquo" to "»", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”", "copy" to "©", "reg" to "®", "trade" to "™", "deg" to "°", "middot" to "·",
        "bull" to "•", "times" to "×", "euro" to "€", "pound" to "£", "sect" to "§", "para" to "¶", "iexcl" to "¡", "iquest" to "¿",
    )

    fun decode(text: String): String {
        if ('&' !in text) return text
        return Regex("""&(#x[0-9a-fA-F]+|#\d+|\w+);""").replace(text) { m ->
            val v = m.groupValues[1]
            when {
                v.startsWith("#x") || v.startsWith("#X") -> v.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                v.startsWith("#") -> v.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> ENTITIES[v] ?: m.value
            }
        }
    }
}

/** XHTML/HTML → bloklar (EPUB bölümleri, kitaplardaki HTML). [base]: görsel yollarının çözüleceği dosya yolu. */
object HtmlBlocks {
    fun parse(html: String, base: String = ""): Chapter {
        val blocks = mutableListOf<Block>()
        val spans = mutableListOf<Span>()
        var bold = 0
        var italic = 0
        var heading = 0
        var quote = 0
        var listItem = false
        var pre = 0
        val preText = StringBuilder()
        var title: String? = null
        var pageTitle: String? = null
        var inTitle = false
        var inBody = !html.contains("<body", ignoreCase = true)
        var tableRows: MutableList<MutableList<String>>? = null
        var cell: StringBuilder? = null

        fun flush() {
            val merged = mergeSpans(spans)
            spans.clear()
            if (merged.isEmpty()) return
            blocks += if (heading > 0) Block.Heading(heading, merged) else Block.Paragraph(merged, quote = quote > 0, listItem = listItem)
            if (heading > 0 && title == null) title = merged.joinToString("") { it.text }.trim()
        }

        for (token in Markup.tokens(html)) {
            when (token) {
                is Markup.Token.Open -> when (token.name) {
                    "body" -> inBody = true
                    "title" -> inTitle = true
                    "b", "strong" -> bold++
                    "i", "em", "cite" -> italic++
                    "h1", "h2", "h3", "h4", "h5", "h6" -> { flush(); heading = token.name[1].digitToInt() }
                    "p", "div", "section", "article", "dd", "dt", "figcaption", "tr" -> if (cell == null) flush()
                    "li" -> { flush(); listItem = true }
                    "blockquote" -> { flush(); quote++ }
                    "br" -> if (pre > 0) preText.append('\n') else spans += Span("\n")
                    "hr" -> { flush(); blocks += Block.Rule }
                    "pre" -> { flush(); pre++ }
                    "table" -> { flush(); tableRows = mutableListOf() }
                    "td", "th" -> cell = StringBuilder()
                    "img", "image" -> {
                        val src = token.attrs["src"] ?: token.attrs["href"]
                        if (src != null && inBody) {
                            flush()
                            blocks += Block.Picture(resolve(base, src), token.attrs["alt"])
                        }
                    }
                }
                is Markup.Token.Close -> when (token.name) {
                    "title" -> inTitle = false
                    "b", "strong" -> bold = (bold - 1).coerceAtLeast(0)
                    "i", "em", "cite" -> italic = (italic - 1).coerceAtLeast(0)
                    "h1", "h2", "h3", "h4", "h5", "h6" -> { flush(); heading = 0 }
                    "p", "div", "section", "article", "dd", "dt", "figcaption" -> if (cell == null) flush()
                    "li" -> { flush(); listItem = false }
                    "blockquote" -> { flush(); quote = (quote - 1).coerceAtLeast(0) }
                    "pre" -> {
                        pre = (pre - 1).coerceAtLeast(0)
                        if (pre == 0 && preText.isNotBlank()) blocks += Block.Preformatted(preText.toString().trimEnd())
                        preText.clear()
                    }
                    "td", "th" -> {
                        val text = cell?.toString()?.trim().orEmpty()
                        tableRows?.let { rows -> if (rows.isEmpty()) rows += mutableListOf<String>(); rows.last() += text }
                        cell = null
                    }
                    "tr" -> tableRows?.add(mutableListOf())
                    "table" -> {
                        val rows = tableRows?.filter { it.isNotEmpty() }.orEmpty()
                        if (rows.isNotEmpty()) blocks += Block.Table(rows)
                        tableRows = null
                    }
                }
                is Markup.Token.Text -> when {
                    inTitle -> pageTitle = pageTitle ?: token.text.trim().takeIf { it.isNotEmpty() }
                    !inBody -> Unit
                    pre > 0 -> preText.append(token.text)
                    cell != null -> cell!!.append(token.text.replace(Regex("\\s+"), " "))
                    else -> {
                        val text = token.text.replace(Regex("[\\t\\r\\n ]+"), " ")
                        if (text.isNotEmpty()) spans += Span(text, bold > 0, italic > 0)
                    }
                }
            }
        }
        flush()
        // Bölümün ilk başlığı, yoksa sayfanın <title>'ı.
        return Chapter(title ?: pageTitle, blocks)
    }

    /** Art arda aynı biçimdeki parçaları birleştirir, baştaki/sondaki boşlukları kırpar. */
    internal fun mergeSpans(spans: List<Span>): List<Span> {
        val out = mutableListOf<Span>()
        for (s in spans) {
            val last = out.lastOrNull()
            if (last != null && last.bold == s.bold && last.italic == s.italic) out[out.lastIndex] = last.copy(text = last.text + s.text)
            else out += s
        }
        if (out.isEmpty()) return out
        out[0] = out[0].copy(text = out[0].text.trimStart())
        out[out.lastIndex] = out.last().copy(text = out.last().text.trimEnd())
        val cleaned = out.filter { it.text.isNotEmpty() }
        return if (cleaned.all { it.text.isBlank() }) emptyList() else cleaned
    }

    fun resolve(base: String, href: String): String {
        val clean = runCatching { URLDecoder.decode(href.substringBefore('#'), "UTF-8") }.getOrDefault(href.substringBefore('#'))
        if (base.isEmpty() || clean.startsWith("/")) return clean.trimStart('/')
        return runCatching { URI("x:/" + base).resolve(URI(null, null, clean, null)).path.trimStart('/') }.getOrDefault(clean)
    }
}

/** Zip arşivinin dosyaları (EPUB, Office, CBZ). Çok büyük dosyalar [limit]'i aşınca atlanır. */
object ZipEntries {
    fun read(input: InputStream, limit: Long = 256L * 1024 * 1024, keep: (String) -> Boolean = { true }): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !keep(entry.name)) continue
                val bytes = zip.readBytes()
                total += bytes.size
                if (total > limit) break
                out[entry.name] = bytes
            }
        }
        return out
    }
}

/** Dosya türlerine göre belgeyi açar. */
object DocumentReader {
    fun epub(files: Map<String, ByteArray>): Document {
        val container = files["META-INF/container.xml"]?.let(::text) ?: throw IllegalArgumentException("EPUB kapsayıcısı yok")
        val opfPath = Markup.tokens(container).filterIsInstance<Markup.Token.Open>().firstOrNull { it.name == "rootfile" }?.attrs?.get("full-path")
            ?: throw IllegalArgumentException("EPUB içerik dosyası yok")
        val opf = files[opfPath]?.let(::text) ?: throw IllegalArgumentException("EPUB içerik dosyası okunamadı")
        val manifest = HashMap<String, Pair<String, String>>() // id → (yol, tür)
        val spine = mutableListOf<String>()
        var title: String? = null
        var author: String? = null
        var coverId: String? = null
        var coverHref: String? = null
        var tocId: String? = null
        var field: String? = null
        for (t in Markup.tokens(opf)) {
            when (t) {
                is Markup.Token.Open -> when (t.name) {
                    "item" -> {
                        val id = t.attrs["id"] ?: continue
                        val href = HtmlBlocks.resolve(opfPath, t.attrs["href"] ?: continue)
                        manifest[id] = href to t.attrs["media-type"].orEmpty()
                        if (t.attrs["properties"]?.contains("cover-image") == true) coverHref = href
                    }
                    "itemref" -> t.attrs["idref"]?.let { spine += it }
                    "spine" -> tocId = t.attrs["toc"]
                    "meta" -> if (t.attrs["name"] == "cover") coverId = t.attrs["content"]
                    "title", "creator" -> field = t.name
                }
                is Markup.Token.Text -> when (field) {
                    "title" -> if (title == null) title = t.text.trim().ifEmpty { null }
                    "creator" -> if (author == null) author = t.text.trim().ifEmpty { null }
                }
                is Markup.Token.Close -> if (t.name == field) field = null
            }
        }
        val tocTitles = tocTitles(files, manifest, tocId)
        val chapters = spine.mapNotNull { id -> manifest[id]?.first }.mapNotNull { path ->
            val html = files[path]?.let(::text) ?: return@mapNotNull null
            val chapter = HtmlBlocks.parse(html, path)
            chapter.copy(title = tocTitles[path] ?: chapter.title)
        }.filter { it.blocks.isNotEmpty() }
        val cover = coverHref ?: coverId?.let { manifest[it]?.first }
        val images = files.filterKeys { name -> IMAGE_FILE.containsMatchIn(name) }
        return Document(title, author, chapters, images, cover?.takeIf { it in files })
    }

    private val IMAGE_FILE = Regex("""\.(jpe?g|png|gif|webp|svg|bmp)$""", RegexOption.IGNORE_CASE)

    /** İçindekiler (NCX ya da EPUB 3 nav) → dosya yolu → başlık. */
    private fun tocTitles(files: Map<String, ByteArray>, manifest: Map<String, Pair<String, String>>, tocId: String?): Map<String, String> {
        val result = HashMap<String, String>()
        val ncx = tocId?.let { manifest[it]?.first } ?: manifest.values.firstOrNull { it.second == "application/x-dtbncx+xml" }?.first
        val source = ncx?.let { files[it] }?.let(::text) ?: return result
        var label: String? = null
        var inText = false
        for (t in Markup.tokens(source)) {
            when (t) {
                is Markup.Token.Open -> when (t.name) {
                    "text" -> inText = true
                    "content" -> t.attrs["src"]?.let { src -> label?.let { result.putIfAbsent(HtmlBlocks.resolve(ncx, src), it) } }
                }
                is Markup.Token.Close -> if (t.name == "text") inText = false
                is Markup.Token.Text -> if (inText) label = t.text.trim().ifEmpty { label }
            }
        }
        return result
    }

    fun fb2(source: String): Document {
        val chapters = mutableListOf<Chapter>()
        val blocks = mutableListOf<Block>()
        val spans = mutableListOf<Span>()
        val resources = HashMap<String, ByteArray>()
        var title: String? = null
        var author = StringBuilder()
        var chapterTitle: String? = null
        var inTitle = false
        var inBody = false
        var inBookTitle = false
        var inAuthor = false
        var bold = 0
        var italic = 0
        var binaryId: String? = null
        val binary = StringBuilder()
        var cover: String? = null
        var inCover = false
        var inPara = false
        fun flush(heading: Boolean = false) {
            val merged = HtmlBlocks.mergeSpans(spans)
            spans.clear()
            if (merged.isEmpty()) return
            if (heading) {
                blocks += Block.Heading(2, merged)
                if (chapterTitle == null) chapterTitle = merged.joinToString("") { it.text }
            } else {
                blocks += Block.Paragraph(merged)
            }
        }
        fun closeChapter() {
            flush()
            if (blocks.isNotEmpty()) chapters += Chapter(chapterTitle, blocks.toList())
            blocks.clear()
            chapterTitle = null
        }
        for (t in Markup.tokens(source)) {
            when (t) {
                is Markup.Token.Open -> when (t.name) {
                    "body" -> inBody = true
                    "section" -> if (inBody) closeChapter()
                    "title" -> { flush(); inTitle = true }
                    "p", "v" -> { flush(inTitle); inPara = true }
                    "strong" -> bold++
                    "emphasis" -> italic++
                    "empty-line" -> flush(inTitle)
                    "book-title" -> inBookTitle = true
                    "author" -> inAuthor = true
                    "coverpage" -> inCover = true
                    "image" -> {
                        val href = (t.attrs["href"] ?: "").removePrefix("#")
                        if (inCover && cover == null) cover = href
                        if (inBody && href.isNotEmpty()) { flush(inTitle); blocks += Block.Picture(href, null) }
                    }
                    "binary" -> { binaryId = t.attrs["id"]; binary.clear() }
                }
                is Markup.Token.Close -> when (t.name) {
                    "body" -> { closeChapter(); inBody = false }
                    "title" -> { flush(heading = true); inTitle = false }
                    "p", "v" -> { flush(inTitle); inPara = false }
                    "strong" -> bold = (bold - 1).coerceAtLeast(0)
                    "emphasis" -> italic = (italic - 1).coerceAtLeast(0)
                    "book-title" -> inBookTitle = false
                    "author" -> inAuthor = false
                    "coverpage" -> inCover = false
                    "binary" -> {
                        binaryId?.let { id -> runCatching { java.util.Base64.getMimeDecoder().decode(binary.toString()) }.getOrNull()?.let { resources[id] = it } }
                        binaryId = null
                    }
                }
                is Markup.Token.Text -> when {
                    binaryId != null -> binary.append(t.text)
                    inBookTitle -> title = (title.orEmpty() + t.text).trim()
                    inAuthor && !inBody -> if (t.text.isNotBlank()) author.append(t.text.trim()).append(' ')
                    inBody && (inPara || inTitle) -> spans += Span(t.text.replace(Regex("\\s+"), " "), bold > 0, italic > 0)
                }
            }
        }
        closeChapter()
        return Document(title?.ifBlank { null }, author.toString().trim().ifBlank { null }, chapters, resources, cover?.takeIf { it in resources })
    }

    /** Word (DOCX) ve OpenDocument metni (ODT). */
    fun word(files: Map<String, ByteArray>): Document {
        files["word/document.xml"]?.let { xml ->
            val blocks = mutableListOf<Block>()
            val spans = mutableListOf<Span>()
            var heading = 0
            var bold = false
            var italic = false
            var inText = false
            var listItem = false
            for (t in Markup.tokens(text(xml))) {
                when (t) {
                    is Markup.Token.Open -> when (t.name) {
                        "p" -> { heading = 0; listItem = false }
                        "pstyle" -> t.attrs["val"]?.let { style ->
                            Regex("""(?i)heading\s*(\d)|ba[sş]l[iı]k\s*(\d)""").find(style)?.let { m -> heading = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() ?: 1 }
                            if (style.equals("Title", true)) heading = 1
                        }
                        "numpr" -> listItem = true
                        "r" -> { bold = false; italic = false }
                        "b" -> bold = t.attrs["val"] != "0" && t.attrs["val"] != "false"
                        "i" -> italic = t.attrs["val"] != "0" && t.attrs["val"] != "false"
                        "t" -> inText = true
                        "tab" -> spans += Span("\t")
                        "br" -> spans += Span("\n")
                    }
                    is Markup.Token.Close -> when (t.name) {
                        "t" -> inText = false
                        "p" -> {
                            val merged = HtmlBlocks.mergeSpans(spans)
                            spans.clear()
                            if (merged.isNotEmpty()) blocks += if (heading > 0) Block.Heading(heading.coerceIn(1, 6), merged) else Block.Paragraph(merged, listItem = listItem)
                        }
                    }
                    is Markup.Token.Text -> if (inText) spans += Span(t.text, bold, italic)
                }
            }
            return Document(null, null, listOf(Chapter(null, blocks)))
        }
        val content = files["content.xml"]?.let(::text) ?: throw IllegalArgumentException("Belge içeriği yok")
        val html = content.replace(Regex("<(/?)text:h\\b[^>]*>"), "<$1h2>").replace(Regex("<(/?)text:p\\b[^>]*>"), "<$1p>")
            .replace(Regex("<(/?)text:list-item\\b[^>]*>"), "<$1li>")
        return Document(null, null, listOf(HtmlBlocks.parse("<body>$html</body>")))
    }

    /** Sunular (PPTX, ODP): her slayt bir bölüm, yalnızca metin. */
    fun slides(files: Map<String, ByteArray>): Document {
        val pptx = files.keys.filter { Regex("""ppt/slides/slide\d+\.xml""").matches(it) }
            .sortedBy { Regex("""\d+""").find(it.substringAfterLast('/'))?.value?.toIntOrNull() ?: 0 }
        if (pptx.isNotEmpty()) {
            return Document(null, null, pptx.mapIndexed { index, path ->
                val paragraphs = mutableListOf<Block>()
                val line = StringBuilder()
                var inText = false
                for (t in Markup.tokens(text(files.getValue(path)))) {
                    when (t) {
                        is Markup.Token.Open -> if (t.name == "t") inText = true
                        is Markup.Token.Close -> when (t.name) {
                            "t" -> inText = false
                            "p" -> { if (line.isNotBlank()) paragraphs += Block.Paragraph(listOf(Span(line.toString().trim()))); line.clear() }
                        }
                        is Markup.Token.Text -> if (inText) line.append(t.text)
                    }
                }
                Chapter("Slayt ${index + 1}", paragraphs)
            })
        }
        return word(files)
    }

    /** Tablolar (XLSX, ODS, CSV): her sayfa bir tablo. */
    fun sheet(files: Map<String, ByteArray>): Document {
        val shared = files["xl/sharedStrings.xml"]?.let { xml ->
            val list = mutableListOf<String>()
            val current = StringBuilder()
            var inT = false
            for (t in Markup.tokens(text(xml))) {
                when (t) {
                    is Markup.Token.Open -> if (t.name == "t") inT = true
                    is Markup.Token.Close -> when (t.name) {
                        "t" -> inT = false
                        "si" -> { list += current.toString(); current.clear() }
                    }
                    is Markup.Token.Text -> if (inT) current.append(t.text)
                }
            }
            list
        }.orEmpty()
        val sheets = files.keys.filter { Regex("""xl/worksheets/sheet\d+\.xml""").matches(it) }
            .sortedBy { Regex("""\d+""").find(it.substringAfterLast('/'))?.value?.toIntOrNull() ?: 0 }
        if (sheets.isNotEmpty()) {
            return Document(null, null, sheets.mapIndexed { index, path ->
                val rows = mutableListOf<MutableList<String>>()
                var type: String? = null
                var column = 0
                var inValue = false
                for (t in Markup.tokens(text(files.getValue(path)))) {
                    when (t) {
                        is Markup.Token.Open -> when (t.name) {
                            "row" -> rows += mutableListOf<String>()
                            "c" -> {
                                type = t.attrs["t"]
                                column = t.attrs["r"]?.let(::columnIndex) ?: (rows.lastOrNull()?.size ?: 0)
                                rows.lastOrNull()?.let { row -> while (row.size < column) row += "" }
                            }
                            "v", "t" -> inValue = true
                        }
                        is Markup.Token.Close -> if (t.name == "v" || t.name == "t") inValue = false
                        is Markup.Token.Text -> if (inValue) {
                            val value = if (type == "s") shared.getOrNull(t.text.trim().toIntOrNull() ?: -1).orEmpty() else t.text
                            rows.lastOrNull()?.let { row -> if (row.size > column) row[column] = row[column] + value else row += value }
                        }
                    }
                }
                Chapter("Sayfa ${index + 1}", listOf(Block.Table(rows.filter { r -> r.any { it.isNotBlank() } }.take(MAX_ROWS))))
            })
        }
        val content = files["content.xml"]?.let(::text) ?: throw IllegalArgumentException("Tablo içeriği yok")
        val html = content.replace(Regex("<(/?)table:table-row\\b[^>]*>"), "<$1tr>").replace(Regex("<(/?)table:table-cell\\b[^>]*>"), "<$1td>")
            .replace(Regex("<(/?)table:table\\b[^>]*>"), "<$1table>")
        return Document(null, null, listOf(HtmlBlocks.parse("<body>$html</body>")))
    }

    /** CSV/TSV: ayırıcı kendiliğinden bulunur, tırnaklar desteklenir. */
    fun csv(source: String): Document {
        val firstLine = source.lineSequence().firstOrNull().orEmpty()
        val separator = listOf('\t', ';', ',').maxByOrNull { c -> firstLine.count { it == c } } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < source.length && rows.size < MAX_ROWS) {
            val c = source[i]
            when {
                quoted && c == '"' && source.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == separator -> { row += cell.toString(); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && source.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return Document(null, null, listOf(Chapter(null, listOf(Block.Table(rows)))))
    }

    /** "C12" → 2 (A=0). */
    internal fun columnIndex(ref: String): Int {
        var index = 0
        for (c in ref) {
            if (!c.isLetter()) break
            index = index * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return (index - 1).coerceAtLeast(0)
    }

    /** Metin kodlaması: XML başlığındaki ya da UTF-8 (BOM atlanır). */
    fun text(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        val charset = Regex("""encoding=["']([\w-]+)["']""").find(head)?.groupValues?.get(1)
            ?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset).removePrefix("\uFEFF")
    }

    private const val MAX_ROWS = 5000
}
