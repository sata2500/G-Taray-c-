package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.ui.components.isDefaultBrowser
import com.gtarayici.browser.ui.components.rememberDefaultBrowserRequest

/**
 * İlk açılışta bir kez gösterilen tek ekranlık tanıtım. Her şey zaten hazırdır: dört kısa madde, varsayılan tarayıcı
 * düğmesi ve "Başla". Ayrıntılar isteyene "Neler yapabilirsin?" rehberi açılır.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, onGuide: () -> Unit = onDone) {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isDefaultBrowser(context)) }
    val requestDefault = rememberDefaultBrowserRequest { isDefault = it }

    // Surface, içerik rengini (onSurface) de sağlar; koyu temada renk belirtilmeyen metinler okunur kalır.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Point(Icons.Rounded.Shield, R.string.onboarding_point_block)
                Point(Icons.Rounded.Headphones, R.string.onboarding_point_media)
                Point(Icons.Rounded.Swipe, R.string.onboarding_point_swipe)
                Point(Icons.Rounded.VpnKey, R.string.onboarding_point_more)
            }
            Spacer(Modifier.height(28.dp))
            if (isDefault) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.onboarding_default_done), color = MaterialTheme.colorScheme.primary)
                }
            } else {
                FilledTonalButton(onClick = requestDefault, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_make_default))
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onDone, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.onboarding_start))
            }
            TextButton(onClick = onGuide) { Text(stringResource(R.string.guide_title)) }
        }
    }
}

@Composable
private fun Point(icon: ImageVector, text: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(22.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
