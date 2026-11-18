package com.gtarayici.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gtarayici.browser.Components
import com.gtarayici.browser.R
import com.gtarayici.browser.data.Bookmark
import com.gtarayici.browser.search.UrlInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.Engine.BrowsingData

/** Tam ekran kaplayan ikincil ekranlar. */
enum class Overlay { NONE, SEARCH, TABS, HISTORY, BOOKMARKS, DOWNLOADS, SETTINGS }

/**
 * Arayüzün tarayıcı üzerinde yaptığı tüm işlemleri tek bir yerde toplar.
 */
class BrowserController(private val context: Context, private val components: Components) {
    var overlay by mutableStateOf(Overlay.NONE)
    var menuOpen by mutableStateOf(false)

    /** Hiç sekme seçili değilken yeni sekmelerin gizli açılıp açılmayacağı. */
    var privateMode by mutableStateOf(components.store.state.selectedTab?.content?.private ?: false)

    private val store get() = components.store
    val selectedTab: TabSessionState? get() = store.state.selectedTab

    fun open(overlay: Overlay) {
        menuOpen = false
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

    fun loadUrl(url: String, inNewTab: Boolean = false) {
        val tab = selectedTab
        if (tab == null || inNewTab) {
            components.tabsUseCases.addTab(url, selectTab = true, private = tab?.content?.private ?: privateMode)
        } else {
            components.sessionUseCases.loadUrl(url, tab.id)
        }
        closeOverlay()
    }

    fun newTab(private: Boolean) {
        menuOpen = false
        privateMode = private
        components.tabsUseCases.addTab("about:blank", selectTab = true, private = private)
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
        if (private) components.tabsUseCases.removePrivateTabs() else components.tabsUseCases.removeNormalTabs()
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
        context.startActivity(
            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun copyLink() {
        val tab = selectedTab ?: return
        menuOpen = false
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", tab.content.url))
        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }

    fun toggleBookmark(isBookmarked: Boolean) {
        val tab = selectedTab ?: return
        menuOpen = false
        val dao = components.database.bookmarks()
        components.applicationScope.launch(Dispatchers.IO) {
            if (isBookmarked) {
                dao.deleteByUrl(tab.content.url)
            } else {
                dao.upsert(Bookmark(url = tab.content.url, title = tab.content.title.ifBlank { UrlInput.host(tab.content.url) }))
            }
        }
    }

    fun clearBrowsingData() {
        components.applicationScope.launch {
            components.tabsUseCases.removeAllTabs()
            withContext(Dispatchers.IO) { components.database.history().clear() }
            components.engine.clearData(BrowsingData.all())
            Toast.makeText(context, R.string.settings_data_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Geri tuşu: önce açık ekranları kapatır, sonra sayfa geçmişinde geri gider,
     * en sonda içinden açıldığı sekmeye döner. İşlenemezse false döner.
     */
    fun handleBack(): Boolean {
        when {
            menuOpen -> menuOpen = false
            overlay != Overlay.NONE -> closeOverlay()
            selectedTab?.content?.canGoBack == true -> goBack()
            selectedTab?.parentId != null -> selectedTab?.let { closeTab(it.id) }
            else -> return false
        }
        return true
    }
}
