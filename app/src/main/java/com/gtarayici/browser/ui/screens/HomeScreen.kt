package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.data.HistoryEntry
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.components.Favicon

/** Ana sayfada gösterilen site kısayolu (sık ziyaret edilen veya yer imi). */
data class HomeSite(val url: String, val title: String)

/**
 * Geçmişten en sık ziyaret edilen siteleri (alan adına göre) çıkarır.
 * Her site için ana sayfası açılır; gizlenen alan adları atlanır.
 */
fun topSites(history: List<HistoryEntry>, hidden: Set<String>, limit: Int = 8): List<HomeSite> =
    history
        .filter { it.url.startsWith("http") }
        .groupBy { hostOf(it.url) }
        .filterKeys { it.isNotBlank() && it !in hidden }
        .map { (host, entries) ->
            val latest = entries.maxBy { it.lastVisited }
            val root = entries.firstOrNull { it.url.trimEnd('/').count { c -> c == '/' } <= 2 }
            val scheme = if (latest.url.startsWith("https")) "https" else "http"
            Triple(
                HomeSite(root?.url ?: "$scheme://${latest.url.substringAfter("://").substringBefore('/')}/", siteTitle(host)),
                entries.sumOf { it.visitCount },
                latest.lastVisited,
            )
        }
        .filter { it.second >= 2 }
        .sortedWith(compareByDescending<Triple<HomeSite, Int, Long>> { it.second }.thenByDescending { it.third })
        .take(limit)
        .map { it.first }

/** Alan adı ("www." olmadan); [UrlInput.host] ile aynı sonucu verir. */
internal fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

/** "m.youtube.com" → "Youtube" gibi kısa, okunur bir ad. */
private fun siteTitle(host: String): String {
    val parts = host.removePrefix("m.").split('.')
    val name = if (parts.size >= 2) parts[parts.size - 2] else parts.first()
    return name.replaceFirstChar { it.titlecase() }
}

/** Boş sekmede görünen başlangıç sayfası. */
@Composable
fun HomeScreen(
    private: Boolean,
    frequent: List<HomeSite>,
    bookmarks: List<HomeSite>,
    onSearchClick: () -> Unit,
    onScanQr: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onHideFrequent: (HomeSite) -> Unit,
    onRemoveBookmark: (HomeSite) -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (frequent.isEmpty() && bookmarks.isEmpty()) 96.dp else 56.dp))
            if (private) {
                Icon(
                    Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onSearchClick)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (private) R.string.private_search_hint else R.string.search_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onScanQr) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        stringResource(R.string.menu_scan_qr),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            if (private) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.home_private_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.home_private_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                SiteGrid(
                    title = stringResource(R.string.home_frequent),
                    sites = frequent,
                    removeLabel = stringResource(R.string.home_hide_site),
                    onOpen = onOpen,
                    onOpenInNewTab = onOpenInNewTab,
                    onRemove = onHideFrequent,
                )
                SiteGrid(
                    title = stringResource(R.string.home_shortcuts),
                    sites = bookmarks,
                    removeLabel = stringResource(R.string.menu_remove_bookmark),
                    onOpen = onOpen,
                    onOpenInNewTab = onOpenInNewTab,
                    onRemove = onRemoveBookmark,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SiteGrid(
    title: String,
    sites: List<HomeSite>,
    removeLabel: String,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onRemove: (HomeSite) -> Unit,
) {
    if (sites.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
    )
    sites.take(8).chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            row.forEach { site ->
                SiteTile(site, removeLabel, onOpen, onOpenInNewTab, onRemove, Modifier.weight(1f))
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SiteTile(
    site: HomeSite,
    removeLabel: String,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onRemove: (HomeSite) -> Unit,
    modifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { onOpen(site.url) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Favicon(site.url, size = 28.dp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                site.title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_open_new_tab)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                onClick = { menu = false; onOpenInNewTab(site.url) },
            )
            DropdownMenuItem(
                text = { Text(removeLabel) },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { menu = false; onRemove(site) },
            )
        }
    }
}
