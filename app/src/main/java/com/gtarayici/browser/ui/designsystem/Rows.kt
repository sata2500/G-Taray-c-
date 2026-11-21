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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
fun RowIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    size: androidx.compose.ui.unit.Dp = Size.iconContainer,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
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

/**
 * Uzun listelerin satırı (geçmiş, indirilenler, sekmeler, sunucular…): saydam zemin, başta simge/görsel, ortada
 * başlık ve açıklama, sonda eylem. Yuvaları eski ListItem ile aynıdır; tıklama [modifier] ile verilir.
 */
@Composable
fun ListRow(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting == null) 56.dp else 72.dp)
            .padding(start = Spacing.lg, end = if (trailing == null) Spacing.lg else Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.widthIn(min = Size.icon), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) { leading() }
            }
            Spacer(Modifier.width(Spacing.lg))
        }
        Column(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge) { headline() }
            }
            if (supporting != null) {
                CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { supporting() }
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.sm))
            CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) { trailing() }
        }
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
            ListRow(
                headline = { Text("Vikipedi") },
                supporting = { Text("tr.wikipedia.org · 14:05") },
                leading = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
            )
        }
    }
}
