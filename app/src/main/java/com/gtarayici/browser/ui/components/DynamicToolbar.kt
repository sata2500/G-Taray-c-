package com.gtarayici.browser.ui.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt
import mozilla.components.concept.engine.EngineView

/**
 * Sayfa kaydırılınca aşağı inip geri gelen adres çubuğu.
 *
 * Çubuk sayfanın üstünde durur; [offset] 0 iken tamamen görünür, [heightPx] iken tamamen gizlidir.
 * Firefox motoruna çubuğun en büyük yüksekliği ve o anki görünür kısmı bildirilir; böylece sayfanın
 * altta sabit duran öğeleri (ör. YouTube'un alt menüsü) çubukla birlikte hareket eder, altında kalmaz.
 */
class DynamicToolbarState {
    var engineView: EngineView? = null

    /** Adres çubuğunun yüksekliği (piksel). */
    var heightPx by mutableIntStateOf(0)
        private set

    /** Ne kadar aşağı kaydığı: 0 = görünür, [heightPx] = gizli. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var enabled = false
    private var animator: ValueAnimator? = null

    /**
     * Çubuğun geçici olarak hep görünür kalması gerekiyorsa (ana sayfa, sayfada arama, çeviri önerisi).
     * Motora bildirilen yükseklik değişmez; böylece sayfa gereksiz yere yeniden yerleşmez.
     */
    var locked = false
        set(value) {
            field = value
            if (value) show()
        }

    /** Gizleme özelliğinin açık olup olmadığını ve çubuk yüksekliğini motora bildirir. */
    fun configure(enabled: Boolean, heightPx: Int) {
        val changed = this.enabled != enabled || this.heightPx != heightPx
        this.enabled = enabled
        this.heightPx = heightPx
        if (!changed) return
        engineView?.setDynamicToolbarMaxHeight(if (enabled) heightPx else 0)
        if (!enabled) set(0f) else engineView?.setVerticalClipping(-offset.roundToInt())
    }

    /** Parmak [dy] kadar dikey hareket etti (aşağı pozitif): yukarı kaydırınca çubuk iner. */
    fun drag(dy: Float) {
        if (!enabled || locked || heightPx == 0) return
        animator?.cancel()
        set((offset - dy).coerceIn(0f, heightPx.toFloat()))
    }

    /** Hareket bitince çubuk yarı yolda kalmaz: tamamen açılır veya kapanır. */
    fun settle() {
        if (!enabled || locked || offset == 0f || offset == heightPx.toFloat()) return
        animateTo(if (offset > heightPx / 2f) heightPx.toFloat() else 0f)
    }

    /** Çubuğu göster (sayfa değişince, arama açılınca vb.). */
    fun show() {
        if (offset != 0f) animateTo(0f)
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(offset, target).apply {
            duration = ANIMATION_MS
            addUpdateListener { set(it.animatedValue as Float) }
            start()
        }
    }

    private fun set(value: Float) {
        offset = value
        engineView?.setVerticalClipping(-value.roundToInt())
    }

    private companion object {
        const val ANIMATION_MS = 180L
    }
}

/**
 * Motor görünümünü saran ve dokunmaları yalnızca izleyen (tüketmeyen) kap. Sayfa gerçekten
 * kaydırılıyorsa (dokunmayı tarayıcı işliyorsa; harita gibi siteler kendi işler) çubuğu hareket ettirir.
 */
@SuppressLint("ViewConstructor")
class ScrollTrackingLayout(
    context: Context,
    private val engineView: EngineView,
    private val toolbar: DynamicToolbarState,
    content: View,
) : FrameLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var lastY = 0f
    private var tracking = false
    private var multiTouch = false

    init {
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                lastY = ev.y
                tracking = false
                multiTouch = false
            }
            // Yakınlaştırma hareketi: çubuk hareket etmez.
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                toolbar.settle()
            }
            MotionEvent.ACTION_MOVE -> if (!multiTouch) {
                val dy = ev.y - lastY
                lastY = ev.y
                if (!tracking) {
                    val totalY = abs(ev.y - startY)
                    tracking = totalY > touchSlop && totalY > abs(ev.x - startX)
                }
                if (tracking) {
                    val detail = engineView.getInputResultDetail()
                    if (detail.isTouchHandledByBrowser() && (detail.canScrollToBottom() || detail.canScrollToTop())) {
                        toolbar.drag(dy)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (tracking) toolbar.settle()
                tracking = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
