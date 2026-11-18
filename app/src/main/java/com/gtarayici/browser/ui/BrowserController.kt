package com.gtarayici.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.gtarayici.browser.Components
import com.gtarayici.browser.R
import com.gtarayici.browser.data.Bookmark
import com.gtarayici.browser.data.Profile
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.security.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.Engine.BrowsingData

/** Tam ekran kaplayan ikincil ekranlar. */
enum class Overlay { NONE, SEARCH, TABS, HISTORY, BOOKMARKS, DOWNLOADS, SETTINGS, PROFILES, EXTENSIONS, VPN }

/**
 * Arayüzün tarayıcı üzerinde yaptığı tüm işlemleri tek bir yerde toplar.
 */
class BrowserController(
    private val activity: FragmentActivity,
    private val components: Components,
    private val captureThumbnail: () -> Unit = {},
) {
    var overlay by mutableStateOf(Overlay.NONE)
    var menuOpen by mutableStateOf(false)
    var findInPageOpen by mutableStateOf(false)
    var youtubeUrl by mutableStateOf<String?>(null)
    var profileSwitcherOpen by mutableStateOf(false)

    /** Görünür sekme yokken yeni sekmelerin gizli açılıp açılmayacağı. */
    var privateMode by mutableStateOf(false)

    private val store get() = components.store
    private val profile: Profile get() = components.profiles.activeProfile

    /** Seçili sekme, yalnızca etkin profile aitse görünür kabul edilir. */
    val selectedTab: TabSessionState?
        get() = store.state.selectedTab?.takeIf { it.contextId == profile.contextId }

    init {
        privateMode = selectedTab?.content?.private ?: false
    }

    fun open(overlay: Overlay) {
        menuOpen = false
        if (overlay == Overlay.TABS) captureThumbnail()
        this.overlay = overlay
    }

    fun closeOverlay() {
        overlay = Overlay.NONE
    }

    /** Adres çubuğuna yazılanı açar: adresse gider, değilse arar. */
    fun submit(input: String) {
        val url = UrlInput.toLoadableUrl(input, components.settings.searchEngine.value)
        loadUrl(url)
        closeOverlay()
    }

    fun loadUrl(url: String, inNewTab: Boolean = false, private: Boolean? = null) {
        val tab = selectedTab
        if (tab == null || inNewTab) {
            components.tabsUseCases.addTab(
                url,
                selectTab = true,
                private = private ?: tab?.content?.private ?: privateMode,
                contextId = profile.contextId,
            )
        } else {
            components.sessionUseCases.loadUrl(url, tab.id)
        }
        closeOverlay()
    }

    fun newTab(private: Boolean) {
        menuOpen = false
        privateMode = private
        components.tabsUseCases.addTab("about:blank", selectTab = true, private = private, contextId = profile.contextId)
        overlay = Overlay.SEARCH
    }

    fun selectTab(id: String) {
        components.tabsUseCases.selectTab(id)
        privateMode = store.state.tabs.firstOrNull { it.id == id }?.content?.private ?: privateMode
        closeOverlay()
    }

    fun closeTab(id: String) {
        components.tabsUseCases.removeTab(id, selectParentIfExists = true)
    }

    fun closeAllTabs(private: Boolean) {
        val ids = store.state.tabs.filter { it.content.private == private && it.contextId == profile.contextId }.map { it.id }
        components.tabsUseCases.removeTabs(ids)
    }

    fun goBack() = selectedTab?.let { components.sessionUseCases.goBack(it.id) }

    fun goForward() = selectedTab?.let { components.sessionUseCases.goForward(it.id) }

    fun reloadOrStop() {
        val tab = selectedTab ?: return
        if (tab.content.loading) components.sessionUseCases.stopLoading(tab.id)
        else components.sessionUseCases.reload(tab.id)
    }

    fun toggleDesktopMode() {
        val tab = selectedTab ?: return
        components.sessionUseCases.requestDesktopSite(!tab.content.desktopMode, tab.id)
        menuOpen = false
    }

    fun share() {
        val tab = selectedTab ?: return
        menuOpen = false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, tab.content.title)
            putExtra(Intent.EXTRA_TEXT, tab.content.url)
        }
        activity.startActivity(Intent.createChooser(send, null))
    }

    fun copyLink() {
        val tab = selectedTab ?: return
        menuOpen = false
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", tab.content.url))
        Toast.makeText(activity, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    fun toggleBookmark(isBookmarked: Boolean) {
        val tab = selectedTab ?: return
        menuOpen = false
        val dao = components.database.bookmarks()
        val profileId = profile.id
        components.applicationScope.launch(Dispatchers.IO) {
            if (isBookmarked) {
                dao.deleteByUrl(profileId, tab.content.url)
            } else {
                dao.upsert(
                    Bookmark(
                        profileId = profileId,
                        url = tab.content.url,
                        title = tab.content.title.ifBlank { UrlInput.host(tab.content.url) },
                    ),
                )
            }
        }
    }

    fun openFindInPage() {
        menuOpen = false
        findInPageOpen = true
    }

    fun closeFindInPage() {
        findInPageOpen = false
        selectedTab?.engineState?.engineSession?.clearFindMatches()
    }

    fun openYouTubeDownload() {
        menuOpen = false
        youtubeUrl = selectedTab?.content?.url
    }

    /** Sitede bir web uygulaması bildirimi varsa uygulama olarak, yoksa kısayol olarak ana ekrana ekler. */
    fun addToHomescreen() {
        menuOpen = false
        if (!components.webAppUseCases.isPinningSupported()) {
            Toast.makeText(activity, R.string.webapp_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        components.applicationScope.launch {
            components.webAppUseCases.addToHomescreen()
        }
    }

    // --- Profiller ------------------------------------------------------------------------

    /** Profile geçer; profil kilitliyse önce kimlik doğrulaması ister. */
    fun switchProfile(target: Profile) {
        profileSwitcherOpen = false
        if (target.id == profile.id) return
        val apply = {
            components.profiles.setActive(target.id)
            val last = store.state.tabs
                .filter { it.contextId == target.contextId && !it.content.private }
                .maxByOrNull { it.lastAccess }
            if (last != null) components.tabsUseCases.selectTab(last.id)
            privateMode = false
            closeOverlay()
        }
        if (target.locked) {
            components.appLock.authenticate(
                activity,
                AppLock.profileKey(target.id),
                activity.getString(R.string.lock_title_profile, target.name),
            ) { ok -> if (ok) apply() }
        } else {
            apply()
        }
    }

    fun deleteProfile(target: Profile) {
        components.applicationScope.launch {
            val ids = store.state.tabs.filter { it.contextId == target.contextId }.map { it.id }
            if (ids.isNotEmpty()) components.tabsUseCases.removeTabs(ids)
            target.contextId?.let { components.runtime.storageController.clearDataForSessionContext(it) }
            components.profiles.delete(target)
        }
    }

    fun unlock(key: String, title: String) {
        components.appLock.authenticate(activity, key, title) { }
    }

    fun clearBrowsingData() {
        components.applicationScope.launch {
            components.tabsUseCases.removeAllTabs()
            withContext(Dispatchers.IO) { components.database.history().clearAll() }
            components.engine.clearData(BrowsingData.all())
            Toast.makeText(activity, R.string.settings_data_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Geri tuşu: önce açık ekranları kapatır, sonra sayfa geçmişinde geri gider,
     * en sonda içinden açıldığı sekmeye döner. İşlenemezse false döner.
     */
    fun handleBack(): Boolean {
        when {
            menuOpen -> menuOpen = false
            profileSwitcherOpen -> profileSwitcherOpen = false
            youtubeUrl != null -> youtubeUrl = null
            overlay != Overlay.NONE -> closeOverlay()
            findInPageOpen -> closeFindInPage()
            selectedTab?.content?.canGoBack == true -> goBack()
            selectedTab?.parentId != null -> selectedTab?.let { closeTab(it.id) }
            else -> return false
        }
        return true
    }
}
