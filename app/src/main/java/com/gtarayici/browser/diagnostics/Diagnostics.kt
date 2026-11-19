package com.gtarayici.browser.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sorun bildirimi için bellek içi zaman çizelgesi (yalnızca cihazda tutulur, kalıcı değildir).
 * Resim içinde resim gibi cihaza bağlı davranışların adımlarını kaydeder; Ayarlar'dan paylaşılabilir.
 */
object Diagnostics {
    private const val MAX_LINES = 300
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun log(message: String) {
        lines.addLast("${format.format(Date())} $message")
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")
}
