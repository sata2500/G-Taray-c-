package com.gtarayici.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.thumbnails.TabThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.EngineView
import kotlin.math.abs
import kotlin.math.sign

/** Kaydırma sırasında yanda görünen sekme. */
data class SwipeTab(val id: String, val title: String, val url: String, val private: Boolean)

/**
 * Araç çubuğunu yana kaydırınca sayfa parmakla birlikte kayar, komşu sekme yandan gelir.
 *
 * Motor görünümü (yüzey) kaydırılamaz; bu yüzden hareket başlarken görünen sayfanın görüntüsü alınır ve
 * hareket boyunca motorun üstünde bu görüntü ile komşu sekmenin küçük resmi kaydırılır. Hareket bitince ya
 * komşu sekmeye geçilir ve görüntü yeni sayfa çizildikten sonra kaybolur ya da sayfa yerine yaylanarak döner.
 */
@Stable
class TabSwipeState(
    private val scope: CoroutineScope,
    private val thumbnails: TabThumbnails,
    private val neighbors: () -> Triple<SwipeTab?, SwipeTab?, SwipeTab?>,
    private val engineView: () -> EngineView?,
    private val select: (String) -> Unit,
) {
    internal val offset = Animatable(0f)
    var active by mutableStateOf(false)
        private set
    var width by mutableFloatStateOf(0f)
    internal var alpha by mutableFloatStateOf(1f)
    internal var currentImage by mutableStateOf<Bitmap?>(null)
    internal var previous by mutableStateOf<SwipeTab?>(null)
    internal var next by mutableStateOf<SwipeTab?>(null)
    internal var current by mutableStateOf<SwipeTab?>(null)
    internal val images = mutableStateOf<Map<String, Bitmap>>(emptyMap())
    private var settle: Job? = null
    private var crossed = false
    /** Parmağın anlık konumu; Animatable'a eşzamansız yazıldığı için ayrıca tutulur. */
    private var raw = 0f

    /** Parmağın konumu, araç çubuğundaki adres alanının birlikte kayması için. */
    val shift: Float get() = offset.value

    fun start() {
        settle?.cancel()
        val (prev, cur, nxt) = neighbors()
        previous = prev
        current = cur
        next = nxt
        crossed = false
        alpha = 1f
        raw = offset.value
        if (!active) {
            currentImage = cur?.let { thumbnails.cached(it.id) }
            images.value = emptyMap()
            // Görüntü birkaç on milisaniyede gelir; gelene kadar sekmenin son küçük resmi kullanılır.
            engineView()?.captureThumbnail { bitmap -> if (bitmap != null && active) currentImage = bitmap }
        }
        active = true
        listOfNotNull(prev, nxt).forEach { tab ->
            thumbnails.cached(tab.id)?.let { images.value += tab.id to it } ?: scope.launch {
                thumbnails.load(tab.id, tab.private)?.let { images.value += tab.id to it }
            }
        }
    }

    /** Geçişe yetecek kadar kaydırıldığında bir kez titreşim vermek için true döner. */
    fun drag(delta: Float): Boolean {
        if (!active) return false
        val towards = if (raw + delta < 0) next else previous
        // Komşu sekme yoksa sayfa lastik gibi direnir.
        raw += if (towards == null) delta * 0.25f else delta
        val target = raw
        scope.launch { offset.snapTo(target) }
        val passed = abs(raw) > width * COMMIT_FRACTION && towards != null
        val buzz = passed && !crossed
        crossed = passed
        return buzz
    }

    fun end(velocity: Float) {
        if (!active) return
        val value = raw
        val direction = when {
            abs(velocity) > FLING_VELOCITY && sign(velocity) == sign(value) -> sign(velocity)
            abs(value) > width * COMMIT_FRACTION -> sign(value)
            else -> 0f
        }
        val target = when {
            direction < 0 -> next
            direction > 0 -> previous
            else -> null
        }
        settle = scope.launch {
            if (target == null) {
                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                finish()
                return@launch
            }
            offset.animateTo(direction * width, tween(durationMillis = 220), initialVelocity = velocity)
            select(target.id)
            // Yeni sekmenin sayfası çizilene kadar görüntü kalır, sonra yumuşakça kaybolur.
            delay(SETTLE_DELAY_MS)
            val fade = Animatable(1f)
            fade.animateTo(0f, tween(durationMillis = 180)) { alpha = value }
            finish()
        }
    }

    fun cancel() {
        if (!active) return
        settle = scope.launch {
            offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            finish()
        }
    }

    private suspend fun finish() {
        offset.snapTo(0f)
        active = false
        alpha = 1f
        currentImage = null
        images.value = emptyMap()
    }

    companion object {
        /** Ekran genişliğinin bu kadarı kaydırılınca bırakıldığında sekme değişir. */
        const val COMMIT_FRACTION = 0.3f
        /** Piksel/saniye; hızlı bir savurma kısa kaydırmada da sekmeyi değiştirir. */
        const val FLING_VELOCITY = 1400f
        const val SETTLE_DELAY_MS = 260L
    }
}

/** Motor görünümünün üstünde, kaydırma sürerken görünen katman. */
@Composable
fun TabSwipeLayer(state: TabSwipeState, modifier: Modifier = Modifier) {
    if (!state.active) return
    val images = state.images.value
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { alpha = state.alpha }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics { },
    ) {
        val offset = state.offset.value
        val width = state.width
        state.previous?.takeIf { offset > 0 }?.let { tab ->
            SwipePage(tab, images[tab.id], Modifier.graphicsLayer { translationX = offset - width })
        }
        state.next?.takeIf { offset < 0 }?.let { tab ->
            SwipePage(tab, images[tab.id], Modifier.graphicsLayer { translationX = offset + width })
        }
        SwipePage(
            state.current,
            state.currentImage,
            Modifier
                .graphicsLayer { translationX = offset }
                .shadow(if (offset != 0f) 12.dp else 0.dp),
        )
    }
}

@Composable
private fun SwipePage(tab: SwipeTab?, image: Bitmap?, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (image != null) {
            Image(
                image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (tab != null) {
            // Küçük resmi olmayan sekme (henüz açılmamış ya da boş): başlık ve adres gösterilir.
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    tab.title.ifBlank { UrlInput.host(tab.url) },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    UrlInput.host(tab.url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
