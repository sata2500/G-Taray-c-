package com.gtarayici.browser.ui.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.ui.theme.GTarayiciTheme

/**
 * Ekran içinde gösterilen hata: anlaşılır cümle ve isteğe bağlı eylem. Ham teknik metin ([detail]) yalnızca
 * "Ayrıntılar" açılınca, seçilebilir (kopyalanabilir) biçimde görünür.
 */
@Composable
fun ErrorNotice(
    message: UiText,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = AppShape.md,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.xs)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.ErrorOutline, null, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                Text(
                    message.resolve(LocalContext.current),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = Spacing.md, end = Spacing.sm).weight(1f),
                )
            }
            AnimatedVisibility(expanded && !detail.isNullOrBlank()) {
                SelectionContainer {
                    Text(
                        detail.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 32.dp, top = Spacing.sm, end = Spacing.sm),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!detail.isNullOrBlank()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.err_details_hide else R.string.err_details_show))
                    }
                }
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

@Preview(name = "Hata bildirimi", showBackground = true)
@Preview(name = "Hata bildirimi – büyük yazı", showBackground = true, fontScale = 2f)
@Composable
private fun ErrorNoticePreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ErrorNotice(
                UiText.Plain("VPN bağlanamadı. Sunucuya ulaşılamadı. Bağlantı bilgilerini kontrol edin veya başka bir sunucu deneyin."),
                detail = "ConnectException: failed to connect to /10.0.0.1 (port 443)",
                actionLabel = "Yeniden dene",
                onAction = {},
            )
            ErrorNotice(UiText.Plain("Sayfa çevrilemedi. Bu dil çifti desteklenmiyor. Başka bir dil seçin."))
        }
    }
}
