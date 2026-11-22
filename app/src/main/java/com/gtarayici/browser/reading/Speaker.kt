package com.gtarayici.browser.reading

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gtarayici.browser.diagnostics.Diagnostics
import java.util.Locale

/**
 * Makaleyi telefonun metin okuma motoruyla (Android TextToSpeech) sesli okur. Bloklar sırayla sıraya alınır;
 * duraklatınca kalınan bloktan devam edilir. Ses cihazda üretilir, metin hiçbir sunucuya gitmez
 * (telefonun seçili ses motoru çevrimdışı çalışıyorsa).
 */
class Speaker(private val context: Context) {
    var title by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set
    var count by mutableIntStateOf(0)
        private set
    var rate by mutableFloatStateOf(1f)
        private set
    var unavailable by mutableStateOf(false)
        private set

    /** Okuma açık mı (çubuk gösterilsin mi). */
    val active: Boolean get() = title != null

    private var tts: TextToSpeech? = null
    private var ready = false
    private var chunks: List<String> = emptyList()
    private var language: String = ""

    fun start(article: Article, from: Int = 0) {
        title = article.title
        language = article.lang
        chunks = listOf(article.title) + article.blocks.map { it.text }.flatMap(::split)
        count = chunks.size
        index = from.coerceIn(0, (count - 1).coerceAtLeast(0))
        unavailable = false
        ensureEngine { play() }
    }

    fun toggle() = if (playing) pause() else play()

    fun pause() {
        playing = false
        tts?.stop()
    }

    fun play() {
        val engine = tts ?: return
        if (!ready || chunks.isEmpty()) return
        playing = true
        engine.setSpeechRate(rate)
        engine.stop()
        // Sıradaki birkaç blok birlikte sıraya alınır; her biri bitince [index] ilerler.
        for (i in index until chunks.size) {
            engine.speak(chunks[i], if (i == index) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), "gt-$i")
        }
    }

    fun skip(delta: Int) {
        index = (index + delta).coerceIn(0, (count - 1).coerceAtLeast(0))
        if (playing) play()
    }

    fun setSpeed(value: Float) {
        rate = value
        if (playing) play()
    }

    fun close() {
        playing = false
        tts?.stop()
        title = null
        chunks = emptyList()
    }

    /** Uygulama kapanırken motor bırakılır. */
    fun release() {
        close()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun ensureEngine(onReady: () -> Unit) {
        if (tts != null && ready) return applyLanguage().also { onReady() }
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                unavailable = true
                Diagnostics.log("metin okuma motoru başlatılamadı ($status)", "sesli okuma")
                return@TextToSpeech
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.removePrefix("gt-")?.toIntOrNull()?.let { index = it }
                }

                override fun onDone(utteranceId: String?) {
                    val i = utteranceId?.removePrefix("gt-")?.toIntOrNull() ?: return
                    if (i >= chunks.size - 1) {
                        playing = false
                        index = 0
                    }
                }

                @Deprecated("Eski API")
                override fun onError(utteranceId: String?) {
                    playing = false
                }
            })
            applyLanguage()
            onReady()
        }
    }

    private fun applyLanguage() {
        val engine = tts ?: return
        val locale = language.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) } ?: Locale.forLanguageTag("tr-TR")
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) engine.setLanguage(Locale.getDefault())
    }

    companion object {
        /** Okuma motorlarının sınırı (~4000 karakter) aşılmasın diye uzun paragraflar cümle sonlarından bölünür. */
        fun split(text: String, max: Int = 1_000): List<String> {
            if (text.length <= max) return listOf(text)
            val out = mutableListOf<String>()
            var rest = text
            while (rest.length > max) {
                val cut = rest.lastIndexOfAny(charArrayOf('.', '!', '?', ';', ','), max).takeIf { it > max / 3 } ?: rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
                out += rest.substring(0, cut + 1).trim()
                rest = rest.substring(cut + 1)
            }
            if (rest.isNotBlank()) out += rest.trim()
            return out
        }
    }
}
