package com.gtarayici.browser.ui

import android.graphics.Bitmap
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.gtarayici.browser.Components
import com.gtarayici.browser.ui.controller.BrowserUiState
import com.gtarayici.browser.ui.controller.ControllerContext
import com.gtarayici.browser.ui.controller.PageActions
import com.gtarayici.browser.ui.controller.PageController
import com.gtarayici.browser.ui.controller.ProfileActions
import com.gtarayici.browser.ui.controller.ProfileController
import com.gtarayici.browser.ui.controller.TabActions
import com.gtarayici.browser.ui.controller.TabController
import com.gtarayici.browser.ui.screens.ReportRequest
import com.gtarayici.browser.ui.screens.hostOf
import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.feature.readerview.ReaderViewFeature

/** Tam ekran kaplayan ikincil ekranlar. */
enum class Overlay { NONE, SEARCH, TABS, HISTORY, BOOKMARKS, DOWNLOADS, SETTINGS, PROFILES, EXTENSIONS, VPN, QR, REPORT, PASSWORDS, READING }

/**
 * Arayüzün tarayıcı üzerinde yaptığı işlemlerin tek giriş noktası. İşin kendisi parçalara ayrılmıştır
 * (ui/controller): sekmeler [TabActions], açık sayfa [PageActions], profiller ve kilitler [ProfileActions];
 * arayüz durumu [BrowserUiState]. Bu sınıf onları birleştirir, gezinme (açık sayfalar, geri tuşu) ve
 * sorun bildirme bağlamını yönetir.
 */
class BrowserController private constructor(
    private val ctx: ControllerContext,
    private val tabs: TabController,
    private val captureThumbnail: () -> Unit,
) : TabActions by tabs,
    PageActions by PageController(ctx, tabs),
    ProfileActions by ProfileController(ctx, tabs) {

    constructor(activity: FragmentActivity, components: Components, captureThumbnail: () -> Unit = {}) :
        this(ControllerContext(activity, components, BrowserUiState()), captureThumbnail)

    private constructor(ctx: ControllerContext, captureThumbnail: () -> Unit) : this(ctx, TabController(ctx), captureThumbnail)

    private val components get() = ctx.components

    // --- Arayüz durumu (Compose gözler) ------------------------------------------------------
    var overlay: Overlay
        get() = ctx.ui.overlay
        set(value) { ctx.ui.overlay = value }
    var menuOpen: Boolean
        get() = ctx.ui.menuOpen
        set(value) { ctx.ui.menuOpen = value }
    var findInPageOpen: Boolean
        get() = ctx.ui.findInPageOpen
        set(value) { ctx.ui.findInPageOpen = value }
    var youtubeUrl: String?
        get() = ctx.ui.youtubeUrl
        set(value) { ctx.ui.youtubeUrl = value }
    var profileSwitcherOpen: Boolean
        get() = ctx.ui.profileSwitcherOpen
        set(value) { ctx.ui.profileSwitcherOpen = value }
    var installDialogOpen: Boolean
        get() = ctx.ui.installDialogOpen
        set(value) { ctx.ui.installDialogOpen = value }
    var translateOpen: Boolean
        get() = ctx.ui.translateOpen
        set(value) { ctx.ui.translateOpen = value }
    var siteInfoOpen: Boolean
        get() = ctx.ui.siteInfoOpen
        set(value) { ctx.ui.siteInfoOpen = value }
    var pageDownloadsOpen: Boolean
        get() = ctx.ui.pageDownloadsOpen
        set(value) { ctx.ui.pageDownloadsOpen = value }
    var onboarding: Boolean
        get() = ctx.ui.onboarding
        set(value) { ctx.ui.onboarding = value }
    var privateMode: Boolean
        get() = ctx.ui.privateMode
        set(value) { ctx.ui.privateMode = value }
    val reportRequest: ReportRequest? get() = ctx.ui.reportRequest

    /** Tanıtım bitince çağrılır (ör. bildirim izni o zaman istenir). */
    var onOnboardingFinished: () -> Unit = {}

    /** Kaydırınca gizlenen adres çubuğunun durumu. */
    val dynamicToolbar = com.gtarayici.browser.ui.components.DynamicToolbarState()

    /** Okuma görünümü: Mozilla bileşeni etkinlik tarafından bağlanır, ayarları arayüzde gösterilir. */
    var readerFeature: () -> ReaderViewFeature?
        get() = ctx.readerFeature
        set(value) { ctx.readerFeature = value }
    val readerControls get() = ctx.readerControls

    /** Giriş formunda kayıtlı hesapları gösteren çubuk (motorun parola seçme isteği). */
    val loginPicker = com.gtarayici.browser.ui.components.LoginPickerState()

    /** Sonra oku, sesli okuma, ekran görüntüsü, PDF. */
    val reading = com.gtarayici.browser.ui.controller.ReadingController(ctx, { dynamicToolbar.engineView }, { open(Overlay.READING) })

    /** Sayfanın o anki tam çözünürlüklü görüntüsünü alır (etkinlik bağlar; gizli sekmede kullanılmaz). */
    var captureScreenshot: ((Bitmap?) -> Unit) -> Unit = { it(null) }

    /** Seçili sekme, yalnızca etkin profile aitse görünür kabul edilir. */
    val selectedTab: TabSessionState? get() = ctx.selectedTab

    private val statePrefs = ctx.activity.getSharedPreferences("gtarayici_state", android.content.Context.MODE_PRIVATE)

    /** Ana sayfadaki "Sık ziyaret edilenler"den kaldırılan alan adları. */
    val hiddenSites = MutableStateFlow(statePrefs.getStringSet(KEY_HIDDEN_SITES, emptySet()).orEmpty().toSet())

    init {
        privateMode = selectedTab?.content?.private ?: false
    }

    fun finishOnboarding() {
        onboarding = false
        statePrefs.edit { putBoolean(KEY_ONBOARDED, true) }
        onOnboardingFinished()
    }

    fun open(overlay: Overlay) {
        menuOpen = false
        if (overlay == Overlay.TABS) captureThumbnail()
        this.overlay = overlay
    }

    fun closeOverlay() = ctx.ui.closeOverlay()

    /**
     * Sorun bildirme sayfasını açar. Açık sayfanın adresi ve görüntüsü yalnızca normal sekmede alınır (gizli sekmeden
     * hiçbir şey rapora girmez); kullanıcı yine de sayfada bunları rapordan çıkarabilir.
     */
    fun openReport() {
        menuOpen = false
        val tab = selectedTab?.takeUnless { it.content.private }
        val site = tab?.content?.url?.takeIf { it.startsWith("http") }
        val crash = components.crashReporter.pending()
        val show = { shot: Bitmap? ->
            ctx.ui.reportRequest = ReportRequest(site, shot, crash)
            overlay = Overlay.REPORT
        }
        if (site != null) captureScreenshot(show) else show(null)
    }

    fun hideFrequentSite(url: String) {
        val next = hiddenSites.value + hostOf(url)
        hiddenSites.value = next
        statePrefs.edit { putStringSet(KEY_HIDDEN_SITES, next) }
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
            pageDownloadsOpen -> pageDownloadsOpen = false
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
