package com.gtarayici.browser.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.gtarayici.browser.search.SearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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

    private inner class Pref<T>(
        private val key: String,
        default: T,
        read: SharedPreferences.(String, T) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> Unit,
    ) {
        private val state = MutableStateFlow(prefs.read(key, default))
        val flow: StateFlow<T> = state.asStateFlow()

        fun set(value: T) {
            prefs.edit { write(key, value) }
            state.value = value
        }
    }

    private fun bool(key: String, default: Boolean) =
        Pref(key, default, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

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
    private val autoUpdatePref = bool("auto_update_check", true)
    private val videoDownloadButtonPref = bool("video_download_button", true)
    private val pullToRefreshPref = bool("pull_to_refresh", true)
    private val toolbarSwipePref = bool("toolbar_swipe_tabs", true)
    private val unloadTabsPref = bool("unload_inactive_tabs", true)
    private val hideToolbarPref = bool("hide_toolbar_on_scroll", true)

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
    val autoUpdateCheck get() = autoUpdatePref.flow
    val videoDownloadButton get() = videoDownloadButtonPref.flow
    val pullToRefresh get() = pullToRefreshPref.flow
    val toolbarSwipeTabs get() = toolbarSwipePref.flow
    val unloadInactiveTabs get() = unloadTabsPref.flow
    val hideToolbarOnScroll get() = hideToolbarPref.flow

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
    fun setAutoUpdateCheck(value: Boolean) = autoUpdatePref.set(value)
    fun setVideoDownloadButton(value: Boolean) = videoDownloadButtonPref.set(value)
    fun setPullToRefresh(value: Boolean) = pullToRefreshPref.set(value)
    fun setToolbarSwipeTabs(value: Boolean) = toolbarSwipePref.set(value)
    fun setUnloadInactiveTabs(value: Boolean) = unloadTabsPref.set(value)
    fun setHideToolbarOnScroll(value: Boolean) = hideToolbarPref.set(value)

    /** Seçili DNS sağlayıcısının DoH adresi; kapalıysa null. */
    fun dohUrl(): String? = when (dnsProvider.value) {
        DnsProvider.OFF -> null
        DnsProvider.CUSTOM -> dnsCustomUrl.value.takeIf { it.startsWith("https://") }
        else -> dnsProvider.value.url
    }
}
