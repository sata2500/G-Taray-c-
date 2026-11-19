package com.gtarayici.browser.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.gtarayici.browser.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Adres çubuğu olmayan pencerelerde (yüklü YouTube uygulaması gibi) video sayfasında beliren küçük indirme düğmesi.
 *
 * Sağ kenarda durur, parmakla yukarı-aşağı taşınabilir (yeri hatırlanır) ve birkaç saniye dokunulmazsa
 * içeriği kapatmamak için soluklaşır.
 */
@Composable
fun VideoDownloadButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Değişince (ör. yeni video) düğme yeniden belirginleşir. */
    resetKey: Any? = null,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("gtarayici_state", Context.MODE_PRIVATE) }
    // Konum, yüksekliğin oranı olarak saklanır (yön değişince de doğru yerde kalır).
    var fraction by remember { mutableFloatStateOf(prefs.getFloat(KEY_POSITION, DEFAULT_POSITION)) }
    var touches by remember { mutableIntStateOf(0) }
    var idle by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible, touches, resetKey) {
        idle = 0
        delay(FADE_DELAY_MS)
        idle = 1
    }
    val alpha by animateFloatAsState(if (idle == 1) 0.45f else 1f, label = "download-alpha")

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val size = 48.dp
        val travel = with(density) { (maxHeight - size).toPx().coerceAtLeast(1f) }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * travel).roundToInt()) }
                .padding(end = 10.dp),
        ) {
            Surface(
                onClick = {
                    touches++
                    onClick()
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(size)
                    .alpha(alpha)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            fraction = (fraction + delta / travel).coerceIn(0f, 1f)
                        },
                        onDragStarted = { touches++ },
                        onDragStopped = {
                            prefs.edit { putFloat(KEY_POSITION, fraction) }
                            touches++
                        },
                    ),
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = stringResource(R.string.menu_download_video),
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

private const val KEY_POSITION = "video_download_button_position"
private const val DEFAULT_POSITION = 0.62f
private const val FADE_DELAY_MS = 3_000L
