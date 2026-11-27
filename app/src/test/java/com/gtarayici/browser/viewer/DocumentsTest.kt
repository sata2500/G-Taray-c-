package com.gtarayici.browser.viewer

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentsTest {
    private fun zip(vararg files: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> files.forEach { (name, body) -> z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry() } }
    }.toByteArray()

    @Test fun htmlBlocksWithFormatting() {
        val chapter = HtmlBlocks.parse(
            """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
            <html><head><title>T</title><style>p{color:red}</style></head><body>
            <h1>Bölüm&nbsp;1</h1><p>Merhaba <b>kalın</b> ve <em>eğik</em> &amp; son.</p>
            <img src="../img/a.png" alt="resim"/><ul><li>bir</li><li>iki</li></ul><hr/></body></html>""",
            "OEBPS/text/ch1.xhtml",
        )
        assertEquals("Bölüm 1", chapter.title)
        val p = chapter.blocks[1] as Block.Paragraph
        assertEquals(listOf(Span("Merhaba "), Span("kalın", bold = true), Span(" ve "), Span("eğik", italic = true), Span(" & son.")), p.spans)
        assertEquals(Block.Picture("OEBPS/img/a.png", "resim"), chapter.blocks[2])
        assertTrue((chapter.blocks[3] as Block.Paragraph).listItem)
        assertEquals(Block.Rule, chapter.blocks.last())
    }

    @Test fun readsEpub() {
        val bytes = zip(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            "OEBPS/content.opf" to """<package><metadata><dc:title>Kitap</dc:title><dc:creator>Yazar</dc:creator><meta name="cover" content="c"/></metadata>
                <manifest><item id="c" href="cover.jpg" media-type="image/jpeg"/><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="a" href="a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="b.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine toc="ncx"><itemref idref="a"/><itemref idref="b"/></spine></package>""",
            "OEBPS/toc.ncx" to """<ncx><navMap><navPoint><navLabel><text>Giriş</text></navLabel><content src="a.xhtml"/></navPoint></navMap></ncx>""",
            "OEBPS/a.xhtml" to "<html><body><p>Bir</p></body></html>",
            "OEBPS/b.xhtml" to "<html><body><h2>İki</h2><p>İkinci</p></body></html>",
            "OEBPS/cover.jpg" to "jpg",
        )
        val doc = DocumentReader.epub(ZipEntries.read(bytes.inputStream()))
        assertEquals("Kitap", doc.title)
        assertEquals("Yazar", doc.author)
        assertEquals(listOf("Giriş", "İki"), doc.chapters.map { it.title })
        assertEquals("OEBPS/cover.jpg", doc.coverPath)
    }

    @Test fun readsFb2() {
        val doc = DocumentReader.fb2(
            """<?xml version="1.0" encoding="utf-8"?><FictionBook><description><title-info><author><first-name>Ad</first-name><last-name>Soyad</last-name></author>
            <book-title>Masal</book-title></title-info></description><body><section><title><p>Bir</p></title><p>Metin <emphasis>eğik</emphasis></p></section>
            <section><title><p>İki</p></title><p>Son</p></section></body></FictionBook>""",
        )
        assertEquals("Masal", doc.title)
        assertEquals("Ad Soyad", doc.author)
        assertEquals(listOf("Bir", "İki"), doc.chapters.map { it.title })
    }

    @Test fun readsDocx() {
        val doc = DocumentReader.word(ZipEntries.read(zip("word/document.xml" to
            """<w:document><w:body><w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Başlık</w:t></w:r></w:p>
            <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Kalın</w:t></w:r><w:r><w:t xml:space="preserve"> düz</w:t></w:r></w:p></w:body></w:document>""").inputStream()))
        val blocks = doc.chapters.single().blocks
        assertEquals(Block.Heading(1, listOf(Span("Başlık"))), blocks[0])
        assertEquals(Block.Paragraph(listOf(Span("Kalın", bold = true), Span(" düz"))), blocks[1])
    }

    @Test fun readsXlsxAndCsv() {
        val doc = DocumentReader.sheet(ZipEntries.read(zip(
            "xl/sharedStrings.xml" to "<sst><si><t>Ad</t></si><si><t>Ali</t></si></sst>",
            "xl/worksheets/sheet1.xml" to """<worksheet><sheetData><row><c r="A1" t="s"><v>0</v></c><c r="C1"><v>5</v></c></row>
                <row><c r="A2" t="s"><v>1</v></c></row></sheetData></worksheet>""",
        ).inputStream()))
        assertEquals(listOf(listOf("Ad", "", "5"), listOf("Ali")), (doc.chapters.single().blocks.single() as Block.Table).rows)
        val csv = DocumentReader.csv("a;b;\"c;d\"\n1;2;3\n")
        assertEquals(listOf(listOf("a", "b", "c;d"), listOf("1", "2", "3")), (csv.chapters.single().blocks.single() as Block.Table).rows)
        assertEquals(27, DocumentReader.columnIndex("AB12"))
    }

    @Test fun kinds() {
        assertEquals(FileKind.EPUB, FileKind.of("a.epub"))
        assertEquals(FileKind.IMAGE, FileKind.of("x.heic"))
        assertEquals(FileKind.VIDEO, FileKind.of("film.mkv"))
        assertEquals(FileKind.SHEET, FileKind.of("t.csv"))
        assertEquals(FileKind.OTHER, FileKind.of("a.svg", "image/svg+xml"))
    }
}
