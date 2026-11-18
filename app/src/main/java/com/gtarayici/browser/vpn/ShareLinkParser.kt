package com.gtarayici.browser.vpn

import java.net.URI
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * VPN/vekil paylaşım bağlantılarını sing-box "outbound" (veya WireGuard "endpoint") JSON'una çevirir.
 *
 * Desteklenenler: vless://, vmess://, trojan://, ss://, hysteria2:// (hy2://), tuic://,
 * WireGuard yapılandırma dosyası metni ve doğrudan sing-box outbound JSON'u.
 */
object ShareLinkParser {
    data class Parsed(val name: String, val protocol: String, val json: JSONObject, val isEndpoint: Boolean = false)

    class ParseException(message: String) : Exception(message)

    fun parse(input: String): Parsed {
        val text = input.trim()
        return when {
            text.startsWith("{") -> parseJson(text)
            text.contains("[Interface]", ignoreCase = true) -> parseWireGuard(text)
            text.startsWith("vless://", true) -> parseVless(text)
            text.startsWith("vmess://", true) -> parseVmess(text)
            text.startsWith("trojan://", true) -> parseTrojan(text)
            text.startsWith("ss://", true) -> parseShadowsocks(text)
            text.startsWith("hysteria2://", true) || text.startsWith("hy2://", true) -> parseHysteria2(text)
            text.startsWith("tuic://", true) -> parseTuic(text)
            Regex("^socks[45h]?://", RegexOption.IGNORE_CASE).containsMatchIn(text) -> parseSocks(text)
            text.startsWith("http://", true) || text.startsWith("https://", true) -> parseHttpProxy(text)
            else -> throw ParseException(
                "Tanınmayan biçim. IKEv2/IPsec (strongSwan), OpenVPN, L2TP ve PPTP şu an desteklenmiyor.",
            )
        }
    }

    // --- Ortak yardımcılar -----------------------------------------------------------------

    private class Link(raw: String) {
        val uri: URI
        val name: String
        val query: Map<String, String>

        init {
            val hashIndex = raw.indexOf('#')
            name = if (hashIndex >= 0) decode(raw.substring(hashIndex + 1)) else ""
            val withoutName = if (hashIndex >= 0) raw.substring(0, hashIndex) else raw
            uri = runCatching { URI(withoutName) }.getOrElse { throw ParseException("Geçersiz bağlantı") }
            query = uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
                val (k, v) = it.split('=', limit = 2)
                decode(k) to decode(v)
            }
        }

        val host: String get() = uri.host?.removePrefix("[")?.removeSuffix("]") ?: throw ParseException("Sunucu adresi yok")
        val port: Int get() = uri.port.takeIf { it > 0 } ?: 443
        val user: String get() = decode(uri.rawUserInfo ?: throw ParseException("Kimlik bilgisi yok"))

        fun q(vararg keys: String): String? = keys.firstNotNullOfOrNull { query[it]?.takeIf(String::isNotBlank) }
    }

    private fun decode(value: String) = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun base64(value: String): String {
        val clean = value.trim().replace('-', '+').replace('_', '/').replace("\n", "")
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return String(java.util.Base64.getDecoder().decode(padded))
    }

    private fun tls(
        serverName: String?,
        insecure: Boolean,
        alpn: String?,
        fingerprint: String?,
        realityKey: String? = null,
        realityShortId: String? = null,
    ) = JSONObject().apply {
        put("enabled", true)
        serverName?.let { put("server_name", it) }
        if (insecure) put("insecure", true)
        alpn?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.takeIf { it.isNotEmpty() }?.let {
            put("alpn", JSONArray(it))
        }
        val fp = fingerprint ?: if (realityKey != null) "chrome" else null
        if (fp != null) put("utls", JSONObject().put("enabled", true).put("fingerprint", fp))
        if (realityKey != null) {
            put("reality", JSONObject().put("enabled", true).put("public_key", realityKey).put("short_id", realityShortId.orEmpty()))
        }
    }

    private fun transport(type: String?, host: String?, path: String?, serviceName: String?): JSONObject? =
        when (type?.lowercase()) {
            null, "", "tcp", "raw" -> null
            "ws", "websocket" -> JSONObject().put("type", "ws").apply {
                path?.let { put("path", it) }
                host?.let { put("headers", JSONObject().put("Host", it)) }
            }
            "grpc" -> JSONObject().put("type", "grpc").put("service_name", serviceName ?: path.orEmpty())
            "http", "h2" -> JSONObject().put("type", "http").apply {
                path?.let { put("path", it) }
                host?.let { put("host", JSONArray(it.split(','))) }
            }
            "httpupgrade" -> JSONObject().put("type", "httpupgrade").apply {
                path?.let { put("path", it) }
                host?.let { put("host", it) }
            }
            else -> throw ParseException("Desteklenmeyen aktarım türü: $type")
        }

    private fun isTrue(value: String?) = value == "1" || value.equals("true", true)

    // --- Protokoller ------------------------------------------------------------------------

    private fun parseVless(raw: String): Parsed {
        val link = Link(raw)
        val json = JSONObject()
            .put("type", "vless")
            .put("server", link.host)
            .put("server_port", link.port)
            .put("uuid", link.user)
        link.q("flow")?.let { json.put("flow", it) }
        val security = link.q("security")
        if (security == "tls" || security == "reality" || security == "xtls") {
            json.put(
                "tls",
                tls(
                    serverName = link.q("sni", "peer") ?: link.host,
                    insecure = isTrue(link.q("allowInsecure", "insecure")),
                    alpn = link.q("alpn"),
                    fingerprint = link.q("fp"),
                    realityKey = if (security == "reality") link.q("pbk") else null,
                    realityShortId = link.q("sid"),
                ),
            )
        }
        transport(link.q("type"), link.q("host"), link.q("path"), link.q("serviceName"))?.let { json.put("transport", it) }
        return Parsed(link.name.ifBlank { link.host }, "VLESS", json)
    }

    private fun parseVmess(raw: String): Parsed {
        val data = runCatching { JSONObject(base64(raw.substringAfter("://").substringBefore('#'))) }
            .getOrElse { throw ParseException("VMess bağlantısı çözülemedi") }
        val server = data.optString("add").ifBlank { throw ParseException("Sunucu adresi yok") }
        val json = JSONObject()
            .put("type", "vmess")
            .put("server", server)
            .put("server_port", data.optString("port").toIntOrNull() ?: 443)
            .put("uuid", data.optString("id"))
            .put("alter_id", data.optString("aid").toIntOrNull() ?: 0)
            .put("security", data.optString("scy").ifBlank { "auto" })
        if (data.optString("tls") == "tls") {
            json.put(
                "tls",
                tls(
                    serverName = data.optString("sni").ifBlank { data.optString("host").ifBlank { server } },
                    insecure = false,
                    alpn = data.optString("alpn").ifBlank { null },
                    fingerprint = data.optString("fp").ifBlank { null },
                ),
            )
        }
        val net = data.optString("net").ifBlank { null }
        transport(net, data.optString("host").ifBlank { null }, data.optString("path").ifBlank { null }, data.optString("path").ifBlank { null })
            ?.let { json.put("transport", it) }
        return Parsed(data.optString("ps").ifBlank { server }, "VMess", json)
    }

    private fun parseTrojan(raw: String): Parsed {
        val link = Link(raw)
        val json = JSONObject()
            .put("type", "trojan")
            .put("server", link.host)
            .put("server_port", link.port)
            .put("password", link.user)
        val security = link.q("security") ?: "tls"
        if (security != "none") {
            json.put(
                "tls",
                tls(
                    serverName = link.q("sni", "peer") ?: link.host,
                    insecure = isTrue(link.q("allowInsecure", "insecure")),
                    alpn = link.q("alpn"),
                    fingerprint = link.q("fp"),
                    realityKey = if (security == "reality") link.q("pbk") else null,
                    realityShortId = link.q("sid"),
                ),
            )
        }
        transport(link.q("type"), link.q("host"), link.q("path"), link.q("serviceName"))?.let { json.put("transport", it) }
        return Parsed(link.name.ifBlank { link.host }, "Trojan", json)
    }

    private fun parseShadowsocks(raw: String): Parsed {
        val name = raw.substringAfter('#', "").let(::decode)
        var body = raw.substringAfter("://").substringBefore('#').substringBefore('?').trimEnd('/')
        if (!body.contains('@')) body = base64(body) // Eski biçim: ss://base64(method:pass@host:port)
        val userInfo = body.substringBeforeLast('@')
        val hostPort = body.substringAfterLast('@')
        val credentials = if (userInfo.contains(':')) decode(userInfo) else base64(userInfo)
        val method = credentials.substringBefore(':')
        val password = credentials.substringAfter(':')
        val host = hostPort.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: throw ParseException("Port yok")
        val json = JSONObject()
            .put("type", "shadowsocks")
            .put("server", host)
            .put("server_port", port)
            .put("method", method)
            .put("password", password)
        return Parsed(name.ifBlank { host }, "Shadowsocks", json)
    }

    private fun parseHysteria2(raw: String): Parsed {
        val link = Link(raw.replaceFirst(Regex("^hy2://", RegexOption.IGNORE_CASE), "hysteria2://"))
        val json = JSONObject()
            .put("type", "hysteria2")
            .put("server", link.host)
            .put("server_port", link.port)
            .put("password", link.user)
            .put(
                "tls",
                tls(link.q("sni", "peer") ?: link.host, isTrue(link.q("insecure", "allowInsecure")), link.q("alpn") ?: "h3", null),
            )
        link.q("obfs")?.let { type ->
            json.put("obfs", JSONObject().put("type", type).put("password", link.q("obfs-password", "obfs_password").orEmpty()))
        }
        return Parsed(link.name.ifBlank { link.host }, "Hysteria2", json)
    }

    private fun parseTuic(raw: String): Parsed {
        val link = Link(raw)
        val user = link.user
        val json = JSONObject()
            .put("type", "tuic")
            .put("server", link.host)
            .put("server_port", link.port)
            .put("uuid", user.substringBefore(':'))
            .put("password", user.substringAfter(':', ""))
            .put("congestion_control", link.q("congestion_control") ?: "bbr")
            .put("tls", tls(link.q("sni") ?: link.host, isTrue(link.q("allow_insecure", "insecure")), link.q("alpn") ?: "h3", null))
        return Parsed(link.name.ifBlank { link.host }, "TUIC", json)
    }

    private fun parseSocks(raw: String): Parsed {
        val link = Link(raw.replaceFirst(Regex("^socks[45h]?://", RegexOption.IGNORE_CASE), "socks://"))
        val json = JSONObject()
            .put("type", "socks")
            .put("server", link.host)
            .put("server_port", link.uri.port.takeIf { it > 0 } ?: 1080)
            .put("version", "5")
        link.uri.rawUserInfo?.let { info ->
            json.put("username", decode(info.substringBefore(':')))
            json.put("password", decode(info.substringAfter(':', "")))
        }
        return Parsed(link.name.ifBlank { link.host }, "SOCKS5", json)
    }

    private fun parseHttpProxy(raw: String): Parsed {
        val secure = raw.startsWith("https://", true)
        val link = Link(raw)
        val json = JSONObject()
            .put("type", "http")
            .put("server", link.host)
            .put("server_port", link.uri.port.takeIf { it > 0 } ?: if (secure) 443 else 8080)
        link.uri.rawUserInfo?.let { info ->
            json.put("username", decode(info.substringBefore(':')))
            json.put("password", decode(info.substringAfter(':', "")))
        }
        if (secure) json.put("tls", tls(link.host, false, null, null))
        return Parsed(link.name.ifBlank { link.host }, if (secure) "HTTPS vekil" else "HTTP vekil", json)
    }

    private fun parseWireGuard(text: String): Parsed {
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var current = ""
        text.lines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            if (line.startsWith("[")) {
                current = line.trim('[', ']').lowercase()
                sections.getOrPut(current) { mutableMapOf() }
            } else if (line.contains('=')) {
                val key = line.substringBefore('=').trim().lowercase()
                val value = line.substringAfter('=').trim()
                sections.getOrPut(current) { mutableMapOf() }[key] = value
            }
        }
        val iface = sections["interface"] ?: throw ParseException("[Interface] bölümü yok")
        val peer = sections["peer"] ?: throw ParseException("[Peer] bölümü yok")
        val endpoint = peer["endpoint"] ?: throw ParseException("Endpoint yok")
        val host = endpoint.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        val port = endpoint.substringAfterLast(':').toIntOrNull() ?: 51820
        val peerJson = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("public_key", peer["publickey"] ?: throw ParseException("PublicKey yok"))
            .put("allowed_ips", JSONArray(listOf("0.0.0.0/0", "::/0")))
        peer["presharedkey"]?.let { peerJson.put("pre_shared_key", it) }
        val json = JSONObject()
            .put("type", "wireguard")
            .put("address", JSONArray(iface["address"].orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)))
            .put("private_key", iface["privatekey"] ?: throw ParseException("PrivateKey yok"))
            .put("peers", JSONArray().put(peerJson))
        iface["mtu"]?.toIntOrNull()?.let { json.put("mtu", it) }
        return Parsed(host, "WireGuard", json, isEndpoint = true)
    }

    private fun parseJson(text: String): Parsed {
        val json = runCatching { JSONObject(text) }.getOrElse { throw ParseException("Geçersiz JSON") }
        val type = json.optString("type").ifBlank { throw ParseException("JSON'da \"type\" alanı yok") }
        val name = json.optString("tag").ifBlank { json.optString("server").ifBlank { type } }
        return Parsed(name, type, json, isEndpoint = type == "wireguard" && json.has("peers"))
    }
}
