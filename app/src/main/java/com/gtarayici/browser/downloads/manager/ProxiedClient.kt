package com.gtarayici.browser.downloads.manager

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response

/**
 * Tarayıcı VPN'inin yerel SOCKS5 vekilinden geçen ağ istemcisi. VPN yalnızca gizli sekmelerdeyken gizli sekme
 * indirmeleri bununla yapılır (motorun kendi bağlantısı o sırada VPN'siz). Alan adı çözümlemesi de vekilde yapılır
 * (DNS sızmaz). Site çerezleri motorda kaldığından yalnızca indirmeyle birlikte gelen çerez başlığı gönderilir.
 */
class ProxiedClient(private val port: Int) : Client() {
    override fun fetch(request: Request): Response {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", port))
        val connection = URL(request.url).openConnection(proxy) as HttpURLConnection
        connection.requestMethod = request.method.name
        connection.instanceFollowRedirects = request.redirect == Request.Redirect.FOLLOW
        request.connectTimeout?.let { (value, unit) -> connection.connectTimeout = unit.toMillis(value).toInt() }
        request.readTimeout?.let { (value, unit) -> connection.readTimeout = unit.toMillis(value).toInt() }
        request.headers?.forEach { connection.setRequestProperty(it.name, it.value) }
        request.body?.let { body ->
            connection.doOutput = true
            body.useStream { input -> connection.outputStream.use { input.copyTo(it) } }
        }
        val status = connection.responseCode
        val headers = MutableHeaders()
        connection.headerFields.forEach { (name, values) -> if (name != null) values.forEach { headers.append(name, it) } }
        val raw: InputStream = (if (status >= 400) connection.errorStream else connection.inputStream) ?: ByteArrayInputStream(ByteArray(0))
        val stream = object : FilterInputStream(raw) {
            override fun close() {
                runCatching { super.close() }
                connection.disconnect()
            }
        }
        return Response(connection.url.toString(), status, headers, Response.Body(stream, connection.contentType))
    }
}
