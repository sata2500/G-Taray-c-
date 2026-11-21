package com.gtarayici.browser.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.ui.theme.GTarayiciTheme
import com.gtarayici.browser.settings.ThemeMode

/** Bölüm başlığı: kartın üstünde, birincil renkte küçük etiket. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.lg + Spacing.xs, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.sm),
    )
}

/**
 * Satırları tek bir "segmentli" kartta toplar: satırlar arasında ince boşluk, dış köşeler büyük.
 * [rows] her biri bir satır çizen composable listesidir; boş (null) satırlar atlanır.
 */
@Composable
fun SectionCard(
    rows: List<(@Composable () -> Unit)?>,
    modifier: Modifier = Modifier,
) {
    val visible = rows.filterNotNull()
    if (visible.isEmpty()) return
    Column(
        modifier.padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        visible.forEachIndexed { index, row ->
            Surface(
                shape = AppShape.segment(index, visible.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) { row() }
        }
    }
}

/** Satırın başındaki, ton dairesi içindeki simge. */
@Composable
fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSecondaryContainer, container: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Box(
        Modifier.size(Size.iconContainer).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Satır simgesinin biçimi: ayarlarda ton dairesi, menü gibi yoğun listelerde düz simge. */
enum class RowIconStyle { Tonal, Plain }

@Composable
private fun RowLayout(
    iconStyle: RowIconStyle,
    icon: ImageVector?,
    title: String,
    summary: String?,
    modifier: Modifier,
    trailing: @Composable (() -> Unit)?,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            if (iconStyle == RowIconStyle.Tonal) {
                RowIcon(icon)
            } else {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Size.icon))
            }
            Spacer(Modifier.width(Spacing.lg))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!summary.isNullOrBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.md))
            trailing()
        }
    }
}

/** Başka bir sayfaya veya pencereye götüren satır. */
@Composable
fun NavRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    showChevron: Boolean = true,
    iconStyle: RowIconStyle = RowIconStyle.Tonal,
    onClick: () -> Unit,
) {
    RowLayout(iconStyle, icon, title, summary, Modifier.clickable(onClick = onClick)) {
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Açık/kapalı bir ayar. Satırın tamamı dokunulabilir; ekran okuyucuya tek anahtar olarak okunur. */
@Composable
fun SwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    icon: ImageVector? = null,
    iconStyle: RowIconStyle = RowIconStyle.Tonal,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    RowLayout(
        iconStyle,
        icon,
        title,
        summary,
        Modifier.toggleable(value = checked, role = Role.Switch) {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onCheckedChange(it)
        },
    ) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Kartın içinde serbest içerik için kenar boşluklu sarmalayıcı. */
@Composable
fun SectionContent(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(Spacing.lg), content = content)
}

@Preview(name = "Bölüm kartı – koyu", showBackground = true, backgroundColor = 0xFF111318)
@Preview(name = "Bölüm kartı – açık", showBackground = true)
@Preview(name = "Bölüm kartı – büyük yazı", showBackground = true, fontScale = 2f)
@Composable
private fun SectionCardPreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM) {
        Column {
            SectionHeader("Gizlilik")
            SectionCard(
                listOf(
                    { SwitchRow("Yalnızca HTTPS", "Tüm bağlantıları şifreli HTTPS'ye yükseltir", true, Icons.AutoMirrored.Rounded.KeyboardArrowRight) {} },
                    { NavRow("Güvenli DNS", "Cloudflare", Icons.AutoMirrored.Rounded.KeyboardArrowRight) {} },
                ),
            )
        }
    }
}
