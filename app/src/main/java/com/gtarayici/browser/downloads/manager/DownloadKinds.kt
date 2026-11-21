package com.gtarayici.browser.downloads.manager

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** İndirilebilir öğenin türü; sayfa taramasında gruplama ve simge için kullanılır. */
enum class MediaKind { VIDEO, AUDIO, STREAM, DOCUMENT, ARCHIVE, APP, IMAGE, OTHER }

/** Dosya adı, uzantı ve tür çıkarımı (Android'e bağlı değildir; birim testleriyle denetlenir). */
object DownloadNames {
    private val VIDEO = setOf("mp4", "m4v", "webm", "mkv", "mov", "avi", "3gp", "flv", "ts", "mpg", "mpeg", "wmv", "ogv")
    private val AUDIO = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "wma", "weba")
    private val STREAM = setOf("m3u8")
    private val DOCUMENT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "csv", "epub", "mobi", "djvu")
    private val ARCHIVE = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso")
    private val APP = setOf("apk", "apks", "xapk", "exe", "msi", "dmg", "deb", "rpm", "appimage")
    private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "svg", "heic", "heif", "tif", "tiff")

    private val MIME_EXTENSIONS = mapOf(
        "video/mp4" to "mp4", "video/webm" to "webm", "video/x-matroska" to "mkv", "video/quicktime" to "mov",
        "video/mp2t" to "ts", "audio/mpeg" to "mp3", "audio/mp4" to "m4a", "audio/aac" to "aac", "audio/ogg" to "ogg",
        "audio/opus" to "opus", "audio/wav" to "wav", "audio/flac" to "flac", "audio/webm" to "weba",
        "application/pdf" to "pdf", "application/zip" to "zip", "application/vnd.android.package-archive" to "apk",
        "image/jpeg" to "jpg", "image/png" to "png", "image/gif" to "gif", "image/webp" to "webp", "image/avif" to "avif",
        "image/svg+xml" to "svg", "text/plain" to "txt", "application/epub+zip" to "epub",
        "application/vnd.apple.mpegurl" to "m3u8", "application/x-mpegurl" to "m3u8",
    )

    /** Adresin yolundaki uzantı (sorgu ve parça olmadan, küçük harf); yoksa boş. */
    fun extension(url: String): String {
        val path = runCatching { URI(url.replace(" ", "%20")).rawPath }.getOrNull() ?: url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        return if ('.' in last) last.substringAfterLast('.').lowercase(Locale.ROOT).take(8) else ""
    }

    fun extensionForMime(mime: String?): String? = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.let { MIME_EXTENSIONS[it] }

    fun kind(url: String, mime: String? = null): MediaKind {
        val type = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val ext = extension(url).ifEmpty { extensionForMime(type).orEmpty() }
        return when {
            ext in STREAM || type.contains("mpegurl") -> MediaKind.STREAM
            ext in VIDEO || type.startsWith("video/") -> MediaKind.VIDEO
            ext in AUDIO || type.startsWith("audio/") -> MediaKind.AUDIO
            ext in IMAGE || type.startsWith("image/") -> MediaKind.IMAGE
            ext in DOCUMENT || type == "application/pdf" -> MediaKind.DOCUMENT
            ext in ARCHIVE -> MediaKind.ARCHIVE
            ext in APP || type == "application/vnd.android.package-archive" -> MediaKind.APP
            else -> MediaKind.OTHER
        }
    }

    /** Sunucunun Content-Disposition başlığındaki dosya adı (RFC 6266, filename* öncelikli). */
    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let { match ->
            val charset = match.groupValues[1].ifBlank { "UTF-8" }
            return runCatching { URLDecoder.decode(match.groupValues[2].trim().trim('"'), charset) }.getOrNull()?.let(::sanitize)
        }
        Regex("""filename\s*=\s*("([^"]*)"|[^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let { match ->
            val raw = match.groupValues[2].ifEmpty { match.groupValues[1] }.trim()
            return sanitize(raw).takeIf { it.isNotBlank() }
        }
        return null
    }

    /** Adresten dosya adı: yolun son parçası (çözülmüş); yoksa alan adı. */
    fun fromUrl(url: String): String {
        val path = runCatching { URI(url.replace(" ", "%20")).rawPath }.getOrNull().orEmpty()
        val last = path.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")
        return sanitize(last ?: host ?: "indirme")
    }

    /**
     * İndirilecek dosyanın son adı: önerilen ad (sayfa başlığı, sunucu) yoksa adresten alınır; uzantısı yoksa
     * içerik türünden eklenir.
     */
    fun finalName(suggested: String?, url: String, mime: String?, kind: MediaKind): String {
        var name = suggested?.let(::sanitize)?.takeIf { it.isNotBlank() } ?: fromUrl(url)
        val hasExtension = name.substringAfterLast('.', "").let { it.isNotEmpty() && it.length <= 5 && it != name }
        if (!hasExtension) {
            val ext = extensionForMime(mime)?.takeUnless { it == "m3u8" }
                ?: extension(url).takeIf { it.isNotEmpty() && it != "m3u8" }
                ?: when (kind) {
                    MediaKind.STREAM, MediaKind.VIDEO -> "mp4"
                    MediaKind.AUDIO -> "m4a"
                    else -> null
                }
            if (ext != null) name = "$name.$ext"
        }
        return name
    }

    /** Dosya sistemlerinde sorun çıkaran karakterleri ayıklar, uzunluğu sınırlar. */
    fun sanitize(name: String): String {
        val clean = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim().trim('.', ' ')
        if (clean.length <= 120) return clean
        val ext = clean.substringAfterLast('.', "").takeIf { it.length in 1..5 }
        return if (ext != null) clean.take(120 - ext.length - 1) + "." + ext else clean.take(120)
    }
}

/** Bir dosyanın eşzamanlı indirilecek parçaları (bayt aralıkları, uçlar dahil). */
data class Segment(val index: Int, val start: Long, val end: Long) {
    val length: Long get() = end - start + 1
}

object SegmentPlanner {
    /** Bu boyutun altındaki dosyalar tek bağlantıyla indirilir (bölmek kazandırmaz). */
    const val MIN_SPLIT_BYTES = 2L * 1024 * 1024
    private const val MIN_SEGMENT_BYTES = 1L * 1024 * 1024

    /**
     * [total] baytı en fazla [connections] parçaya böler; parçalar en az 1 MB'dir. Boyut bilinmiyorsa veya
     * sunucu aralık isteklerini desteklemiyorsa tek parça döner (boyut bilinmiyorsa uç -1).
     */
    fun plan(total: Long, connections: Int, rangesSupported: Boolean): List<Segment> {
        if (total <= 0) return listOf(Segment(0, 0, -1))
        if (!rangesSupported || total < MIN_SPLIT_BYTES || connections <= 1) return listOf(Segment(0, 0, total - 1))
        val count = minOf(connections.toLong(), total / MIN_SEGMENT_BYTES).toInt().coerceAtLeast(1)
        val size = total / count
        return (0 until count).map { i ->
            val start = i * size
            val end = if (i == count - 1) total - 1 else start + size - 1
            Segment(i, start, end)
        }
    }
}
