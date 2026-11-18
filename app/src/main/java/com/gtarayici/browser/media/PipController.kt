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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.gtarayici.browser.Components
import com.gtarayici.browser.R
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
class PipController(private val activity: ComponentActivity, private val components: Components) {
    private data class MediaInfo(val tabId: String, val playing: Boolean, val features: Long, val width: Long, val height: Long)

    private var media: MediaInfo? = null
    private var forcedFullscreen = false
    private val supported = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

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
        activity.lifecycleScope.launch {
            components.store.flow()
                .map { state -> state.mediaInfo() }
                .distinctUntilChanged()
                .collect { info ->
                    media = info
                    updateParams()
                }
        }
    }

    fun stop() {
        if (supported) runCatching { activity.unregisterReceiver(receiver) }
    }

    private val enabled get() = supported && components.settings.autoPictureInPicture.value

    /** Android 12 öncesinde otomatik geçiş yoktur; çıkışta elle geçilir. */
    fun onUserLeaveHint() {
        if (!enabled || media?.playing != true || activity.isInPictureInPictureMode) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            runCatching { activity.enterPictureInPictureMode(buildParams()) }
        }
    }

    fun onPictureInPictureModeChanged(inPip: Boolean) {
        val tab = mediaTab()
        if (inPip) {
            if (tab != null && tab.content.fullScreen.not()) {
                forcedFullscreen = true
                components.extensions.requestVideoFullscreen()
            }
        } else {
            if (forcedFullscreen) {
                forcedFullscreen = false
                components.extensions.exitVideoFullscreen()
                tab?.let { components.sessionUseCases.exitFullscreen(it.id) }
            }
            // Küçük pencere kapatıldıysa (etkinlik görünür değil) oynatmayı durdur.
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                tab?.mediaSessionState?.controller?.pause()
            }
        }
    }

    private fun updateParams() {
        if (!supported) return
        runCatching { activity.setPictureInPictureParams(buildParams()) }
    }

    private fun buildParams(): PictureInPictureParams {
        val info = media
        val builder = PictureInPictureParams.Builder()
        val ratio = if (info != null && info.width > 0 && info.height > 0) {
            Rational(info.width.toInt(), info.height.toInt())
        } else {
            Rational(16, 9)
        }
        // Android yalnızca 1:2.39 ile 2.39:1 arasındaki oranları kabul eder.
        val clamped = ratio.toFloat().coerceIn(1 / 2.39f, 2.39f)
        builder.setAspectRatio(if (clamped == ratio.toFloat()) ratio else Rational((clamped * 1000).toInt(), 1000))
        builder.setActions(actions(info))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enabled && info?.playing == true)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
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
        return components.store.state.tabs.firstOrNull { it.id == id }
    }

    private fun BrowserState.mediaInfo(): MediaInfo? {
        val candidates = listOfNotNull(selectedTab) + tabs
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
    }
}
