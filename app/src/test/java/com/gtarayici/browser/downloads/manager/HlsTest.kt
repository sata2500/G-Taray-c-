package com.gtarayici.browser.downloads.manager

import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsTest {
    @Test
    fun masterPicksHighestBandwidthAndResolvesRelativeUrls() {
        val master = HlsParser.parse(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            https://cdn.example.org/hd/index.m3u8
            """.trimIndent(),
            "https://example.org/video/master.m3u8",
        ) as HlsPlaylist.Master
        assertEquals("https://example.org/video/low/index.m3u8", master.variants.first().url)
        assertEquals("https://cdn.example.org/hd/index.m3u8", HlsParser.best(master)!!.url)
        assertEquals("1920x1080", HlsParser.best(master)!!.resolution)
    }

    @Test
    fun mediaPlaylistSegmentsKeysAndLiveFlag() {
        val media = HlsParser.parse(
            """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.0,
            seg10.m4s
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x000102030405060708090a0b0c0d0e0f
            #EXTINF:4.0,
            seg11.m4s
            #EXT-X-ENDLIST
            """.trimIndent(),
            "https://example.org/v/index.m3u8",
        ) as HlsPlaylist.Media
        assertEquals(listOf(10L, 11L), media.segments.map { it.sequence })
        assertNull(media.segments[0].key)
        assertEquals("https://example.org/v/key.bin", media.segments[1].key!!.uri)
        assertEquals(15, media.segments[1].key!!.iv!![15].toInt())
        assertEquals("https://example.org/v/init.mp4", media.initUrl)
        assertTrue(!media.live)
        val live = HlsParser.parse("#EXTM3U\n#EXTINF:2,\na.ts\n", "https://e.org/x.m3u8") as HlsPlaylist.Media
        assertTrue(live.live)
        val drm = HlsParser.parse("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\"\n#EXTINF:2,\na.ts\n#EXT-X-ENDLIST", "https://e.org/x.m3u8") as HlsPlaylist.Media
        assertEquals("SAMPLE-AES", drm.unsupportedEncryption)
    }

    @Test
    fun downloadsAndDecryptsAes128InOrder() = runBlocking {
        val key = ByteArray(16) { it.toByte() }
        val segments = (0 until 9).map { i -> ByteArray(1000 + i) { (i * 31 + it).toByte() } }
        fun encrypt(data: ByteArray, sequence: Long): ByteArray {
            val iv = ByteArray(16).also { for (b in 0 until 8) it[15 - b] = (sequence shr (8 * b)).toByte() }
            return Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }.doFinal(data)
        }
        val base = "https://example.org/s/"
        val playlist = buildString {
            append("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:5\n#EXT-X-KEY:METHOD=AES-128,URI=\"k.key\"\n")
            segments.indices.forEach { append("#EXTINF:2,\nseg$it.ts\n") }
            append("#EXT-X-ENDLIST\n")
        }
        val files = mutableMapOf("${base}index.m3u8" to playlist.toByteArray(), "${base}k.key" to key)
        segments.forEachIndexed { i, data -> files["${base}seg$i.ts"] = encrypt(data, 5L + i) }
        val hls = HlsDownloader(FakeServer(files, ranges = false))
        val (_, media) = hls.resolve("${base}index.m3u8", RequestContext())
        val out = ByteArrayOutputStream()
        var lastDone = 0
        hls.download(media, RequestContext(), out, parallel = 4) { done, _ -> lastDone = done }
        assertEquals(segments.size, lastDone)
        assertArrayEquals(segments.reduce { a, b -> a + b }, out.toByteArray())
    }

    @Test(expected = UnsupportedStreamException::class)
    fun liveStreamsAreRefused() = runBlocking {
        val files = mapOf("https://e.org/live.m3u8" to "#EXTM3U\n#EXTINF:2,\na.ts\n".toByteArray())
        HlsDownloader(FakeServer(files, ranges = false)).resolve("https://e.org/live.m3u8", RequestContext())
        Unit
    }
}
