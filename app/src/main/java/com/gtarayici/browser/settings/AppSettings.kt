package com.gtarayici.browser.settings

import android.content.Context
import androidx.core.content.edit
import com.gtarayici.browser.search.SearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Uygulama ayarları. Varsayılanlar "kur ve kullan" ilkesine göre en güvenli seçeneklerdir.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("gtarayici_settings", Context.MODE_PRIVATE)

    private val _searchEngine = MutableStateFlow(
        runCatching { SearchEngine.valueOf(prefs.getString(KEY_SEARCH, null) ?: "") }
            .getOrDefault(SearchEngine.DUCKDUCKGO),
    )
    val searchEngine: StateFlow<SearchEngine> = _searchEngine.asStateFlow()

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _strictTracking = MutableStateFlow(prefs.getBoolean(KEY_STRICT, true))
    val strictTracking: StateFlow<Boolean> = _strictTracking.asStateFlow()

    private val _httpsOnly = MutableStateFlow(prefs.getBoolean(KEY_HTTPS_ONLY, true))
    val httpsOnly: StateFlow<Boolean> = _httpsOnly.asStateFlow()

    private val _gpc = MutableStateFlow(prefs.getBoolean(KEY_GPC, true))
    val globalPrivacyControl: StateFlow<Boolean> = _gpc.asStateFlow()

    fun setSearchEngine(value: SearchEngine) {
        prefs.edit { putString(KEY_SEARCH, value.name) }
        _searchEngine.value = value
    }

    fun setTheme(value: ThemeMode) {
        prefs.edit { putString(KEY_THEME, value.name) }
        _theme.value = value
    }

    fun setStrictTracking(value: Boolean) {
        prefs.edit { putBoolean(KEY_STRICT, value) }
        _strictTracking.value = value
    }

    fun setHttpsOnly(value: Boolean) {
        prefs.edit { putBoolean(KEY_HTTPS_ONLY, value) }
        _httpsOnly.value = value
    }

    fun setGlobalPrivacyControl(value: Boolean) {
        prefs.edit { putBoolean(KEY_GPC, value) }
        _gpc.value = value
    }

    private companion object {
        const val KEY_SEARCH = "search_engine"
        const val KEY_THEME = "theme"
        const val KEY_STRICT = "strict_tracking"
        const val KEY_HTTPS_ONLY = "https_only"
        const val KEY_GPC = "global_privacy_control"
    }
}
