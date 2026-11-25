package com.gtarayici.browser.downloads.manager

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.gtarayici.browser.diagnostics.Diagnostics
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.action.DownloadAction
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Response

/** Kuyruğa eklenecek indirme. */
data class NewDownload(
    val url: String,
    val suggestedName: String? = null,
    val mime: String? = null,
    val kind: MediaKind = DownloadNames.kind(url, mime),
    val context: RequestContext = RequestContext(),
    /** Mozilla indiricisinin hazır verdiği yanıt (ör. blob:, data: veya POST ile gelen dosya); tek akışla yazılır. */
    val response: Response? = null,
    /** Mozilla indirme kaydının kimliği (varsa aynı kayıt güncellenir). */
    val id: String = UUID.randomUUID().toString(),
)

enum class TaskStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

/** Arayüzün gördüğü indirme durumu. */
data class DownloadTask(
    val id: String,
    val name: String,
    val url: String,
    val kind: MediaKind,
    val status: TaskStatus,
    val total: Long = -1,
    val done: Long = 0,
    /** Bayt/saniye */
    val speed: Long = 0,
    val connections: Int = 1,
    /** HLS gibi boyutu bilinmeyen indirmelerde parça bazlı ilerleme (0..1). */
    val fraction: Float? = null,
    val error: String? = null,
    val private: Boolean = false,
) {
    val progress: Float? get() = fraction ?: if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
    val remainingMs: Long? get() = if (speed > 0 && total > 0) (total - done) * 1000 / speed else null
}

/**
 * G-Tarayıcı'nın indirme yöneticisi: çok bağlantılı ([SegmentedDownloader]) ve HLS ([HlsDownloader]) indirmeler,
 * aynı anda en fazla [MAX_RUNNING] indirme, duraklat/sürdür/iptal, hız ve kalan süre. Her indirme Mozilla'nın
 * indirme kayıtlarına da yazılır; böylece İndirilenler ekranında görünür ve tamamlanınca duyurulur.
 */
class DownloadQueue(
    private val context: Context,
    private val client: Client,
    private val store: BrowserStore,
    private val scope: CoroutineScope,
    private val connections: () -> Int = { DEFAULT_CONNECTIONS },
    /** Gizli sekme indirmeleri isteğe bağlı olarak buraya şifreli kaydedilir. */
    private val vault: com.gtarayici.browser.vault.Vault? = null,
    private val encryptPrivate: () -> Boolean = { false },
    /** VPN yalnızca gizli sekmelerdeyse VPN'in yerel portu: gizli sekme indirmeleri bu porttan geçer. */
    private val privateProxy: () -> Int? = { null },
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /**
     * İsteğin geçeceği ağ yolu. Normalde tarayıcı motoru (site çerezleri, tarayıcı VPN'i). VPN yalnızca gizli
     * sekmelerdeyse motorun bağlantısı VPN'siz olduğundan gizli sekme indirmeleri doğrudan VPN'in yerel vekilinden
     * gider; normal sekme indirmeleri hız için doğrudan bağlanır.
     */
    fun clientFor(private: Boolean): Client {
        val port = privateProxy()
        return if (private && port != null) ProxiedClient(port) else client
    }

    private fun segmentedFor(ctx: RequestContext) = SegmentedDownloader(clientFor(ctx.private))
    private fun hlsFor(ctx: RequestContext) = HlsDownloader(clientFor(ctx.private))

    /** Şifresiz dosya: kasaya gidecekse uygulamanın kendi alanında geçici, değilse telefonun İndirilenler klasöründe. */
    private fun createFile(runtime: Runtime, name: String, mime: String?): PublicFile =
        if (runtime.encrypt && vault != null) PublicFile.createLocal(context, vault.incomingFile(), name) else PublicFile.create(context, name, mime)

    /** Çalışma zamanı bilgisi: kayıt, dosya, parçalar (duraklatınca korunur). */
    private class Runtime(val request: NewDownload, val encrypt: Boolean = false) {
        var job: Job? = null
        var file: PublicFile? = null
        var parts: List<SegmentProgress>? = null
        var probe: Probe? = null
        var name: String? = null
        var mime: String? = request.mime
        val bytes = AtomicLong(0)
    }

    private val runtimes = ConcurrentHashMap<String, Runtime>()

    fun enqueue(request: NewDownload): String {
        // Aynı indirme ikinci kez bildirilirse (ör. etkinlik yeniden oluşturulunca) süren iş bozulmasın.
        if (runtimes[request.id]?.job?.isActive == true) return request.id
        val name = DownloadNames.finalName(request.suggestedName, request.url, request.mime, request.kind)
        val encrypt = request.context.private && vault != null && encryptPrivate()
        runtimes[request.id] = Runtime(request, encrypt).also { it.name = name }
        _tasks.update { list ->
            list.filterNot { it.id == request.id } +
                DownloadTask(request.id, name, request.url, request.kind, TaskStatus.QUEUED, private = request.context.private)
        }
        report(request.id)
        schedule()
        return request.id
    }

    fun pause(id: String) {
        val runtime = runtimes[id] ?: return
        runtime.job?.cancel()
        runtime.job = null
        edit(id) { it.copy(status = TaskStatus.PAUSED, speed = 0) }
        report(id)
        schedule()
    }

    fun resume(id: String) {
        if (runtimes[id] == null) return
        edit(id) { it.copy(status = TaskStatus.QUEUED, error = null) }
        report(id)
        schedule()
    }

    /** Hatalı indirmeyi baştan dener. */
    fun retry(id: String) {
        val runtime = runtimes[id] ?: return
        runtime.file?.discard()
        runtime.file = null
        runtime.parts = null
        runtime.probe = null
        runtime.bytes.set(0)
        edit(id) { it.copy(status = TaskStatus.QUEUED, done = 0, error = null, fraction = null) }
        schedule()
    }

    fun cancel(id: String) {
        val runtime = runtimes.remove(id) ?: return
        runtime.job?.cancel()
        runtime.file?.discard()
        _tasks.update { list -> list.filterNot { it.id == id } }
        store.dispatch(DownloadAction.RemoveDownloadAction(id))
        schedule()
    }

    /** Bu indirme bu yöneticiye mi ait (duraklat/sürdür düğmeleri için)? */
    fun owns(id: String): Boolean = runtimes.containsKey(id)

    fun task(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    @Synchronized
    private fun schedule() {
        val running = _tasks.value.count { it.status == TaskStatus.RUNNING }
        val waiting = _tasks.value.filter { it.status == TaskStatus.QUEUED }
        waiting.take((MAX_RUNNING - running).coerceAtLeast(0)).forEach { start(it.id) }
        val active = _tasks.value.any { it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED }
        if (active) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ManagedDownloadService::class.java)) }
        }
    }

    private fun start(id: String) {
        val runtime = runtimes[id] ?: return
        // Aynı iş iki kez başlatılırsa iki iş parçacığı aynı dosyaya yazar ve dosya bozulur.
        if (runtime.job?.isActive == true) return
        edit(id) { it.copy(status = TaskStatus.RUNNING, error = null) }
        runtime.job = scope.launch(Dispatchers.IO) {
            val meter = launch { measure(id, runtime) }
            try {
                run(id, runtime)
                meter.cancel()
                val file = runtime.file!!
                var (directory, savedName) = file.commit()
                runtime.file = null
                // Gizli kasaya: şifrelenir, şifresiz geçici dosya silinir.
                if (runtime.encrypt && vault != null) {
                    val item = vault.import(file.localFile!!, runtime.name ?: savedName, runtime.mime)
                    directory = com.gtarayici.browser.vault.Vault.DOWNLOAD_PREFIX + item.id
                    savedName = item.name
                }
                runtimes.remove(id)
                edit(id) { it.copy(status = TaskStatus.COMPLETED, name = savedName, speed = 0, done = runtime.bytes.get(), fraction = null) }
                complete(id, runtime, directory, savedName)
            } catch (e: CancellationException) {
                meter.cancel()
                throw e
            } catch (e: Exception) {
                meter.cancel()
                Diagnostics.log("indirme başarısız: ${DownloadErrors.code(e)}", "indirme")
                edit(id) { it.copy(status = TaskStatus.FAILED, speed = 0, error = DownloadErrors.reason(context, e)) }
                report(id)
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) { schedule() }
            }
        }
    }

    private suspend fun run(id: String, runtime: Runtime) {
        val request = runtime.request
        val ctx = request.context
        val isStream = request.kind == MediaKind.STREAM || DownloadNames.extension(request.url) == "m3u8" ||
            request.mime?.contains("mpegurl", ignoreCase = true) == true
        when {
            isStream -> downloadStream(id, runtime)
            request.response != null && runtime.parts == null && !worthSplitting(runtime) -> {
                val file = runtime.file ?: createFile(runtime, runtime.name!!, runtime.mime).also { runtime.file = it }
                file.reset()
                runtime.bytes.set(0)
                Diagnostics.log("hazır bağlantıdan yazılıyor (${request.mime ?: "tür bilinmiyor"})", "indirme")
                val expectPdf = request.mime?.startsWith("application/pdf") == true
                request.response.body.useStream { input ->
                    val buffer = ByteArray(64 * 1024)
                    var position = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (position == 0L && expectPdf && !looksLikePdf(buffer, read)) {
                            throw IllegalStateException("PDF yerine başka bir içerik geldi")
                        }
                        file.sink.write(position, buffer, read)
                        position += read
                        runtime.bytes.addAndGet(read.toLong())
                    }
                }
            }
            else -> {
                val probe = runtime.probe ?: segmentedFor(ctx).probe(request.url, ctx).also { runtime.probe = it }
                // Adres beklenen dosya yerine bir web sayfası döndürüyorsa (ör. süresi dolmuş bağlantı, oturum
                // gerektiren dosya, "PDF olarak kaydet") sayfanın HTML'i dosya diye kaydedilmez.
                if (servesPageInsteadOf(request.mime, probe.mime)) {
                    throw PageInsteadOfFileException()
                }
                Diagnostics.log("adresten indiriliyor (${probe.mime ?: "tür bilinmiyor"})", "indirme")
                if (runtime.file == null) {
                    // Sunucunun önerdiği ad yalnızca sayfadan ad gelmediyse kullanılır.
                    val name = DownloadNames.finalName(
                        request.suggestedName ?: probe.fileName,
                        probe.finalUrl,
                        probe.mime ?: request.mime,
                        request.kind,
                    )
                    runtime.name = name
                    runtime.mime = probe.mime ?: request.mime
                    runtime.file = createFile(runtime, name, runtime.mime)
                }
                // Bağlantı başına birkaç parça: hızlı biten bağlantı sıradaki parçayı alır, yavaş bir bağlantı dosyanın
                // sonunu tek başına beklemez.
                val parallel = connections()
                val parts = runtime.parts ?: SegmentPlanner.plan(probe.total, parallel, probe.rangesSupported, PIECES_PER_CONNECTION)
                    .map(::SegmentProgress).also { runtime.parts = it }
                runtime.bytes.set(parts.sumOf { it.done.get() })
                edit(id) { it.copy(name = runtime.name!!, total = probe.total, connections = minOf(parts.size, parallel)) }
                report(id)
                try {
                    segmentedFor(ctx).download(probe.finalUrl, ctx, parts, runtime.file!!.sink, onBytes = { runtime.bytes.addAndGet(it) }, parallel = parallel)
                } catch (e: SegmentedDownloader.RangeNotHonoredException) {
                    // Sunucu parça isteğine uymadı: tek bağlantıyla baştan.
                    val single = listOf(SegmentProgress(Segment(0, 0, probe.total - 1)))
                    runtime.parts = single
                    runtime.bytes.set(0)
                    edit(id) { it.copy(connections = 1) }
                    segmentedFor(ctx).download(probe.finalUrl, ctx, single, runtime.file!!.sink, onBytes = { runtime.bytes.addAndGet(it) })
                }
            }
        }
    }

    /**
     * HLS akışı: parçalar sırayla tek dosyaya yazılır. Ses ayrı bir listedeyse (birçok sitede kaliteler sessiz gelir,
     * ses ayrı akar) ikisi de indirilip yeniden kodlamadan tek bir MP4'te birleştirilir.
     */
    private suspend fun downloadStream(id: String, runtime: Runtime) {
        val request = runtime.request
        val ctx = request.context
        val hls = hlsFor(ctx)
        val resolved = hls.resolve(request.url, ctx)
        val media = resolved.media
        val parallel = connections().coerceIn(4, 12)
        val baseName = runtime.name?.substringBeforeLast('.')
        fun nameFor(ext: String) = DownloadNames.finalName(baseName?.let { "$it.$ext" }, request.url, null, MediaKind.VIDEO)
        val videoExt = if (media.initUrl != null) "mp4" else "ts"
        // HLS kaldığı yerden sürdürülemez; her başlangıçta dosya baştan yazılır.
        runtime.file?.discard()
        runtime.file = null
        runtime.bytes.set(0)
        edit(id) { it.copy(connections = parallel, fraction = 0f) }
        val audio = resolved.audio
        if (audio == null) {
            val name = nameFor(videoExt)
            runtime.name = name
            runtime.mime = if (media.initUrl != null) "video/mp4" else "video/mp2t"
            val file = createFile(runtime, name, runtime.mime).also { runtime.file = it }
            edit(id) { it.copy(name = name) }
            val count = media.segments.size
            hls.download(media, ctx, file.outputStream(), parallel = parallel) { done, bytes ->
                runtime.bytes.set(bytes)
                edit(id) { it.copy(fraction = done.toFloat() / count) }
            }
            return
        }
        val work = java.io.File(context.cacheDir, "hls-$id").apply { deleteRecursively(); mkdirs() }
        try {
            val videoFile = java.io.File(work, "video.$videoExt")
            val audioFile = java.io.File(work, "audio.${if (audio.initUrl != null) "mp4" else "ts"}")
            val count = media.segments.size + audio.segments.size
            var videoBytes = 0L
            videoFile.outputStream().buffered().use { out ->
                hls.download(media, ctx, out, parallel = parallel) { done, bytes ->
                    videoBytes = bytes
                    runtime.bytes.set(bytes)
                    edit(id) { it.copy(fraction = done.toFloat() / count) }
                }
            }
            audioFile.outputStream().buffered().use { out ->
                hls.download(audio, ctx, out, parallel = parallel) { done, bytes ->
                    runtime.bytes.set(videoBytes + bytes)
                    edit(id) { it.copy(fraction = (media.segments.size + done).toFloat() / count) }
                }
            }
            val merged = java.io.File(work, "out.mp4")
            val (source, ext) = runCatching {
                com.gtarayici.browser.media.TrackMuxer.mux(videoFile, audioFile, merged)
                merged to "mp4"
            }.getOrElse {
                // Birleştirilemedi: en azından görüntü kaydedilir.
                Diagnostics.log("ses ve görüntü birleştirilemedi; yalnızca görüntü kaydedildi", "indirme")
                videoFile to videoExt
            }
            val name = nameFor(ext)
            runtime.name = name
            runtime.mime = if (ext == "mp4") "video/mp4" else "video/mp2t"
            val file = createFile(runtime, name, runtime.mime).also { runtime.file = it }
            edit(id) { it.copy(name = name) }
            // Akış kapatılmaz: dosya kanalı tamamlama (commit) için açık kalmalı.
            val out = file.outputStream()
            source.inputStream().use { it.copyTo(out, 256 * 1024) }
            out.flush()
            runtime.bytes.set(source.length())
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * Motor dosyayı hazır bir bağlantıyla verdiyse: sunucu parçalı indirmeyi destekliyor ve dosya büyükse hazır
     * bağlantı kapatılıp çok bağlantılı indirmeye geçilir. Aksi halde (tek seferlik adres, POST, blob: vb.)
     * hazır bağlantı kullanılır.
     */
    private suspend fun worthSplitting(runtime: Runtime): Boolean {
        val request = runtime.request
        if (!request.url.startsWith("http")) return false
        val probe = runtime.probe ?: runCatching { segmentedFor(request.context).probe(request.url, request.context) }.getOrNull() ?: return false
        if (!shouldSplit(request.mime, probe, connections())) return false
        runtime.probe = probe
        runCatching { request.response?.close() }
        return true
    }

    /** Her saniye hız ve ilerlemeyi günceller (hız son 3 sn'nin ortalaması). */
    private suspend fun measure(id: String, runtime: Runtime) {
        val samples = ArrayDeque<Long>()
        while (coroutineScopeActive()) {
            delay(1_000)
            val now = runtime.bytes.get()
            samples.addLast(now)
            if (samples.size > 4) samples.removeFirst()
            val speed = if (samples.size >= 2) (samples.last() - samples.first()) / (samples.size - 1) else 0
            edit(id) { it.copy(done = now, speed = speed.coerceAtLeast(0)) }
            report(id)
        }
    }

    private suspend fun coroutineScopeActive(): Boolean = kotlin.coroutines.coroutineContext.isActive

    private fun complete(id: String, runtime: Runtime, directory: String, savedName: String) {
        val size = runtime.bytes.get()
        scope.launch(Dispatchers.Main) {
            store.dispatch(
                DownloadAction.UpdateDownloadAction(
                    base(id).copy(
                        fileName = savedName,
                        contentType = runtime.mime,
                        contentLength = size,
                        currentBytesCopied = size,
                        status = DownloadState.Status.COMPLETED,
                        directoryPath = directory,
                    ),
                ),
            )
        }
    }

    /** İlerlemeyi Mozilla indirme kaydına yansıtır (İndirilenler ekranı bunu gösterir). */
    private fun report(id: String) {
        val task = task(id) ?: return
        val status = when (task.status) {
            TaskStatus.QUEUED, TaskStatus.RUNNING -> DownloadState.Status.DOWNLOADING
            TaskStatus.PAUSED -> DownloadState.Status.PAUSED
            TaskStatus.COMPLETED -> DownloadState.Status.COMPLETED
            TaskStatus.FAILED -> DownloadState.Status.FAILED
        }
        scope.launch(Dispatchers.Main) {
            val existing = store.state.downloads[id]
            val state = (existing ?: base(id)).copy(
                fileName = task.name,
                contentLength = task.total.takeIf { it > 0 },
                currentBytesCopied = task.done,
                status = status,
            )
            if (existing == null) {
                // Mozilla'nın indirme ara katmanı "yeni indirme" eklenince kendi indirme hizmetini başlatıp aynı dosyayı
                // ikinci kez indiriyordu (her indirme iki kopya). Hizmet bitmiş/başarısız kayıtlar için başlatılmadığından
                // kayıt önce bu durumla eklenir, hemen ardından gerçek durumuyla güncellenir.
                store.dispatch(DownloadAction.AddDownloadAction(state.copy(status = DownloadState.Status.FAILED)))
                if (status != DownloadState.Status.FAILED) store.dispatch(DownloadAction.UpdateDownloadAction(state))
            } else {
                store.dispatch(DownloadAction.UpdateDownloadAction(state))
            }
        }
    }

    private fun base(id: String): DownloadState {
        val runtime = runtimes[id]
        val existing = store.state.downloads[id]
        return existing ?: DownloadState(
            id = id,
            url = runtime?.request?.url ?: task(id)?.url ?: "",
            fileName = runtime?.name,
            contentType = runtime?.mime,
            private = runtime?.request?.context?.private ?: false,
            referrerUrl = runtime?.request?.context?.referer,
            userAgent = runtime?.request?.context?.userAgent,
            skipConfirmation = true,
        )
    }

    private fun edit(id: String, change: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    /** Uygulama yeniden açıldığında yarım kalmış eski kayıtlar "başarısız" sayılır; "Yeniden dene" ile baştan alınır. */
    fun markInterrupted() {
        store.state.downloads.values
            .filter { (it.status == DownloadState.Status.DOWNLOADING || it.status == DownloadState.Status.PAUSED) && !owns(it.id) }
            .forEach { store.dispatch(DownloadAction.UpdateDownloadAction(it.copy(status = DownloadState.Status.FAILED))) }
    }

    /** Mozilla kaydından yeniden başlatır (İndirilenler → "Yeniden dene"). */
    fun retryFromState(state: DownloadState) {
        if (owns(state.id)) {
            retry(state.id)
            return
        }
        enqueue(
            NewDownload(
                url = state.url,
                suggestedName = state.fileName,
                mime = state.contentType,
                context = RequestContext(userAgent = state.userAgent, referer = state.referrerUrl, private = state.private),
                id = state.id,
            ),
        )
    }

    companion object {
        /**
         * Hazır bağlantı bırakılıp adresten yeniden (çok parçalı) indirilsin mi? Adresin verdiği dosya, motorun verdiği
         * dosyayla aynı türde olmalı: "PDF olarak kaydet"te adres sayfanın kendisidir ve HTML döner; o zaman yeniden
         * indirmek PDF yerine sayfanın HTML'ini kaydeder (0.14–0.15'te PDF'ler bu yüzden bozuk açılıyordu).
         */
        internal fun looksLikePdf(buffer: ByteArray, length: Int): Boolean =
            length >= 5 && String(buffer, 0, 5, Charsets.US_ASCII) == "%PDF-"

        /** İstenen dosya HTML değilken adres HTML döndürüyor mu? */
        internal fun servesPageInsteadOf(requestMime: String?, servedMime: String?): Boolean {
            val wanted = requestMime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val served = servedMime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            if (wanted.isEmpty() || wanted == "application/octet-stream" || wanted.contains("html")) return false
            return served == "text/html" || served == "application/xhtml+xml"
        }

        internal fun shouldSplit(requestMime: String?, probe: Probe, connections: Int): Boolean {
            if (!probe.rangesSupported || probe.total < SegmentPlanner.MIN_SPLIT_BYTES || connections <= 1) return false
            if (requestMime.isNullOrBlank() || requestMime == "application/octet-stream") return true
            val wanted = requestMime.substringBefore(';').trim().lowercase()
            val served = probe.mime?.substringBefore(';')?.trim()?.lowercase() ?: return false
            return wanted == served
        }
        const val MAX_RUNNING = 3
        const val PIECES_PER_CONNECTION = 3
        const val DEFAULT_CONNECTIONS = 8
    }
}
