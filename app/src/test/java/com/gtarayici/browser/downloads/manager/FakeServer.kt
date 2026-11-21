package com.gtarayici.browser.downloads.manager

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response

/** Testler için bellek içi sunucu: adres → içerik; isteğe bağlı aralık desteği ve bağlantı kopması. */
class FakeServer(
    private val files: Map<String, ByteArray>,
    private val ranges: Boolean = true,
    /** İlk N yanıtın gövdesi bu kadar bayttan sonra koparılır (yeniden deneme testi). */
    private val cutAfter: Int = -1,
    private val cutCount: Int = 0,
) : Client() {
    val requests = AtomicInteger()
    val rangeHeaders = java.util.Collections.synchronizedList(mutableListOf<String?>())
    private val cuts = AtomicInteger(cutCount)

    override fun fetch(request: Request): Response {
        requests.incrementAndGet()
        val data = files[request.url] ?: return Response(request.url, 404, MutableHeaders(), Response.Body(ByteArrayInputStream(ByteArray(0))))
        val range = request.headers?.get("Range")
        rangeHeaders += range
        if (range != null && ranges) {
            val spec = range.removePrefix("bytes=")
            val start = spec.substringBefore('-').toLong()
            val end = spec.substringAfter('-').toLongOrNull() ?: (data.size - 1L)
            val slice = data.copyOfRange(start.toInt(), (end + 1).toInt().coerceAtMost(data.size))
            val headers = MutableHeaders(
                "Content-Range" to "bytes $start-$end/${data.size}",
                "Content-Length" to slice.size.toString(),
                "Content-Type" to "video/mp4",
            )
            return Response(request.url, 206, headers, Response.Body(body(slice)))
        }
        val headers = MutableHeaders("Content-Length" to data.size.toString(), "Content-Type" to "video/mp4")
        return Response(request.url, 200, headers, Response.Body(body(data)))
    }

    private fun body(bytes: ByteArray): InputStream {
        if (cutAfter < 0 || cuts.getAndDecrement() <= 0) return ByteArrayInputStream(bytes)
        // Bağlantı yarı yolda kopar.
        return object : InputStream() {
            private var position = 0
            override fun read(): Int {
                if (position >= cutAfter) throw IOException("Connection reset")
                return if (position < bytes.size) bytes[position++].toInt() and 0xFF else -1
            }
        }
    }
}

/** Bellekte dosya; parçalar konumlarına yazılır. */
class MemorySink(size: Int) : DownloadSink {
    val data = ByteArray(size)
    override fun write(position: Long, buffer: ByteArray, length: Int) {
        synchronized(data) { System.arraycopy(buffer, 0, data, position.toInt(), length) }
    }
}
