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
    }

    fun refresh() {
        engine.listInstalledWebExtensions(
            onSuccess = { list -> _installed.value = list.filterNot { it.id == HELPER_ID }.sortedBy { it.displayName() } },
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
                // Arka planda oynatma ve resim içinde resim gizli sekmelerde de çalışsın.
                if (!extension.isAllowedInPrivateBrowsing()) setAllowedInPrivate(extension, true)
            },
            onError = { logger.error("Yardımcı eklenti kurulamadı", it) },
        )
    }

    private fun registerHelperMessages(extension: WebExtension) {
        val handler = object : MessageHandler {
            override fun onPortMessage(message: Any, port: Port) {
                val json = message as? JSONObject ?: return
                if (json.optString("type") == "video-rect") {
                    _videoRect.value = VideoRect(
                        json.optDouble("x"), json.optDouble("y"), json.optDouble("w"), json.optDouble("h"),
                        json.optDouble("vw"), json.optDouble("vh"),
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
        const val NATIVE_APP = "gtarayici"
        const val AMO_ANDROID_URL = "https://addons.mozilla.org/android/"
        private const val UBLOCK_AMO_URL = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        private const val KEY_UBLOCK_INSTALLED = "ublock_installed"
        private const val KEY_LAST_UPDATE = "last_update_check"
    }
}

/** Videonun görünür alan (viewport) içindeki konumu, CSS pikseli cinsinden. */
data class VideoRect(val x: Double, val y: Double, val w: Double, val h: Double, val viewportWidth: Double, val viewportHeight: Double) {
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
