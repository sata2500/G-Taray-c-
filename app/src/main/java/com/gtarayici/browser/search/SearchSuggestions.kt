package com.gtarayici.browser.search

import android.net.Uri
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.isSuccess
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Arama motorunun öneri servisi (yazarken çıkan tamamlamalar). Yalnızca yazılan metin seçili arama motoruna gider;
 * çerez gönderilmez. Gizli sekmelerde ve ayar kapalıyken hiç istek yapılmaz.
 */
object SearchSuggestions {
    private fun endpoint(engine: SearchEngine): String? = when (engine) {
        SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/ac/?type=list&q=%s"
        SearchEngine.GOOGLE -> "https://suggestqueries.google.com/complete/search?client=firefox&hl=tr&q=%s"
        SearchEngine.BING -> "https://api.bing.com/osjson.aspx?query=%s"
        SearchEngine.BRAVE -> "https://search.brave.com/api/suggest?q=%s"
        SearchEngine.ECOSIA -> "https://ac.ecosia.org/autocomplete?type=list&q=%s"
        SearchEngine.YANDEX -> "https://suggest.yandex.com.tr/suggest-ff.cgi?part=%s"
        SearchEngine.STARTPAGE -> "https://www.startpage.com/osuggestions?q=%s"
        SearchEngine.CUSTOM -> null
    }

    /** Ağ isteği yapar (ana iş parçacığında çağrılmamalı). Hata olursa boş liste. */
    fun fetch(client: Client, engine: SearchEngine, query: String, limit: Int = 4): List<String> {
        val url = endpoint(engine)?.replace("%s", Uri.encode(query)) ?: return emptyList()
        return runCatching {
            client.fetch(
                Request(
                    url = url,
                    connectTimeout = 3L to TimeUnit.SECONDS,
                    readTimeout = 3L to TimeUnit.SECONDS,
                    cookiePolicy = Request.CookiePolicy.OMIT,
                    private = true,
                ),
            ).use { response ->
                if (!response.isSuccess) return@use emptyList()
                parse(response.body.string(), query, limit)
            }
        }.getOrDefault(emptyList())
    }

    /** OpenSearch biçimi: ["sorgu", ["öneri1", "öneri2", ...]]. Yazılanın aynısı önerilmez. */
    fun parse(json: String, query: String, limit: Int): List<String> {
        val array = JSONArray(json).optJSONArray(1) ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optString(it).trim().takeIf { s -> s.isNotEmpty() } }
            .filterNot { it.equals(query.trim(), ignoreCase = true) }
            .distinct()
            .take(limit)
    }
}
