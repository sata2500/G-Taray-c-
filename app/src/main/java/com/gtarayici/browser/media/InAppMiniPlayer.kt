package com.gtarayici.browser.media

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.gtarayici.browser.Components
import com.gtarayici.browser.diagnostics.Diagnostics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.lib.state.ext.flow
import org.mozilla.geckoview.GeckoView

/** Uygulama içi küçük pencerenin ne zaman açık olacağı (saf, test edilebilir). */
object MiniPlayerRules {
    /**
     * [hasTarget]: video başka bir sekmede arka planda kaldı ya da üstünde bir menü/ekran açık. [showing]: pencere zaten
     * açık (duraklatılan video pencerede kalır; yeni pencere yalnızca oynayan video için açılır).
     */
    fun shouldShow(
        enabled: Boolean,
        blocked: Boolean,
        hasTarget: Boolean,
        siteAllowed: Boolean,
        suppressed: Boolean,
        playing: Boolean,
        paused: Boolean,
        showing: Boolean,
    ): Boolean = enabled && !blocked && hasTarget && siteAllowed && !suppressed && (playing || (showing && paused))
}

/**
 * Uygulama içi küçük pencere: video oynarken başka bir sekmeye geçilince ya da uygulamanın bir ekranı (ayarlar,
 * sekmeler, geçmiş…) açılınca video, sayfanın üstünde yüzen küçük bir pencerede oynamaya devam eder.
 *
 * Görüntü, sistemin küçük penceresiyle aynı yoldan gelir: Gecko oynayan videoyu ayrı bir oynatıcı oturumuna kopyalar
 * ([NativeVideoPip]). Başka sekmeye geçildiğinde videonun sayfası, ana görünümün arkasındaki görünmeyen bir motor
 * görünümünde ([keeper]) çizilmeye devam eder; YouTube gibi oynatıcılar ancak sayfa çizilirken video yükler.
 */
class InAppMiniPlayer(
    private val activity: ComponentActivity,
    private val components: Components,
    /** Ana motor görünümünün arkasında duran kap; [keeper] buraya eklenir. */
    private val keeperHolder: FrameLayout,
    /** Seçili sekmeyi gösteren ana motor görünümü. */
    private val mainView: () -> View?,
) {
    private var keeper: EngineView? = null
    private var keeperSession: EngineSession? = null
    private var keeperTabId: String? = null

    private val native = NativeVideoPip(
        activity,
        components.runtime,
        sourceView = { NativeVideoPip.findGeckoView(if (keeperTabId != null && keeperTabId == tabId) keeper?.asView() else mainView()) },
        videoRect = { view ->
            val url = components.store.state.tabs.firstOrNull { it.id == tabId }?.content?.url
            components.extensions.videoRectFor(url)?.toViewPixels(view.width, view.height)
        },
    )

    /** Küçük pencerede gösterilen video görünümü. */
    val view: View get() = native.pipView

    /** Pencerede gösterilen sekme; pencere kapalıyken null. */
    var tabId by mutableStateOf<String?>(null)
        private set

    /** Pencere açık mı (arayüz bunu izler). */
    val visible: Boolean get() = native.active && tabId != null

    private var overlayOpen = false
    private var systemPip = false
    private var fullscreen = false
    /** Kullanıcının kapattığı ya da videosu kopyalanamayan sekme: yeniden bir geçiş olana kadar pencere açılmaz. */
    private var suppressedTabId: String? = null
    private var shownByUs = false

    private val enabled get() = components.settings.inAppPictureInPicture.value

    fun start() {
        activity.lifecycleScope.launch {
            components.store.flow()
                .map { state ->
                    state.selectedTabId to state.tabs.map { Triple(it.id, it.mediaSessionState?.playbackState, it.engineState.engineSession) }
                }
                .distinctUntilChanged()
                .collect { evaluate() }
        }
        activity.lifecycleScope.launch { components.settings.inAppPictureInPicture.collect { evaluate() } }
        // Gecko pencereyi kendisi kapattıysa (video bulunamadı, video kaldırıldı) aynı video için yeniden denenmez.
        activity.lifecycleScope.launch {
            snapshotFlow { native.active }.collect { active ->
                if (!active && shownByUs) {
                    shownByUs = false
                    suppressedTabId = tabId
                    tabId = null
                    evaluate()
                }
            }
        }
    }

    /** Uygulamanın tam ekran bir sayfası (ayarlar, sekmeler…) açıldı ya da kapandı. */
    fun setOverlayOpen(open: Boolean) {
        if (overlayOpen == open) return
        overlayOpen = open
        if (!open && keeperTabId == null) suppressedTabId = null
        evaluate()
    }

    /** Sistemin küçük penceresi açıkken uygulama içi pencere kapanır; sistem penceresi aynı videoyu gösterir. */
    fun setSystemPip(inPip: Boolean) {
        systemPip = inPip
        evaluate()
    }

    fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        evaluate()
    }

    /** Arka plandaki sekmenin videosu için sistem penceresinin kaynağı (yoksa null: seçili sekme kullanılır). */
    fun backgroundSourceView(): View? = if (keeperTabId != null) keeper?.asView() else null

    /** Kullanıcı pencereyi kapattı: video duraklatılır. */
    fun close() {
        val id = tabId ?: return
        stopNative()
        suppressedTabId = id
        components.store.state.tabs.firstOrNull { it.id == id }?.mediaSessionState?.controller?.pause()
        evaluate()
    }

    fun togglePlayback() {
        val media = components.store.state.tabs.firstOrNull { it.id == tabId }?.mediaSessionState ?: return
        if (media.playbackState == MediaSession.PlaybackState.PLAYING) media.controller.pause() else media.controller.play()
    }

    /** Ana görünüm [session]'ı göstermek üzere: oturum arka plan görünümündeyse önce oradan alınır. */
    internal fun beforeShown(session: EngineSession) {
        if (session === keeperSession) {
            stopNative()
            releaseKeeper()
        }
    }

    /** Ana görünüm [session]'ı artık göstermiyor (başka sekmeye geçildi). Video oynuyorsa pencereye alınır. */
    internal fun afterHidden(session: EngineSession) {
        if (!enabled || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val tab = components.store.state.tabs.firstOrNull { it.engineState.engineSession === session } ?: return
        if (tab.mediaSessionState?.playbackState != MediaSession.PlaybackState.PLAYING) return
        if (!components.siteSettings.pipAllowed(tab.content.url)) return
        suppressedTabId = null
        if (keeperTabId != null && keeperTabId != tab.id) {
            stopNative()
            releaseKeeper()
        }
        val view = keeper ?: createKeeper()
        runCatching { view.render(session) }
            .onFailure {
                Diagnostics.pip("küçük pencere: video sayfası arka planda tutulamadı (${it.javaClass.simpleName})")
                return
            }
        keeperSession = session
        keeperTabId = tab.id
        Diagnostics.pip("küçük pencere: başka sekmeye geçildi, video arka planda sürüyor")
        evaluate()
    }

    private fun evaluate() {
        val state = components.store.state
        val keeperTab = keeperTabId?.let { id -> state.tabs.firstOrNull { it.id == id } }
        // Sekme kapandı ya da motor oturumu değişti (ör. çöktü, yeniden yüklendi).
        if (keeperTabId != null && keeperTab?.engineState?.engineSession !== keeperSession) {
            stopNative()
            releaseKeeper()
        }
        val target: TabSessionState? = when {
            keeperTabId != null -> keeperTab
            overlayOpen -> state.selectedTab
            else -> null
        }
        val playback = target?.mediaSessionState?.playbackState
        val showing = native.active && tabId != null && tabId == target?.id
        val want = MiniPlayerRules.shouldShow(
            enabled = enabled,
            blocked = systemPip || fullscreen,
            hasTarget = target != null,
            siteAllowed = target?.let { components.siteSettings.pipAllowed(it.content.url) } ?: false,
            suppressed = target != null && target.id == suppressedTabId,
            playing = playback == MediaSession.PlaybackState.PLAYING,
            paused = playback == MediaSession.PlaybackState.PAUSED,
            showing = showing,
        )
        when {
            want && !showing && target != null -> {
                if (native.active) stopNative()
                tabId = target.id
                shownByUs = true
                if (native.enter()) {
                    Diagnostics.pip("küçük pencere açıldı (${if (keeperTabId != null) "başka sekme" else "uygulama ekranı"})")
                } else {
                    shownByUs = false
                    tabId = null
                }
            }
            !want && native.active -> stopNative()
        }
        // Sistem penceresi arka plandaki sayfayı kullanabilir; o açıkken sayfa bırakılmaz.
        if (!want && !systemPip && keeperTabId != null) releaseKeeper()
    }

    private fun stopNative() {
        shownByUs = false
        tabId = null
        native.exit(dismissed = false)
    }

    private fun createKeeper(): EngineView = components.engine.createView(activity).also { view ->
        // Ana görünümün arkasında durur; SurfaceView'lar üst üste binince sıra belirsiz olduğundan TextureView ile çizilir.
        NativeVideoPip.findGeckoView(view.asView())?.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
        keeperHolder.addView(view.asView(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        keeper = view
    }

    private fun releaseKeeper() {
        if (keeperTabId == null && keeperSession == null) return
        runCatching { keeper?.release() }
        keeperSession = null
        keeperTabId = null
    }
}

/**
 * Ana motor görünümü: sekme değişimlerini [InAppMiniPlayer]'a bildirir. Gösterilecek oturum arka plan görünümündeyse
 * önce oradan bırakılır (bir oturum aynı anda tek görünümde olabilir); gizlenen sekmenin videosu pencereye alınır.
 */
class SwitchAwareEngineView(
    private val inner: EngineView,
    private val mini: () -> InAppMiniPlayer?,
) : EngineView by inner {
    private var current: EngineSession? = null

    override fun render(session: EngineSession) {
        val previous = current
        mini()?.beforeShown(session)
        inner.render(session)
        current = session
        if (previous != null && previous !== session) mini()?.afterHidden(previous)
    }

    override fun release() {
        val previous = current
        inner.release()
        current = null
        previous?.let { mini()?.afterHidden(it) }
    }
}
