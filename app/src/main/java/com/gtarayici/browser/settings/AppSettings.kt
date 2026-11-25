package com.gtarayici.browser.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.gtarayici.browser.search.SearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Menünün ana ızgarasında varsayılan olarak duran öğeler (en sık kullanılanlar). */
const val DEFAULT_MENU_PINS = "find,desktop,page_files,save_later,bookmarks,history,downloads,settings"

/** Veri tasarrufu: kapalı, ölçülü/yavaş bağlantıda kendiliğinden ya da her zaman açık. */
enum class DataSaverMode { OFF, AUTO, ON }

/** Sayfa önbelleğinin (HTTP disk önbelleği) en büyük boyutu; [AUTO] Gecko'nun kendi hesabıdır. */
enum class CacheSize(val megabytes: Int) { AUTO(0), MB_256(256), MB_512(512), GB_1(1024), GB_2(2048) }

/** DNS-over-HTTPS sağlayıcıları. [OFF] cihazın kendi DNS ayarını kullanır. */
enum class DnsProvider(val title: String, val url: String) {
    OFF("Sistem varsayılanı", ""),
    CLOUDFLARE("Cloudflare", "https://mozilla.cloudflare-dns.com/dns-query"),
    QUAD9("Quad9 (zararlı site engelleme)", "https://dns.quad9.net/dns-query"),
    ADGUARD("AdGuard (reklam engelleme)", "https://dns.adguard-dns.com/dns-query"),
    MULLVAD("Mullvad (reklam engelleme)", "https://adblock.dns.mullvad.net/dns-query"),
    NEXTDNS("NextDNS", "https://dns.nextdns.io"),
    CUSTOM("Özel adres", ""),
}

/**
 * Uygulama ayarları. Varsayılanlar "kur ve kullan" ilkesine göre en güvenli seçeneklerdir.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("gtarayici_settings", Context.MODE_PRIVATE)

    /** Tüm ayarlar; yedekten geri yüklenince yeniden okunur. */
    private val registry = mutableListOf<Pref<*>>()

    private inner class Pref<T>(
        private val key: String,
        private val default: T,
        private val read: SharedPreferences.(String, T) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> Unit,
    ) {
        private val state = MutableStateFlow(prefs.read(key, default))
        val flow: StateFlow<T> = state.asStateFlow()

        init {
            registry += this
        }

        fun reload() {
            state.value = prefs.read(key, default)
        }

        fun set(value: T) {
            prefs.edit { write(key, value) }
            state.value = value
        }
    }

    private fun bool(key: String, default: Boolean) =
        Pref(key, default, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

    private fun int(key: String, default: Int) =
        Pref(key, default, { k, d -> getInt(k, d) }, { k, v -> putInt(k, v) })

    private fun string(key: String, default: String) =
        Pref(key, default, { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })

    private inline fun <reified E : Enum<E>> enum(key: String, default: E) = Pref(
        key,
        default,
        { k, d -> runCatching { enumValueOf<E>(getString(k, null) ?: "") }.getOrDefault(d) },
        { k, v -> putString(k, v.name) },
    )

    private val searchEnginePref = enum("search_engine", SearchEngine.DUCKDUCKGO)
    private val themePref = enum("theme", ThemeMode.SYSTEM)
    private val strictPref = bool("strict_tracking", true)
    private val httpsPref = bool("https_only", true)
    private val gpcPref = bool("global_privacy_control", true)
    private val cookieBannerPref = bool("cookie_banner_reject", true)
    private val dnsPref = enum("dns_provider", DnsProvider.OFF)
    private val dnsCustomPref = string("dns_custom_url", "")
    private val dnsStrictPref = bool("dns_strict", false)
    private val lockPrivatePref = bool("lock_private_tabs", false)
    private val securePrivatePref = bool("secure_private_tabs", true)
    private val backgroundPlayPref = bool("background_play", true)
    private val sponsorBlockPref = bool("sponsorblock", true)
    private val autoPipPref = bool("auto_pip", true)
    private val inAppPipPref = bool("in_app_pip", false)
    private val autoUpdatePref = bool("auto_update_check", true)
    private val videoDownloadButtonPref = bool("video_download_button", true)
    private val pullToRefreshPref = bool("pull_to_refresh", true)
    private val toolbarSwipePref = bool("toolbar_swipe_tabs", true)
    private val unloadTabsPref = bool("unload_inactive_tabs", true)
    private val hideToolbarPref = bool("hide_toolbar_on_scroll", true)
    private val hintsPref = bool("contextual_hints", true)
    private val diagnosticsPref = bool("diagnostics_log", true)
    private val crashPromptPref = bool("crash_prompt", true)
    private val dataSaverPref = enum("data_saver", DataSaverMode.AUTO)
    private val dataSaverImagesPref = bool("data_saver_block_images", false)
    private val cacheSizePref = enum("cache_size", CacheSize.MB_512)
    private val savePasswordsPref = bool("save_passwords", true)
    private val autofillPasswordsPref = bool("autofill_passwords", true)
    private val textZoomPref = int("text_zoom", 100)
    private val darkSitesPref = bool("dark_sites", false)
    private val searchSuggestionsPref = bool("search_suggestions", true)
    private val vpnKeepAlivePref = bool("vpn_keep_alive", true)
    private val customSearchPref = string("custom_search_url", "")
    private val menuPinsPref = string("menu_pins", DEFAULT_MENU_PINS)

    val searchEngine get() = searchEnginePref.flow
    val theme get() = themePref.flow
    val strictTracking get() = strictPref.flow
    val httpsOnly get() = httpsPref.flow
    val globalPrivacyControl get() = gpcPref.flow
    val cookieBannerReject get() = cookieBannerPref.flow
    val dnsProvider get() = dnsPref.flow
    val dnsCustomUrl get() = dnsCustomPref.flow
    val dnsStrict get() = dnsStrictPref.flow
    val lockPrivateTabs get() = lockPrivatePref.flow
    val securePrivateTabs get() = securePrivatePref.flow
    val backgroundPlay get() = backgroundPlayPref.flow
    val sponsorBlock get() = sponsorBlockPref.flow
    val autoPictureInPicture get() = autoPipPref.flow
    val inAppPictureInPicture get() = inAppPipPref.flow
    val autoUpdateCheck get() = autoUpdatePref.flow
    val videoDownloadButton get() = videoDownloadButtonPref.flow
    val pullToRefresh get() = pullToRefreshPref.flow
    val toolbarSwipeTabs get() = toolbarSwipePref.flow
    val unloadInactiveTabs get() = unloadTabsPref.flow
    val hideToolbarOnScroll get() = hideToolbarPref.flow
    val contextualHints get() = hintsPref.flow
    val diagnosticsLog get() = diagnosticsPref.flow
    val crashPrompt get() = crashPromptPref.flow
    val dataSaver get() = dataSaverPref.flow
    val dataSaverBlockImages get() = dataSaverImagesPref.flow
    val cacheSize get() = cacheSizePref.flow
    val savePasswords get() = savePasswordsPref.flow
    val autofillPasswords get() = autofillPasswordsPref.flow
    /** Sayfalardaki yazıların genel boyutu (yüzde); siteye özel boyut bunu geçersiz kılar. */
    val textZoom get() = textZoomPref.flow
    /** Karanlık teması olmayan sitelere de karanlık tema (Dark Reader eklentisiyle). */
    val darkSites get() = darkSitesPref.flow
    /** Yazarken arama motorundan öneri alınsın mı (gizli sekmelerde hiç alınmaz). */
    val searchSuggestions get() = searchSuggestionsPref.flow
    /** VPN açıkken uygulama arka planda kapatılmasın (ön plan hizmeti). */
    val vpnKeepAlive get() = vpnKeepAlivePref.flow
    /** Özel arama motorunun adres kalıbı (%s aranan metin). */
    val customSearchUrl get() = customSearchPref.flow
    /** Menünün ana ızgarasındaki öğeler (sırasıyla, virgülle ayrılmış kimlikler); diğerleri "Daha fazla"da. */
    val menuPins get() = menuPinsPref.flow

    fun setSearchEngine(value: SearchEngine) = searchEnginePref.set(value)
    fun setTheme(value: ThemeMode) = themePref.set(value)
    fun setStrictTracking(value: Boolean) = strictPref.set(value)
    fun setHttpsOnly(value: Boolean) = httpsPref.set(value)
    fun setGlobalPrivacyControl(value: Boolean) = gpcPref.set(value)
    fun setCookieBannerReject(value: Boolean) = cookieBannerPref.set(value)
    fun setDnsProvider(value: DnsProvider) = dnsPref.set(value)
    fun setDnsCustomUrl(value: String) = dnsCustomPref.set(value.trim())
    fun setDnsStrict(value: Boolean) = dnsStrictPref.set(value)
    fun setLockPrivateTabs(value: Boolean) = lockPrivatePref.set(value)
    fun setSecurePrivateTabs(value: Boolean) = securePrivatePref.set(value)
    fun setBackgroundPlay(value: Boolean) = backgroundPlayPref.set(value)
    fun setSponsorBlock(value: Boolean) = sponsorBlockPref.set(value)
    fun setAutoPictureInPicture(value: Boolean) = autoPipPref.set(value)
    fun setInAppPictureInPicture(value: Boolean) = inAppPipPref.set(value)
    fun setAutoUpdateCheck(value: Boolean) = autoUpdatePref.set(value)
    fun setVideoDownloadButton(value: Boolean) = videoDownloadButtonPref.set(value)
    fun setPullToRefresh(value: Boolean) = pullToRefreshPref.set(value)
    fun setToolbarSwipeTabs(value: Boolean) = toolbarSwipePref.set(value)
    fun setUnloadInactiveTabs(value: Boolean) = unloadTabsPref.set(value)
    fun setHideToolbarOnScroll(value: Boolean) = hideToolbarPref.set(value)
    fun setContextualHints(value: Boolean) = hintsPref.set(value)
    fun setDiagnosticsLog(value: Boolean) = diagnosticsPref.set(value)
    fun setCrashPrompt(value: Boolean) = crashPromptPref.set(value)
    fun setDataSaver(value: DataSaverMode) = dataSaverPref.set(value)
    fun setDataSaverBlockImages(value: Boolean) = dataSaverImagesPref.set(value)
    fun setCacheSize(value: CacheSize) = cacheSizePref.set(value)
    fun setSavePasswords(value: Boolean) = savePasswordsPref.set(value)
    fun setAutofillPasswords(value: Boolean) = autofillPasswordsPref.set(value)
    fun setTextZoom(value: Int) = textZoomPref.set(value)
    fun setDarkSites(value: Boolean) = darkSitesPref.set(value)
    fun setSearchSuggestions(value: Boolean) = searchSuggestionsPref.set(value)
    fun setVpnKeepAlive(value: Boolean) = vpnKeepAlivePref.set(value)
    fun setMenuPins(ids: List<String>) = menuPinsPref.set(ids.joinToString(","))
    fun setCustomSearchUrl(value: String) {
        customSearchPref.set(value.trim())
        com.gtarayici.browser.search.CustomSearch.template = value.trim()
    }

    init {
        com.gtarayici.browser.search.CustomSearch.template = customSearchPref.flow.value
    }

    /** Yedek için ayarların ham hali (anahtar → değer). */
    fun exportAll(): Map<String, Any?> = prefs.all.toMap()

    /** Yedekten gelen ayarları yazar ve uygulamadaki değerleri tazeler. Bilinmeyen türler atlanır. */
    fun importAll(values: Map<String, Any?>) {
        prefs.edit {
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
        registry.forEach { it.reload() }
        com.gtarayici.browser.search.CustomSearch.template = customSearchPref.flow.value
    }

    /** Bağlamsal ipuçlarının kalıcı durumu (bkz. ui/feedback/Hints). */
    val hintStore = object : com.gtarayici.browser.ui.feedback.HintStore {
        override val enabled get() = hintsPref.flow.value
        override fun isSeen(hint: com.gtarayici.browser.ui.feedback.Hint) = hint.key in seenHints()
        override fun markSeen(hint: com.gtarayici.browser.ui.feedback.Hint) =
            prefs.edit { putStringSet("hints_seen", seenHints() + hint.key) }
    }

    private fun seenHints(): Set<String> = prefs.getStringSet("hints_seen", null).orEmpty()

    /** Seçili DNS sağlayıcısının DoH adresi; kapalıysa null. */
    fun dohUrl(): String? = when (dnsProvider.value) {
        DnsProvider.OFF -> null
        DnsProvider.CUSTOM -> dnsCustomUrl.value.takeIf { it.startsWith("https://") }
        else -> dnsProvider.value.url
    }
}
