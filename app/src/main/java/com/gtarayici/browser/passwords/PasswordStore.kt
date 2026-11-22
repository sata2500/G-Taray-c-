package com.gtarayici.browser.passwords

import android.content.Context
import androidx.core.content.edit
import com.gtarayici.browser.data.LoginDao
import com.gtarayici.browser.data.LoginRow
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginEntry

/** Ekranda gösterilen, şifresi çözülmüş kayıt. */
data class SavedLogin(
    val guid: String,
    val origin: String,
    val username: String,
    val password: String,
    val timesUsed: Long,
    val timeLastUsed: Long,
    val timePasswordChanged: Long,
) {
    val host: String get() = LoginMatching.displayHost(origin)
}

/**
 * Kayıtlı parolalar. Her profilin parolaları ayrıdır; motor hangi sekmenin sorduğunu söylemediği için etkin
 * profilinkiler kullanılır. Parolalar diskte yalnızca şifreli durur.
 */
class PasswordStore(
    context: Context,
    private val dao: LoginDao,
    private val cipher: LoginCipher,
    private val activeProfileId: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.getSharedPreferences("gtarayici_logins", Context.MODE_PRIVATE)
    private val _neverSaveVersion = MutableStateFlow(0)

    /** İstisna listesi değiştikçe artar; ekran [neverSave] listesini yeniden okur. */
    val neverSaveVersion: StateFlow<Int> = _neverSaveVersion.asStateFlow()

    /** Parola kaydedilmesi istenmeyen siteler (etkin profil). */
    fun neverSave(): Set<String> = prefs.getStringSet(neverKey(), null).orEmpty().toSet()

    fun logins(profileId: String): Flow<List<SavedLogin>> =
        dao.all(profileId).map { rows -> rows.mapNotNull(::open) }.flowOn(Dispatchers.Default)

    /** Motorun sorduğu alan adına ait kayıtlar. */
    suspend fun forDomain(domain: String): List<Login> = io {
        dao.allOnce(activeProfileId())
            .filter { LoginMatching.belongsTo(it.host, domain) }
            .mapNotNull { row -> open(row)?.let { row.toLogin(it) } }
    }

    /** Kaydedilecek giriş mevcut bir kaydı mı güncelliyor (aynı site + aynı kullanıcı adı)? */
    suspend fun findLoginToUpdate(entry: LoginEntry): Login? = io {
        dao.allOnce(activeProfileId())
            .mapNotNull { row -> open(row)?.let { row to it } }
            .firstOrNull { (_, login) -> LoginMatching.sameAccount(login.origin, login.username, entry.origin, entry.username) }
            ?.let { (row, login) -> row.toLogin(login) }
    }

    /** Motorun "kaydet" onayıyla gelen giriş: aynı hesap varsa parolası güncellenir, yoksa eklenir. */
    suspend fun save(entry: LoginEntry, guid: String? = null) = io {
        if (entry.password.isEmpty()) return@io
        val profile = activeProfileId()
        val now = clock()
        val existing = guid?.let { dao.get(it) }
            ?: dao.allOnce(profile).firstOrNull { row ->
                open(row)?.let { LoginMatching.sameAccount(it.origin, it.username, entry.origin, entry.username) } == true
            }
        val previous = existing?.let(::open)
        dao.upsert(
            LoginRow(
                guid = existing?.guid ?: UUID.randomUUID().toString(),
                profileId = existing?.profileId ?: profile,
                origin = entry.origin,
                host = LoginMatching.hostOf(entry.origin),
                formActionOrigin = entry.formActionOrigin ?: existing?.formActionOrigin,
                httpRealm = entry.httpRealm ?: existing?.httpRealm,
                usernameField = entry.usernameField.ifEmpty { existing?.usernameField.orEmpty() },
                passwordField = entry.passwordField.ifEmpty { existing?.passwordField.orEmpty() },
                username = cipher.encrypt(entry.username),
                password = cipher.encrypt(entry.password),
                timesUsed = existing?.timesUsed ?: 0,
                timeCreated = existing?.timeCreated ?: now,
                timeLastUsed = existing?.timeLastUsed ?: now,
                timePasswordChanged = if (existing != null && previous?.password == entry.password) existing.timePasswordChanged else now,
            ),
        )
    }

    /** Elle düzenleme (parola ekranı). */
    suspend fun edit(guid: String, username: String, password: String) = io {
        val row = dao.get(guid) ?: return@io
        val previous = open(row)
        dao.upsert(
            row.copy(
                username = cipher.encrypt(username),
                password = cipher.encrypt(password),
                timePasswordChanged = if (previous?.password == password) row.timePasswordChanged else clock(),
            ),
        )
    }

    /** Elle ekleme (parola ekranı). */
    suspend fun add(origin: String, username: String, password: String) =
        save(LoginEntry(origin = origin, formActionOrigin = origin, httpRealm = null, usernameField = "", passwordField = "", username = username, password = password))

    /** Yedek için tüm profillerin kayıtları, şifresi çözülmüş olarak (yedek ayrıca parolayla şifrelenir). */
    suspend fun exportAll(): List<Pair<LoginRow, SavedLogin>> = io {
        dao.everything().mapNotNull { row -> open(row)?.let { row to it } }
    }

    /** Yedekten gelen kayıt; aynı profilde aynı hesap varsa atlanır. Eklendiyse true. */
    suspend fun import(row: LoginRow, username: String, password: String): Boolean = io {
        val exists = dao.allOnce(row.profileId).any { existing ->
            open(existing)?.let { LoginMatching.sameAccount(it.origin, it.username, row.origin, username) } == true
        }
        if (exists || password.isEmpty()) return@io false
        dao.upsert(row.copy(guid = UUID.randomUUID().toString(), username = cipher.encrypt(username), password = cipher.encrypt(password)))
        true
    }

    suspend fun touch(guid: String) = io {
        val row = dao.get(guid) ?: return@io
        dao.upsert(row.copy(timesUsed = row.timesUsed + 1, timeLastUsed = clock()))
    }

    suspend fun delete(guid: String): LoginRow? = io { dao.get(guid)?.also { dao.delete(guid) } }

    /** "Geri al" için silinen kaydı aynen geri koyar. */
    suspend fun restore(row: LoginRow) = io { dao.upsert(row) }

    fun isNeverSave(origin: String): Boolean = LoginMatching.normalizeOrigin(origin) in neverSave()

    fun addNeverSave(origin: String) = updateNeverSave { it + LoginMatching.normalizeOrigin(origin) }

    fun removeNeverSave(origin: String) = updateNeverSave { it - origin }

    private fun updateNeverSave(change: (Set<String>) -> Set<String>) {
        prefs.edit { putStringSet(neverKey(), change(neverSave())) }
        _neverSaveVersion.value++
    }

    private fun neverKey() = "never_save:${activeProfileId()}"

    /** Şifresi çözülemeyen kayıt (ör. anahtar silinmiş) gösterilmez; uygulamayı çökertmez. */
    private fun open(row: LoginRow): SavedLogin? = runCatching {
        SavedLogin(
            guid = row.guid,
            origin = row.origin,
            username = cipher.decrypt(row.username),
            password = cipher.decrypt(row.password),
            timesUsed = row.timesUsed,
            timeLastUsed = row.timeLastUsed,
            timePasswordChanged = row.timePasswordChanged,
        )
    }.getOrNull()

    private fun LoginRow.toLogin(plain: SavedLogin) = Login(
        guid = guid,
        username = plain.username,
        password = plain.password,
        origin = origin,
        formActionOrigin = formActionOrigin,
        httpRealm = httpRealm,
        usernameField = usernameField,
        passwordField = passwordField,
        timesUsed = timesUsed,
        timeCreated = timeCreated,
        timeLastUsed = timeLastUsed,
        timePasswordChanged = timePasswordChanged,
    )

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
