package com.gtarayici.browser.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hata ayıklama için bellek içi kayıt (yalnızca cihazda, kalıcı değildir).
 * Resim içinde resim gibi cihaza özgü davranışların zaman çizelgesini kullanıcıya gösterir.
 */
object Diagnostics {
    private const val MAX_LINES = 400
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val native = ArrayDeque<String>()
    private val pages = LinkedHashMap<String, List<String>>()

    @Synchronized
    fun log(message: String) {
        native.addLast("${format.format(Date())} $message")
        while (native.size > MAX_LINES) native.removeFirst()
    }

    /** Sayfadan (yardımcı eklenti) gelen kayıt. */
    @Synchronized
    fun pageLog(url: String, top: Boolean, lines: List<String>) {
        pages[(if (top) "" else "[çerçeve] ") + url] = lines
    }

    @Synchronized
    fun clearPages() = pages.clear()

    @Synchronized
    fun dump(): String = buildString {
        appendLine("== Uygulama")
        native.forEach { appendLine(it) }
        pages.forEach { (url, lines) ->
            appendLine()
            appendLine("== Sayfa: $url")
            lines.forEach { appendLine(it) }
        }
    }
}
