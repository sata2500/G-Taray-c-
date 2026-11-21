package com.gtarayici.browser.vpn

import java.util.Locale

/** VPN bilgilerinin kullanıcıya gösterilen, teknik olmayan biçimleri. */
object VpnLabels {
    /** "vless" → "VLESS", "socks" → "SOCKS5 vekil sunucu" gibi okunur protokol adı. */
    fun protocol(protocol: String): String = when (protocol.lowercase(Locale.ROOT)) {
        "vless" -> "VLESS"
        "vmess" -> "VMess"
        "trojan" -> "Trojan"
        "shadowsocks", "ss" -> "Shadowsocks"
        "hysteria2", "hy2" -> "Hysteria 2"
        "tuic" -> "TUIC"
        "wireguard" -> "WireGuard"
        "socks" -> "SOCKS5 vekil sunucu"
        "http" -> "HTTP vekil sunucu"
        "openvpn" -> "OpenVPN"
        else -> protocol
    }

    /** ISO ülke kodundan bayrak emojisi ("DE" → 🇩🇪); geçersizse boş. */
    fun flag(countryCode: String?): String {
        val code = countryCode?.trim()?.uppercase(Locale.ROOT) ?: return ""
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
        return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }

    /** ISO ülke kodundan Türkçe ülke adı ("DE" → "Almanya"). */
    fun country(countryCode: String?): String? {
        val code = countryCode?.trim()?.takeIf { it.length == 2 } ?: return null
        return Locale("", code.uppercase(Locale.ROOT)).getDisplayCountry(Locale.forLanguageTag("tr")).takeIf { it.isNotBlank() && it != code }
    }
}
