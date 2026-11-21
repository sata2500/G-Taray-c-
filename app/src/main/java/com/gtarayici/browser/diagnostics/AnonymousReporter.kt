package com.gtarayici.browser.diagnostics

import com.gtarayici.browser.BuildConfig
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Sorun bildirimini hesap gerektirmeden gönderir: rapor G-Tarayıcı'nın aracı sunucusuna (tools/report-relay) gider,
 * aracı GitHub'da hata kaydı açar. Gönderilen metin, kullanıcının önizlemede gördüğü metnin aynısıdır; ekran görüntüsü
 * gönderilmez.
 */
object AnonymousReporter {
    val available: Boolean get() = BuildConfig.REPORT_RELAY_URL.startsWith("https://")

    /** Başarılıysa açılan kaydın numarası. */
    suspend fun send(report: IssueReport, proxy: Proxy = Proxy.NO_PROXY): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            check(available) { "Anonim bildirim yapılandırılmamış" }
            val connection = URL("${BuildConfig.REPORT_RELAY_URL}/report").openConnection(proxy) as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("X-GT-Client", "G-Tarayici/${BuildConfig.VERSION_NAME}")
                val payload = payload(report, BuildConfig.VERSION_NAME).toByteArray()
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
                val code = connection.responseCode
                if (code == 429) throw RateLimited()
                check(code in 200..299) { "HTTP $code" }
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getInt("number")
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Aracıya gönderilen içerik (birim testiyle denetlenir). */
    internal fun payload(report: IssueReport, version: String): String = JSONObject()
        .put("title", report.title)
        .put("body", report.body().take(MAX_BODY))
        .put("category", report.category.tag)
        .put("version", version)
        .toString()

    class RateLimited : Exception("Too many requests")

    private const val MAX_BODY = 58_000
}
