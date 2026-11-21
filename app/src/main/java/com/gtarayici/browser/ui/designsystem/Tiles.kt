package com.gtarayici.browser.ui.designsystem

import androidx.compose.runtime.remember
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.ui.theme.GTarayiciTheme

/**
 * Menü ve ana sayfa kutucuğu: ton kutusu içinde simge (veya site simgesi) + altında okunur etiket.
 * [leading] verilirse simge yerine o çizilir (ör. site simgesi).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(AppShape.md)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { role = Role.Button }
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = AppShape.md,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(Size.tile),
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    leading != null -> leading()
                    icon != null -> Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(Size.icon))
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        TileLabel(label)
    }
}

/**
 * Kutucuk etiketi: en fazla iki satır. Büyük yazıda tek bir kelime ("İndirilenler", "Wikipedia") kutu genişliğine
 * sığmıyorsa kelimenin ortadan bölünmesi yerine yazı en çok %30 küçültülür.
 */
@Composable
private fun TileLabel(label: String) {
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val available = constraints.maxWidth
        val widest = remember(label, style, available) {
            label.split(' ').filter { it.isNotEmpty() }.maxOfOrNull {
                measurer.measure(it, style, softWrap = false, maxLines = 1).size.width
            } ?: 0
        }
        val scale = if (widest > available && widest > 0) (available.toFloat() / widest).coerceAtLeast(MIN_LABEL_SCALE) else 1f
        Text(
            label,
            style = if (scale < 1f) style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale) else style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private const val MIN_LABEL_SCALE = 0.7f

/** Kutucukları eşit genişlikte satırlara dizer; son satır boşluklarla tamamlanır. */
@Composable
fun TileGrid(columns: Int, count: Int, modifier: Modifier = Modifier, item: @Composable (index: Int, Modifier) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        (0 until count).chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { index -> item(index, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Sakin bir durum çipi (ör. "Koruma açık"). Dokunulabilir; simge ve metin aynı renkte.
 */
@Composable
fun StatusChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = AppShape.full,
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = Spacing.md, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Preview(name = "Kutucuklar – koyu", showBackground = true, backgroundColor = 0xFF111318)
@Preview(name = "Kutucuklar – büyük yazı", showBackground = true, fontScale = 2f)
@Composable
private fun TilesPreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM) {
        Column(Modifier.padding(Spacing.lg)) {
            StatusChip("Koruma açık · izleyiciler engelleniyor", Icons.Rounded.Shield) {}
            Spacer(Modifier.height(Spacing.lg))
            TileGrid(columns = 4, count = 6) { i, m -> Tile("Kutucuk ${i + 1}", m, icon = Icons.Rounded.Add) {} }
        }
    }
}
