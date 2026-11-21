package com.gtarayici.browser.ui.controller

import com.gtarayici.browser.R
import com.gtarayici.browser.data.Profile
import com.gtarayici.browser.security.AppLock
import com.gtarayici.browser.ui.feedback.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.Engine.BrowsingData

/** Profiller, kilitler ve tarama verisi: profil değiştirme/silme, kilit açma, verileri temizleme. */
interface ProfileActions {
    /** Profile geçer; profil kilitliyse önce kimlik doğrulaması ister. */
    fun switchProfile(target: Profile)
    fun deleteProfile(target: Profile)
    fun unlock(key: String, title: String)
    fun clearBrowsingData()
}

class ProfileController(private val ctx: ControllerContext, private val tabs: TabActions) : ProfileActions {
    private val components get() = ctx.components
    private val store get() = ctx.store

    override fun switchProfile(target: Profile) {
        ctx.ui.profileSwitcherOpen = false
        if (target.id == ctx.profile.id) return
        val apply = {
            components.profiles.setActive(target.id)
            val last = store.state.tabs
                .filter { it.contextId == target.contextId && !it.content.private }
                .maxByOrNull { it.lastAccess }
            if (last != null) components.tabsUseCases.selectTab(last.id)
            ctx.ui.privateMode = false
            ctx.ui.closeOverlay()
        }
        if (target.locked) {
            components.appLock.authenticate(
                ctx.activity,
                AppLock.profileKey(target.id),
                ctx.activity.getString(R.string.lock_title_profile, target.name),
            ) { ok -> if (ok) apply() }
        } else {
            apply()
        }
    }

    override fun deleteProfile(target: Profile) {
        components.applicationScope.launch {
            val ids = store.state.tabs.filter { it.contextId == target.contextId }.map { it.id }
            if (ids.isNotEmpty()) components.tabsUseCases.removeTabs(ids)
            tabs.forgetClosedTabs()
            target.contextId?.let { components.runtime.storageController.clearDataForSessionContext(it) }
            components.profiles.delete(target)
        }
    }

    override fun unlock(key: String, title: String) {
        components.appLock.authenticate(ctx.activity, key, title) { }
    }

    override fun clearBrowsingData() {
        components.applicationScope.launch {
            components.tabsUseCases.removeAllTabs()
            tabs.forgetClosedTabs()
            withContext(Dispatchers.IO) { components.database.history().clearAll() }
            components.engine.clearData(BrowsingData.all())
            ctx.messenger.success(UiText.res(R.string.settings_data_cleared))
        }
    }
}
