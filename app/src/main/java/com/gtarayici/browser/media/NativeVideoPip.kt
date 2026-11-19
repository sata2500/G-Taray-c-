package com.gtarayici.browser.media

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mozilla.components.support.base.log.logger.Logger
import com.gtarayici.browser.diagnostics.Diagnostics
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PictureInPictureController

/**
 * GeckoView'ın yerel video resim içinde resim desteği (GeckoView 160+).
 *
 * Küçük pencerede tarayıcı sayfası küçültülmez. Bunun yerine Gecko, o an oynayan videoyu ayrı bir
 * "PiP oturumuna" kopyalar ve bu oturum, [pipView] içinde tek başına gösterilir. Kaynak sayfa bu süre
 * boyunca etkin tutulur, böylece sayfa videoyu duraklatmaz.
 *
 * Ayrı bir etkinlik açmak yerine aynı pencere kullanılır: uygulama arka plana geçerken başka bir
 * etkinlik başlatmak Android'in arka plan kısıtlamalarına takılabilir.
 */
@OptIn(ExperimentalGeckoViewApi::class)
class NativeVideoPip(
    context: Context,
    private val runtime: GeckoRuntime,
    /** O an ekranda gösterilen sekmenin GeckoView'ı (kaynak oturum buradan alınır). */
    private val sourceView: () -> GeckoView?,
) {
    private val logger = Logger("NativeVideoPip")

    /** Küçük pencerede gösterilecek görünüm (yalnızca video). */
    val pipView: GeckoView = GeckoView(context).apply {
        // Kaynak sayfa PiP sırasında da görünür ve çizilir durumda kalır (YouTube gibi MSE oynatıcılar
        // yeni video parçalarını ancak sayfa çizilirken yükler). Bu görünüm onun üstünde durur; iki
        // SurfaceView'ın üst üste binme sırası belirsiz olduğundan TextureView ile çizilir.
        setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
        setBackgroundColor(Color.BLACK)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        visibility = View.GONE
    }

    /** true iken arayüz tarayıcı görünümünü gizleyip [pipView]'ı gösterir. */
    var active by mutableStateOf(false)
        private set

    private var source: GeckoSession? = null
    private var pipSession: GeckoSession? = null
    private var onFailure: (() -> Unit)? = null

    /**
     * Oynayan video için PiP oturumu ister. Kaynak oturum bulunamazsa false döner (çağıran
     * eski yönteme geçer). İstek sonradan başarısız olursa [failure] çağrılır.
     */
    fun enter(failure: () -> Unit): Boolean {
        val session = sourceView()?.session ?: return false
        source = session
        onFailure = failure
        val pip = session.sessionPictureInPicture
        // Sayfa gizlendiğinde "etkin değil" duruma düşüp videoyu durdurmasın.
        pip.setKeepActive(true)
        session.setPictureInPictureDelegate(object : PictureInPictureController.SessionPictureInPicture.Delegate {
            override fun onEnterPictureInPicture(session: GeckoSession, placementToken: String) {
                Diagnostics.log("Gecko PiP oynatıcısı istedi (anahtar ${if (placementToken.isEmpty()) "boş" else "var"})")
                if (placementToken.isEmpty()) fail("boş yerleştirme anahtarı") else attach(placementToken)
            }

            override fun onExitPictureInPicture(session: GeckoSession) {
                Diagnostics.log("Gecko PiP penceresinin kapanmasını istedi")
                // Gecko pencerenin kapanmasını istedi (ör. video kaldırıldı).
                releasePipSession()
            }
        })
        active = true
        pipView.visibility = View.VISIBLE
        // Sayfa oynayan bir video bulamazsa Gecko pencere açmadan isteği tamamlar; bu durumda eski yönteme geçilir.
        pipView.postDelayed({ if (active && source === session && pipSession == null) fail("video bulunamadı") }, ATTACH_TIMEOUT_MS)
        // Gecko oynayan videoyu odaklı çerçevede arar; uygulamadan çıkarken GeckoView odağı bırakır.
        session.setFocused(true)
        Diagnostics.log("yerel PiP isteniyor")
        pip.request().exceptionally<Void> { error ->
            pipView.post { fail(error.message ?: "istek reddedildi") }
            null
        }
        return true
    }

    private fun attach(token: String) {
        releasePipSession()
        // Kaynak oturumun ayarları (gizli mod, profil/contextId) korunur; yalnızca yerleştirme anahtarı eklenir.
        val settings = source?.settings?.let { GeckoSessionSettings.Builder(it) } ?: GeckoSessionSettings.Builder()
        val session = GeckoSession(settings.pictureInPictureToken(token).build())
        session.open(runtime)
        pipView.setSession(session)
        session.loadUri("about:blank")
        session.compositorController.onPipModeChanged(true)
        pipSession = session
        Diagnostics.log("PiP oynatıcı oturumu açıldı")
    }

    private fun fail(reason: String) {
        logger.warn("Yerel video PiP kullanılamadı: $reason")
        Diagnostics.log("yerel PiP başarısız: $reason")
        val callback = onFailure
        exit(dismissed = false)
        callback?.invoke()
    }

    /**
     * Küçük pencere kapandığında çağrılır. [dismissed] true ise (kullanıcı pencereyi kapattı)
     * kaynak sayfa da etkin olmayan duruma alınır; tarayıcıya dönüldüyse görünüm onu yeniden etkinleştirir.
     */
    fun exit(dismissed: Boolean) {
        val session = source
        source = null
        onFailure = null
        if (session != null && session.isOpen) {
            val pip = session.sessionPictureInPicture
            runCatching { pip.exit() }
            pip.setKeepActive(false)
            session.setPictureInPictureDelegate(null)
            if (dismissed) session.setActive(false)
        }
        releasePipSession()
        pipView.visibility = View.GONE
        active = false
    }

    private fun releasePipSession() {
        val session = pipSession ?: return
        pipSession = null
        runCatching { session.compositorController.onPipModeChanged(false) }
        pipView.releaseSession()
        session.close()
    }

    companion object {
        private const val ATTACH_TIMEOUT_MS = 2500L

        /** a-c'nin motor görünümünün içindeki GeckoView'ı bulur. */
        fun findGeckoView(view: View?): GeckoView? = when (view) {
            is GeckoView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findGeckoView(view.getChildAt(it)) }
            else -> null
        }
    }
}
