package com.gtarayici.browser.ui.feedback

import androidx.annotation.StringRes
import com.gtarayici.browser.R

/** Bir özelliğin ilk kullanıldığı anda bir kez gösterilen bağlamsal ipuçları. */
enum class Hint(val key: String, @StringRes val text: Int) {
    YouTubeVideo("youtube_video", R.string.hint_youtube_video),
    ManyTabs("many_tabs", R.string.hint_many_tabs),
    VpnError("vpn_error", R.string.hint_vpn_error),
}

/** İpuçlarının kalıcı durumu (açık/kapalı, görülenler). Uygulamada AppSettings, testte bellek içi sahte sınıf. */
interface HintStore {
    val enabled: Boolean
    fun isSeen(hint: Hint): Boolean
    fun markSeen(hint: Hint)
}

/**
 * İpucu kuralları:
 * - Ayarlardan kapatılabilir; kapalıyken hiç gösterilmez.
 * - Her ipucu yalnızca bir kez gösterilir (gösterildiği an "görüldü" sayılır).
 * - Aynı anda en fazla bir ipucu: son ipucundan sonra [GAP_MS] geçmeden yenisi gösterilmez.
 */
class HintRules(private val store: HintStore, private val clock: () -> Long = System::currentTimeMillis) {
    private var lastShownAt: Long? = null

    fun canShow(hint: Hint): Boolean {
        if (!store.enabled || store.isSeen(hint)) return false
        val last = lastShownAt ?: return true
        return clock() - last >= GAP_MS
    }

    /** Kurallar izin veriyorsa ipucunu "gösterildi" olarak işaretler ve true döner. */
    fun consume(hint: Hint): Boolean {
        if (!canShow(hint)) return false
        store.markSeen(hint)
        lastShownAt = clock()
        return true
    }

    companion object {
        const val GAP_MS = 10 * 60 * 1000L
        const val MANY_TABS = 5

        /** Çok sekme ipucu yalnızca alt çubuğu kaydırarak sekme değiştirme açıksa anlamlıdır. */
        fun manyTabs(count: Int, toolbarSwipeEnabled: Boolean) = toolbarSwipeEnabled && count >= MANY_TABS
    }
}

/** Kurallar izin verirse ipucunu ortak mesaj kanalında gösterir. */
class Hints(private val rules: HintRules, private val messenger: Messenger) {
    fun offer(hint: Hint) {
        if (!rules.consume(hint)) return
        messenger.show(
            Message(
                text = UiText.res(hint.text),
                kind = MessageKind.Hint,
                action = MessageAction(UiText.res(R.string.action_got_it)) {},
                long = true,
                key = HINT_KEY,
            ),
        )
    }

    private companion object {
        const val HINT_KEY = "ipucu"
    }
}
