package com.gtarayici.browser.vpn

/**
 * Yapıştırılan metni tek tek sunucu bağlantılarına ayırır: JSON ve WireGuard yapılandırması tek parça kalır,
 * abonelik içerikleri (düz veya base64, satır satır bağlantılar) bölünür.
 */
internal fun splitVpnInput(input: String): List<String> {
    val text = input.trim()
    if (text.startsWith("{") || text.contains("[Interface]", ignoreCase = true)) return listOf(text)
    val decoded = if (!text.contains("://")) {
        runCatching { String(java.util.Base64.getMimeDecoder().decode(text)) }.getOrDefault(text)
    } else {
        text
    }
    return decoded.lines().map(String::trim).filter { it.contains("://") && !it.startsWith("#") }
}
