package com.gtarayici.browser.search

import java.util.Locale

/**
 * Arama kutuları için harf ve aksan duyarsız eşleştirme. SQLite'ın LIKE'ı yalnızca ASCII harfleri katlar;
 * "ista" ile "İstanbul" eşleşmez. Burada İ/I/ı/i aynı sayılır, Türkçe harfler düz karşılıklarıyla da eşleşir
 * ("turkiye" → "Türkiye", "cubuk" → "çubuğu"); adresler okunur (çözülmüş) biçimiyle aranır.
 */
object TextMatch {
    fun fold(text: String): String {
        val lower = text.lowercase(Locale.ROOT).replace("̇", "")
        val out = StringBuilder(lower.length)
        for (c in lower) {
            out.append(
                when (c) {
                    'ı' -> 'i'
                    'ç' -> 'c'
                    'ğ' -> 'g'
                    'ö' -> 'o'
                    'ş' -> 's'
                    'ü' -> 'u'
                    'â' -> 'a'
                    'î' -> 'i'
                    'û' -> 'u'
                    else -> c
                },
            )
        }
        return out.toString()
    }

    /** [query], [texts]'ten birinde geçiyor mu? */
    fun containsAny(query: String, vararg texts: String): Boolean {
        val q = fold(query.trim())
        if (q.isEmpty()) return true
        val variants = listOfNotNull(q, softened(q))
        return texts.any { text -> val t = fold(text); variants.any { t.contains(it) } }
    }

    /**
     * Türkçede ek alınca sondaki ünsüz yumuşar: "çubuk" → "çubuğu", "kitap" → "kitabı". Katlanmış sorgunun
     * yumuşamış biçimi de aranır (ğ, katlamada g olur).
     */
    private fun softened(folded: String): String? {
        if (folded.length < 3) return null
        val soft = when (folded.last()) {
            'k' -> 'g'
            'p' -> 'b'
            't' -> 'd'
            'c' -> 'c'
            else -> return null
        }
        return if (soft == folded.last()) null else folded.dropLast(1) + soft
    }

    /** [query] başlıkta veya adresin okunur biçiminde geçiyor mu? */
    fun matches(query: String, title: String, url: String): Boolean {
        if (query.isBlank()) return false
        return containsAny(query, title, UrlInput.displayUrl(url))
    }
}
