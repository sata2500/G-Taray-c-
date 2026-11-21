package com.gtarayici.browser.downloads.manager

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.gtarayici.browser.diagnostics.Diagnostics
import com.gtarayici.browser.ui.feedback.HumanErrors
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
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val segmented = SegmentedDownloader(client)
    private val hls = HlsDownloader(client)

    /** Çalışma zamanı bilgisi: kayıt, dosya, parçalar (duraklatınca korunur). */
    private class Runtime(val request: NewDownload) {
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
        val name = DownloadNames.finalName(request.suggestedName, request.url, request.mime, request.kind)
        runtimes[request.id] = Runtime(request).also { it.name = name }
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
        edit(id) { it.copy(status = TaskStatus.RUNNING, error = null) }
        runtime.job = scope.launch(Dispatchers.IO) {
            val meter = launch { measure(id, runtime) }
            try {
                run(id, runtime)
                meter.cancel()
                val (directory, savedName) = runtime.file!!.commit()
                runtime.file = null
                runtimes.remove(id)
                edit(id) { it.copy(status = TaskStatus.COMPLETED, name = savedName, speed = 0, done = runtime.bytes.get(), fraction = null) }
                complete(id, runtime, directory, savedName)
            } catch (e: CancellationException) {
                meter.cancel()
                throw e
            } catch (e: Exception) {
                meter.cancel()
                val detail = HumanErrors.detail(e)
                Diagnostics.log("indirme başarısız: $detail", "indirme")
                edit(id) { it.copy(status = TaskStatus.FAILED, speed = 0, error = detail) }
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
            isStream -> {
                val (_, media) = hls.resolve(request.url, ctx)
                val name = DownloadNames.finalName(
                    runtime.name?.substringBeforeLast('.')?.let { "$it.${if (media.initUrl != null) "mp4" else "ts"}" },
                    request.url,
                    null,
                    MediaKind.VIDEO,
                )
                runtime.name = name
                runtime.mime = if (media.initUrl != null) "video/mp4" else "video/mp2t"
                // HLS kaldığı yerden sürdürülemez; her başlangıçta dosya baştan yazılır.
                runtime.file?.discard()
                val file = PublicFile.create(context, name, runtime.mime).also { runtime.file = it }
                runtime.bytes.set(0)
                edit(id) { it.copy(name = name, connections = 6, fraction = 0f) }
                val count = media.segments.size
                hls.download(media, ctx, file.outputStream()) { done, bytes ->
                    runtime.bytes.set(bytes)
                    edit(id) { it.copy(fraction = done.toFloat() / count) }
                }
            }
            request.response != null && runtime.parts == null && !worthSplitting(runtime) -> {
                val file = runtime.file ?: PublicFile.create(context, runtime.name!!, runtime.mime).also { runtime.file = it }
                file.reset()
                runtime.bytes.set(0)
                request.response.body.useStream { input ->
                    val buffer = ByteArray(64 * 1024)
                    var position = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        file.sink.write(position, buffer, read)
                        position += read
                        runtime.bytes.addAndGet(read.toLong())
                    }
                }
            }
            else -> {
                val probe = runtime.probe ?: segmented.probe(request.url, ctx).also { runtime.probe = it }
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
                    runtime.file = PublicFile.create(context, name, runtime.mime)
                }
                val parts = runtime.parts ?: SegmentPlanner.plan(probe.total, connections(), probe.rangesSupported)
                    .map(::SegmentProgress).also { runtime.parts = it }
                runtime.bytes.set(parts.sumOf { it.done.get() })
                edit(id) { it.copy(name = runtime.name!!, total = probe.total, connections = parts.size) }
                report(id)
                try {
                    segmented.download(probe.finalUrl, ctx, parts, runtime.file!!.sink) { runtime.bytes.addAndGet(it) }
                } catch (e: SegmentedDownloader.RangeNotHonoredException) {
                    // Sunucu parça isteğine uymadı: tek bağlantıyla baştan.
                    val single = listOf(SegmentProgress(Segment(0, 0, probe.total - 1)))
                    runtime.parts = single
                    runtime.bytes.set(0)
                    edit(id) { it.copy(connections = 1) }
                    segmented.download(probe.finalUrl, ctx, single, runtime.file!!.sink) { runtime.bytes.addAndGet(it) }
                }
            }
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
        val probe = runtime.probe ?: runCatching { segmented.probe(request.url, request.context) }.getOrNull() ?: return false
        if (!probe.rangesSupported || probe.total < SegmentPlanner.MIN_SPLIT_BYTES || connections() <= 1) return false
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
            store.dispatch(if (existing == null) DownloadAction.AddDownloadAction(state) else DownloadAction.UpdateDownloadAction(state))
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
        const val MAX_RUNNING = 3
        const val DEFAULT_CONNECTIONS = 8
    }
}
