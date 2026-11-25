package com.gtarayici.browser.youtube

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
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
import kotlinx.coroutines.flow.update
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

    /** Süren (ve sırada bekleyen) işler: İndirilenler ekranından iptal için. */
    private val active = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_ID)?.let { id -> active[id]?.cancel() }
            if (active.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: return START_NOT_STICKY
        val job = Job(
            id = YouTubeDownloads.PREFIX + java.util.UUID.randomUUID(),
            private = intent.getBooleanExtra(EXTRA_PRIVATE, false),
            pageUrl = intent.getStringExtra(EXTRA_PAGE_URL) ?: "https://www.youtube.com/",
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
        // İndirilenler ekranında hemen görünür; ilerleme orada da izlenir.
        scope.launch { record(job, DownloadState.Status.DOWNLOADING) }
        active[job.id] = scope.launch {
            try {
                queue.withLock { runJob(job) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    components.store.dispatch(DownloadAction.RemoveDownloadAction(job.id))
                }
                runCatching { notifications.cancel(job.notificationId) }
            } finally {
                active.remove(job.id)
                YouTubeDownloads.progress.update { it - job.id }
                if (running.decrementAndGet() == 0) {
                    ServiceCompat.stopForeground(this@YouTubeDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class Job(
        val id: String,
        val private: Boolean,
        val pageUrl: String,
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
            val size = output.length()
            val (directory, savedName) = publish(output, job)
            record(job, DownloadState.Status.COMPLETED, savedName, size, directory)
            done(job, success = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = com.gtarayici.browser.ui.feedback.HumanErrors.detail(e)
            com.gtarayici.browser.diagnostics.Diagnostics.log("indirme başarısız: $detail", "video")
            record(job, DownloadState.Status.FAILED)
            done(job, success = false, message = detail)
        } finally {
            estimators.remove(job.notificationId)
            work.deleteRecursively()
        }
    }

    /** Parça parça (Range) indirir; YouTube tek parça büyük isteklerde hızı düşürebilir. */
    private suspend fun download(job: Job, url: String, target: File, fromPercent: Int, toPercent: Int) =
        withContext(Dispatchers.IO) {
            val proxy = components.vpn.javaProxy(private = job.private)
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
                    if (code !in 200..299) error("HTTP $code")
                    if (total < 0) {
                        total = connection.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                            ?: connection.contentLengthLong.takeIf { it > 0 } ?: Long.MAX_VALUE
                    }
                    var read = 0L
                    connection.inputStream.use { input ->
                        read = copy(input, out) { bytes ->
                            val percent = fromPercent + (downloaded + bytes).toDouble() * (toPercent - fromPercent) / total.coerceAtLeast(1)
                            val estimator = estimators.getOrPut(job.notificationId) { com.gtarayici.browser.ui.feedback.ProgressEstimator() }
                            val remaining = estimator.update((percent * 100).toLong(), 100_00L)
                            update(job, getString(R.string.yt_downloading), percent.toInt(), remaining)
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
    private val estimators = java.util.concurrent.ConcurrentHashMap<Int, com.gtarayici.browser.ui.feedback.ProgressEstimator>()

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

    private fun mux(videoFile: File, audioFile: File, output: File) =
        com.gtarayici.browser.media.TrackMuxer.mux(videoFile, audioFile, output)

    /** Dosyayı İndirilenler klasörüne yazar; klasörü ve gerçek dosya adını döndürür (aynı ad varsa sistem "(1)" ekler). */
    private suspend fun publish(file: File, job: Job): Pair<String, String> {
        // Gizli sekmeden indirildiyse ve ayar açıksa gizli kasaya şifreli kaydedilir.
        if (job.private && components.settings.encryptPrivateDownloads.value) {
            val item = components.vault.import(file, job.fileName, job.mime)
            return com.gtarayici.browser.vault.Vault.DOWNLOAD_PREFIX + item.id to item.name
        }
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
            val savedName = contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: job.fileName
            return publicDir.absolutePath to savedName
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        file.copyTo(File(dir, job.fileName), overwrite = true)
        return dir.absolutePath to job.fileName
    }

    /**
     * İndirme kaydı (İndirilenler ekranı). Mozilla'nın indirme ara katmanı "yeni indirme" görünce kendi hizmetiyle
     * dosyayı yeniden indirmesin diye kayıt önce "başarısız" eklenir, hemen ardından gerçek durumuyla güncellenir.
     */
    private suspend fun record(
        job: Job,
        status: DownloadState.Status,
        fileName: String = job.fileName,
        size: Long? = null,
        directory: String? = null,
    ) = withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
        val store = components.store
        val existing = store.state.downloads[job.id]
        val state = (existing ?: DownloadState(
            id = job.id,
            url = job.pageUrl,
            private = job.private,
            skipConfirmation = true,
        )).copy(
            fileName = fileName,
            contentType = job.mime,
            contentLength = size,
            currentBytesCopied = size ?: 0,
            status = status,
            directoryPath = directory ?: existing?.directoryPath ?: "",
        )
        if (existing == null) {
            store.dispatch(DownloadAction.AddDownloadAction(state.copy(status = DownloadState.Status.FAILED)))
            if (status != DownloadState.Status.FAILED) store.dispatch(DownloadAction.UpdateDownloadAction(state))
        } else {
            store.dispatch(DownloadAction.UpdateDownloadAction(state))
        }
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
    private fun update(job: Job, text: String, percent: Int, remainingMs: Long? = null) {
        YouTubeDownloads.progress.update { it + (job.id to (percent / 100f).coerceIn(0f, 1f)) }
        // "İndiriliyor · %45 · yaklaşık 2 dk kaldı"
        val parts = listOfNotNull(text, "%$percent", remainingMs?.let { com.gtarayici.browser.ui.feedback.ProgressEstimator.remainingText(it).resolve(this) })
        runCatching { notifications.notify(FOREGROUND_ID, progressNotification(job, parts.joinToString(" · "), percent)) }
    }

    @SuppressLint("MissingPermission")
    private fun done(job: Job, success: Boolean, message: String? = null) {
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(job.title)
            .setContentText(
                if (success) getString(R.string.yt_done, job.fileName) else com.gtarayici.browser.ui.feedback.HumanErrors.message(com.gtarayici.browser.ui.feedback.ErrorArea.VideoDownload, message).resolve(this),
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        runCatching { notifications.notify(job.notificationId, notification) }
    }

    companion object {
        const val ACTION_CANCEL = "com.gtarayici.browser.YT_CANCEL"
        const val EXTRA_ID = "id"
        const val EXTRA_PRIVATE = "private"
        const val EXTRA_PAGE_URL = "page_url"
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

/** YouTube indirmelerinin ilerlemesi (İndirilenler ekranı bunu gösterir) ve iptali. */
object YouTubeDownloads {
    const val PREFIX = "yt-"

    /** İndirme kimliği → ilerleme (0..1). */
    val progress = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Float>>(emptyMap())

    fun owns(id: String): Boolean = id.startsWith(PREFIX)

    fun cancel(context: android.content.Context, id: String) {
        runCatching {
            context.startService(
                Intent(context, YouTubeDownloadService::class.java).setAction(YouTubeDownloadService.ACTION_CANCEL)
                    .putExtra(YouTubeDownloadService.EXTRA_ID, id),
            )
        }
    }
}
