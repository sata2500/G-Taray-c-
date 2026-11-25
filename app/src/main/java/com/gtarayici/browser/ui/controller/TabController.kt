package com.gtarayici.browser.ui.controller

import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.Overlay
import mozilla.components.browser.state.action.UndoAction

/** Sekme işlemleri: açma, seçme, kapatma, geri getirme. */
interface TabActions {
    /** Adres çubuğuna yazılanı açar: adresse gider, değilse arar. */
    fun submit(input: String)
    fun loadUrl(url: String, inNewTab: Boolean = false, private: Boolean? = null)
    fun newTab(private: Boolean)

    /** Gruba yeni boş sekme ekler ve adres girişini açar. */
    fun newTabInGroup(groupId: String)
    fun selectTab(id: String)

    /** Gizli sekmelerden çıkar: bu profilin son normal sekmesine döner, yoksa boş bir normal sekme açar. */
    fun leavePrivate()
    fun closeTab(id: String)
    fun closeCurrentTab()
    fun closeAllTabs(private: Boolean)

    /** Aynı türdeki (normal/gizli) sekmeler arasında bir öncekine veya sonrakine geçer. */
    fun switchAdjacentTab(delta: Int)

    /** Son kapatılan sekmeleri geri getirir. */
    fun undoCloseTabs()

    /** Veriler temizlenince kapatılan sekmeler "Geri al" ile geri getirilemez. */
    fun forgetClosedTabs()
}

class TabController(private val ctx: ControllerContext) : TabActions {
    private val components get() = ctx.components
    private val store get() = ctx.store

    override fun submit(input: String) {
        val url = UrlInput.toLoadableUrl(input, components.settings.searchEngine.value)
        loadUrl(url)
        ctx.ui.closeOverlay()
    }

    override fun loadUrl(url: String, inNewTab: Boolean, private: Boolean?) {
        val tab = ctx.selectedTab
        if (tab == null || inNewTab) {
            components.tabsUseCases.addTab(
                url,
                selectTab = true,
                private = private ?: tab?.content?.private ?: ctx.ui.privateMode,
                contextId = ctx.profile.contextId,
            )
        } else {
            components.sessionUseCases.loadUrl(url, tab.id)
        }
        ctx.ui.closeOverlay()
    }

    override fun newTab(private: Boolean) {
        ctx.ui.menuOpen = false
        ctx.ui.privateMode = private
        components.tabsUseCases.addTab("about:blank", selectTab = true, private = private, contextId = ctx.profile.contextId)
        ctx.ui.overlay = Overlay.SEARCH
    }

    override fun newTabInGroup(groupId: String) {
        ctx.ui.privateMode = false
        val id = components.tabsUseCases.addTab("about:blank", selectTab = true, private = false, contextId = ctx.profile.contextId)
        components.tabGroups.addTab(groupId, id)
        ctx.ui.overlay = Overlay.SEARCH
    }

    override fun leavePrivate() {
        val last = store.state.tabs
            .filter { it.contextId == ctx.profile.contextId && !it.content.private }
            .maxByOrNull { it.lastAccess }
        ctx.ui.privateMode = false
        if (last != null) {
            components.tabsUseCases.selectTab(last.id)
        } else {
            components.tabsUseCases.addTab("about:blank", selectTab = true, private = false, contextId = ctx.profile.contextId)
        }
        ctx.ui.closeOverlay()
    }

    override fun selectTab(id: String) {
        components.tabsUseCases.selectTab(id)
        ctx.ui.privateMode = store.state.tabs.firstOrNull { it.id == id }?.content?.private ?: ctx.ui.privateMode
        ctx.ui.closeOverlay()
    }

    override fun closeTab(id: String) {
        components.tabsUseCases.removeTab(id, selectParentIfExists = true)
    }

    override fun closeCurrentTab() {
        ctx.selectedTab?.let { closeTab(it.id) }
    }

    override fun closeAllTabs(private: Boolean) {
        val ids = store.state.tabs.filter { it.content.private == private && it.contextId == ctx.profile.contextId }.map { it.id }
        components.tabsUseCases.removeTabs(ids)
    }

    override fun switchAdjacentTab(delta: Int) {
        val current = ctx.selectedTab ?: return
        val tabs = store.state.tabs.filter { it.contextId == ctx.profile.contextId && it.content.private == current.content.private }
        val index = tabs.indexOfFirst { it.id == current.id }
        val target = tabs.getOrNull(index + delta) ?: return
        components.tabsUseCases.selectTab(target.id)
    }

    override fun undoCloseTabs() {
        components.tabsUseCases.undo.invoke()
    }

    override fun forgetClosedTabs() {
        store.dispatch(UndoAction.ClearRecoverableTabs(store.state.undoHistory.tag ?: return))
    }
}
