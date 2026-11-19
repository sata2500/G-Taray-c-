package com.gtarayici.browser.media

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gtarayici.browser.Components
import com.gtarayici.browser.R
import com.gtarayici.browser.diagnostics.Diagnostics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.lib.state.ext.flow

/**
 * Resim içinde resim (PiP).
 *
 * - Video oynarken uygulamadan çıkılınca (ana ekran, son uygulamalar, başka uygulamaya geçiş),
 *   video tam ekranda olmasa da otomatik olarak küçük pencereye geçer; oynayan video tam ekrana alınır.
 * - Küçük pencerede önceki / oynat-duraklat / sonraki düğmeleri bulunur.
 * - Küçük pencere kapatılınca oynatma duraklatılır.
 */
class PipController(
    private val activity: ComponentActivity,
    private val components: Components,
    /** Yüklü web uygulaması penceresi gibi tek bir sekmeye bağlıysa o sekmenin kimliği. */
    private val fixedTabId: String? = null,
    /** Sekmenin gösterildiği motor görünümü. */
    private val engineView: () -> android.view.View? = { null },
) {
    private val native = NativeVideoPip(
        activity,
        components.runtime,
        sourceView = { NativeVideoPip.findGeckoView(engineView()) },
        videoRect = { view -> components.extensions.videoRect.value?.toViewPixels(view.width, view.height) },
    )

    /** Küçük pencerede gösterilen, yalnızca videoyu içeren görünüm (arayüz bunu sayfanın üstüne yerleştirir). */
    val pipView: android.view.View get() = native.pipView

    private data class MediaInfo(val tabId: String, val playing: Boolean, val features: Long, val width: Long, val height: Long)

    private var media: MediaInfo? = null
    private val supported = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    private val enabled get() = supported && components.settings.autoPictureInPicture.value

    /** Küçük penceredeki önceki / oynat-duraklat / sonraki düğmeleri. */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val controller = mediaTab()?.mediaSessionState?.controller ?: return
            when (intent.getStringExtra(EXTRA_COMMAND)) {
                CMD_PLAY -> controller.play()
                CMD_PAUSE -> controller.pause()
                CMD_NEXT -> controller.nextTrack()
                CMD_PREVIOUS -> controller.previousTrack()
            }
        }
    }

    fun start() {
        if (!supported) return
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        activity.lifecycle.addObserver(lifecycleObserver)
        activity.lifecycleScope.launch {
            components.store.flow()
                .map { state -> state.mediaInfo() }
                .distinctUntilChanged()
                .collect { info ->
                    media = info
                    updateParams()
                }
        }
        // Videonun ekrandaki konumu değişince geçiş animasyonu ipucu güncellenir.
        activity.lifecycleScope.launch {
            components.extensions.videoRect.collect { if (media?.playing == true) updateParams() }
        }
    }

    fun stop() {
        if (supported) runCatching { activity.unregisterReceiver(receiver) }
    }

    /**
     * Kullanıcı uygulamadan ayrılıyor (ana ekran, son uygulamalar, başka uygulamaya geçiş, bildirim).
     *
     * Android 12+ ana ekran hareketinde otomatik geçişi kendisi yapar ve bu durumda bu çağrı gelmez.
     * Bazı üreticiler (ör. vivo) doğrudan uygulama değiştirmede otomatik geçişi yapmaz; o zaman
     * sistem yine bu ipucunu verir ve geçiş elle istenir. Sistem bunu yalnızca gerçekten başka bir
     * göreve geçilirken kabul eder; uygulama içinde açılan pencerelerde PiP başlamaz.
     */
    fun onUserLeaveHint() {
        leaveHintSeen = true
        log("ayrılma ipucu")
        enterManually("ayrılma ipucu")
    }

    /**
     * Etkinlik ön plandaki "en üst" konumunu kaybetti. Bazı üreticilerin hızlı uygulama değiştirme
     * hareketi (ör. vivo'da alt çubuğu yana kaydırmak) ayrılma ipucu vermez ve uygulama arka plana
     * düştükten sonra PiP isteğini reddeder; bu an, uygulama henüz açıkken PiP isteyebilmenin son fırsatıdır.
     * Uygulamanın kendi açtığı ekranlarda (paylaşım, dosya seçici, izinler) tetiklenmez.
     */
    fun onTopResumedChanged(isTop: Boolean) {
        log("en üst etkinlik=$isTop")
        if (isTop) return
        val ownLaunch = android.os.SystemClock.uptimeMillis() - lastOwnLaunch < OWN_LAUNCH_GRACE_MS
        if (ownLaunch) return
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        enterManually("en üst konum kaybı")
    }

    /** Uygulama kendisi bir ekran açarken çağrılır (bu sırada PiP'e geçilmez). */
    fun noteOwnLaunch() {
        lastOwnLaunch = android.os.SystemClock.uptimeMillis()
    }

    private var leaveHintSeen = false
    private var lastOwnLaunch = 0L

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            leaveHintSeen = false
        }

        // Ayrılma ipucu hiç gelmeden başka bir uygulama öne geçtiyse son bir deneme yapılır.
        override fun onPause(owner: LifecycleOwner) {
            log("duraklatıldı")
            if (!leaveHintSeen && !activity.isFinishing && !activity.isChangingConfigurations) enterManually("duraklatma")
        }

        override fun onStop(owner: LifecycleOwner) {
            log("durduruldu")
        }
    }

    /** Yalnızca video oynarken (PiP'in söz konusu olduğu durumlarda) tanılama kaydı tutulur. */
    private fun log(event: String) {
        if (media?.playing == true) Diagnostics.log("PiP olayı: $event (PiP=${activity.isInPictureInPictureMode})")
    }

    private fun enterManually(reason: String) {
        if (!enabled || media?.playing != true || activity.isInPictureInPictureMode) return
        // Otomatik geçiş bayrağı açıkken sistem, duraklatılan etkinliğin elle isteğini reddeder.
        val entered = runCatching { activity.enterPictureInPictureMode(buildParams(autoEnter = false)) }
            .onFailure { Diagnostics.log("PiP elle istenemedi ($reason): ${it.message}") }
            .getOrDefault(false)
        Diagnostics.log("PiP elle istendi ($reason): ${if (entered) "kabul edildi" else "sistem reddetti"}")
    }

    fun onPictureInPictureModeChanged(inPip: Boolean) {
        val tab = mediaTab()
        Diagnostics.log("PiP ${if (inPip) "başladı" else "bitti"} (yaşam döngüsü=${activity.lifecycle.currentState})")
        if (inPip) {
            if (tab != null && !native.enter()) Diagnostics.log("PiP: kaynak görünüm bulunamadı")
        } else {
            // Pencere kapatıldıysa etkinlik artık görünür değildir; büyütüldüyse tarayıcıya dönülür.
            val dismissed = !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            native.exit(dismissed)
            if (dismissed) tab?.mediaSessionState?.controller?.pause()
            updateParams()
        }
    }

    private fun updateParams() {
        if (!supported) return
        runCatching { activity.setPictureInPictureParams(buildParams()) }
    }

    private fun buildParams(autoEnter: Boolean = true): PictureInPictureParams {
        val info = media
        val builder = PictureInPictureParams.Builder()
        val hint = sourceRectHint()
        val ratio = when {
            info != null && info.width > 0 && info.height > 0 -> Rational(info.width.toInt(), info.height.toInt())
            hint != null -> Rational(hint.width(), hint.height())
            else -> Rational(16, 9)
        }
        // Android yalnızca 1:2.39 ile 2.39:1 arasındaki oranları kabul eder.
        val clamped = ratio.toFloat().coerceIn(1 / 2.39f, 2.39f)
        builder.setAspectRatio(if (clamped == ratio.toFloat()) ratio else Rational((clamped * 1000).toInt(), 1000))
        // Geçiş animasyonu tüm sayfadan değil, videonun ekrandaki yerinden başlar.
        hint?.let { builder.setSourceRectHint(it) }
        builder.setActions(actions(info))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter && enabled && info?.playing == true)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    /** Videonun pencere koordinatlarındaki yeri (yardımcı eklentinin bildirdiği konumdan). */
    private fun sourceRectHint(): android.graphics.Rect? {
        if (activity.isInPictureInPictureMode) return null
        val view = NativeVideoPip.findGeckoView(engineView()) ?: return null
        val rect = components.extensions.videoRect.value?.toViewPixels(view.width, view.height) ?: return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        rect.offset(location[0], location[1])
        return rect
    }

    private fun actions(info: MediaInfo?): List<RemoteAction> {
        if (info == null) return emptyList()
        val list = mutableListOf<RemoteAction>()
        if (info.features and MediaSession.Feature.PREVIOUS_TRACK != 0L) {
            list += action(CMD_PREVIOUS, R.drawable.ic_pip_previous, R.string.pip_previous)
        }
        list += if (info.playing) {
            action(CMD_PAUSE, R.drawable.ic_pip_pause, R.string.pip_pause)
        } else {
            action(CMD_PLAY, R.drawable.ic_pip_play, R.string.pip_play)
        }
        if (info.features and MediaSession.Feature.NEXT_TRACK != 0L) {
            list += action(CMD_NEXT, R.drawable.ic_pip_next, R.string.pip_next)
        }
        return list
    }

    private fun action(command: String, icon: Int, title: Int): RemoteAction {
        val intent = Intent(ACTION).setPackage(activity.packageName).putExtra(EXTRA_COMMAND, command)
        val pending = PendingIntent.getBroadcast(
            activity,
            command.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = activity.getString(title)
        return RemoteAction(Icon.createWithResource(activity, icon), label, label, pending)
    }

    private fun mediaTab(): SessionState? {
        val id = media?.tabId ?: return null
        val state = components.store.state
        return state.tabs.firstOrNull { it.id == id } ?: state.customTabs.firstOrNull { it.id == id }
    }

    private fun BrowserState.mediaInfo(): MediaInfo? {
        val candidates: List<SessionState> = if (fixedTabId != null) {
            listOfNotNull(tabs.firstOrNull { it.id == fixedTabId } ?: customTabs.firstOrNull { it.id == fixedTabId })
        } else {
            listOfNotNull(selectedTab) + tabs
        }
        val tab = candidates.firstOrNull { it.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING }
            ?: candidates.firstOrNull { it.mediaSessionState?.playbackState == MediaSession.PlaybackState.PAUSED }
            ?: return null
        val state = tab.mediaSessionState ?: return null
        return MediaInfo(
            tabId = tab.id,
            playing = state.playbackState == MediaSession.PlaybackState.PLAYING,
            features = state.features.flags,
            width = state.elementMetadata?.width ?: 0,
            height = state.elementMetadata?.height ?: 0,
        )
    }

    private companion object {
        const val ACTION = "com.gtarayici.browser.PIP_CONTROL"
        const val EXTRA_COMMAND = "command"
        const val CMD_PLAY = "play"
        const val CMD_PAUSE = "pause"
        const val CMD_NEXT = "next"
        const val CMD_PREVIOUS = "previous"
        const val OWN_LAUNCH_GRACE_MS = 2_000L
    }
}
