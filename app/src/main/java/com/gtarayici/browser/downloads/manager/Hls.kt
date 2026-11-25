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
    /** Birden çok kalite: her biri ayrı bir medya listesi. [audio]: ses grubu → ayrı ses listeleri. */
    data class Master(val variants: List<Variant>, val audio: Map<String, List<Rendition>> = emptyMap()) : HlsPlaylist

    data class Media(
        val segments: List<HlsSegment>,
        /** fMP4 akışlarında parçalardan önce yazılan başlatma parçası. */
        val initUrl: String?,
        val initRange: ByteRange? = null,
        val live: Boolean,
        /** Desteklenmeyen şifreleme (ör. SAMPLE-AES, DRM) varsa yöntemin adı. */
        val unsupportedEncryption: String?,
    ) : HlsPlaylist

    /** [audioGroup]: görüntünün sesi ayrı bir listedeyse o ses grubunun adı. */
    data class Variant(val url: String, val bandwidth: Long, val resolution: String?, val audioGroup: String? = null)

    /** Ayrı ses listesi (#EXT-X-MEDIA TYPE=AUDIO). */
    data class Rendition(val url: String, val default: Boolean, val language: String?)
}

/** Dosyanın bir bölümü: [offset] baytından başlayan [length] bayt. */
data class ByteRange(val offset: Long, val length: Long) {
    val header: String get() = "bytes=$offset-${offset + length - 1}"
}

data class HlsSegment(val url: String, val sequence: Long, val key: HlsKey?, val range: ByteRange? = null)

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
            val audio = LinkedHashMap<String, MutableList<HlsPlaylist.Rendition>>()
            lines.forEachIndexed { i, line ->
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    val attrs = attributes(line.substringAfter(':'))
                    val uri = lines.getOrNull(i + 1)?.takeUnless { it.startsWith("#") } ?: return@forEachIndexed
                    variants += HlsPlaylist.Variant(
                        resolve(baseUrl, uri),
                        attrs["BANDWIDTH"]?.toLongOrNull() ?: 0,
                        attrs["RESOLUTION"],
                        attrs["AUDIO"],
                    )
                } else if (line.startsWith("#EXT-X-MEDIA:")) {
                    val attrs = attributes(line.substringAfter(':'))
                    val uri = attrs["URI"]
                    val group = attrs["GROUP-ID"]
                    // URI'siz ses kaydı: ses zaten görüntüyle aynı listede.
                    if (attrs["TYPE"] == "AUDIO" && uri != null && group != null) {
                        audio.getOrPut(group) { mutableListOf() } +=
                            HlsPlaylist.Rendition(resolve(baseUrl, uri), attrs["DEFAULT"] == "YES", attrs["LANGUAGE"])
                    }
                }
            }
            return HlsPlaylist.Master(variants, audio)
        }
        var sequence = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE") }?.substringAfter(':')?.trim()?.toLongOrNull() ?: 0
        var key: HlsKey? = null
        var unsupported: String? = null
        var init: String? = null
        var initRange: ByteRange? = null
        val segments = mutableListOf<HlsSegment>()
        // #EXT-X-BYTERANGE: sıradaki parça, dosyanın bir bölümü. Başlangıç verilmezse önceki bölümün bittiği yerden.
        var pendingRange: Pair<Long, Long?>? = null
        var nextOffset = 0L
        var lastRangeUrl: String? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-BYTERANGE:") -> pendingRange = parseRange(line.substringAfter(':'))
                line.startsWith("#EXT-X-KEY") -> {
                    val attrs = attributes(line.substringAfter(':'))
                    key = when (val method = attrs["METHOD"]?.uppercase()) {
                        null, "NONE" -> null
                        "AES-128" -> HlsKey(resolve(baseUrl, attrs["URI"] ?: ""), attrs["IV"]?.let(::hex))
                        else -> { unsupported = method; null }
                    }
                }
                line.startsWith("#EXT-X-MAP") -> {
                    val attrs = attributes(line.substringAfter(':'))
                    attrs["URI"]?.let { init = resolve(baseUrl, it) }
                    initRange = attrs["BYTERANGE"]?.let(::parseRange)?.let { (length, offset) -> ByteRange(offset ?: 0, length) }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val url = resolve(baseUrl, line)
                    val range = pendingRange?.let { (length, offset) ->
                        val start = offset ?: if (url == lastRangeUrl) nextOffset else 0L
                        nextOffset = start + length
                        lastRangeUrl = url
                        ByteRange(start, length)
                    }
                    pendingRange = null
                    segments += HlsSegment(url, sequence++, key, range)
                }
            }
        }
        return HlsPlaylist.Media(
            segments,
            init,
            initRange,
            live = lines.none { it.startsWith("#EXT-X-ENDLIST") },
            unsupportedEncryption = unsupported,
        )
    }

    /** "uzunluk[@başlangıç]" → (uzunluk, başlangıç). */
    private fun parseRange(text: String): Pair<Long, Long?>? {
        val length = text.substringBefore('@').trim().toLongOrNull() ?: return null
        return length to text.substringAfter('@', "").trim().toLongOrNull()
    }

    /** Seçilen kalitenin ayrı ses listesi (varsayılan olan, yoksa ilki); ses görüntüyle birlikteyse null. */
    fun audioFor(master: HlsPlaylist.Master, variant: HlsPlaylist.Variant): HlsPlaylist.Rendition? {
        val group = master.audio[variant.audioGroup ?: return null] ?: return null
        return group.firstOrNull { it.default } ?: group.firstOrNull()
    }

    /** En yüksek kalite (bant genişliğine, eşitse çözünürlüğe göre). */
    fun best(master: HlsPlaylist.Master): HlsPlaylist.Variant? = master.variants.maxWithOrNull(
        compareBy<HlsPlaylist.Variant> { it.bandwidth }.thenBy { variant ->
            variant.resolution?.split('x')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 2 }?.let { it[0] * it[1] } ?: 0
        },
    )

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
    /** Çözümlenmiş akış: görüntü listesi ve (ses ayrı bir listedeyse) ses listesi. */
    data class Resolved(val url: String, val media: HlsPlaylist.Media, val audio: HlsPlaylist.Media? = null)

    /** Medya listesini bulur (gerekirse en yüksek kaliteyi ve onun ayrı ses listesini seçer). */
    suspend fun resolve(url: String, context: RequestContext): Resolved = withContext(Dispatchers.IO) {
        var current = url
        var audioUrl: String? = null
        repeat(3) {
            when (val playlist = HlsParser.parse(text(current, context), current)) {
                is HlsPlaylist.Master -> {
                    val best = HlsParser.best(playlist) ?: throw IOException("Akışta oynatılabilir kalite yok")
                    current = best.url
                    audioUrl = HlsParser.audioFor(playlist, best)?.url
                }
                is HlsPlaylist.Media -> {
                    checkPlayable(playlist)
                    // Ayrı ses alınamazsa video yine indirilir (sessiz olmaktansa hata vermek yerine).
                    val audio = audioUrl?.let { address ->
                        runCatching { HlsParser.parse(text(address, context), address) as? HlsPlaylist.Media }
                            .getOrNull()?.takeIf { it.segments.isNotEmpty() && !it.live && it.unsupportedEncryption == null }
                    }
                    return@withContext Resolved(current, playlist, audio)
                }
            }
        }
        throw IOException("Akış çözümlenemedi")
    }

    private fun checkPlayable(playlist: HlsPlaylist.Media) {
        if (playlist.live) throw UnsupportedStreamException(UnsupportedStreamException.Reason.LIVE)
        if (playlist.unsupportedEncryption != null) throw UnsupportedStreamException(UnsupportedStreamException.Reason.ENCRYPTED)
        if (playlist.segments.isEmpty()) throw IOException("Akışta parça yok")
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
        media.initUrl?.let { init -> bytes(init, context, media.initRange).also { output.write(it); written += it.size } }
        val segments = media.segments
        var index = 0
        while (index < segments.size) {
            val window = segments.subList(index, minOf(index + parallel, segments.size))
            val results = window.map { segment ->
                async(Dispatchers.IO) {
                    val data = withRetry { bytes(segment.url, context, segment.range) }
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

    private fun bytes(url: String, context: RequestContext, range: ByteRange? = null): ByteArray {
        val headers = MutableHeaders()
        context.userAgent?.let { headers.set("User-Agent", it) }
        context.referer?.takeIf { it.startsWith("http") }?.let { referer ->
            headers.set("Referer", referer)
            // Oynatıcılar listeyi ve parçaları betikle (XHR) ister; tarayıcı bu isteklerde Origin de gönderir ve bazı
            // sunucular onu bekler.
            originOf(referer)?.let { headers.set("Origin", it) }
        }
        range?.let { headers.set("Range", it.header) }
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

/** "https://ornek.com/yol" → "https://ornek.com". */
internal fun originOf(url: String): String? = runCatching {
    val uri = URI(url)
    val port = if (uri.port > 0) ":${uri.port}" else ""
    if (uri.scheme == null || uri.host == null) null else "${uri.scheme}://${uri.host}$port"
}.getOrNull()

/** İndirilemeyen akış: canlı yayın veya DRM/özel şifreleme. */
class UnsupportedStreamException(val reason: Reason) : IOException(reason.name) {
    enum class Reason { LIVE, ENCRYPTED }
}
