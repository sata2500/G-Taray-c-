package com.gtarayici.browser.ui.components

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.media.InAppMiniPlayer
import kotlinx.coroutines.delay
import mozilla.components.concept.engine.mediasession.MediaSession
import kotlin.math.roundToInt

/**
 * Uygulama içi küçük pencere: sürüklenerek taşınır; dokununca oynat/duraklat, kapat ve "sekmeye dön" düğmeleri
 * görünür, çift dokunmak videonun sekmesine döndürür.
 */
@Composable
fun MiniPlayerWindow(mini: InAppMiniPlayer, onExpand: (tabId: String) -> Unit) {
    val tabId = mini.tabId ?: return
    if (!mini.visible) return
    val context = LocalContext.current
    val components = context.components
    val playing by components.store.observe { state ->
        state.tabs.firstOrNull { it.id == tabId }?.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING
    }
    val size by components.store.observe { state ->
        state.tabs.firstOrNull { it.id == tabId }?.mediaSessionState?.elementMetadata?.let { it.width to it.height }
    }
    val latest by components.extensions.videoRect.collectAsState()
    val tabUrl = components.store.state.tabs.firstOrNull { it.id == tabId }?.content?.url
    val rect = latest?.takeIf { com.gtarayici.browser.extensions.samePage(it.page, tabUrl) }
    // Önce videonun gerçek boyutu (dikey Shorts gibi), sonra motorun bildirdiği boyut; yoksa 16:9.
    val ratio = (
        rect?.takeIf { it.naturalWidth > 0 && it.naturalHeight > 0 }?.let { it.naturalWidth.toFloat() / it.naturalHeight }
            ?: size?.takeIf { it.first > 0 && it.second > 0 }?.let { it.first.toFloat() / it.second }
            ?: (16f / 9f)
        ).coerceIn(9f / 16f, 16f / 9f)

    var controls by remember { mutableStateOf(false) }
    LaunchedEffect(controls, playing) {
        if (controls && playing) {
            delay(CONTROLS_TIMEOUT_MS)
            controls = false
        }
    }
    var offset by remember { mutableStateOf<Offset?>(null) }
    val shape = RoundedCornerShape(16.dp)
    // Kullanıcının seçtiği boyut (iki parmakla ya da köşedeki tutamaçla); saklanır.
    val prefs = remember { context.getSharedPreferences("gtarayici_state", android.content.Context.MODE_PRIVATE) }
    var sizeScale by remember { mutableStateOf(prefs.getFloat(KEY_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)) }
    var resizing by remember { mutableStateOf(false) }
    LaunchedEffect(sizeScale) {
        delay(400)
        prefs.edit().putFloat(KEY_SCALE, sizeScale).apply()
        resizing = false
    }
    // Açılış: pencere köşesinden büyüyerek ve belirerek gelir (her yeni videoda yeniden).
    val appear = remember(tabId) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(tabId) {
        appear.animateTo(
            1f,
            androidx.compose.animation.core.spring(
                dampingRatio = 0.75f,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        val density = LocalDensity.current
        val base = if (ratio >= 1f) maxWidth * 0.6f else maxWidth * 0.38f
        // Pencere ekrandan taşmaz: genişlik de yükseklik de sınırlıdır.
        val targetWidth = (base * sizeScale).coerceIn(140.dp, minOf(maxWidth, maxHeight * ratio))
        // Video oranı değişince (ör. yatay videodan dikey Shorts'a) pencere yumuşakça yeniden boyutlanır; parmakla
        // boyutlandırırken parmağı gecikmesiz izler.
        val spec: androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.unit.Dp> =
            if (resizing) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring()
        val width by androidx.compose.animation.core.animateDpAsState(targetWidth, spec, label = "mini-width")
        val height by androidx.compose.animation.core.animateDpAsState(targetWidth / ratio, spec, label = "mini-height")
        val basePx = with(density) { base.toPx() }.coerceAtLeast(1f)
        val maxX = with(density) { (maxWidth - width).toPx() }.coerceAtLeast(0f)
        val maxY = with(density) { (maxHeight - height).toPx() }.coerceAtLeast(0f)
        // İlk konum: sağ alt köşe, alttaki adres çubuğunun üstü.
        val start = Offset(maxX, (maxY - with(density) { 72.dp.toPx() }).coerceAtLeast(0f))
        fun clamp(o: Offset) = Offset(o.x.coerceIn(0f, maxX), o.y.coerceIn(0f, maxY))
        val position = clamp(offset ?: start)
        val currentPosition by rememberUpdatedState(position)
        val clampNow by rememberUpdatedState(::clamp)

        Box(
            Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .size(width, height)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 0.6f + 0.4f * p
                    scaleY = 0.6f + 0.4f * p
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                }
                .shadow(8.dp, shape)
                .clip(shape)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { mini.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize(),
            )
            // Dokunmalar videoya (sayfaya) değil pencereye gider.
            Box(
                Modifier
                    .fillMaxSize()
                    // Tek parmak: taşı. İki parmak: büyüt/küçült.
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (zoom != 1f) {
                                resizing = true
                                sizeScale = (sizeScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            }
                            offset = clampNow(currentPosition + pan)
                        }
                    }
                    .pointerInput(tabId) {
                        detectTapGestures(onTap = { controls = !controls }, onDoubleTap = { onExpand(tabId) })
                    },
            )
            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                    IconButton(onClick = { onExpand(tabId) }, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.Rounded.OpenInFull, stringResource(R.string.mini_player_expand), tint = Color.White)
                    }
                    IconButton(onClick = { mini.close() }, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.mini_player_close), tint = Color.White)
                    }
                    // Boyutlandırma tutamacı (sol alt köşe): sola/yukarı çekince büyür, sağa/aşağı itince küçülür.
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .size(44.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(onDragEnd = { resizing = false }) { change, drag ->
                                    change.consume()
                                    resizing = true
                                    val delta = (-drag.x + drag.y * -0.5f) / basePx
                                    val before = sizeScale
                                    sizeScale = (sizeScale + delta).coerceIn(MIN_SCALE, MAX_SCALE)
                                    // Sağ alt köşe yerinde kalsın: pencere sola/yukarı doğru büyür.
                                    val grown = (sizeScale - before) * basePx
                                    offset = clampNow(currentPosition - Offset(grown, grown / ratio))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.OpenWith,
                            stringResource(R.string.mini_player_resize),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { mini.togglePlayback() }, modifier = Modifier.align(Alignment.Center).size(56.dp)) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (playing) R.string.pip_pause else R.string.pip_play),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val CONTROLS_TIMEOUT_MS = 3_000L
private const val KEY_SCALE = "mini_player_scale"
private const val MIN_SCALE = 0.55f
private const val MAX_SCALE = 1.65f
