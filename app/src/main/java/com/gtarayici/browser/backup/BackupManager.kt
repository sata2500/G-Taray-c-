package com.gtarayici.browser.backup

import android.content.Context
import android.net.Uri
import com.gtarayici.browser.BuildConfig
import com.gtarayici.browser.Components
import com.gtarayici.browser.data.Bookmark
import com.gtarayici.browser.data.HistoryEntry
import com.gtarayici.browser.data.LoginRow
import com.gtarayici.browser.data.Profile
import com.gtarayici.browser.vpn.VpnServer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Yedeğe nelerin gireceği. Parolalar yalnızca yedek parolayla şifrelenirse girer. */
data class BackupOptions(val history: Boolean, val password: String?)

/** Geri yüklemede eklenenlerin sayısı (zaten olanlar atlanır, hiçbir şey silinmez). */
data class RestoreSummary(
    val bookmarks: Int = 0,
    val history: Int = 0,
    val profiles: Int = 0,
    val vpnServers: Int = 0,
    val passwords: Int = 0,
    val settings: Boolean = false,
)

/**
 * Yer imleri, geçmiş, profiller, ayarlar, VPN sunucuları ve (şifreli yedekte) parolalar tek dosyaya yazılır ve
 * geri yüklenir. Geri yükleme birleştirir: var olan hiçbir şey silinmez veya değiştirilmez (ayarlar hariç).
 * Site verileri (çerezler, oturumlar) yedeğe girmez; bunlar motorun profilinde durur ve taşınamaz.
 */
class BackupManager(private val context: Context, private val components: Components) {
    private val db get() = components.database

    suspend fun write(uri: Uri, options: BackupOptions) = withContext(Dispatchers.IO) {
        val content = JSONObject()
            .put("app", BuildConfig.VERSION_NAME)
            .put("created", System.currentTimeMillis())
            .put("settings", settingsJson(components.settings.exportAll()))
            .put("profiles", JSONArray(db.profiles().list().filter { it.id != com.gtarayici.browser.data.DEFAULT_PROFILE_ID }.map(::profileJson)))
            .put("bookmarks", JSONArray(db.bookmarks().everything().map(::bookmarkJson)))
            .put("vpn", JSONArray(components.vpn.servers.value.map(::vpnJson)))
        if (options.history) content.put("history", JSONArray(db.history().everything().map(::historyJson)))
        if (!options.password.isNullOrEmpty()) {
            content.put("logins", JSONArray(components.passwords.exportAll().map { (row, plain) -> loginJson(row, plain.username, plain.password) }))
        }
        val text = BackupCodec.encode(content, options.password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Dosya yazılamadı")
    }

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Dosya okunamadı")
    }

    /** [text] yedek dosyasının içeriği; şifreliyse [password] gerekir ([BackupException]). */
    suspend fun restore(text: String, password: String?): RestoreSummary = withContext(Dispatchers.IO) {
        val content = BackupCodec.decode(text, password)
        var summary = RestoreSummary()

        content.optJSONObject("settings")?.let { json ->
            val values = settingsFrom(json)
            withContext(Dispatchers.Main) {
                components.settings.importAll(values)
                components.applyPrivacySettings()
            }
            summary = summary.copy(settings = values.isNotEmpty())
        }

        val existingProfiles = db.profiles().list().map { it.id }.toSet()
        var profiles = 0
        content.optJSONArray("profiles").objects().forEach { o ->
            val profile = Profile(
                id = o.getString("id"),
                name = o.getString("name"),
                colorIndex = o.optInt("colorIndex"),
                contextId = o.optString("contextId").ifEmpty { null },
                locked = o.optBoolean("locked"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
            if (profile.id !in existingProfiles) {
                db.profiles().upsert(profile)
                profiles++
            }
        }
        // Yedekteki profil artık yoksa (silinmişse) kayıtları varsayılan profile girer.
        val knownProfiles = db.profiles().list().map { it.id }.toSet() + com.gtarayici.browser.data.DEFAULT_PROFILE_ID
        fun profileOf(o: JSONObject) = o.optString("profileId").takeIf { it in knownProfiles } ?: com.gtarayici.browser.data.DEFAULT_PROFILE_ID

        val existingBookmarks = db.bookmarks().everything().map { it.profileId to it.url }.toMutableSet()
        var bookmarks = 0
        content.optJSONArray("bookmarks").objects().forEach { o ->
            val profileId = profileOf(o)
            val url = o.optString("url")
            if (url.isEmpty() || !existingBookmarks.add(profileId to url)) return@forEach
            db.bookmarks().upsert(
                Bookmark(profileId = profileId, url = url, title = o.optString("title"), createdAt = o.optLong("createdAt"), folder = o.optString("folder")),
            )
            bookmarks++
        }

        var history = 0
        content.optJSONArray("history").objects().forEach { o ->
            val profileId = profileOf(o)
            val url = o.optString("url")
            if (url.isEmpty() || db.history().find(profileId, url) != null) return@forEach
            db.history().upsert(HistoryEntry(profileId, url, o.optString("title"), o.optLong("lastVisited"), o.optInt("visitCount", 1)))
            history++
        }

        val existingLinks = components.vpn.servers.value.map { it.link }.toSet()
        val newServers = content.optJSONArray("vpn").objects()
            .map { o -> VpnServer(UUID.randomUUID().toString(), o.optString("name"), o.optString("protocol"), o.optString("link"), o.optString("username"), o.optString("password")) }
            .filter { it.link.isNotEmpty() && it.link !in existingLinks }
        if (newServers.isNotEmpty()) withContext(Dispatchers.Main) { components.vpn.add(newServers) }

        var passwords = 0
        content.optJSONArray("logins").objects().forEach { o ->
            val origin = o.optString("origin")
            val row = LoginRow(
                guid = "",
                profileId = profileOf(o),
                origin = origin,
                host = com.gtarayici.browser.passwords.LoginMatching.hostOf(origin),
                formActionOrigin = o.optString("formActionOrigin").ifEmpty { null },
                httpRealm = o.optString("httpRealm").ifEmpty { null },
                usernameField = o.optString("usernameField"),
                passwordField = o.optString("passwordField"),
                username = "",
                password = "",
                timesUsed = o.optLong("timesUsed"),
                timeCreated = o.optLong("timeCreated"),
                timeLastUsed = o.optLong("timeLastUsed"),
                timePasswordChanged = o.optLong("timePasswordChanged"),
            )
            if (components.passwords.import(row, o.optString("username"), o.optString("password"))) passwords++
        }

        summary.copy(bookmarks = bookmarks, history = history, profiles = profiles, vpnServers = newServers.size, passwords = passwords)
    }

    // --- Yer imleri (HTML, diğer tarayıcılarla alışveriş) ------------------------------------

    /** Etkin profile HTML yer imi dosyası aktarır; zaten olanlar atlanır. Eklenen sayısını döner. */
    suspend fun importBookmarksHtml(uri: Uri): Int = withContext(Dispatchers.IO) {
        val profileId = components.profiles.active.value.id
        val existing = db.bookmarks().list(profileId).map { it.url }.toMutableSet()
        var added = 0
        BookmarkHtml.parse(read(uri)).forEach { b ->
            if (!existing.add(b.url)) return@forEach
            db.bookmarks().upsert(
                Bookmark(profileId = profileId, url = b.url, title = b.title, folder = b.folder, createdAt = b.addedAt ?: System.currentTimeMillis()),
            )
            added++
        }
        added
    }

    /** Etkin profilin yer imlerini HTML olarak yazar. Yazılan sayısını döner. */
    suspend fun exportBookmarksHtml(uri: Uri): Int = withContext(Dispatchers.IO) {
        val list = db.bookmarks().list(components.profiles.active.value.id)
        val html = BookmarkHtml.export(list.map { ImportedBookmark(it.url, it.title, it.folder, it.createdAt) })
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(html.toByteArray(Charsets.UTF_8)) } ?: error("Dosya yazılamadı")
        list.size
    }

    // --- JSON ----------------------------------------------------------------------------

    private fun profileJson(p: Profile) = JSONObject()
        .put("id", p.id).put("name", p.name).put("colorIndex", p.colorIndex).put("contextId", p.contextId ?: "")
        .put("locked", p.locked).put("createdAt", p.createdAt)

    private fun bookmarkJson(b: Bookmark) = JSONObject()
        .put("profileId", b.profileId).put("url", b.url).put("title", b.title).put("createdAt", b.createdAt).put("folder", b.folder)

    private fun historyJson(h: HistoryEntry) = JSONObject()
        .put("profileId", h.profileId).put("url", h.url).put("title", h.title).put("lastVisited", h.lastVisited).put("visitCount", h.visitCount)

    private fun vpnJson(s: VpnServer) = JSONObject()
        .put("name", s.name).put("protocol", s.protocol).put("link", s.link).put("username", s.username).put("password", s.password)

    private fun loginJson(row: LoginRow, username: String, password: String) = JSONObject()
        .put("profileId", row.profileId).put("origin", row.origin).put("formActionOrigin", row.formActionOrigin ?: "")
        .put("httpRealm", row.httpRealm ?: "").put("usernameField", row.usernameField).put("passwordField", row.passwordField)
        .put("username", username).put("password", password).put("timesUsed", row.timesUsed).put("timeCreated", row.timeCreated)
        .put("timeLastUsed", row.timeLastUsed).put("timePasswordChanged", row.timePasswordChanged)

    companion object {
        /** Ayar türleri JSON'da kaybolmasın diye her değer türüyle yazılır. */
        fun settingsJson(values: Map<String, Any?>): JSONObject {
            val json = JSONObject()
            values.forEach { (key, value) ->
                val typed = when (value) {
                    is Boolean -> JSONObject().put("t", "b").put("v", value)
                    is Int -> JSONObject().put("t", "i").put("v", value)
                    is Long -> JSONObject().put("t", "l").put("v", value)
                    is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                    is String -> JSONObject().put("t", "s").put("v", value)
                    is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.filterIsInstance<String>()))
                    else -> null
                }
                typed?.let { json.put(key, it) }
            }
            return json
        }

        fun settingsFrom(json: JSONObject): Map<String, Any?> = json.keys().asSequence().mapNotNull { key ->
            val o = json.optJSONObject(key) ?: return@mapNotNull null
            val value: Any? = when (o.optString("t")) {
                "b" -> o.optBoolean("v")
                "i" -> o.optInt("v")
                "l" -> o.optLong("v")
                "f" -> o.optDouble("v").toFloat()
                "s" -> o.optString("v")
                "set" -> o.optJSONArray("v").let { a -> (0 until (a?.length() ?: 0)).map { a!!.getString(it) }.toSet() }
                else -> null
            }
            value?.let { key to it }
        }.toMap()

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
