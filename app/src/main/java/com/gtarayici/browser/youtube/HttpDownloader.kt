package com.gtarayici.browser.youtube

import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

/** NewPipe Extractor için HTTP istemcisi. Açıksa tarayıcı VPN'i üzerinden bağlanır. */
class HttpDownloader(private val proxy: () -> Proxy?) : Downloader() {
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val connection = (proxy()?.let { url.openConnection(it) } ?: url.openConnection()) as HttpURLConnection
        connection.requestMethod = request.httpMethod()
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", YouTubeDownloader.USER_AGENT)
        request.headers().forEach { (name, values) -> values.forEach { connection.addRequestProperty(name, it) } }
        request.dataToSend()?.let { body ->
            connection.doOutput = true
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        val body = stream?.bufferedReader()?.use { it.readText() }
        val headers = connection.headerFields.filterKeys { it != null }
        return Response(code, connection.responseMessage, headers, body, connection.url.toString())
    }
}
