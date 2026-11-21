package com.gtarayici.browser.downloads.manager

import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request

/** HLS (.m3u8) oynatma listesinin çözümlenmiş hali. */
sealed interface HlsPlaylist {
    /** Birden çok kalite: her biri ayrı bir medya listesi. */
    data class Master(val variants: List<Variant>) : HlsPlaylist

    data class Media(
        val segments: List<HlsSegment>,
        /** fMP4 akışlarında parçalardan önce yazılan başlatma parçası. */
        val initUrl: String?,
        val live: Boolean,
        /** Desteklenmeyen şifreleme (ör. SAMPLE-AES, DRM) varsa yöntemin adı. */
        val unsupportedEncryption: String?,
    ) : HlsPlaylist

    data class Variant(val url: String, val bandwidth: Long, val resolution: String?)
}

data class HlsSegment(val url: String, val sequence: Long, val key: HlsKey?)

data class HlsKey(val uri: String, val iv: ByteArray?) {
    override fun equals(other: Any?) = other is HlsKey && other.uri == uri && other.iv.contentEquals(iv)
    override fun hashCode() = uri.hashCode()
}

object HlsParser {
    fun parse(text: String, baseUrl: String): HlsPlaylist {
        val lines = text.lines().map(String::trim).filter { it.isNotEmpty() }
        require(lines.firstOrNull()?.startsWith("#EXTM3U") == true) { "Geçerli bir HLS oynatma listesi değil" }
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            val variants = mutableListOf<HlsPlaylist.Variant>()
            lines.forEachIndexed { i, line ->
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val attrs = attributes(line.substringAfter(':'))
                    val uri = lines.getOrNull(i + 1)?.takeUnless { it.startsWith("#") } ?: return@forEachIndexed
                    variants += HlsPlaylist.Variant(resolve(baseUrl, uri), attrs["BANDWIDTH"]?.toLongOrNull() ?: 0, attrs["RESOLUTION"])
                }
            }
            return HlsPlaylist.Master(variants)
        }
        var sequence = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE") }?.substringAfter(':')?.trim()?.toLongOrNull() ?: 0
        var key: HlsKey? = null
        var unsupported: String? = null
        var init: String? = null
        val segments = mutableListOf<HlsSegment>()
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY") -> {
                    val attrs = attributes(line.substringAfter(':'))
                    key = when (val method = attrs["METHOD"]?.uppercase()) {
                        null, "NONE" -> null
                        "AES-128" -> HlsKey(resolve(baseUrl, attrs["URI"] ?: ""), attrs["IV"]?.let(::hex))
                        else -> { unsupported = method; null }
                    }
                }
                line.startsWith("#EXT-X-MAP") -> attributes(line.substringAfter(':'))["URI"]?.let { init = resolve(baseUrl, it) }
                line.startsWith("#") -> Unit
                else -> segments += HlsSegment(resolve(baseUrl, line), sequence++, key)
            }
        }
        return HlsPlaylist.Media(segments, init, live = lines.none { it.startsWith("#EXT-X-ENDLIST") }, unsupportedEncryption = unsupported)
    }

    /** En yüksek kalite (bant genişliğine göre). */
    fun best(master: HlsPlaylist.Master): HlsPlaylist.Variant? = master.variants.maxByOrNull { it.bandwidth }

    internal fun attributes(text: String): Map<String, String> =
        Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""").findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim('"') }

    internal fun resolve(base: String, relative: String): String =
        runCatching { URI(base).resolve(relative.replace(" ", "%20")).toString() }.getOrDefault(relative)

    private fun hex(value: String): ByteArray {
        val clean = value.removePrefix("0x").removePrefix("0X").padStart(32, '0')
        return ByteArray(16) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}

/** HLS akışını tek dosyaya indirir (.ts veya fMP4 ise .mp4). */
class HlsDownloader(private val client: Client) {
    /** Medya listesini bulur (gerekirse en yüksek kaliteyi seçer). */
    suspend fun resolve(url: String, context: RequestContext): Pair<String, HlsPlaylist.Media> = withContext(Dispatchers.IO) {
        var current = url
        repeat(3) {
            when (val playlist = HlsParser.parse(text(current, context), current)) {
                is HlsPlaylist.Master -> current = HlsParser.best(playlist)?.url ?: throw IOException("Akışta oynatılabilir kalite yok")
                is HlsPlaylist.Media -> {
                    if (playlist.live) throw UnsupportedStreamException(UnsupportedStreamException.Reason.LIVE)
                    if (playlist.unsupportedEncryption != null) throw UnsupportedStreamException(UnsupportedStreamException.Reason.ENCRYPTED)
                    if (playlist.segments.isEmpty()) throw IOException("Akışta parça yok")
                    return@withContext current to playlist
                }
            }
        }
        throw IOException("Akış çözümlenemedi")
    }

    /**
     * Parçaları [parallel] adet eşzamanlı indirir ve sırayla [output]'a yazar. [onSegment] tamamlanan parça
     * sayısını ve o ana kadar yazılan baytı bildirir.
     */
    suspend fun download(
        media: HlsPlaylist.Media,
        context: RequestContext,
        output: OutputStream,
        parallel: Int = 6,
        onSegment: (done: Int, bytes: Long) -> Unit,
    ) = coroutineScope {
        val keys = HashMap<String, ByteArray>()
        var written = 0L
        media.initUrl?.let { init -> bytes(init, context).also { output.write(it); written += it.size } }
        val segments = media.segments
        var index = 0
        while (index < segments.size) {
            val window = segments.subList(index, minOf(index + parallel, segments.size))
            val results = window.map { segment ->
                async(Dispatchers.IO) {
                    val data = withRetry { bytes(segment.url, context) }
                    val key = segment.key ?: return@async data
                    val secret = synchronized(keys) { keys[key.uri] } ?: withRetry { bytes(key.uri, context) }.also {
                        synchronized(keys) { keys[key.uri] = it }
                    }
                    decrypt(data, secret, key.iv ?: sequenceIv(segment.sequence))
                }
            }
            results.forEach { deferred ->
                val data = deferred.await()
                output.write(data)
                written += data.size
                index++
                onSegment(index, written)
            }
        }
        output.flush()
    }

    private suspend fun <T> withRetry(block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                if (++attempt > 4) throw e
                kotlinx.coroutines.delay(400L * attempt)
            }
        }
    }

    private fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun sequenceIv(sequence: Long): ByteArray = ByteArray(16).also { iv ->
        for (i in 0 until 8) iv[15 - i] = (sequence shr (8 * i)).toByte()
    }

    private fun text(url: String, context: RequestContext): String = String(bytes(url, context))

    private fun bytes(url: String, context: RequestContext): ByteArray {
        val headers = MutableHeaders()
        context.userAgent?.let { headers.set("User-Agent", it) }
        context.referer?.takeIf { it.startsWith("http") }?.let { headers.set("Referer", it) }
        val response = client.fetch(
            Request(
                url = url,
                headers = headers,
                connectTimeout = 20L to TimeUnit.SECONDS,
                readTimeout = 30L to TimeUnit.SECONDS,
                cookiePolicy = Request.CookiePolicy.INCLUDE,
                private = context.private,
            ),
        )
        return response.use {
            if (it.status !in 200..299) throw SegmentedDownloader.HttpStatusException(it.status)
            it.body.useStream { stream -> stream.readBytes() }
        }
    }
}

/** İndirilemeyen akış: canlı yayın veya DRM/özel şifreleme. */
class UnsupportedStreamException(val reason: Reason) : IOException(reason.name) {
    enum class Reason { LIVE, ENCRYPTED }
}
