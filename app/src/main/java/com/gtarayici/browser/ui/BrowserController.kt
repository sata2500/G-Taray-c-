package com.gtarayici.browser.ui

import com.gtarayici.browser.components
import com.gtarayici.browser.ui.feedback.UiText
import android.content.ClipData
import androidx.core.content.edit
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.gtarayici.browser.Components
import com.gtarayici.browser.R
import com.gtarayici.browser.data.Bookmark
import com.gtarayici.browser.data.Profile
import com.gtarayici.browser.reader.ReaderControls
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.security.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.action.UndoAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.Engine.BrowsingData
import mozilla.components.feature.readerview.ReaderViewFeature

/** Tam ekran kaplayan ikincil ekranlar. */
enum class Overlay { NONE, SEARCH, TABS, HISTORY, BOOKMARKS, DOWNLOADS, SETTINGS, PROFILES, EXTENSIONS, VPN, QR, REPORT }

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
    var installDialogOpen by mutableStateOf(false)
    var translateOpen by mutableStateOf(false)
    var siteInfoOpen by mutableStateOf(false)

    /** İlk açılış tanıtımı gösteriliyor mu (yalnızca yeni kurulumda, bir kez). */
    var onboarding by mutableStateOf(false)

    /** Tanıtım bitince çağrılır (ör. bildirim izni o zaman istenir). */
    var onOnboardingFinished: () -> Unit = {}

    fun finishOnboarding() {
        onboarding = false
        activity.getSharedPreferences("gtarayici_state", android.content.Context.MODE_PRIVATE).edit { putBoolean(KEY_ONBOARDED, true) }
        onOnboardingFinished()
    }

    /** Kaydırınca gizlenen adres çubuğunun durumu. */
    val dynamicToolbar = com.gtarayici.browser.ui.components.DynamicToolbarState()

    /** Okuma görünümü: Mozilla bileşeni etkinlik tarafından bağlanır, ayarları arayüzde gösterilir. */
    var readerFeature: () -> ReaderViewFeature? = { null }
    val readerControls = ReaderControls(activity)

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

    /** Sayfanın o anki tam çözünürlüklü görüntüsünü alır (etkinlik bağlar; gizli sekmede kullanılmaz). */
    var captureScreenshot: ((android.graphics.Bitmap?) -> Unit) -> Unit = { it(null) }

    /** "Sorun bildir" için toplanan bağlam; [Overlay.REPORT] açıkken dolu. */
    var reportRequest by mutableStateOf<com.gtarayici.browser.ui.screens.ReportRequest?>(null)
        private set

    /**
     * Sorun bildirme sayfasını açar. Açık sayfanın adresi ve görüntüsü yalnızca normal sekmede alınır (gizli sekmeden
     * hiçbir şey rapora girmez); kullanıcı yine de sayfada bunları rapordan çıkarabilir.
     */
    fun openReport() {
        menuOpen = false
        val tab = selectedTab?.takeUnless { it.content.private }
        val site = tab?.content?.url?.takeIf { it.startsWith("http") }
        val crash = components.crashReporter.pending()
        val show = { shot: android.graphics.Bitmap? ->
            reportRequest = com.gtarayici.browser.ui.screens.ReportRequest(site, shot, crash)
            overlay = Overlay.REPORT
        }
        if (site != null) captureScreenshot(show) else show(null)
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

    /** Gruba yeni boş sekme ekler ve adres girişini açar. */
    fun newTabInGroup(groupId: String) {
        privateMode = false
        val id = components.tabsUseCases.addTab("about:blank", selectTab = true, private = false, contextId = profile.contextId)
        components.tabGroups.addTab(groupId, id)
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

    /** Aynı türdeki (normal/gizli) sekmeler arasında bir öncekine veya sonrakine geçer. */
    fun switchAdjacentTab(delta: Int) {
        val current = selectedTab ?: return
        val tabs = store.state.tabs.filter { it.contextId == profile.contextId && it.content.private == current.content.private }
        val index = tabs.indexOfFirst { it.id == current.id }
        val target = tabs.getOrNull(index + delta) ?: return
        components.tabsUseCases.selectTab(target.id)
    }

    fun closeCurrentTab() {
        selectedTab?.let { closeTab(it.id) }
    }

    /** Panodaki metni adres çubuğuna yazılmış gibi açar (adresse gider, değilse arar). */
    fun pasteAndGo() {
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            activity.components.messenger.info(UiText.res(R.string.toolbar_clipboard_empty))
            return
        }
        submit(text)
    }

    private val statePrefs = activity.getSharedPreferences("gtarayici_state", android.content.Context.MODE_PRIVATE)

    /** Ana sayfadaki "Sık ziyaret edilenler"den kaldırılan alan adları. */
    val hiddenSites = kotlinx.coroutines.flow.MutableStateFlow(statePrefs.getStringSet(KEY_HIDDEN_SITES, emptySet()).orEmpty().toSet())

    fun hideFrequentSite(url: String) {
        val next = hiddenSites.value + com.gtarayici.browser.ui.screens.hostOf(url)
        hiddenSites.value = next
        statePrefs.edit { putStringSet(KEY_HIDDEN_SITES, next) }
    }

    fun removeBookmark(url: String) {
        val profileId = profile.id
        components.applicationScope.launch(Dispatchers.IO) { components.database.bookmarks().deleteByUrl(profileId, url) }
    }

    /** Veriler temizlenince kapatılan sekmeler "Geri al" ile geri getirilemez. */
    private fun forgetClosedTabs() {
        store.dispatch(UndoAction.ClearRecoverableTabs(store.state.undoHistory.tag ?: return))
    }

    /** Son kapatılan sekmeleri geri getirir. */
    fun undoCloseTabs() {
        components.tabsUseCases.undo.invoke()
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
        activity.components.messenger.success(UiText.res(R.string.link_copied))
    }

    fun toggleBookmark(isBookmarked: Boolean) {
        val tab = selectedTab ?: return
        menuOpen = false
        val dao = components.database.bookmarks()
        val profileId = profile.id
        // Menü kapandığı için işlemin sonucu bir mesajla gösterilir; kaldırma "Geri al" ile geri alınabilir.
        components.applicationScope.launch(Dispatchers.IO) {
            if (isBookmarked) {
                val removed = dao.list(profileId).firstOrNull { it.url == tab.content.url }
                dao.deleteByUrl(profileId, tab.content.url)
                if (removed != null) {
                    components.messenger.undoable(UiText.res(R.string.bookmark_removed), UNDO_BOOKMARK_KEY) {
                        components.applicationScope.launch(Dispatchers.IO) { dao.upsert(removed) }
                    }
                } else {
                    components.messenger.info(UiText.res(R.string.bookmark_removed))
                }
            } else {
                components.messenger.success(UiText.res(R.string.bookmark_added))
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

    /** Okuma görünümünü açar veya kapatır. */
    fun toggleReader(active: Boolean) {
        menuOpen = false
        val feature = readerFeature() ?: return
        if (active) feature.hideReaderView() else feature.showReaderView()
    }

    fun openReaderSettings() {
        menuOpen = false
        readerFeature()?.showControls()
    }

    fun openTranslate() {
        menuOpen = false
        translateOpen = true
    }

    fun openQrScanner() {
        menuOpen = false
        overlay = Overlay.QR
    }

    fun openYouTubeDownload() {
        menuOpen = false
        youtubeUrl = selectedTab?.content?.url
    }

    fun openInstallDialog() {
        menuOpen = false
        installDialogOpen = true
    }

    /** İzin ekranına gidilirken yüklenmek istenen sekme; dönüşte izin verildiyse yükleme sürdürülür. */
    private var pendingWebApkTabId: String? = null

    /** Siteyi gerçek bir Android uygulaması (WebAPK) olarak yükler. */
    fun installWebApk() {
        installDialogOpen = false
        val tab = selectedTab ?: return
        val installer = components.webApkInstaller
        if (!installer.canInstall()) {
            pendingWebApkTabId = tab.id
            activity.components.messenger.info(UiText.res(R.string.webapk_permission))
            installer.requestPermission()
            return
        }
        pendingWebApkTabId = null
        activity.components.messenger.info(UiText.res(R.string.webapk_preparing))
        components.applicationScope.launch {
            when (val result = installer.install(tab)) {
                is com.gtarayici.browser.webapk.WebApkInstaller.Result.Failed ->
                    activity.components.messenger.error(com.gtarayici.browser.ui.feedback.HumanErrors.message(com.gtarayici.browser.ui.feedback.ErrorArea.WebApk, result.message))
                com.gtarayici.browser.webapk.WebApkInstaller.Result.NeedsPermission -> installer.requestPermission()
                com.gtarayici.browser.webapk.WebApkInstaller.Result.Started -> Unit
            }
        }
    }

    /**
     * Etkinlik öne gelince çağrılır: "Bilinmeyen uygulamaları yükle" ekranından dönüldüyse ve izin verildiyse
     * yarım kalan yükleme kendiliğinden sürer; izin verilmediyse bekleyen istek bırakılır.
     */
    fun resumePendingWebApkInstall() {
        val id = pendingWebApkTabId ?: return
        pendingWebApkTabId = null
        if (components.webApkInstaller.canInstall() && selectedTab?.id == id) installWebApk()
    }

    /** Ana ekrana basit bir kısayol (veya web uygulaması kısayolu) ekler. */
    fun addToHomescreen() {
        menuOpen = false
        installDialogOpen = false
        if (!components.webAppUseCases.isPinningSupported()) {
            activity.components.messenger.warning(UiText.res(R.string.webapp_not_supported))
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
            forgetClosedTabs()
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
            forgetClosedTabs()
            withContext(Dispatchers.IO) { components.database.history().clearAll() }
            components.engine.clearData(BrowsingData.all())
            activity.components.messenger.success(UiText.res(R.string.settings_data_cleared))
        }
    }

    /**
     * Geri tuşu: önce açık ekranları kapatır, sonra sayfa geçmişinde geri gider,
     * en sonda içinden açıldığı sekmeye döner. İşlenemezse false döner.
     */
    fun handleBack(): Boolean {
        when {
            onboarding -> finishOnboarding()
            menuOpen -> menuOpen = false
            siteInfoOpen -> siteInfoOpen = false
            readerControls.open -> readerControls.hideControls()
            translateOpen -> translateOpen = false
            profileSwitcherOpen -> profileSwitcherOpen = false
            youtubeUrl != null -> youtubeUrl = null
            overlay != Overlay.NONE -> closeOverlay()
            findInPageOpen -> closeFindInPage()
            readerFeature()?.onBackPressed() == true -> Unit
            selectedTab?.content?.canGoBack == true -> goBack()
            selectedTab?.parentId != null -> selectedTab?.let { closeTab(it.id) }
            else -> return false
        }
        return true
    }

    companion object {
        private const val KEY_HIDDEN_SITES = "home_hidden_sites"
        private const val UNDO_BOOKMARK_KEY = "geri-al-yer-imi"
        const val KEY_ONBOARDED = "onboarding_done"

        /** Yeni kurulumsa (önceki sürümlerden kalma durum yoksa) tanıtım gösterilir. */
        fun needsOnboarding(prefs: android.content.SharedPreferences): Boolean {
            if (prefs.getBoolean(KEY_ONBOARDED, false)) return false
            // Önceki sürümleri kullananlar tanıtımı görmez.
            if (prefs.contains("asked_notifications")) {
                prefs.edit { putBoolean(KEY_ONBOARDED, true) }
                return false
            }
            return true
        }
    }
}
