package com.gtarayici.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import com.gtarayici.browser.ui.designsystem.AppShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.gtarayici.browser.settings.ThemeMode

// Yedek marka paleti (dinamik renk yokken: Android 9–11). Tohum renk #00696D, camgöbeği–yeşil.
private val LightColors = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF0F4),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6364),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF051F20),
    tertiary = Color(0xFF4E5F7D),
    tertiaryContainer = Color(0xFFD6E3FF),
    onTertiaryContainer = Color(0xFF0A1C36),
    background = Color(0xFFF5FAFA),
    surface = Color(0xFFF5FAFA),
    onSurface = Color(0xFF171D1D),
    onSurfaceVariant = Color(0xFF3F4949),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F5),
    surfaceContainer = Color(0xFFE9EFEF),
    surfaceContainerHigh = Color(0xFFE3E9E9),
    surfaceContainerHighest = Color(0xFFDEE4E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D4D8),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    onPrimaryContainer = Color(0xFF9CF0F4),
    secondary = Color(0xFFB0CCCD),
    onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF324B4C),
    onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFB5C7E9),
    tertiaryContainer = Color(0xFF364764),
    onTertiaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF0E1415),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDEE4E4),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF171D1D),
    surfaceContainer = Color(0xFF1B2121),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
)

/** Gizli sekmelerde kullanılan mor tonlu, her zaman koyu renk şeması (tüm yüzey tonları tanımlı). */
private val PrivateColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF1A1325),
    surface = Color(0xFF1A1325),
    onSurface = Color(0xFFE9E0EE),
    onSurfaceVariant = Color(0xFFCBC2D6),
    outline = Color(0xFF948DA0),
    outlineVariant = Color(0xFF4A4458),
    surfaceContainerLowest = Color(0xFF140E1E),
    surfaceContainerLow = Color(0xFF21192D),
    surfaceContainer = Color(0xFF261D33),
    surfaceContainerHigh = Color(0xFF32283F),
    surfaceContainerHighest = Color(0xFF3D334A),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun GTarayiciTheme(
    mode: ThemeMode,
    private: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(mode)
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        private -> PrivateColors
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        // Material 3 Expressive hareket şeması: konum için yaylı, renk/opaklık için sönümlü geçişler.
        motionScheme = MotionScheme.expressive(),
        shapes = AppShape.material,
        content = content,
    )
}
