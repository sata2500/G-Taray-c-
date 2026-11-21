package com.gtarayici.browser.downloads.manager

import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedDownloaderTest {
    private val url = "https://example.org/video.mp4"
    private val file = Random(7).nextBytes(5 * 1024 * 1024 + 123)

    private fun runDownload(server: FakeServer, connections: Int = 8): Pair<MemorySink, Long> = runBlocking {
        val downloader = SegmentedDownloader(server, retries = 3)
        val probe = downloader.probe(url, RequestContext())
        val parts = SegmentPlanner.plan(probe.total, connections, probe.rangesSupported).map(::SegmentProgress)
        val sink = MemorySink(file.size)
        val bytes = AtomicLong()
        downloader.download(url, RequestContext(), parts, sink) { bytes.addAndGet(it) }
        sink to bytes.get()
    }

    @Test
    fun probeReadsTotalAndRangeSupport() = runBlocking {
        val probe = SegmentedDownloader(FakeServer(mapOf(url to file))).probe(url, RequestContext())
        assertEquals(file.size.toLong(), probe.total)
        assertTrue(probe.rangesSupported)
        assertEquals("video/mp4", probe.mime)
    }

    @Test
    fun parallelSegmentsReassembleExactly() {
        val server = FakeServer(mapOf(url to file))
        val (sink, bytes) = runDownload(server)
        assertArrayEquals(file, sink.data)
        assertEquals(file.size.toLong(), bytes)
        // Yoklama + 5 parça (5 MB / en az 1 MB'lık parçalar)
        assertEquals(6, server.requests.get())
    }

    @Test
    fun brokenConnectionsResumeFromWhereTheyStopped() {
        // İlk 3 yanıt 100 KB sonra kopar; parçalar kaldıkları bayttan sürmeli.
        val server = FakeServer(mapOf(url to file), cutAfter = 100 * 1024, cutCount = 4)
        val (sink, bytes) = runDownload(server)
        assertArrayEquals(file, sink.data)
        assertEquals(file.size.toLong(), bytes)
        assertTrue(server.rangeHeaders.any { it != null && !it.endsWith("-0") && it.substringAfter('=').substringBefore('-').toLong() % (1024 * 1024) != 0L })
    }

    @Test
    fun serverWithoutRangesUsesSingleConnection() {
        val server = FakeServer(mapOf(url to file), ranges = false)
        val (sink, _) = runDownload(server)
        assertArrayEquals(file, sink.data)
        assertEquals(2, server.requests.get())
    }

    @Test
    fun plannerSplitsOnlyLargeRangeFriendlyFiles() {
        assertEquals(1, SegmentPlanner.plan(1_000_000, 8, true).size)
        assertEquals(1, SegmentPlanner.plan(50_000_000, 8, false).size)
        val parts = SegmentPlanner.plan(50_000_000, 8, true)
        assertEquals(8, parts.size)
        assertEquals(0, parts.first().start)
        assertEquals(49_999_999, parts.last().end)
        assertEquals(50_000_000L, parts.sumOf { it.length })
        parts.zipWithNext().forEach { (a, b) -> assertEquals(a.end + 1, b.start) }
        assertFalse(SegmentPlanner.plan(-1, 8, true).first().end >= 0)
    }
}
