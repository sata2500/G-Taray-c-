package com.gtarayici.browser.passwords

import com.gtarayici.browser.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.LoginValidationDelegate
import mozilla.components.feature.prompts.login.LoginExceptions
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/**
 * Motor ile parola deposu arasındaki köprü: motor bir giriş formu görünce kayıtları sorar ([onLoginFetch]),
 * kullanıcı "Kaydet"e basınca kaydı iletir ([onLoginSave]), doldurulan kaydı bildirir ([onLoginUsed]).
 */
class GeckoLoginStorage(
    private val store: PasswordStore,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) : Autocomplete.StorageDelegate {

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> {
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        if (!settings.autofillPasswords.value) return GeckoResult.fromValue(emptyArray())
        scope.launch {
            val logins = runCatching { store.forDomain(domain) }.getOrDefault(emptyList())
            result.complete(
                logins.map { login ->
                    Autocomplete.LoginEntry.Builder()
                        .guid(login.guid)
                        .origin(login.origin)
                        .formActionOrigin(login.formActionOrigin)
                        .httpRealm(login.httpRealm)
                        .username(login.username)
                        .password(login.password)
                        .build()
                }.toTypedArray(),
            )
        }
        return result
    }

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        scope.launch {
            store.save(
                LoginEntry(
                    origin = login.origin,
                    formActionOrigin = login.formActionOrigin,
                    httpRealm = login.httpRealm,
                    usernameField = "",
                    passwordField = "",
                    username = login.username,
                    password = login.password,
                ),
                guid = login.guid,
            )
        }
    }

    override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) {
        val guid = login.guid ?: return
        scope.launch { store.touch(guid) }
    }

    /** "Kaydet" penceresinin "Güncelle" mi "Kaydet" mi diyeceğine karar verir. */
    val validation = object : LoginValidationDelegate {
        override fun shouldUpdateOrCreateAsync(entry: LoginEntry): Deferred<LoginValidationDelegate.Result> = scope.async {
            store.findLoginToUpdate(entry)?.let { LoginValidationDelegate.Result.CanBeUpdated(it) }
                ?: LoginValidationDelegate.Result.CanBeCreated
        }
    }

    /** "Bu sitede asla" seçilen siteler. */
    val exceptions = object : LoginExceptions {
        override suspend fun isLoginExceptionByOrigin(origin: String): Boolean = store.isNeverSave(origin)

        override suspend fun addLoginException(origin: String) = store.addNeverSave(origin)
    }
}
