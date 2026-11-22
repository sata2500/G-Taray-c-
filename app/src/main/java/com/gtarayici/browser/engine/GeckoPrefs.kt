package com.gtarayici.browser.engine

import mozilla.components.support.base.log.logger.Logger
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoPreferenceController

/**
 * GeckoView tercihlerini (about:config) koddan ayarlar. GeckoRuntime oluşturulduktan sonra çağrılmalıdır.
 * Kullanıcı dalına yazılan değerler Gecko profilinde kalıcıdır.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalGeckoViewApi::class])
object GeckoPrefs {
    private val logger = Logger("GeckoPrefs")

    fun set(name: String, value: Any) {
        val branch = GeckoPreferenceController.PREF_BRANCH_USER
        val result = when (value) {
            is Boolean -> GeckoPreferenceController.setGeckoPref(name, value, branch)
            is Int -> GeckoPreferenceController.setGeckoPref(name, value, branch)
            is String -> GeckoPreferenceController.setGeckoPref(name, value, branch)
            else -> error("Desteklenmeyen tercih türü: $name")
        }
        result.exceptionally<Void> { error ->
            logger.warn("Tercih ayarlanamadı: $name", error)
            null
        }
    }

    /** Kullanıcı dalındaki değeri siler; tercih Gecko'nun varsayılanına döner. */
    fun clear(name: String) {
        GeckoPreferenceController.clearGeckoUserPref(name).exceptionally<Void> { error ->
            logger.warn("Tercih sıfırlanamadı: $name", error)
            null
        }
    }
}
