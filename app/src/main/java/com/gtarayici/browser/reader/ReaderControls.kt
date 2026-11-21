package com.gtarayici.browser.reader

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import mozilla.components.feature.readerview.ReaderViewFeature.ColorScheme
import mozilla.components.feature.readerview.ReaderViewFeature.FontType
import mozilla.components.feature.readerview.view.ReaderViewControlsView

/**
 * Okuma görünümü ayarları (yazı tipi, boyut, renk) ile Mozilla'nın okuma modu bileşeni arasındaki köprü.
 *
 * Bileşen bir Android görünümü bekler; görünüm yalnızca açık/kapalı durumunu taşır, ayarlar
 * arayüzde [ReaderControlsSheet] ile gösterilir.
 */
@SuppressLint("ViewConstructor")
class ReaderControls(context: Context) : View(context), ReaderViewControlsView {
    override var listener: ReaderViewControlsView.Listener? = null

    var currentFont by mutableStateOf(FontType.SANSSERIF)
        private set
    var currentSize by mutableIntStateOf(DEFAULT_SIZE)
        private set
    var currentScheme by mutableStateOf(ColorScheme.LIGHT)
        private set
    var open by mutableStateOf(false)
        private set

    init {
        visibility = GONE
    }

    override fun setFont(font: FontType) {
        currentFont = font
    }

    override fun setFontSize(size: Int) {
        currentSize = size
    }

    override fun setColorScheme(scheme: ColorScheme) {
        currentScheme = scheme
    }

    override fun showControls() {
        visibility = VISIBLE
        open = true
    }

    override fun hideControls() {
        visibility = GONE
        open = false
    }

    override fun tryInflate(): Boolean = false

    fun selectFont(font: FontType) {
        currentFont = font
        listener?.onFontChanged(font)
    }

    fun selectScheme(scheme: ColorScheme) {
        currentScheme = scheme
        listener?.onColorSchemeChanged(scheme)
    }

    fun bigger() {
        listener?.onFontSizeIncreased()?.let { currentSize = it }
    }

    fun smaller() {
        listener?.onFontSizeDecreased()?.let { currentSize = it }
    }

    private companion object {
        const val DEFAULT_SIZE = 3
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderControlsSheet(controls: ReaderControls) {
    if (!controls.open) return
    ModalBottomSheet(
        onDismissRequest = { controls.hideControls() },
        sheetState = com.gtarayici.browser.ui.designsystem.rememberFullSheetState(),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleMedium)

            SectionLabel(R.string.reader_font)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = controls.currentFont == FontType.SANSSERIF,
                    onClick = { controls.selectFont(FontType.SANSSERIF) },
                    label = { Text(stringResource(R.string.reader_font_sans)) },
                )
                FilterChip(
                    selected = controls.currentFont == FontType.SERIF,
                    onClick = { controls.selectFont(FontType.SERIF) },
                    label = { Text(stringResource(R.string.reader_font_serif)) },
                )
            }

            SectionLabel(R.string.reader_size)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalIconButton(onClick = { controls.smaller() }) {
                    Icon(Icons.Filled.Remove, stringResource(R.string.reader_smaller))
                }
                Text("${controls.currentSize}", style = MaterialTheme.typography.titleMedium)
                FilledTonalIconButton(onClick = { controls.bigger() }) {
                    Icon(Icons.Filled.Add, stringResource(R.string.reader_bigger))
                }
            }

            SectionLabel(R.string.reader_colors)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ColorScheme.LIGHT to R.string.reader_light,
                    ColorScheme.SEPIA to R.string.reader_sepia,
                    ColorScheme.DARK to R.string.reader_dark,
                ).forEach { (scheme, label) ->
                    FilterChip(
                        selected = controls.currentScheme == scheme,
                        onClick = { controls.selectScheme(scheme) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}
