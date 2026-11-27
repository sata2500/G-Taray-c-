package com.gtarayici.browser.extensions

import android.content.Context
import androidx.core.content.edit
import com.gtarayici.browser.settings.AppSettings
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.lib.state.ext.flow
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.support.base.log.logger.Logger
import org.json.JSONObject

/**
 * Eklentileri yönetir: hazır gelen uBlock Origin ve G-Tarayıcı yardımcısını kurar,
 * kullanıcının kurduğu eklentileri listeler, açar/kapatır, kaldırır ve günlük olarak günceller.
 */
class ExtensionManager(
    private val context: Context,
    private val engine: Engine,
    private val store: BrowserStore,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
    /** Veri tasarrufu açıkken yardımcı eklenti sitelere `Save-Data: on` başlığını gönderir. */
    private val saveData: StateFlow<Boolean> = MutableStateFlow(false),
    /** VPN yalnızca gizli sekmelerde kullanılıyorsa yerel vekil portu: yardımcı eklenti gizli sekmeleri oraya yönlendirir. */
    private val privateProxy: StateFlow<Int?> = MutableStateFlow(null),
) {
    private val logger = Logger("ExtensionManager")
    private val prefs = context.getSharedPreferences("gtarayici_extensions", Context.MODE_PRIVATE)

    private val _installed = MutableStateFlow<List<WebExtension>>(emptyList())
    val installed: StateFlow<List<WebExtension>> = _installed.asStateFlow()

    /** Kullanıcıya sormadan onaylanacak (uygulamayla birlikte gelen) eklentiler. */
    private val autoApprove = mutableSetOf<String>()

    fun isAutoApproved(id: String): Boolean = id in autoApprove

    private val _videoRect = MutableStateFlow<VideoRect?>(null)

    /** Oynayan videonun sayfadaki son konumu (CSS pikseli); PiP geçiş animasyonu ve son kare için. */
    val videoRect: StateFlow<VideoRect?> = _videoRect.asStateFlow()

    private val _videoHidden = MutableStateFlow(false)

    /**
     * Oynayan video var ama görünmüyor (ör. YouTube Music şarkı modu: yalnızca ses ve kapak resmi). Bu durumda
     * uygulamadan çıkınca küçük pencere (PiP) açılmaz, ses arka planda sürer.
     */
    val videoHidden: StateFlow<Boolean> = _videoHidden.asStateFlow()

    /** "Yalnızca ses" bildiren sayfa. */
    @Volatile private var hiddenPage: String? = null

    /**
     * [url] sayfasındaki videonun konumu. Bilgi başka bir sekmenin sayfasından geldiyse null: video bilgisi tek bir
     * değer olarak tutulduğundan, ör. arka plandaki YouTube Müzik sekmesi başka sitedeki videoyu etkilemesin.
     */
    fun videoRectFor(url: String?): VideoRect? = _videoRect.value?.takeIf { samePage(it.page, url) }

    /** [url] sayfasında oynayan video yalnızca ses mi (görüntü gizli)? */
    fun videoHiddenFor(url: String?): Boolean = _videoHidden.value && samePage(hiddenPage, url)

    /** Yardımcı eklenti kurulunca çağrılır (ör. sayfa taraması için sekme bağlantıları). */
    var onHelperReady: (WebExtension) -> Unit = {}

    fun start() {
        // Uygulamayla gelen eklentilerin izin istemi kullanıcıya gösterilmeden onaylanır.
        scope.launch {
            store.flow().map { it.webExtensionPromptRequest }.distinctUntilChanged().collect { request ->
                if (request is WebExtensionPromptRequest.AfterInstallation.Permissions.Required &&
                    autoApprove.remove(request.extension.id)
                ) {
                    request.onConfirm(PermissionPromptResponse(isPermissionsGranted = true, isPrivateModeGranted = true))
                    store.dispatch(WebExtensionAction.ConsumePromptRequestWebExtensionAction)
                }
            }
        }
        installHelper()
        installBundledUBlockOnce()
        refresh()
        updateIfDue()
        // "Sitelere karanlık tema" ayarı Dark Reader eklentisini kurar/açar/kapatır.
        scope.launch { settings.darkSites.drop(1).collect { applyDarkReader(it) } }
    }

    /**
     * Dark Reader'ı ayara göre açar veya kapatır. Kurulu değilse addons.mozilla.org'dan kurulur (kullanıcı ayarı
     * kendisi açtığı için izin istemi sorulmadan onaylanır). Kapatınca eklenti kaldırılmaz, yalnızca devre dışı kalır.
     */
    private fun applyDarkReader(enabled: Boolean) {
        engine.listInstalledWebExtensions(onSuccess = { list ->
            val existing = list.firstOrNull { it.id == DARK_READER_ID }
            when {
                existing != null -> if (existing.isEnabled() != enabled) setEnabled(existing, enabled)
                enabled -> {
                    autoApprove += DARK_READER_ID
                    install(DARK_READER_URL) { result ->
                        result.onSuccess { setAllowedInPrivate(it, true) }
                            .onFailure {
                                logger.warn("Dark Reader kurulamadı", it)
                                autoApprove -= DARK_READER_ID
                                settings.setDarkSites(false)
                                onDarkReaderFailed(it)
                            }
                    }
                }
            }
        })
    }

    /** Dark Reader kurulamazsa (ör. internet yok) arayüze bildirilir. */
    var onDarkReaderFailed: (Throwable) -> Unit = {}

    /**
     * Motor kurulu eklentileri yükleyene kadar bekler (en fazla [timeoutMs]). Açılışta sekmeler bundan sonra geri
     * yüklenir; aksi halde ilk sayfa uBlock Origin hazır olmadan, reklamlar engellenmeden açılabiliyordu.
     */
    suspend fun awaitLoaded(timeoutMs: Long) {
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                engine.listInstalledWebExtensions(
                    onSuccess = { if (continuation.isActive) continuation.resumeWith(Result.success(Unit)) },
                    onError = { if (continuation.isActive) continuation.resumeWith(Result.success(Unit)) },
                )
            }
        }
    }

    fun refresh() {
        engine.listInstalledWebExtensions(
            // Yerleşik eklentiler (yardımcı eklenti, Mozilla'nın okuma görünümü) kullanıcıya gösterilmez; kapatılırsa
            // ilgili özellikler bozulur.
            onSuccess = { list -> _installed.value = list.filterNot { it.isBuiltIn() }.sortedBy { it.displayName() } },
            onError = { logger.warn("Eklentiler listelenemedi", it) },
        )
    }

    fun setEnabled(extension: WebExtension, enabled: Boolean) {
        if (enabled) {
            engine.enableWebExtension(extension, onSuccess = { refresh() }, onError = { refresh() })
        } else {
            engine.disableWebExtension(extension, onSuccess = { refresh() }, onError = { refresh() })
        }
    }

    fun setAllowedInPrivate(extension: WebExtension, allowed: Boolean) {
        engine.setAllowedInPrivateBrowsing(extension, allowed, onSuccess = { refresh() }, onError = { refresh() })
    }

    fun uninstall(extension: WebExtension) {
        engine.uninstallWebExtension(extension, onSuccess = { refresh() }, onError = { _, _ -> refresh() })
    }

    /** AMO veya başka bir adresten (.xpi) eklenti kurar. İzin istemi arayüzde gösterilir. */
    fun install(url: String, onDone: (Result<WebExtension>) -> Unit = {}) {
        engine.installWebExtension(
            url,
            onSuccess = {
                refresh()
                onDone(Result.success(it))
            },
            onError = { onDone(Result.failure(it)) },
        )
    }

    private fun installHelper() {
        engine.installBuiltInWebExtension(
            id = HELPER_ID,
            url = "resource://android/assets/extensions/helper/",
            onSuccess = { extension ->
                registerHelperMessages(extension)
                onHelperReady(extension)
                // Arka planda oynatma ve resim içinde resim gizli sekmelerde de çalışsın.
                if (!extension.isAllowedInPrivateBrowsing()) setAllowedInPrivate(extension, true)
            },
            onError = { logger.error("Yardımcı eklenti kurulamadı", it) },
        )
    }

    /** Yardımcı eklentinin arka plan betiğine açık bağlantı; ayar değişiklikleri buradan iletilir. */
    private var helperPort: Port? = null

    private fun postSaveData(port: Port? = helperPort) {
        runCatching { port?.postMessage(JSONObject().put("type", "save-data").put("on", saveData.value)) }
    }

    private fun postPrivateProxy(port: Port? = helperPort) {
        runCatching {
            port?.postMessage(JSONObject().put("type", "private-proxy").put("port", privateProxy.value ?: JSONObject.NULL))
        }
    }

    private fun registerHelperMessages(extension: WebExtension) {
        scope.launch { saveData.collect { postSaveData() } }
        scope.launch { privateProxy.collect { postPrivateProxy() } }
        val handler = object : MessageHandler {
            override fun onPortConnected(port: Port) {
                helperPort = port
                postSaveData(port)
                postPrivateProxy(port)
            }

            override fun onPortDisconnected(port: Port) {
                if (helperPort == port) helperPort = null
            }

            override fun onPortMessage(message: Any, port: Port) {
                val json = message as? JSONObject ?: return
                val page = json.optString("page").ifEmpty { null }
                if (json.optString("type") == "video-rect" && json.optBoolean("hidden")) {
                    hiddenPage = page
                    _videoHidden.value = true
                    _videoRect.value = null
                } else if (json.optString("type") == "video-rect") {
                    hiddenPage = null
                    _videoHidden.value = false
                    _videoRect.value = VideoRect(
                        json.optDouble("x"), json.optDouble("y"), json.optDouble("w"), json.optDouble("h"),
                        json.optDouble("vw"), json.optDouble("vh"),
                        json.optInt("nw"), json.optInt("nh"),
                        page = page,
                    )
                }
            }

            override fun onMessage(message: Any, source: EngineSession?): Any =
                JSONObject()
                    .put("backgroundPlay", settings.backgroundPlay.value)
                    .put("sponsorBlock", settings.sponsorBlock.value)
        }
        extension.registerBackgroundMessageHandler(NATIVE_APP, handler)
    }

    /** uBlock Origin ilk açılışta, internet gerektirmeden pakette gelen imzalı dosyadan kurulur. */
    private fun installBundledUBlockOnce() {
        if (prefs.getBoolean(KEY_UBLOCK_INSTALLED, false)) return
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "ublock_origin.xpi").also { target ->
                    context.assets.open("extensions/ublock_origin.xpi").use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
            }
            autoApprove += UBLOCK_ID
            install(file.toURI().toString()) { result ->
                result.onSuccess { extension ->
                    prefs.edit { putBoolean(KEY_UBLOCK_INSTALLED, true) }
                    // Reklam engelleme gizli sekmelerde de çalışsın.
                    setAllowedInPrivate(extension, true)
                }.onFailure {
                    logger.warn("Gömülü uBlock Origin kurulamadı, AMO'dan deneniyor", it)
                    autoApprove += UBLOCK_ID
                    install(UBLOCK_AMO_URL) { retry ->
                        retry.onSuccess { ext ->
                            prefs.edit { putBoolean(KEY_UBLOCK_INSTALLED, true) }
                            setAllowedInPrivate(ext, true)
                        }
                    }
                }
                file.delete()
            }
        }
    }

    /** Kurulu eklentileri günde en fazla bir kez günceller. */
    private fun updateIfDue() {
        val last = prefs.getLong(KEY_LAST_UPDATE, 0)
        if (System.currentTimeMillis() - last < TimeUnit.DAYS.toMillis(1)) return
        prefs.edit { putLong(KEY_LAST_UPDATE, System.currentTimeMillis()) }
        engine.listInstalledWebExtensions(onSuccess = { list ->
            list.filterNot { it.isBuiltIn() }.forEach { extension ->
                engine.updateWebExtension(extension, onSuccess = { refresh() }, onError = { _, _ -> })
            }
        })
    }

    companion object {
        const val HELPER_ID = "helper@gtarayici.app"
        const val UBLOCK_ID = "uBlock0@raymondhill.net"
        const val DARK_READER_ID = "addon@darkreader.org"
        private const val DARK_READER_URL = "https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi"
        const val NATIVE_APP = "gtarayici"
        const val AMO_ANDROID_URL = "https://addons.mozilla.org/android/"
        private const val UBLOCK_AMO_URL = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        private const val KEY_UBLOCK_INSTALLED = "ublock_installed"
        private const val KEY_LAST_UPDATE = "last_update_check"
    }
}

/** İki sayfa adresi aynı sayfa mı (# sonrası yok sayılır); adreslerden biri bilinmiyorsa eşit sayılır. */
internal fun samePage(a: String?, b: String?): Boolean {
    if (a == null || b == null) return true
    return a.substringBefore('#') == b.substringBefore('#')
}

/** Videonun görünür alan (viewport) içindeki konumu, CSS pikseli cinsinden. */
data class VideoRect(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val viewportWidth: Double,
    val viewportHeight: Double,
    /** Videonun gerçek piksel boyutu (bilinmiyorsa 0); küçük pencerenin en-boy oranı bundan alınır. */
    val naturalWidth: Int = 0,
    val naturalHeight: Int = 0,
    /** Bilginin geldiği sayfanın adresi (bilinmiyorsa null). */
    val page: String? = null,
) {
    /** Bu dikdörtgeni, genişliği [viewWidth] piksel olan motor görünümünün piksel koordinatlarına çevirir. */
    fun toViewPixels(viewWidth: Int, viewHeight: Int): android.graphics.Rect? {
        if (viewportWidth <= 0 || viewWidth <= 0) return null
        val scale = viewWidth / viewportWidth
        val rect = android.graphics.Rect(
            (x * scale).toInt(), (y * scale).toInt(), ((x + w) * scale).toInt(), ((y + h) * scale).toInt(),
        )
        return if (rect.intersect(0, 0, viewWidth, viewHeight) && rect.width() > 16 && rect.height() > 16) rect else null
    }
}

fun WebExtension.displayName(): String = getMetadata()?.name?.takeIf { it.isNotBlank() } ?: id
