package com.gtarayici.browser.youtube

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.gtarayici.browser.MainActivity
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState

/**
 * YouTube indirmelerini bildirimle arka planda yürütür. Yüksek çözünürlükte görüntü ve ses ayrı
 * indirilir, ardından cihaz üzerinde (yeniden kodlama yapmadan) tek bir MP4 dosyasında birleştirilir.
 */
class YouTubeDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Mutex()
    private val running = AtomicInteger(0)
    private val notifications by lazy { NotificationManagerCompat.from(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, getString(R.string.yt_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: return START_NOT_STICKY
        val job = Job(
            title = title,
            videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL),
            audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL),
            extension = intent.getStringExtra(EXTRA_EXTENSION) ?: "mp4",
            mime = intent.getStringExtra(EXTRA_MIME) ?: "video/mp4",
            notificationId = NEXT_ID.incrementAndGet(),
        )
        val foreground = progressNotification(job, getString(R.string.yt_waiting), 0)
        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            foreground,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        running.incrementAndGet()
        scope.launch {
            queue.withLock { runJob(job) }
            if (running.decrementAndGet() == 0) {
                ServiceCompat.stopForeground(this@YouTubeDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class Job(
        val title: String,
        val videoUrl: String?,
        val audioUrl: String?,
        val extension: String,
        val mime: String,
        val notificationId: Int,
    ) {
        val fileName: String get() = safeFileName(title) + "." + extension
    }

    private suspend fun runJob(job: Job) {
        val work = File(cacheDir, "yt-${job.notificationId}").apply { mkdirs() }
        try {
            val output: File = when {
                job.videoUrl != null && job.audioUrl != null -> {
                    val video = File(work, "video.mp4")
                    val audio = File(work, "audio.m4a")
                    download(job, job.videoUrl, video, 0, 85)
                    download(job, job.audioUrl, audio, 85, 97)
                    update(job, getString(R.string.yt_merging), 98)
                    File(work, "out.mp4").also { mux(video, audio, it) }
                }
                else -> File(work, "out.${job.extension}").also {
                    download(job, job.videoUrl ?: job.audioUrl ?: error("Adres yok"), it, 0, 99)
                }
            }
            val directory = publish(output, job)
            registerInDownloads(job, output.length(), directory)
            done(job, success = true)
        } catch (e: Exception) {
            done(job, success = false, message = e.message)
        } finally {
            work.deleteRecursively()
        }
    }

    /** Parça parça (Range) indirir; YouTube tek parça büyük isteklerde hızı düşürebilir. */
    private suspend fun download(job: Job, url: String, target: File, fromPercent: Int, toPercent: Int) =
        withContext(Dispatchers.IO) {
            val proxy = components.vpn.javaProxy()
            var downloaded = 0L
            var total = -1L
            target.outputStream().buffered().use { out ->
                while (total < 0 || downloaded < total) {
                    val end = downloaded + CHUNK - 1
                    val connection = URL(url).openConnection(proxy) as HttpURLConnection
                    connection.connectTimeout = 20_000
                    connection.readTimeout = 30_000
                    connection.setRequestProperty("User-Agent", YouTubeDownloader.USER_AGENT)
                    connection.setRequestProperty("Range", "bytes=$downloaded-$end")
                    val code = connection.responseCode
                    if (code !in 200..299) error("Sunucu hatası: $code")
                    if (total < 0) {
                        total = connection.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                            ?: connection.contentLengthLong.takeIf { it > 0 } ?: Long.MAX_VALUE
                    }
                    var read = 0L
                    connection.inputStream.use { input ->
                        read = copy(input, out) { bytes ->
                            val percent = fromPercent + ((downloaded + bytes) * (toPercent - fromPercent) / total.coerceAtLeast(1))
                            update(job, getString(R.string.yt_downloading), percent.toInt())
                        }
                    }
                    connection.disconnect()
                    if (read == 0L) break
                    downloaded += read
                    if (code == 200) break // Sunucu Range desteklemiyorsa tüm dosya tek seferde geldi.
                }
            }
        }

    private var lastUpdate = 0L

    private fun copy(input: InputStream, out: java.io.OutputStream, onProgress: (Long) -> Unit): Long {
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            count += n
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 700) {
                lastUpdate = now
                onProgress(count)
            }
        }
        return count
    }

    /** Görüntü ve ses izlerini yeniden kodlamadan tek bir MP4'te birleştirir. */
    @SuppressLint("WrongConstant")
    private fun mux(videoFile: File, audioFile: File, output: File) {
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = listOf(videoFile to "video/", audioFile to "audio/").map { (file, prefix) ->
            val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME).orEmpty().startsWith(prefix)
            }
            extractor.selectTrack(track)
            Triple(extractor, extractor.getTrackFormat(track), muxer.addTrack(extractor.getTrackFormat(track)))
        }
        muxer.start()
        val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        extractors.forEach { (extractor, _, outTrack) ->
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, extractor.sampleTime, flags)
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            extractor.release()
        }
        muxer.stop()
        muxer.release()
    }

    /** Dosyayı herkese açık "İndirilenler" klasörüne taşır; dizin yolunu döndürür. */
    private fun publish(file: File, job: Job): String {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, job.fileName)
                put(MediaStore.Downloads.MIME_TYPE, job.mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Dosya oluşturulamadı")
            contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            return publicDir.absolutePath
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        file.copyTo(File(dir, job.fileName), overwrite = true)
        return dir.absolutePath
    }

    private suspend fun registerInDownloads(job: Job, size: Long, directory: String) = withContext(Dispatchers.Main) {
        components.store.dispatch(
            DownloadAction.AddDownloadAction(
                DownloadState(
                    url = "https://www.youtube.com/",
                    fileName = job.fileName,
                    contentType = job.mime,
                    contentLength = size,
                    currentBytesCopied = size,
                    status = DownloadState.Status.COMPLETED,
                    directoryPath = directory,
                ),
            ),
        )
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOWNLOADS),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(job: Job, text: String, percent: Int) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(job.title)
            .setContentText(text)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()

    @SuppressLint("MissingPermission")
    private fun update(job: Job, text: String, percent: Int) {
        runCatching { notifications.notify(FOREGROUND_ID, progressNotification(job, "$text · %$percent", percent)) }
    }

    @SuppressLint("MissingPermission")
    private fun done(job: Job, success: Boolean, message: String? = null) {
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(job.title)
            .setContentText(
                if (success) getString(R.string.yt_done, job.fileName) else getString(R.string.yt_failed, message.orEmpty()),
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        runCatching { notifications.notify(job.notificationId, notification) }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_AUDIO_URL = "audio_url"
        const val EXTRA_EXTENSION = "extension"
        const val EXTRA_MIME = "mime"
        private const val CHANNEL = "youtube_downloads"
        private const val FOREGROUND_ID = 7300
        private const val CHUNK = 8L * 1024 * 1024
        private val NEXT_ID = AtomicInteger(7300)

        fun safeFileName(title: String): String =
            title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(120).ifBlank { "video" }
    }
}
