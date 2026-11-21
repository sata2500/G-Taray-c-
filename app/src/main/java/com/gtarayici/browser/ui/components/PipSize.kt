package com.gtarayici.browser.ui.components

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize

/**
 * Motor görünümünün boyutunu resim içinde resim sırasında PiP öncesindeki boyutta sabitler.
 *
 * Böylece web sayfası, pencere küçüldüğünde çok küçük bir ekran boyutu (ör. 220×124) görüp yerleşimini
 * değiştirmez; YouTube gibi oynatıcılar boyut değişikliğine tepki verip oynatmayı bozamaz. Görünüm
 * küçük pencereden taşar ama zaten PiP oynatıcısı tarafından örtülür.
 */
@Composable
fun Modifier.keepSizeDuringPip(inPip: Boolean): Modifier {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenArea = remember { screenArea(context) }
    var frozen by remember { mutableStateOf(IntSize.Zero) }
    return if (inPip && frozen != IntSize.Zero) {
        with(density) { this@keepSizeDuringPip.requiredSize(frozen.width.toDp(), frozen.height.toDp()) }
    } else {
        fillMaxSize().onSizeChanged { size ->
            // PiP'e geçişin ilk karesinde küçülmüş boyut gelebilir; yalnızca tam ekrana yakın boyutlar saklanır.
            if (!inPip && size.width.toLong() * size.height >= screenArea * 0.3) {
                frozen = size
            }
        }
    }
}

private fun screenArea(context: Context): Long {
    val wm = context.getSystemService(WindowManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.maximumWindowMetrics.bounds
        bounds.width().toLong() * bounds.height()
    } else {
        val metrics = context.resources.displayMetrics
        metrics.widthPixels.toLong() * metrics.heightPixels
    }
}
