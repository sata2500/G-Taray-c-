package com.gtarayici.browser.search

import androidx.core.net.toUri
import android.net.Uri
import mozilla.components.support.ktx.util.URLStringUtils

/** Gizlilik dostu seçenekler önce gelir; varsayılan DuckDuckGo'dur. */
enum class SearchEngine(val title: String, private val template: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    YANDEX("Yandex", "https://yandex.com.tr/search/?text=%s"),
    ;

    fun searchUrl(query: String): String = template.replace("%s", Uri.encode(query.trim()))
}

object UrlInput {
    /**
     * Kullanıcının yazdığı metni yüklenecek adrese dönüştürür: adres gibi görünüyorsa
     * (şema yoksa HTTPS ile) açılır, değilse seçili arama motorunda aranır.
     */
    fun toLoadableUrl(input: String, engine: SearchEngine): String {
        val text = input.trim()
        if (text.isEmpty()) return "about:blank"
        val lower = text.lowercase()
        return when {
            DIRECT_SCHEMES.any { lower.startsWith(it) } -> text
            text.contains(' ') || !URLStringUtils.isURLLike(text) -> engine.searchUrl(text)
            text.contains("://") -> text
            else -> "https://$text"
        }
    }

    /** Adres çubuğunda gösterilecek sade metin: şema ve "www." gizlenir. */
    fun displayUrl(url: String): String {
        if (url.isBlank() || url == "about:blank") return ""
        val plain = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        // Yol kısmındaki "%C4%B0stanbul" gibi kodlamalar okunur hale getirilir; alan adına dokunulmaz.
        val slash = plain.indexOf('/')
        return if (slash < 0) plain else plain.substring(0, slash) + decodeForDisplay(plain.substring(slash))
    }

    /**
     * Yüzde kodlamalarını yalnızca gösterim için çözer. Anlamı değiştirecek ayraçlar (/ ? # & = % +) ve boşluk,
     * denetim karakterleri kodlu bırakılır; geçersiz UTF-8 dizileri olduğu gibi kalır.
     */
    internal fun decodeForDisplay(text: String): String {
        if ('%' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '%') {
                // Ardışık %XX dizisini baytlara topla.
                val bytes = java.io.ByteArrayOutputStream()
                var j = i
                while (j + 2 < text.length && text[j] == '%' && text[j + 1].isHex() && text[j + 2].isHex()) {
                    bytes.write(text.substring(j + 1, j + 3).toInt(16))
                    j += 3
                }
                if (j == i) {
                    out.append(text[i]); i++; continue
                }
                out.append(decodeRun(text.substring(i, j), bytes.toByteArray()))
                i = j
            } else {
                out.append(text[i]); i++
            }
        }
        return out.toString()
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun decodeRun(encoded: String, bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val decoded = runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }.getOrNull()
            ?: return encoded
        if (decoded.any { it.code <= 0x20 || it.code == 0x7F || it in KEEP_ENCODED || Character.isISOControl(it) ||
                Character.getType(it) == Character.FORMAT.toInt() }
        ) {
            return encoded
        }
        return decoded
    }

    private const val KEEP_ENCODED = "/?#&=%+"

    private val DIRECT_SCHEMES = listOf("about:", "view-source:", "data:", "moz-extension:")

    fun host(url: String): String =
        runCatching { url.toUri().host.orEmpty().removePrefix("www.") }.getOrDefault("")
}
