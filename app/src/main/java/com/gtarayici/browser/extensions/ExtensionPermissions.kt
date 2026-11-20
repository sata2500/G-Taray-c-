package com.gtarayici.browser.extensions

import com.gtarayici.browser.R

/**
 * Eklenti izinlerini kullanıcının anlayacağı açıklamalara çevirir ("tabs" → "Tarayıcı sekmelerine erişme").
 * Firefox'taki gibi uyarı gerektirmeyen izinler (storage, alarms…) gösterilmez; tanınmayan izinler şeffaflık
 * için ham adıyla kalır.
 */
object ExtensionPermissions {
    sealed interface Line {
        data class Text(val res: Int) : Line
        data class WithArg(val res: Int, val arg: String) : Line
        data class Raw(val text: String) : Line
    }

    private val DESCRIBED = mapOf(
        "tabs" to R.string.ext_perm_tabs,
        "history" to R.string.ext_perm_history,
        "topSites" to R.string.ext_perm_history,
        "bookmarks" to R.string.ext_perm_bookmarks,
        "downloads" to R.string.ext_perm_downloads,
        "downloads.open" to R.string.ext_perm_downloads_open,
        "clipboardRead" to R.string.ext_perm_clipboard_read,
        "clipboardWrite" to R.string.ext_perm_clipboard_write,
        "geolocation" to R.string.ext_perm_geolocation,
        "notifications" to R.string.ext_perm_notifications,
        "privacy" to R.string.ext_perm_privacy,
        "browserSettings" to R.string.ext_perm_browser_settings,
        "proxy" to R.string.ext_perm_proxy,
        "management" to R.string.ext_perm_management,
        "nativeMessaging" to R.string.ext_perm_native_messaging,
        "webNavigation" to R.string.ext_perm_web_navigation,
        "sessions" to R.string.ext_perm_sessions,
        "tabHide" to R.string.ext_perm_tab_hide,
        "declarativeNetRequest" to R.string.ext_perm_block_content,
        "declarativeNetRequestFeedback" to R.string.ext_perm_history,
        "pkcs11" to R.string.ext_perm_pkcs11,
    )

    /** Kullanıcıya ayrıca söylenmesi gerekmeyen izinler. */
    private val SILENT = setOf(
        "storage", "unlimitedStorage", "alarms", "contextMenus", "menus", "menus.overrideContext", "activeTab",
        "webRequest", "webRequestBlocking", "webRequestFilterResponse", "webRequestFilterResponse.serviceWorkerScript",
        "cookies", "idle", "dns", "scripting", "theme", "identity", "search", "contextualIdentities",
        "captivePortal", "browsingData", "find", "userScripts", "offscreen",
    )

    private val ALL_SITES = setOf("<all_urls>", "*://*/*", "http://*/*", "https://*/*")

    fun describe(permissions: List<String>): List<Line> {
        val lines = mutableListOf<Line>()
        val hosts = mutableListOf<String>()
        var allSites = false
        for (p in permissions.distinct()) {
            when {
                p in ALL_SITES -> allSites = true
                p in SILENT -> Unit
                DESCRIBED[p] != null -> lines += Line.Text(DESCRIBED.getValue(p))
                p.contains("://") -> host(p)?.let { hosts += it } ?: run { lines += Line.Raw(p) }
                else -> lines += Line.Raw(p)
            }
        }
        val siteLines = when {
            allSites -> listOf(Line.Text(R.string.ext_perm_all_sites))
            hosts.isEmpty() -> emptyList()
            hosts.distinct().size <= 3 -> hosts.distinct().map { Line.WithArg(R.string.ext_perm_site, it) }
            else -> listOf(Line.WithArg(R.string.ext_perm_many_sites, hosts.distinct().size.toString()))
        }
        // Site erişimi en önemli izindir, başa yazılır; yinelenen açıklamalar (ör. history/topSites) birleştirilir.
        return (siteLines + lines).distinct()
    }

    // Site deseninden alan adı: "https://<yıldız>.example.com/<yol>" → "example.com"
    internal fun host(pattern: String): String? {
        val rest = pattern.substringAfter("://", "")
        val host = rest.substringBefore('/').removePrefix("*.").substringBefore(':')
        return host.takeIf { it.isNotEmpty() && it != "*" }
    }
}
