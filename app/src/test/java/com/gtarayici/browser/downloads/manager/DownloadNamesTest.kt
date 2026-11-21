package com.gtarayici.browser.downloads.manager

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadNamesTest {
    @Test
    fun kindsFromExtensionAndMime() {
        assertEquals(MediaKind.VIDEO, DownloadNames.kind("https://e.org/a/b.MP4?token=1"))
        assertEquals(MediaKind.STREAM, DownloadNames.kind("https://e.org/live/index.m3u8#x"))
        assertEquals(MediaKind.AUDIO, DownloadNames.kind("https://e.org/x", "audio/mpeg"))
        assertEquals(MediaKind.DOCUMENT, DownloadNames.kind("https://e.org/rapor.pdf"))
        assertEquals(MediaKind.ARCHIVE, DownloadNames.kind("https://e.org/a.tar.gz"))
        assertEquals(MediaKind.APP, DownloadNames.kind("https://e.org/app.apk"))
        assertEquals(MediaKind.IMAGE, DownloadNames.kind("https://e.org/p", "image/webp; charset=x"))
        assertEquals(MediaKind.OTHER, DownloadNames.kind("https://e.org/page.html"))
    }

    @Test
    fun contentDispositionNames() {
        assertEquals("rapor 2026.pdf", DownloadNames.fromContentDisposition("attachment; filename=\"rapor 2026.pdf\""))
        assertEquals("İstanbul.mp4", DownloadNames.fromContentDisposition("attachment; filename=x.mp4; filename*=UTF-8''%C4%B0stanbul.mp4"))
        assertEquals("a_b.zip", DownloadNames.fromContentDisposition("attachment; filename=a/b.zip"))
        assertNull(DownloadNames.fromContentDisposition("inline"))
    }

    @Test
    fun finalNamesGetExtensions() {
        assertEquals("Güzel video.mp4", DownloadNames.finalName("Güzel video", "https://e.org/v?id=1", "video/mp4", MediaKind.VIDEO))
        assertEquals("Haber.mp4", DownloadNames.finalName("Haber", "https://e.org/x/index.m3u8", null, MediaKind.STREAM))
        assertEquals("belge.pdf", DownloadNames.finalName(null, "https://e.org/dosyalar/belge.pdf?dl=1", null, MediaKind.DOCUMENT))
        assertEquals("e.org", DownloadNames.finalName(null, "https://www.e.org/", null, MediaKind.OTHER))
        assertEquals("a_b_c", DownloadNames.sanitize("a:b?c"))
        assertEquals(120, DownloadNames.sanitize("x".repeat(300) + ".pdf").length)
    }

    @Test
    fun pageScanParsingDedupesAndClassifies() {
        val json = JSONObject(
            """
            {"type":"resources","title":"Sayfa","blob":true,"items":[
              {"url":"https://e.org/v.mp4","type":"video","w":1280,"h":720,"duration":95},
              {"url":"https://e.org/v.mp4","type":"media"},
              {"url":"https://e.org/s/master.m3u8","type":"video"},
              {"url":"https://e.org/kitap.pdf","type":"link","text":"Kitabı indir"},
              {"url":"blob:https://e.org/123","type":"video"},
              {"url":"https://e.org/f.jpg","type":"image","w":800,"h":600}
            ]}
            """.trimIndent(),
        )
        val scan = PageScan.parse(json)
        assertEquals(4, scan.resources.size)
        assertEquals(true, scan.hasStreamingVideo)
        val video = scan.resources.first { it.url.endsWith("v.mp4") }
        assertEquals(MediaKind.VIDEO, video.kind)
        assertEquals(1280, video.width)
        assertEquals(95, video.durationSeconds)
        assertEquals(MediaKind.STREAM, scan.resources.first { it.url.endsWith(".m3u8") }.kind)
        assertEquals(listOf(MediaKind.VIDEO, MediaKind.STREAM, MediaKind.DOCUMENT, MediaKind.IMAGE), scan.groups.keys.toList())
    }
}
