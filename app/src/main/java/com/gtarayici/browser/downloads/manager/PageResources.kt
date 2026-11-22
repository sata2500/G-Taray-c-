package com.gtarayici.browser.downloads.manager

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.lib.state.ext.flow
import org.json.JSONObject

/** Sayfada bulunan indirilebilir bir öğe. */
data class PageResource(
    val url: String,
    val kind: MediaKind,
    /** Bağlantı metni, görselin alt metni veya "download" önerisi. */
    val label: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val durationSeconds: Int = 0,
)

/** Bir sayfanın tarama sonucu. */
data class PageScan(
    val resources: List<PageResource>,
    val title: String,
    /** Sayfada parça parça akıtılan (MSE/blob) bir video var; çoğu zaman doğrudan indirilemez. */
    val hasStreamingVideo: Boolean,
) {
    val groups: Map<MediaKind, List<PageResource>> get() =
        resources.groupBy { it.kind }.toSortedMap(compareBy { ORDER.indexOf(it) })

    companion object {
        /** Grupların gösterim sırası. */
        val ORDER = listOf(
            MediaKind.VIDEO, MediaKind.STREAM, MediaKind.AUDIO, MediaKind.DOCUMENT,
            MediaKind.ARCHIVE, MediaKind.APP, MediaKind.IMAGE, MediaKind.OTHER,
        )

        /** Yardımcı eklentinin "resources" iletisini çözümler; aynı adres bir kez alınır. */
        fun parse(json: JSONObject): PageScan {
            val array = json.optJSONArray("items")
            val seen = LinkedHashMap<String, PageResource>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val url = o.optString("url").takeIf { it.startsWith("http") } ?: continue
                    val kind = when (o.optString("type")) {
                        "video" -> if (DownloadNames.extension(url) == "m3u8") MediaKind.STREAM else MediaKind.VIDEO
                        "audio" -> MediaKind.AUDIO
                        "stream" -> MediaKind.STREAM
                        "image" -> MediaKind.IMAGE
                        else -> DownloadNames.kind(url)
                    }
                    val resource = PageResource(
                        url = url,
                        kind = kind,
                        label = o.optString("text").trim().takeIf { it.isNotEmpty() },
                        width = o.optInt("w"),
                        height = o.optInt("h"),
                        durationSeconds = o.optInt("duration"),
                    )
                    // Aynı adres birden çok yoldan bulunduysa daha fazla bilgi içeren kayıt kalır.
                    val before = seen[url]
                    seen[url] = if (before == null) resource else before.copy(
                        label = before.label ?: resource.label,
                        width = maxOf(before.width, resource.width),
                        height = maxOf(before.height, resource.height),
                        durationSeconds = maxOf(before.durationSeconds, resource.durationSeconds),
                        kind = if (before.kind == MediaKind.OTHER) resource.kind else before.kind,
                    )
                }
            }
            return PageScan(seen.values.toList(), json.optString("title"), json.optBoolean("blob"))
        }
    }
}

/**
 * Sayfadaki indirilebilir öğeleri yardımcı eklentiye sorar. Her sekmenin içerik betiğiyle ayrı bir bağlantı kurulur;
 * bağlantının kurulabilmesi için dinleyici sekme oluşur oluşmaz kaydedilir.
 */
class ResourceScanner(private val store: BrowserStore, private val scope: CoroutineScope) {
    private var extension: WebExtension? = null
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<PageScan>>()
    private val ids = AtomicInteger()

    private val articles = ConcurrentHashMap<Int, CompletableDeferred<JSONObject?>>()

    private val handler = object : MessageHandler {
        override fun onPortMessage(message: Any, port: Port) {
            val json = message as? JSONObject ?: return
            when (json.optString("type")) {
                "resources" -> pending.remove(json.optInt("id"))?.complete(PageScan.parse(json))
                "article" -> articles.remove(json.optInt("id"))?.complete(json.optJSONObject("article"))
            }
        }
    }

    /** Sayfanın makale metnini ister (başlık, site, bloklar); makale bulunamazsa veya yanıt yoksa null. */
    suspend fun article(session: EngineSession): JSONObject? {
        val helper = extension ?: return null
        register(session)
        val port = helper.getConnectedPort(APP, session) ?: return null
        val id = ids.incrementAndGet()
        val result = CompletableDeferred<JSONObject?>()
        articles[id] = result
        port.postMessage(JSONObject().put("type", "article").put("id", id))
        return withTimeoutOrNull(TIMEOUT_MS) { result.await() }.also { articles.remove(id) }
    }

    /** Yardımcı eklenti kurulunca çağrılır. */
    fun attach(helper: WebExtension) {
        extension = helper
        scope.launch {
            store.flow()
                .map { state -> (state.tabs + state.customTabs).mapNotNull { it.engineState.engineSession } }
                .distinctUntilChanged()
                .collect { sessions -> sessions.forEach(::register) }
        }
    }

    private fun register(session: EngineSession) {
        val helper = extension ?: return
        if (!helper.hasContentMessageHandler(session, APP)) helper.registerContentMessageHandler(session, APP, handler)
    }

    /** Sayfayı tarar; eklenti hazır değilse veya sayfa yanıt vermezse null. */
    suspend fun scan(session: EngineSession): PageScan? {
        val helper = extension ?: return null
        register(session)
        val port = helper.getConnectedPort(APP, session) ?: return null
        val id = ids.incrementAndGet()
        val result = CompletableDeferred<PageScan>()
        pending[id] = result
        port.postMessage(JSONObject().put("type", "collect").put("id", id))
        return withTimeoutOrNull(TIMEOUT_MS) { result.await() }.also { pending.remove(id) }
    }

    private companion object {
        const val APP = "gtResources"
        const val TIMEOUT_MS = 4_000L
    }
}
