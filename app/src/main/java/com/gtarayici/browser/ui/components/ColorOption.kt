package com.gtarayici.browser.ui.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gtarayici.browser.R

/**
 * Renk seçicideki bir daire. Yalnızca `clickable` kullanılınca daireler ekran okuyucuya etiketsiz ve seçili
 * durumu belirsiz görünür; burada "Renk 3 / 8" etiketi ve seçili durumu (radyo düğmesi) verilir.
 */
@Composable
fun Modifier.colorOption(index: Int, count: Int, selected: Boolean, onClick: () -> Unit): Modifier {
    val label = stringResource(R.string.color_option, index + 1, count)
    return this
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = label }
}
