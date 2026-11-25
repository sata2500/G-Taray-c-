package com.gtarayici.browser.downloads.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsExtrasTest {
    @Test fun parsesSeparateAudioRendition() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",LANGUAGE="tr",DEFAULT=YES,URI="audio/tr.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",LANGUAGE="en",URI="audio/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,AUDIO="aud"
            low.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080,AUDIO="aud"
            high.m3u8
        """.trimIndent()
        val master = HlsParser.parse(text, "https://cdn.example.com/v/master.m3u8") as HlsPlaylist.Master
        val best = HlsParser.best(master)!!
        assertEquals("https://cdn.example.com/v/high.m3u8", best.url)
        assertEquals("https://cdn.example.com/v/audio/tr.m3u8", HlsParser.audioFor(master, best)?.url)
    }

    @Test fun audioInsideVideoHasNoRendition() {
        val text = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv.m3u8"
        val master = HlsParser.parse(text, "https://e.com/m.m3u8") as HlsPlaylist.Master
        assertNull(HlsParser.audioFor(master, master.variants.first()))
    }

    @Test fun parsesByteRanges() {
        val text = """
            #EXTM3U
            #EXT-X-MAP:URI="main.mp4",BYTERANGE="700@0"
            #EXTINF:4,
            #EXT-X-BYTERANGE:1000@700
            main.mp4
            #EXTINF:4,
            #EXT-X-BYTERANGE:500
            main.mp4
            #EXT-X-ENDLIST
        """.trimIndent()
        val media = HlsParser.parse(text, "https://e.com/x/list.m3u8") as HlsPlaylist.Media
        assertEquals(ByteRange(0, 700), media.initRange)
        assertEquals(ByteRange(700, 1000), media.segments[0].range)
        assertEquals(ByteRange(1700, 500), media.segments[1].range)
        assertEquals("bytes=1700-2199", media.segments[1].range!!.header)
    }

    @Test fun originFromReferer() {
        assertEquals("https://player.example.com", originOf("https://player.example.com/embed/abc?x=1"))
        assertEquals("http://e.com:8080", originOf("http://e.com:8080/a"))
    }

    @Test fun classifiesErrors() {
        assertEquals(DownloadErrors.Kind.EXPIRED to 404, DownloadErrors.classify(SegmentedDownloader.HttpStatusException(404)))
        assertEquals(DownloadErrors.Kind.FORBIDDEN to 403, DownloadErrors.classify(SegmentedDownloader.HttpStatusException(403)))
        assertEquals(DownloadErrors.Kind.SERVER to 503, DownloadErrors.classify(SegmentedDownloader.HttpStatusException(503)))
        assertEquals(DownloadErrors.Kind.PAGE_INSTEAD to null, DownloadErrors.classify(PageInsteadOfFileException()))
        assertEquals(
            DownloadErrors.Kind.PROTECTED to null,
            DownloadErrors.classify(UnsupportedStreamException(UnsupportedStreamException.Reason.ENCRYPTED)),
        )
        assertEquals(DownloadErrors.Kind.NETWORK to null, DownloadErrors.classify(java.io.IOException("reset")))
        assertEquals("expired HTTP 404", DownloadErrors.code(SegmentedDownloader.HttpStatusException(404)))
    }

    @Test fun plansMorePiecesThanConnections() {
        val parts = SegmentPlanner.plan(total = 100L * 1024 * 1024, connections = 8, rangesSupported = true, piecesPerConnection = 3)
        assertEquals(24, parts.size)
        assertEquals(100L * 1024 * 1024, parts.sumOf { it.length })
        assertTrue(parts.zipWithNext().all { (a, b) -> a.end + 1 == b.start })
    }
}
