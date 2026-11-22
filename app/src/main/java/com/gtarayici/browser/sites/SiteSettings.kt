package com.gtarayici.browser.sites

import android.content.Context
import androidx.core.content.edit
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.lib.state.ext.flow
import org.json.JSONObject

/** Bir siteye özel ayarlar. [zoom] yüzde olarak yazı boyutu; null ise genel ayar kullanılır. */
data class SitePrefs(val zoom: Int? = null, val desktop: Boolean = false) {
    val isDefault: Boolean get() = zoom == null && !desktop
}

/** Yazı boyutu adımları ve sınırları (saf, test edilebilir). */
object TextZoom {
    const val MIN = 50
    const val MAX = 300
    const val STEP = 10
    val CHOICES = listOf(80, 90, 100, 110, 120, 130, 150, 175, 200)

    fun step(current: Int, up: Boolean): Int = (current + if (up) STEP else -STEP).coerceIn(MIN, MAX)

    /** Motora verilecek çarpan: telefonun yazı ölçeği × seçilen yüzde. */
    fun factor(systemScale: Float, percent: Int): Float = systemScale * percent / 100f
}

/**
 * Siteye özel yazı boyutu ve masaüstü görünümü. Site adı "www." olmadan tutulur (www.example.com ve
 * example.com aynı sayılır). Profiller arasında ortaktır.
 */
class SiteSettings(context: Context) {
    private val prefs = context.getSharedPreferences("gtarayici_sites", Context.MODE_PRIVATE)
    private val _sites = MutableStateFlow(load())
    val sites: StateFlow<Map<String, SitePrefs>> = _sites.asStateFlow()

    fun get(url: String): SitePrefs = _sites.value[key(url)] ?: SitePrefs()

    fun setZoom(url: String, percent: Int?) = update(url) { it.copy(zoom = percent) }

    fun setDesktop(url: String, on: Boolean) = update(url) { it.copy(desktop = on) }

    fun reset(host: String) = update(host) { SitePrefs() }

    private fun update(url: String, change: (SitePrefs) -> SitePrefs) {
        val host = key(url)
        if (host.isEmpty()) return
        val updated = change(_sites.value[host] ?: SitePrefs())
        _sites.value = if (updated.isDefault) _sites.value - host else _sites.value + (host to updated)
        save()
    }

    private fun load(): Map<String, SitePrefs> = runCatching {
        val json = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        json.keys().asSequence().associateWith { host ->
            val o = json.getJSONObject(host)
            SitePrefs(zoom = if (o.has("zoom")) o.getInt("zoom") else null, desktop = o.optBoolean("desktop"))
        }
    }.getOrDefault(emptyMap())

    private fun save() {
        val json = JSONObject()
        _sites.value.forEach { (host, site) ->
            json.put(host, JSONObject().apply { site.zoom?.let { put("zoom", it) }; put("desktop", site.desktop) })
        }
        prefs.edit { putString(KEY, json.toString()) }
    }

    companion object {
        private const val KEY = "sites"

        /** "https://www.Example.com/yol" → "example.com"; site adı ya da adres verilebilir. */
        fun key(urlOrHost: String): String {
            val host = if (urlOrHost.contains("://")) UrlInput.host(urlOrHost) else urlOrHost
            return host.lowercase().removePrefix("www.")
        }
    }
}

/**
 * Açık sekmenin sitesine göre ayarları uygular:
 *  - Yazı boyutu motorda geneldir; sekme veya site değiştikçe o sitenin (yoksa genel) boyutuna ayarlanır.
 *  - Masaüstü görünümü istenen sitelerde sekme kendiliğinden masaüstü görünümüne geçer.
 */
class SiteSettingsApplier(
    private val context: Context,
    private val store: BrowserStore,
    private val engine: Engine,
    private val sites: SiteSettings,
    private val settings: AppSettings,
    private val sessionUseCases: SessionUseCases,
    private val scope: CoroutineScope,
) {
    fun start() {
        // Yazı boyutunu telefonun yazı ölçeğiyle birlikte biz yönetiriz (aksi halde motor çarpanı yok sayar).
        engine.settings.automaticFontSizeAdjustment = false
        scope.launch {
            val selected = store.flow().let { flow ->
                combine(flow, sites.sites, settings.textZoom) { state, _, global ->
                    val tab = state.selectedTab
                    Triple(tab?.id, tab?.content?.url.orEmpty(), global) to (tab?.content?.desktopMode == true)
                }
            }
            selected.distinctUntilChanged().collect { (info, desktopNow) ->
                val (tabId, url, global) = info
                val site = sites.get(url)
                applyZoom(site.zoom ?: global)
                if (tabId != null && site.desktop && !desktopNow && url.startsWith("http")) {
                    sessionUseCases.requestDesktopSite(true, tabId)
                }
            }
        }
    }

    private var lastFactor = -1f

    private fun applyZoom(percent: Int) {
        val factor = TextZoom.factor(context.resources.configuration.fontScale, percent)
        if (factor == lastFactor) return
        lastFactor = factor
        engine.settings.fontSizeFactor = factor
    }

    /** Telefonun yazı ölçeği değişince (Ayarlar → Ekran) yeniden uygulanır. */
    fun onConfigurationChanged() {
        lastFactor = -1f
        val tab = store.state.selectedTab
        applyZoom(sites.get(tab?.content?.url.orEmpty()).zoom ?: settings.textZoom.value)
    }
}
