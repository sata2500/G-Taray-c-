package com.gtarayici.browser.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow

/**
 * Uygulamanın sağlığını izler ve tanılama kaydına yazar:
 *  - Arayüz takılmaları: ana iş parçacığı birkaç saniye yanıt vermezse (o anda ne yaptığıyla birlikte).
 *  - Önceki oturumun nasıl bittiği (Android 11+): çökme, "uygulama yanıt vermiyor", bellek yetersizliği.
 *  - Sayfa süreçlerinin çökmesi.
 */
object HealthWatchers {

    fun start(context: Context, store: BrowserStore, scope: CoroutineScope) {
        MainThreadWatchdog().start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scope.launch(Dispatchers.IO) { logPreviousExits(context) }
        }
        scope.launch {
            store.flow()
                .map { state -> state.tabs.count { it.engineState.crashed } }
                .distinctUntilChanged()
                .collect { crashed -> if (crashed > 0) Diagnostics.log("sayfa süreci çöktü ($crashed sekme etkilendi)", "motor") }
        }
    }

    /** Android'in kaydettiği, son bakıştan bu yana olan olağan dışı çıkışlar. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun logPreviousExits(context: Context) {
        val prefs = context.getSharedPreferences("gtarayici_state", Context.MODE_PRIVATE)
        val since = prefs.getLong(KEY_LAST_EXIT, 0)
        val exits = runCatching {
            context.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
                .orEmpty()
        }.getOrDefault(emptyList())
        exits.filter { it.timestamp > since && it.processName == context.packageName }
            .sortedBy { it.timestamp }
            .forEach { exit ->
                val reason = ExitReasons.describe(exit.reason) ?: return@forEach
                val detail = if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                    runCatching { exit.traceInputStream?.bufferedReader()?.use { ExitReasons.mainThreadFrames(it.readText()) } }
                        .getOrNull()?.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
                } else {
                    exit.description?.take(DESCRIPTION_LIMIT)?.let { " · $it" }.orEmpty()
                }
                Diagnostics.log("önceki oturum: $reason (${ExitReasons.time(exit.timestamp)})$detail", "sağlık")
            }
        exits.maxOfOrNull { it.timestamp }?.let { latest -> prefs.edit { putLong(KEY_LAST_EXIT, maxOf(latest, since)) } }
    }

    private const val KEY_LAST_EXIT = "last_exit_seen"
    private const val MAX_EXITS = 10
    private const val DESCRIPTION_LIMIT = 160
}

/** Çıkış nedenlerinin Türkçe karşılıkları ve ANR izinden ana iş parçacığının özeti (saf, test edilebilir). */
object ExitReasons {
    /** Kaydedilmeye değer nedenler; olağan kapanışlar (kullanıcı kapattı, arka planda sistem kapattı) için null. */
    fun describe(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "uygulama yanıt vermedi (ANR)"
        ApplicationExitInfo.REASON_CRASH -> "uygulama çöktü"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "motor/yerel kod çöktü"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "bellek yetersizliği yüzünden kapatıldı"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "aşırı kaynak kullanımı yüzünden kapatıldı"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "açılışta başlatılamadı"
        else -> null
    }

    /** ANR izinden "main" iş parçacığının ilk birkaç çerçevesi: takılmanın nerede olduğunu gösterir. */
    fun mainThreadFrames(trace: String, limit: Int = 4): String {
        val start = trace.indexOf("\"main\"")
        if (start < 0) return ""
        return trace.substring(start).lineSequence()
            .drop(1)
            .takeWhile { it.isNotBlank() }
            .map { it.trim() }
            .filter { it.startsWith("at ") || it.startsWith("native: ") }
            .map { it.removePrefix("at ").substringBefore("(") }
            .take(limit)
            .joinToString(" ← ")
    }

    fun time(ms: Long): String = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US).format(java.util.Date(ms))
}

/**
 * Ana iş parçacığını her saniye yoklar; [THRESHOLD_MS] boyunca yanıt gelmezse takılmayı ve ana iş parçacığının o anki
 * yığınını (en üstteki birkaç çağrı) kaydeder, kurtulunca toplam süreyi yazar. Hata ayıklayıcı bağlıyken çalışmaz.
 */
private class MainThreadWatchdog : Thread("gt-watchdog") {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var lastTick = SystemClock.uptimeMillis()
    private val tick = Runnable { lastTick = SystemClock.uptimeMillis() }

    init {
        isDaemon = true
        priority = MIN_PRIORITY
    }

    override fun run() {
        var stalledSince = 0L
        while (true) {
            main.post(tick)
            val before = SystemClock.uptimeMillis()
            try {
                sleep(INTERVAL_MS)
            } catch (_: InterruptedException) {
                return
            }
            val now = SystemClock.uptimeMillis()
            // Bu iş parçacığı da geç uyandıysa süreç bütünüyle dondurulmuştur (arka planda sistem dondurucusu);
            // bu bir arayüz takılması değildir.
            if (now - before > INTERVAL_MS * 2 || Debug.isDebuggerConnected()) {
                lastTick = now
                stalledSince = 0L
                continue
            }
            val silent = now - lastTick
            if (silent >= THRESHOLD_MS && stalledSince == 0L) {
                stalledSince = lastTick
                val frames = Looper.getMainLooper().thread.stackTrace.take(STACK_FRAMES)
                    .joinToString(" ← ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
                Diagnostics.log("arayüz ${silent / 1000} sn yanıt vermiyor · $frames", "takılma")
            } else if (silent < INTERVAL_MS * 2 && stalledSince != 0L) {
                Diagnostics.log("arayüz yeniden yanıt veriyor (takılma ~${(lastTick - stalledSince) / 1000} sn)", "takılma")
                stalledSince = 0L
            }
        }
    }

    companion object {
        const val INTERVAL_MS = 1_000L
        const val THRESHOLD_MS = 4_000L
        const val STACK_FRAMES = 5
    }
}
