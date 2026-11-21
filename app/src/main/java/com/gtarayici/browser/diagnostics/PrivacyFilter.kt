package com.gtarayici.browser.diagnostics

/**
 * Tanılama kaydına ve sorun bildirimlerine giren her metni kişisel bilgilerden arındırır. Amaç, kaydın paylaşıldığında
 * kimseyi tanımlatmamasıdır; sorunu anlamak için gereken kadarı (site alan adı, hata türü) kalır.
 *
 * - Web adresleri: yalnızca şema ve alan adı kalır ("https://ornek.com/…"); yol, sorgu ve parça silinir.
 * - VPN/vekil bağlantıları (vless://, ss://…): sunucu ve kimlik bilgisi tamamen gizlenir.
 * - E-posta, IP adresi, telefon numarası, UUID ve uzun anahtar/belirteç benzeri diziler maskelenir.
 * - Cihazdaki dosya yolları ("/storage/…", "/data/…") gizlenir.
 */
object PrivacyFilter {

    fun mask(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        out = URL.replace(out) { maskUrl(it.value) }
        out = EMAIL.replace(out, "<e-posta>")
        out = UUID.replace(out, "<kimlik>")
        out = IPV6.replace(out, "<ip>")
        out = IPV4.replace(out, "<ip>")
        out = PATH.replace(out, "<yol>")
        out = TOKEN.replace(out, "<gizli>")
        out = PHONE.replace(out) { if (it.value.count(Char::isDigit) >= 10) "<numara>" else it.value }
        return out
    }

    /** Adresi yalnızca alan adına indirger. Kayıtta gösterilecek "site" bilgisi de bununla üretilir. */
    fun maskUrl(url: String): String {
        val scheme = url.substringBefore("://", "").lowercase()
        if (scheme !in WEB_SCHEMES) return "$scheme://<gizli>"
        val rest = url.substringAfter("://")
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@').substringBefore(':').ifBlank { return "$scheme://<gizli>" }
        val maskedHost = if (IPV4.matches(host) || host.startsWith("[")) "<ip>" else host.lowercase()
        val hasMore = rest.length > authority.length && rest.substring(authority.length).trim('/').isNotEmpty()
        return "$scheme://$maskedHost" + if (hasMore) "/…" else ""
    }

    private val WEB_SCHEMES = setOf("http", "https", "ws", "wss")

    private val URL = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]{1,15}://[^\s"'<>]+""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val UUID = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")
    private val IPV4 = Regex("""\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)\b""")
    private val IPV6 = Regex("""\b(?:[0-9a-fA-F]{1,4}:){3,7}[0-9a-fA-F]{1,4}\b""")
    private val PATH = Regex("""(?:/storage|/sdcard|/data/(?:user|data|media))/[^\s"':]+""")
    // "=" yalnızca base64 dolgusu olarak sonda kabul edilir; "anahtar=<değer>" gibi adın kendisi korunur.
    private val TOKEN = Regex("""\b(?=[A-Za-z0-9_\-+/]*\d)(?=[A-Za-z0-9_\-+/]*[A-Za-z])[A-Za-z0-9_\-+/]{32,}={0,2}""")
    private val PHONE = Regex("""\+?\d[\d \-()]{8,}\d""")
}
