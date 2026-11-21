package com.gtarayici.browser.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.ui.components.colorOption
import com.gtarayici.browser.ui.theme.GTarayiciTheme

/**
 * Renk seçicideki tek renk: 48 dp dokunma alanı içinde 32 dp daire. Seçiliyken onay işareti ve dış halka;
 * ekran okuyucuya "Renk 3 / 8" ve seçili durumu (radyo düğmesi) okunur.
 */
@Composable
fun ColorSwatch(color: Color, index: Int, count: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Size.touchTarget)
            .clip(CircleShape)
            .colorOption(index, count, selected, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .padding(if (selected) 3.dp else 0.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    null,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Renk seçeneklerini satıra sığmazsa alt satıra kaydırarak dizer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(colors: List<Color>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        colors.forEachIndexed { index, color ->
            ColorSwatch(color, index, colors.size, index == selected) { onSelect(index) }
        }
    }
}

@Preview(name = "Renk seçici", showBackground = true, widthDp = 300)
@Preview(name = "Renk seçici – büyük yazı", showBackground = true, widthDp = 300, fontScale = 2f)
@Composable
private fun ColorPickerPreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM) {
        var selected by remember { mutableIntStateOf(2) }
        ColorPicker(
            listOf(0xFF00696D, 0xFF3F5F90, 0xFF8E4585, 0xFFB3261E, 0xFFE8A33B, 0xFF4C662B, 0xFF6750A4, 0xFFFFD8E4).map(::Color),
            selected,
            { selected = it },
        )
    }
}
