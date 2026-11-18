package com.gtarayici.browser.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtarayici.browser.components
import com.gtarayici.browser.search.UrlInput
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.icons.compose.Loader
import mozilla.components.browser.icons.compose.Placeholder
import mozilla.components.browser.icons.compose.WithIcon
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow

/** Tarayıcı durumunun bir parçasını Compose durumu olarak izler; yalnızca değiştiğinde yeniden çizer. */
@Composable
fun <T> BrowserStore.observe(selector: (BrowserState) -> T): State<T> {
    val flow = remember(this) { flow().map(selector).distinctUntilChanged() }
    return flow.collectAsState(initial = selector(state))
}

/** Sitenin simgesi; yüklenemezse alan adının ilk harfi gösterilir. */
@Composable
fun Favicon(url: String, modifier: Modifier = Modifier, size: Dp = 24.dp, private: Boolean = false) {
    val icons = LocalContext.current.components.icons
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4)), contentAlignment = Alignment.Center) {
        if (url.startsWith("http")) {
            icons.Loader(url, isPrivate = private) {
                Placeholder { LetterIcon(url, size) }
                WithIcon { icon -> Image(icon.painter, contentDescription = null, modifier = Modifier.size(size)) }
            }
        } else {
            LetterIcon(url, size)
        }
    }
}

@Composable
private fun LetterIcon(url: String, size: Dp) {
    val letter = UrlInput.host(url).firstOrNull()?.uppercaseChar()?.toString() ?: "G"
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.5f).sp,
        )
    }
}
