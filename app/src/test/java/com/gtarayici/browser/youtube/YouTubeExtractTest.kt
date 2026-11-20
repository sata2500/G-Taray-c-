package com.gtarayici.browser.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class YouTubeExtractTest {
    @Test
    fun parsesVideoIds() {
        assertEquals("jNQXAC9IVRw", YouTubeDownloader.videoId("https://www.youtube.com/watch?v=jNQXAC9IVRw&t=3"))
        assertEquals("jNQXAC9IVRw", YouTubeDownloader.videoId("https://m.youtube.com/watch?v=jNQXAC9IVRw"))
        assertEquals("jNQXAC9IVRw", YouTubeDownloader.videoId("https://youtu.be/jNQXAC9IVRw"))
        assertEquals("abcdEFGhijk", YouTubeDownloader.videoId("https://youtube.com/shorts/abcdEFGhijk"))
        assertEquals(null, YouTubeDownloader.videoId("https://www.youtube.com/feed/subscriptions"))
    }

    @Test
    fun formatsApproximateSizes() {
        assertEquals("~25 MB", YouTubeDownloader.sizeLabel(24_944_676))
        assertEquals("~3,4 MB", YouTubeDownloader.sizeLabel(3_400_000))
        assertEquals("~1,3 GB", YouTubeDownloader.sizeLabel(1_250_000_000))
        assertEquals("~0,1 MB", YouTubeDownloader.sizeLabel(20_000))
        assertEquals(null, YouTubeDownloader.sizeLabel(-1))
    }

    /** Gerçek ağ testi: yalnızca GT_NETWORK_TESTS=1 iken çalışır. */
    @Test
    fun extractsDownloadOptions() {
        assumeTrue(System.getenv("GT_NETWORK_TESTS") == "1")
        YouTubeDownloader.init { null }
        val video = YouTubeDownloader.extract("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        println("Başlık: ${video.title} / ${video.uploader}")
        video.options.forEach { println(" - ${it.label} | ${it.detail} | merge=${it.needsMerge}") }
        assertTrue(video.options.isNotEmpty())

        val hd = YouTubeDownloader.extract("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        hd.options.forEach { println(" * ${it.label} | ${it.detail} | merge=${it.needsMerge}") }
        assertTrue("Yüksek çözünürlük birleştirme seçeneği yok", hd.options.any { it.needsMerge })
    }
}
