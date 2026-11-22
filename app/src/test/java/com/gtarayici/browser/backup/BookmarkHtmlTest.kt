package com.gtarayici.browser.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkHtmlTest {
    private val chrome = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        <DL><p>
            <DT><H3 ADD_DATE="1700000000" PERSONAL_TOOLBAR_FOLDER="true">Yer işareti çubuğu</H3>
            <DL><p>
                <DT><A HREF="https://haber.com/" ADD_DATE="1700000001" ICON="data:image/png;base64,AAA">Haber &amp; Gündem</A>
                <DT><H3>İş</H3>
                <DL><p>
                    <DT><A HREF="https://is.example.com/panel">Panel</A>
                    <DT><A HREF="javascript:alert(1)">Yer imi betiği</A>
                </DL><p>
                <DT><A HREF="https://sonra.com/">Klasörden sonra</A>
            </DL><p>
            <DT><A HREF="https://kok.com/">Kök</A>
        </DL><p>
    """.trimIndent()

    @Test
    fun parsesFoldersAndSkipsScripts() {
        val list = BookmarkHtml.parse(chrome)
        assertEquals(listOf("https://haber.com/", "https://is.example.com/panel", "https://sonra.com/", "https://kok.com/"), list.map { it.url })
        assertEquals("Haber & Gündem", list[0].title)
        assertEquals("", list[0].folder) // kök klasör yok sayılır
        assertEquals("İş", list[1].folder)
        assertEquals("", list[2].folder) // klasör kapandıktan sonra
        assertEquals(1_700_000_001_000L, list[0].addedAt)
    }

    @Test
    fun exportRoundTrips() {
        val items = listOf(
            ImportedBookmark("https://a.com/?x=1&y=2", "A <b>", "", 1_700_000_000_000L),
            ImportedBookmark("https://b.com/", "B", "Okunacaklar", null),
        )
        val back = BookmarkHtml.parse(BookmarkHtml.export(items))
        assertEquals(items.map { it.url to it.title }, back.map { it.url to it.title })
        assertEquals(listOf("", "Okunacaklar"), back.map { it.folder })
    }
}
