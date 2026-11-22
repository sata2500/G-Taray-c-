package com.gtarayici.browser.backup

/** HTML yer imi dosyasındaki (Chrome, Firefox, Edge, Samsung… dışa aktarımı) bir kayıt. */
data class ImportedBookmark(val url: String, val title: String, val folder: String, val addedAt: Long?)

/**
 * Tarayıcıların ortak "Netscape yer imi" HTML biçimi. Klasörler iç içe olabilir; G-Tarayıcı'da klasörler tek
 * düzey olduğu için en içteki klasörün adı kullanılır. "Yer işareti çubuğu" gibi kök klasörler yok sayılır.
 */
object BookmarkHtml {
    private val folderOpen = Regex("""<H3[^>]*>(.*?)</H3>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val link = Regex("""<A\s([^>]*)>(.*?)</A>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val listClose = Regex("""</DL>""", RegexOption.IGNORE_CASE)
    private val token = Regex("""<H3[^>]*>.*?</H3>|<A\s[^>]*>.*?</A>|</DL>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val attr = Regex("""([A-Za-z_]+)\s*=\s*"([^"]*)"""")

    /** Kök sayılan, içeriği doğrudan köke alınan klasörler. */
    private val ROOTS = setOf(
        "bookmarks bar", "bookmarks toolbar", "other bookmarks", "mobile bookmarks", "bookmarks menu", "bookmarks",
        "favorites bar", "yer işareti çubuğu", "yer imleri çubuğu", "diğer yer işaretleri", "diğer yer imleri",
        "mobil yer işaretleri", "mobil yer imleri", "yer imleri araç çubuğu", "yer imleri menüsü", "sık kullanılanlar çubuğu",
    )

    fun parse(html: String): List<ImportedBookmark> {
        val stack = ArrayDeque<String>()
        val out = mutableListOf<ImportedBookmark>()
        for (match in token.findAll(html)) {
            val text = match.value
            when {
                folderOpen.matches(text) -> {
                    // Klasör başlığından sonra gelen <DL> onun içeriğidir; </DL> görülünce yığından çıkar.
                    stack.addLast(decode(folderOpen.find(text)!!.groupValues[1]).trim())
                }
                listClose.matches(text) -> if (stack.isNotEmpty()) stack.removeLast()
                else -> {
                    val m = link.find(text) ?: continue
                    val attrs = attr.findAll(m.groupValues[1]).associate { it.groupValues[1].uppercase() to decode(it.groupValues[2]) }
                    val url = attrs["HREF"]?.trim().orEmpty()
                    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) continue
                    val title = decode(m.groupValues[2].replace(Regex("<[^>]+>"), "")).trim()
                    out += ImportedBookmark(
                        url = url,
                        title = title.ifEmpty { url },
                        folder = stack.lastOrNull { it.lowercase() !in ROOTS }.orEmpty(),
                        addedAt = attrs["ADD_DATE"]?.toLongOrNull()?.let { if (it < 100_000_000_000) it * 1000 else it / 1000 },
                    )
                }
            }
        }
        return out.distinctBy { it.url to it.folder }
    }

    fun export(bookmarks: List<ImportedBookmark>): String = buildString {
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        appendLine("<!-- G-Tarayıcı yer imleri. Diğer tarayıcılarda \"Yer imlerini içe aktar → HTML\" ile açılabilir. -->")
        appendLine("""<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">""")
        appendLine("<TITLE>Bookmarks</TITLE>")
        appendLine("<H1>Bookmarks</H1>")
        appendLine("<DL><p>")
        bookmarks.groupBy { it.folder }.toSortedMap().forEach { (folder, items) ->
            val indent = if (folder.isEmpty()) "    " else "        "
            if (folder.isNotEmpty()) {
                appendLine("    <DT><H3>${encode(folder)}</H3>")
                appendLine("    <DL><p>")
            }
            items.forEach { b ->
                val date = b.addedAt?.let { """ ADD_DATE="${it / 1000}"""" }.orEmpty()
                appendLine("""$indent<DT><A HREF="${encode(b.url)}"$date>${encode(b.title)}</A>""")
            }
            if (folder.isNotEmpty()) appendLine("    </DL><p>")
        }
        appendLine("</DL><p>")
    }

    private fun encode(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun decode(text: String) = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&nbsp;", " ").replace("&amp;", "&")
}
