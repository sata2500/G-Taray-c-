package com.gtarayici.browser.profiles

import android.content.Context
import androidx.core.content.edit
import com.gtarayici.browser.data.AppDatabase
import com.gtarayici.browser.data.DEFAULT_PROFILE_ID
import com.gtarayici.browser.data.Profile
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Profil renkleri (avatar ve araç çubuğu göstergesi için). */
val PROFILE_COLORS = listOf(
    0xFF1A73E8, 0xFF188038, 0xFFE37400, 0xFFD93025, 0xFF9334E6, 0xFF007B83, 0xFFC5221F, 0xFF5F6368,
)

/**
 * Profilleri ve etkin profili yönetir. Profil listesi Room'da, etkin profil SharedPreferences'ta tutulur.
 */
class ProfileManager(
    context: Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope,
    private val defaultName: String,
) {
    private val prefs = context.getSharedPreferences("gtarayici_profiles", Context.MODE_PRIVATE)

    private val defaultProfile = Profile(DEFAULT_PROFILE_ID, defaultName, 0, contextId = null, createdAt = 0)

    /** Uygulama açılışında profiller hazır olsun diye listeyi bir kez senkron okuruz (küçük tablo). */
    private val initial: List<Profile> = runBlocking(Dispatchers.IO) {
        val list = database.profiles().list()
        if (list.none { it.id == DEFAULT_PROFILE_ID }) {
            database.profiles().upsert(defaultProfile)
            listOf(defaultProfile) + list
        } else {
            list
        }
    }

    val profiles: StateFlow<List<Profile>> =
        database.profiles().all().stateIn(scope, SharingStarted.Eagerly, initial)

    private val activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, DEFAULT_PROFILE_ID) ?: DEFAULT_PROFILE_ID)

    val active: StateFlow<Profile> = combine(profiles, activeId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull { it.id == DEFAULT_PROFILE_ID } ?: defaultProfile
    }.stateIn(scope, SharingStarted.Eagerly, initial.firstOrNull { it.id == activeId.value } ?: defaultProfile)

    val activeProfile: Profile get() = active.value

    fun byContextId(contextId: String?): Profile? = profiles.value.firstOrNull { it.contextId == contextId }

    fun byId(id: String): Profile? = profiles.value.firstOrNull { it.id == id }

    fun setActive(id: String) {
        prefs.edit { putString(KEY_ACTIVE, id) }
        activeId.value = id
    }

    fun create(name: String, colorIndex: Int, locked: Boolean): Profile {
        val id = UUID.randomUUID().toString()
        val profile = Profile(
            id = id,
            name = name.trim().ifBlank { defaultName },
            colorIndex = colorIndex,
            contextId = "gtarayici-profile-$id",
            locked = locked,
        )
        scope.launch(Dispatchers.IO) { database.profiles().upsert(profile) }
        return profile
    }

    fun update(profile: Profile) {
        scope.launch(Dispatchers.IO) { database.profiles().upsert(profile) }
    }

    /** Profili ve ona ait geçmiş/yer imlerini siler. Site verileri çağıran tarafından temizlenir. */
    suspend fun delete(profile: Profile) {
        if (profile.id == DEFAULT_PROFILE_ID) return
        if (activeId.value == profile.id) setActive(DEFAULT_PROFILE_ID)
        withContext(Dispatchers.IO) {
            database.history().clear(profile.id)
            database.bookmarks().clear(profile.id)
            database.logins().clear(profile.id)
            database.profiles().delete(profile.id)
        }
    }

    private companion object {
        const val KEY_ACTIVE = "active_profile"
    }
}
