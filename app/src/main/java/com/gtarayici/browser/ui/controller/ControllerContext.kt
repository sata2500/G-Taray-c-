package com.gtarayici.browser.ui.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.gtarayici.browser.Components
import com.gtarayici.browser.data.Profile
import com.gtarayici.browser.reader.ReaderControls
import com.gtarayici.browser.ui.Overlay
import com.gtarayici.browser.ui.screens.ReportRequest
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.feature.readerview.ReaderViewFeature

/**
 * Arayüzün gözlediği durum: hangi tam ekran sayfanın, alt sayfanın veya pencerenin açık olduğu.
 * Değerler Compose durumudur; değişince ilgili ekran kendini yeniden çizer.
 */
class BrowserUiState {
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

    /** Görünür sekme yokken yeni sekmelerin gizli açılıp açılmayacağı. */
    var privateMode by mutableStateOf(false)

    /** "Sorun bildir" için toplanan bağlam; [Overlay.REPORT] açıkken dolu. */
    var reportRequest by mutableStateOf<ReportRequest?>(null)

    fun closeOverlay() {
        overlay = Overlay.NONE
    }
}

/** Denetleyici parçalarının ortak bağlamı: etkinlik, uygulama bileşenleri, arayüz durumu ve seçili sekme. */
class ControllerContext(
    val activity: FragmentActivity,
    val components: Components,
    val ui: BrowserUiState,
) {
    /** Okuma görünümü: Mozilla bileşeni etkinlik tarafından bağlanır, ayarları arayüzde gösterilir. */
    var readerFeature: () -> ReaderViewFeature? = { null }
    val readerControls = ReaderControls(activity)

    val store get() = components.store
    val messenger get() = components.messenger
    val profile: Profile get() = components.profiles.activeProfile

    /** Seçili sekme, yalnızca etkin profile aitse görünür kabul edilir. */
    val selectedTab: TabSessionState?
        get() = store.state.selectedTab?.takeIf { it.contextId == profile.contextId }
}
