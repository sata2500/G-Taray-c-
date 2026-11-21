package com.gtarayici.browser.ui.feedback

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.ui.designsystem.AppShape
import com.gtarayici.browser.ui.designsystem.Spacing
import com.gtarayici.browser.ui.theme.GTarayiciTheme
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * [Messenger] kuyruğunun ilk mesajını alt çubuğun üstünde gösterir. Süre dolunca, eylem seçilince veya yana
 * kaydırılınca kapanır; ardından sıradaki mesaj gelir.
 */
@Composable
fun MessageHost(messenger: Messenger, modifier: Modifier = Modifier) {
    val queue by messenger.current.collectAsState()
    val message = queue.firstOrNull()
    val motion = MaterialTheme.motionScheme
    val spatial = motion.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = motion.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = message,
        modifier = modifier,
        contentKey = { it?.id },
        transitionSpec = {
            (slideInVertically(spatial) { it / 2 } + fadeIn(effects))
                .togetherWith(slideOutVertically(spatial) { it / 2 } + fadeOut(effects))
        },
        label = "mesaj",
    ) { shown ->
        if (shown == null) return@AnimatedContent
        LaunchedEffect(shown.id) {
            delay(if (shown.long) Messenger.LONG_MS else Messenger.SHORT_MS)
            messenger.dismiss(shown)
        }
        MessageCard(
            text = shown.text.resolve(LocalContext.current),
            kind = shown.kind,
            actionLabel = shown.action?.label?.resolve(LocalContext.current),
            onAction = {
                shown.action?.onClick?.invoke()
                messenger.dismiss(shown)
            },
            onSwipe = { messenger.dismiss(shown) },
        )
    }
}

@Composable
private fun MessageCard(
    text: String,
    kind: MessageKind,
    actionLabel: String?,
    onAction: () -> Unit,
    onSwipe: () -> Unit,
) {
    val (icon, accent) = kindStyle(kind)
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = AppShape.lg,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, amount -> total += amount },
                        onDragEnd = { if (abs(total) > 80.dp.toPx()) onSwipe() },
                    )
                },
        ) {
            Row(
                Modifier.heightIn(min = 56.dp).padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
                if (actionLabel != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel, color = MaterialTheme.colorScheme.inversePrimary, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Spacer(Modifier.width(Spacing.sm))
                }
            }
        }
    }
}

@Composable
private fun kindStyle(kind: MessageKind): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> = when (kind) {
    MessageKind.Success -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.inversePrimary
    MessageKind.Info -> Icons.Rounded.Info to MaterialTheme.colorScheme.inverseOnSurface
    MessageKind.Hint -> Icons.Rounded.Lightbulb to MaterialTheme.colorScheme.inversePrimary
    MessageKind.Warning -> Icons.Rounded.WarningAmber to Color(0xFFFFB951)
    MessageKind.Error -> Icons.Rounded.ErrorOutline to Color(0xFFFFB4AB)
}

@Preview(name = "Mesajlar", showBackground = true)
@Preview(name = "Mesajlar – büyük yazı", showBackground = true, fontScale = 2f)
@Composable
private fun MessagePreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM) {
        Column(Modifier.padding(vertical = Spacing.lg)) {
            MessageCard("Yer imlerine eklendi", MessageKind.Success, null, {}, {})
            Spacer(Modifier.size(Spacing.sm))
            MessageCard("Sekme kapatıldı", MessageKind.Info, "Geri al", {}, {})
            Spacer(Modifier.size(Spacing.sm))
            MessageCard("İnternet bağlantısı yok", MessageKind.Warning, null, {}, {})
            Spacer(Modifier.size(Spacing.sm))
            MessageCard("Sunucuya ulaşılamadı — bağlantı bilgilerini kontrol edin veya başka bir sunucu deneyin", MessageKind.Error, "Yeniden dene", {}, {})
        }
    }
}
