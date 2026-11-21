package com.gtarayici.browser.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.ui.theme.GTarayiciTheme

/** Geçmiş, yer imleri, indirilenler ve ayarlar gibi tam ekran sayfaların ortak iskeleti. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets(0),
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        // Yatay ekranda ve tablette satırlar ekran boyunca uzamasın: içerik en fazla [Size.maxContentWidth] genişlikte,
        // ortada durur (başlık ile açıklama arası çok uzayıp okunması zorlaşıyordu).
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            content(Modifier.widthIn(max = Size.maxContentWidth).fillMaxSize())
        }
    }
}

/**
 * "Ekle" türü genişletilmiş FAB. Material 3'ün ExtendedFloatingActionButton'ı içindeki metni erişilebilirlik
 * ağacına aktarmıyor (ekran okuyucu düğmeyi etiketsiz okuyor); etiket düğmenin kendisine verilir.
 */
@Composable
fun AddFab(label: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Rounded.Add, null) },
        text = { Text(label) },
        shape = AppShape.md,
        modifier = Modifier.semantics {
            contentDescription = label
            role = Role.Button
        },
    )
}

/**
 * Boş liste/sonuç durumu: ton dairesinde simge, açıklama ve isteğe bağlı eylem ("Bir site açın" gibi).
 * Simge verilmezse yalnızca metin gösterilir.
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            if (icon != null) {
                RowIcon(
                    icon,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    size = 72.dp,
                )
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** Uzun süren yükleme için ortalanmış Material 3 Expressive yükleme göstergesi ve açıklama. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(text: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LoadingIndicator(Modifier.size(48.dp))
            if (text != null) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(name = "Boş durum", showBackground = true)
@Preview(name = "Boş durum – büyük yazı", showBackground = true, fontScale = 2f)
@Composable
private fun EmptyStatePreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM) {
        Column {
            EmptyState("Henüz geçmiş yok", Modifier.size(360.dp, 280.dp), Icons.Rounded.History, "Bir site açın") {}
            LoadingState("Video bilgileri alınıyor…")
        }
    }
}
