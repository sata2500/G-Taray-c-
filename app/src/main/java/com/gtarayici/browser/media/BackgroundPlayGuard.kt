package com.gtarayici.browser.media

import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.gtarayici.browser.Components
import mozilla.components.concept.engine.mediasession.MediaSession

/**
 * "Arka planda oynatma" kapalıyken uygulamadan çıkınca (veya ekran kapanınca) oynayan medyayı duraklatır.
 *
 * Motor oturumu arka planda da etkin kaldığı için sayfa hiçbir zaman "gizli" olmaz; yardımcı eklentinin
 * görünürlük hilesini kapatmak tek başına YouTube'u durdurmaz, diğer siteler için de bir etkisi yoktur.
 * Ayar bu yüzden uygulama tarafında uygulanır. Küçük pencereye (PiP) geçildiyse oynatma sürer.
 */
class BackgroundPlayGuard(
    private val activity: ComponentActivity,
    private val components: Components,
    /** Yüklü web uygulaması penceresi gibi tek bir sekmeye bağlıysa o sekmenin kimliği. */
    private val fixedTabId: String? = null,
) : DefaultLifecycleObserver {

    fun start() = activity.lifecycle.addObserver(this)

    override fun onStop(owner: LifecycleOwner) {
        if (components.settings.backgroundPlay.value) return
        if (activity.isChangingConfigurations || activity.isInPictureInPictureMode) return
        val state = components.store.state
        val sessions = if (fixedTabId != null) {
            listOfNotNull(state.tabs.firstOrNull { it.id == fixedTabId } ?: state.customTabs.firstOrNull { it.id == fixedTabId })
        } else {
            state.tabs
        }
        sessions.forEach { tab ->
            val media = tab.mediaSessionState ?: return@forEach
            if (media.playbackState == MediaSession.PlaybackState.PLAYING) media.controller.pause()
        }
    }
}
