package com.gtarayici.browser.youtube

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.gtarayici.browser.Components
import java.net.Proxy
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/** İndirme seçeneği: tek dosya (sesli video veya yalnız ses) ya da birleştirilecek görüntü + ses. */
data class YouTubeOption(
    val label: String,
    val detail: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val extension: String,
    val mimeType: String,
) {
    val needsMerge: Boolean get() = videoUrl != null && audioUrl != null
}

data class YouTubeVideo(val title: String, val uploader: String, val options: List<YouTubeOption>)

/**
 * YouTube videolarını NewPipe Extractor ile çözümler. Akış adresleri doğrudan YouTube'dan alınır;
 * Google hesabı veya resmi API gerekmez. Ağ istekleri, açıksa tarayıcı VPN'i üzerinden gider.
 */
class YouTubeDownloader(private val context: Context, private val components: Components) {
    private val initialized by lazy {
        init { components.vpn.javaProxy() }
        true
    }

    suspend fun fetch(pageUrl: String): Result<YouTubeVideo> = withContext(Dispatchers.IO) {
        runCatching {
            check(initialized)
            extract(pageUrl)
        }
    }

    fun start(video: YouTubeVideo, option: YouTubeOption) {
        val intent = Intent(context, YouTubeDownloadService::class.java)
            .putExtra(YouTubeDownloadService.EXTRA_TITLE, video.title)
            .putExtra(YouTubeDownloadService.EXTRA_VIDEO_URL, option.videoUrl)
            .putExtra(YouTubeDownloadService.EXTRA_AUDIO_URL, option.audioUrl)
            .putExtra(YouTubeDownloadService.EXTRA_EXTENSION, option.extension)
            .putExtra(YouTubeDownloadService.EXTRA_MIME, option.mimeType)
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Android 15; Mobile; rv:140.0) Gecko/140.0 Firefox/140.0"

        /** NewPipe'ı başlatır. [proxy] null dönerse sistemin varsayılan ağ ayarı kullanılır. */
        fun init(proxy: () -> Proxy?) {
            NewPipe.init(HttpDownloader(proxy), Localization("tr", "TR"), ContentCountry("TR"))
        }

        /** Video sayfasını çözümler ve indirme seçeneklerini üretir (ağ isteği yapar). */
        fun extract(pageUrl: String): YouTubeVideo {
            val extractor = ServiceList.YouTube.getStreamExtractor(canonicalUrl(pageUrl))
            extractor.fetchPage()

            val progressive = extractor.videoStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.MPEG_4 && it.isUrl }
                .sortedByDescending { it.height }
            val videoOnly = extractor.videoOnlyStreams
                .filter {
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.MPEG_4 &&
                        it.isUrl && it.codec.orEmpty().startsWith("avc1")
                }
                .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { it.height }.thenByDescending { it.fps })
                .distinctBy { it.height }
            val audio = extractor.audioStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            val bestM4a = audio.filter { it.format == MediaFormat.M4A }
                .filter { it.audioTrackType == null || it.audioTrackType?.name == "ORIGINAL" }
                .ifEmpty { audio.filter { it.format == MediaFormat.M4A } }
                .maxByOrNull { it.averageBitrate }
            val bestOpus = audio.filter { it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.WEBMA }
                .maxByOrNull { it.averageBitrate }

            val options = mutableListOf<YouTubeOption>()
            if (bestM4a != null) {
                videoOnly.filter { it.height > (progressive.firstOrNull()?.height ?: 0) }.forEach { video ->
                    options += YouTubeOption(
                        label = "Video ${video.height}p${if (video.fps > 30) " ${video.fps}fps" else ""}",
                        detail = "MP4 · görüntü ve ses birleştirilir",
                        videoUrl = video.content,
                        audioUrl = bestM4a.content,
                        extension = "mp4",
                        mimeType = "video/mp4",
                    )
                }
            }
            progressive.forEach { video ->
                options += YouTubeOption("Video ${video.height}p", "MP4 · hızlı", video.content, null, "mp4", "video/mp4")
            }
            bestM4a?.let {
                options += YouTubeOption("Yalnız ses", "M4A · ${it.averageBitrate.coerceAtLeast(0)} kbps", null, it.content, "m4a", "audio/mp4")
            }
            bestOpus?.let {
                options += YouTubeOption("Yalnız ses", "Opus (WebM) · ${it.averageBitrate.coerceAtLeast(0)} kbps", null, it.content, "webm", "audio/webm")
            }
            check(options.isNotEmpty()) { "Bu video için indirilebilir bir biçim bulunamadı" }
            return YouTubeVideo(extractor.name, extractor.uploaderName.orEmpty(), options)
        }

        fun isVideoPage(url: String): Boolean = videoId(url) != null

        fun videoId(url: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            val host = uri.host?.removePrefix("www.")?.removePrefix("m.") ?: return null
            val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            val query = uri.rawQuery.orEmpty().split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            return when {
                host == "youtu.be" -> segments.firstOrNull()
                host == "youtube.com" || host == "music.youtube.com" -> when {
                    uri.path == "/watch" -> query["v"]
                    segments.firstOrNull() in setOf("shorts", "live", "embed") -> segments.getOrNull(1)
                    else -> null
                }
                else -> null
            }?.takeIf { it.length in 8..16 }
        }

        private fun canonicalUrl(url: String) = videoId(url)?.let { "https://www.youtube.com/watch?v=$it" } ?: url
    }
}
