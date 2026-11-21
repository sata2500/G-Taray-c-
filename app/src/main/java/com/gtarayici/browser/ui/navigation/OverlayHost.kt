package com.gtarayici.browser.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Tam ekran sayfaları (ayarlar, geçmiş, sekmeler…) tek yerden, animasyonlu açıp kapatan katman.
 *
 * - Açılış: sağdan kısa kayma + solma; kapanış ters yönde. Süreler ve yaylar Material 3 Expressive hareket
 *   şemasından gelir (bkz. docs/DESIGN.md).
 * - Android 14+ öngörülü geri hareketi: sayfa parmakla birlikte küçülür ve kenara kayar; bırakılınca kapanır,
 *   vazgeçilince yaylanarak yerine döner. Sayfaların kendi iç geri işleyicileri (alt sayfa, klasör) önceliklidir.
 * - Her sayfa bir Surface içinde çizilir: renk verilmeyen metinler de doğru içerik rengini alır.
 *
 * [none] hiçbir sayfanın açık olmadığı durumdur; [fullBleed] true dönen sayfalar (ör. kamera) sistem çubuklarının
 * altına kadar ve zeminsiz çizilir.
 */
@Composable
fun <T> OverlayHost(
    current: T,
    none: T,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    fullBleed: (T) -> Boolean = { false },
    content: @Composable (T) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    // Yeni bir sayfa açıldığında önceki hareketten kalan dönüşüm sıfırlanır.
    LaunchedEffect(current) { progress.snapTo(0f) }

    PredictiveBackHandler(enabled = current != none) { events ->
        try {
            events.collect { event ->
                edge = event.swipeEdge
                progress.snapTo(event.progress)
            }
            onBack()
        } catch (e: CancellationException) {
            scope.launch { progress.animateTo(0f, spring) }
            throw e
        }
    }

    val offsetSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val shift = with(LocalDensity.current) { 48.dp.roundToPx() }

    AnimatedContent(
        targetState = current,
        modifier = modifier,
        transitionSpec = {
            if (targetState == none) {
                // Kapanış: sayfa sağa kayarak kaybolur, altındaki tarayıcı zaten yerinde.
                ContentTransform(
                    targetContentEnter = EnterTransition.None,
                    initialContentExit = slideOutHorizontally(offsetSpec) { shift } + fadeOut(fastFade),
                    targetContentZIndex = 0f,
                )
            } else {
                (slideInHorizontally(offsetSpec) { shift } + fadeIn(fadeSpec))
                    .togetherWith(fadeOut(fastFade))
                    .apply { targetContentZIndex = 1f }
            }
        },
        label = "tam ekran sayfa",
    ) { page ->
        if (page == none) return@AnimatedContent
        val p = progress.value
        val dragged = Modifier.graphicsLayer {
            val scale = 1f - 0.08f * p
            scaleX = scale
            scaleY = scale
            translationX = (if (edge == BackEventCompat.EDGE_RIGHT) -1f else 1f) * p * 32.dp.toPx()
            shape = RoundedCornerShape(28.dp * p)
            clip = p > 0f
        }
        if (fullBleed(page)) {
            Box(Modifier.fillMaxSize().then(dragged)) { content(page) }
        } else {
            Surface(Modifier.fillMaxSize().then(dragged), color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) { content(page) }
            }
        }
    }
}
