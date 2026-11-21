package com.gtarayici.browser.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Yakalanmamış istisnaları (çökme) yerel dosyaya yazar; uygulama bir sonraki açılışta kullanıcıya bildirmek isteyip
 * istemediğini sorar. Hiçbir şey otomatik gönderilmez. Yığın izi [PrivacyFilter] ile maskelenir.
 */
class CrashReporter(private val file: File, private val environment: () -> String) {

    /** Varsayılan işleyiciyi sarar: önce rapor yazılır, sonra sistemin işleyicisi (uygulamayı kapatır) çalışır. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    internal fun record(threadName: String, error: Throwable, now: Long = System.currentTimeMillis()) {
        Diagnostics.error("çökme", error, "iş parçacığı $threadName")
        Diagnostics.flush()
        file.parentFile?.mkdirs()
        file.writeText(format(threadName, error, now))
    }

    internal fun format(threadName: String, error: Throwable, now: Long): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val lines = trace.lines().filter { it.isNotBlank() }.take(MAX_TRACE_LINES)
        return buildString {
            appendLine("Zaman: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(now))}")
            appendLine(environment())
            appendLine("İş parçacığı: $threadName")
            appendLine()
            lines.forEach { appendLine(PrivacyFilter.mask(it)) }
        }.trimEnd()
    }

    /** Bildirilmeyi bekleyen çökme raporu (yoksa null). */
    fun pending(): String? = file.takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }?.ifBlank { null }

    /** Rapor bildirildi veya kullanıcı vazgeçti. */
    fun dismiss() {
        file.delete()
    }

    private companion object {
        const val MAX_TRACE_LINES = 60
    }
}
