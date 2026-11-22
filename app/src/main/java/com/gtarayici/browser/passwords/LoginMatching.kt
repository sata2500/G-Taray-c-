package com.gtarayici.browser.passwords

import java.net.URI

/** Site adresleriyle ilgili saf kurallar (birim testli). */
object LoginMatching {
    /** "https://Giris.Example.com:443/yol" → "giris.example.com"; çözülemezse boş. */
    fun hostOf(origin: String): String =
        runCatching { URI(origin.trim()).host?.lowercase()?.removeSuffix(".") }.getOrNull().orEmpty()

    /** Motorun sorduğu alan adına (ör. "example.com") ait mi: kendisi ya da alt alan adı. */
    fun belongsTo(host: String, domain: String): Boolean {
        val d = domain.lowercase().removePrefix(".").removeSuffix(".")
        if (host.isEmpty() || d.isEmpty()) return false
        return host == d || host.endsWith(".$d")
    }

    /** Listede gösterilecek kısa site adı: "www." atılır. */
    fun displayHost(origin: String): String = hostOf(origin).removePrefix("www.").ifEmpty { origin }

    /** Aynı kayıt sayılır mı: aynı köken ve aynı kullanıcı adı (kullanıcı adı yoksa ikisi de boş). */
    fun sameAccount(originA: String, userA: String, originB: String, userB: String): Boolean =
        normalizeOrigin(originA) == normalizeOrigin(originB) && userA == userB

    fun normalizeOrigin(origin: String): String = runCatching {
        val uri = URI(origin.trim())
        val scheme = uri.scheme?.lowercase() ?: return origin.trim()
        val host = uri.host?.lowercase() ?: return origin.trim()
        val port = if (uri.port == -1 || (scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)) "" else ":${uri.port}"
        "$scheme://$host$port"
    }.getOrDefault(origin.trim())
}
