package com.gtarayici.browser.ui.feedback

import androidx.annotation.StringRes
import com.gtarayici.browser.R
import com.gtarayici.browser.vpn.OpenVpnConfig
import com.gtarayici.browser.vpn.ShareLinkParser
import com.gtarayici.browser.vpn.splitVpnInput
import java.net.URI

/**
 * Alanların yazarken denetimi. Her işlev geçerliyse null, değilse alanın altında gösterilecek cümleyi döner.
 * Boş alan hata sayılmaz (kullanıcı henüz yazmadı); kaydet düğmesi ayrıca boşken kapalıdır.
 */
object InputValidation {

    /** DNS-over-HTTPS adresi: https:// ile başlamalı, geçerli bir sunucu adı içermeli. */
    @StringRes
    fun dohUrl(value: String): Int? {
        val text = value.trim()
        if (text.isEmpty()) return null
        if (text.any(Char::isWhitespace)) return R.string.validation_no_spaces
        if (!text.startsWith("https://", ignoreCase = true)) return R.string.validation_doh_https
        val host = runCatching { URI(text).host }.getOrNull()
        if (host.isNullOrBlank() || !host.contains('.')) return R.string.validation_doh_host
        return null
    }

    /** Yer imi adresi: boşluk içermemeli; şeması yoksa bir alan adına benzemeli. */
    @StringRes
    fun bookmarkUrl(value: String): Int? {
        val text = value.trim()
        if (text.isEmpty()) return R.string.validation_url_empty
        if (text.any(Char::isWhitespace)) return R.string.validation_no_spaces
        if (text.startsWith("about:", ignoreCase = true)) return null
        val withScheme = if (SCHEME.containsMatchIn(text)) text else "https://$text"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return R.string.validation_url_invalid
        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            val host = uri.host
            if (host.isNullOrBlank() || (!host.contains('.') && host != "localhost")) return R.string.validation_url_invalid
        }
        return null
    }

    /** Şeması olmayan yer imi adresine https:// ekler (ör. "ornek.com" → "https://ornek.com"). */
    fun normalizeBookmarkUrl(value: String): String {
        val text = value.trim()
        return if (SCHEME.containsMatchIn(text) || text.startsWith("about:", true)) text else "https://$text"
    }

    /** VPN sunucusu bağlantısı veya yapılandırması; bütün satırlar ayrıştırılabilmeli. */
    @StringRes
    fun vpnLink(value: String): Int? {
        val text = value.trim()
        if (text.isEmpty() || OpenVpnConfig.looksLike(text)) return null
        val entries = splitVpnInput(text)
        if (entries.isEmpty()) return R.string.validation_vpn_unknown
        val failed = entries.count { runCatching { ShareLinkParser.parse(it) }.isFailure }
        return when {
            failed == 0 -> null
            entries.all { entry -> KNOWN_VPN_PREFIXES.none { entry.startsWith(it, ignoreCase = true) } } ->
                R.string.validation_vpn_unknown
            else -> R.string.validation_vpn_broken
        }
    }

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val KNOWN_VPN_PREFIXES = listOf(
        "vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://", "tuic://", "socks", "http://", "https://",
        "{", "[interface]",
    )
}
