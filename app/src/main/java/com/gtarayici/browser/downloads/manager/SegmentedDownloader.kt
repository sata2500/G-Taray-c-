package com.gtarayici.browser.downloads.manager

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response

/** İndirilen baytların yazıldığı hedef; parçalar dosyanın kendi konumlarına yazılır. */
interface DownloadSink {
    fun write(position: Long, buffer: ByteArray, length: Int)
}

/** İstek başlıkları: tarayıcının kimliği ve sayfanın adresi (birçok site bunlarsız indirmeye izin vermez). */
data class RequestContext(
    val userAgent: String? = null,
    val referer: String? = null,
    val private: Boolean = false,
    /** Mozilla indiricisinin verdiği çerez başlığı (motor çerezleri zaten ekler; yalnızca boşsa kullanılır). */
    val cookie: String? = null,
)

/** Sunucunun dosya hakkında söyledikleri. */
data class Probe(
    val total: Long,
    val rangesSupported: Boolean,
    val mime: String?,
    val fileName: String?,
    val finalUrl: String,
)

/** Parçaların ilerlemesi; duraklatıp sürdürmek için saklanır. */
class SegmentProgress(val segment: Segment) {
    val done = AtomicLong(0)
    val complete: Boolean get() = segment.end >= 0 && done.get() >= segment.length
}

/**
 * Çok bağlantılı (IDM benzeri) indirici. Sunucu aralık isteklerini (Range) destekliyorsa dosya parçalara
 * bölünür ve her parça ayrı bağlantıdan, dosyadaki kendi yerine yazılır; kopan bağlantı kaldığı bayttan
 * yeniden denenir. Desteklemiyorsa tek bağlantıyla indirilir.
 *
 * İstekler tarayıcı motorunun ağ katmanından ([Client]) geçer: site çerezleri ve tarayıcı VPN'i geçerlidir.
 */
class SegmentedDownloader(
    private val client: Client,
    private val retries: Int = 5,
) {
    suspend fun probe(url: String, context: RequestContext): Probe = withContext(Dispatchers.IO) {
        val response = fetch(url, context, range = "bytes=0-0")
        response.use {
            if (it.status !in 200..299) throw HttpStatusException(it.status)
            val headers = it.headers
            val contentRange = headers["Content-Range"]
            val total = when {
                it.status == 206 && contentRange != null -> contentRange.substringAfter('/').trim().toLongOrNull() ?: -1
                else -> headers["Content-Length"]?.toLongOrNull() ?: -1
            }
            Probe(
                total = total,
                rangesSupported = it.status == 206 && total > 0,
                mime = headers["Content-Type"],
                fileName = DownloadNames.fromContentDisposition(headers["Content-Disposition"]),
                finalUrl = it.url.ifBlank { url },
            )
        }
    }

    /**
     * [parts] parçalarını eşzamanlı indirir. [onBytes] her yazılan bayt miktarını bildirir.
     * Durdurulursa (iptal) parçaların ilerlemesi [parts] içinde kalır ve aynı listeyle sürdürülebilir.
     */
    suspend fun download(
        url: String,
        context: RequestContext,
        parts: List<SegmentProgress>,
        sink: DownloadSink,
        /** Aynı anda açık bağlantı sayısı; parçalar bundan fazlaysa biten bağlantı sıradaki parçayı alır. */
        parallel: Int = parts.size,
        onBytes: (Long) -> Unit,
    ) = coroutineScope {
        val gate = kotlinx.coroutines.sync.Semaphore(parallel.coerceIn(1, parts.size.coerceAtLeast(1)))
        parts.filterNot { it.complete }.map { part ->
            async(Dispatchers.IO) {
                gate.acquire()
                try {
                    downloadPart(url, context, part, sink, onBytes, single = parts.size == 1)
                } finally {
                    gate.release()
                }
            }
        }.awaitAll()
        Unit
    }

    private suspend fun downloadPart(
        url: String,
        context: RequestContext,
        part: SegmentProgress,
        sink: DownloadSink,
        onBytes: (Long) -> Unit,
        single: Boolean,
    ) {
        var attempt = 0
        while (true) {
            coroutineContext.ensureActive()
            val from = part.segment.start + part.done.get()
            val knownEnd = part.segment.end >= 0
            if (knownEnd && from > part.segment.end) return
            try {
                // Tek parçalı ve boyutu bilinmeyen indirmede baştan başlanır (sunucu aralığı desteklemiyor olabilir).
                val range = when {
                    knownEnd -> "bytes=$from-${part.segment.end}"
                    from > 0 -> "bytes=$from-"
                    else -> null
                }
                fetch(url, context, range).use { response ->
                    when {
                        response.status == 206 -> Unit
                        response.status == 200 && from == 0L -> Unit
                        // Sunucu aralığı yok sayıp tüm dosyayı gönderdiyse ve bu parça baştan başlamıyorsa kullanılamaz.
                        response.status == 200 -> if (single) part.done.set(0) else throw RangeNotHonoredException()
                        else -> throw HttpStatusException(response.status)
                    }
                    response.body.useStream { input -> copy(input, part, sink, onBytes) }
                }
                if (!knownEnd || part.complete) return
                // Bağlantı erken kapandı: kaldığı yerden devam.
                throw IOException("Bağlantı erken kapandı")
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                // 4xx kalıcıdır (izin yok, bulunamadı); yeniden denemek işe yaramaz. 429 ve 5xx denenir.
                if (e.status in 400..499 && e.status != 429 && e.status != 408) throw e
                if (++attempt > retries) throw e
                delay(backoff(attempt))
            } catch (e: RangeNotHonoredException) {
                throw e
            } catch (e: IOException) {
                if (++attempt > retries) throw e
                delay(backoff(attempt))
            }
        }
    }

    private fun copy(input: InputStream, part: SegmentProgress, sink: DownloadSink, onBytes: (Long) -> Unit) {
        val buffer = ByteArray(BUFFER)
        val end = part.segment.end
        while (true) {
            var read = input.read(buffer)
            if (read < 0) return
            if (end >= 0) {
                val remaining = part.segment.length - part.done.get()
                if (remaining <= 0) return
                if (read > remaining) read = remaining.toInt()
            }
            sink.write(part.segment.start + part.done.get(), buffer, read)
            part.done.addAndGet(read.toLong())
            onBytes(read.toLong())
        }
    }

    private fun fetch(url: String, context: RequestContext, range: String?): Response {
        val headers = MutableHeaders()
        range?.let { headers.set("Range", it) }
        context.userAgent?.let { headers.set("User-Agent", it) }
        context.referer?.takeIf { it.startsWith("http") }?.let { headers.set("Referer", it) }
        context.cookie?.takeIf { it.isNotBlank() }?.let { headers.set("Cookie", it) }
        // Sıkıştırma kapalı: parça uzunlukları ve dosya konumları bayt bayt doğru olmalı.
        headers.set("Accept-Encoding", "identity")
        val request = Request(
            url = url,
            headers = headers,
            connectTimeout = 20L to TimeUnit.SECONDS,
            readTimeout = 30L to TimeUnit.SECONDS,
            redirect = Request.Redirect.FOLLOW,
            cookiePolicy = Request.CookiePolicy.INCLUDE,
            private = context.private,
        )
        return client.fetch(request)
    }

    private fun backoff(attempt: Int): Long = (500L shl (attempt - 1).coerceAtMost(5)).coerceAtMost(15_000)

    class HttpStatusException(val status: Int) : IOException("HTTP $status")
    class RangeNotHonoredException : IOException("Sunucu parçalı indirmeyi desteklemiyor")

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
