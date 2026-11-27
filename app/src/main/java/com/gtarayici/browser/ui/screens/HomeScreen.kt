package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.gtarayici.browser.components
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.data.HistoryEntry
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.ui.components.Favicon
import com.gtarayici.browser.ui.designsystem.AppShape
import com.gtarayici.browser.ui.designsystem.Spacing
import com.gtarayici.browser.ui.designsystem.StatusChip
import com.gtarayici.browser.ui.designsystem.Tile
import com.gtarayici.browser.ui.designsystem.TileGrid
import com.gtarayici.browser.ui.theme.GTarayiciTheme

/** Ana sayfada gösterilen site kısayolu (sık ziyaret edilen veya yer imi). */
data class HomeSite(val url: String, val title: String)

/**
 * Geçmişten en sık ziyaret edilen siteleri (alan adına göre) çıkarır.
 * Her site için ana sayfası açılır; gizlenen alan adları atlanır.
 */
fun topSites(
    history: List<HistoryEntry>,
    hidden: Set<String>,
    limit: Int = 8,
    failed: Set<String> = emptySet(),
): List<HomeSite> =
    history
        // Hata sayfası gösterilen (açılamayan) adresler sık ziyaret edilen sayılmaz.
        .filter { it.url.startsWith("http") && it.url !in failed }
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
        .let(::disambiguate)

/**
 * Aynı kısa adı alan siteleri ayırt eder: tr.wikipedia.org ve en.wikipedia.org "Wikipedia (tr)" ve
 * "Wikipedia (en)" olur.
 */
private fun disambiguate(sites: List<HomeSite>): List<HomeSite> {
    val duplicates = sites.groupingBy { it.title }.eachCount().filterValues { it > 1 }.keys
    if (duplicates.isEmpty()) return sites
    return sites.map { site ->
        if (site.title !in duplicates) return@map site
        val host = hostOf(site.url).removePrefix("m.")
        val label = host.split('.').takeIf { it.size > 2 }?.first() ?: host
        site.copy(title = "${site.title} ($label)")
    }
}

/** Alan adı ("www." olmadan); [UrlInput.host] ile aynı sonucu verir. */
internal fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

/** "m.youtube.com" → "Youtube" gibi kısa, okunur bir ad. */
internal fun siteTitle(host: String): String {
    val parts = host.removePrefix("m.").split('.')
    // "hurriyet.com.tr", "bbc.co.uk" gibi iki parçalı ülke uzantılarında ad bir önceki parçadadır (önceden "Com" çıkıyordu).
    val secondLevel = parts.size >= 3 && parts.last().length == 2 && parts[parts.size - 2] in SECOND_LEVEL_LABELS
    val name = when {
        secondLevel -> parts[parts.size - 3]
        parts.size >= 2 -> parts[parts.size - 2]
        else -> parts.first()
    }
    return name.replaceFirstChar { it.titlecase() }
}

/** Ülke uzantılarının altındaki genel ikinci düzey adlar (com.tr, org.tr, gov.tr, co.uk, com.br…). */
private val SECOND_LEVEL_LABELS = setOf(
    "com", "org", "net", "gov", "edu", "co", "ac", "k12", "bel", "gen", "av", "web", "biz", "info", "tv", "pol", "tsk", "nom",
)

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
    onProtectionClick: () -> Unit = {},
    /** Kitaplıkta yarım kalan bir kitabı açar. */
    onOpenBook: (com.gtarayici.browser.library.Book) -> Unit = {},
) {
    // Surface içerik rengini (onSurface) de sağlar: renk belirtilmeyen metinler koyu temada siyah kalmaz.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(if (frequent.isEmpty() && bookmarks.isEmpty()) 88.dp else 56.dp))
                Emblem(private)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    if (private) stringResource(R.string.home_private_title) else greeting(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.lg))
                // Büyük arama kutusu: dokununca arama açılır (alttaki adres çubuğuyla aynı yer).
                SearchPill(private, onSearchClick, onScanQr)
                Spacer(Modifier.height(Spacing.lg))

                if (private) {
                    Surface(
                        shape = AppShape.lg,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Spacing.xl)) {
                            Text(
                                stringResource(R.string.home_private_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    StatusChip(stringResource(R.string.home_protection_on), Icons.Rounded.Shield, onClick = onProtectionClick)
                    Spacer(Modifier.height(Spacing.xl))
                    ContinueReading(onOpenBook)
                    if (frequent.isEmpty() && bookmarks.isEmpty()) {
                        Text(
                            stringResource(R.string.home_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Spacing.xl),
                        )
                    }
                    SiteSection(
                        title = stringResource(R.string.home_frequent),
                        sites = frequent,
                        removeLabel = stringResource(R.string.home_hide_site),
                        onOpen = onOpen,
                        onOpenInNewTab = onOpenInNewTab,
                        onRemove = onHideFrequent,
                    )
                    SiteSection(
                        title = stringResource(R.string.home_shortcuts),
                        sites = bookmarks,
                        removeLabel = stringResource(R.string.menu_remove_bookmark),
                        onOpen = onOpen,
                        onOpenInNewTab = onOpenInNewTab,
                        onRemove = onRemoveBookmark,
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

/** Günün saatine göre selamlama. */
@Composable
private fun greeting(): String {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_day
            in 18..22 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        },
    )
}

/** Ana sayfanın arama kutusu: büyük, yuvarlak; sağda QR kod okuyucu. */
@Composable
private fun SearchPill(private: Boolean, onClick: () -> Unit, onScanQr: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(if (private) R.string.private_search_hint else R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onScanQr) { Icon(Icons.Rounded.QrCodeScanner, stringResource(R.string.menu_tile_qr)) }
        }
    }
}

/** Kitaplıkta yarım kalan kitaplar: en son açılan üçü. */
@Composable
private fun ContinueReading(onOpen: (com.gtarayici.browser.library.Book) -> Unit) {
    val library = androidx.compose.ui.platform.LocalContext.current.components.library
    val books by library.books.collectAsState()
    val reading = books.filter { it.opened > 0 && library.progress(it) in 0.005f..0.98f }.sortedByDescending { it.opened }.take(3)
    if (reading.isEmpty()) return
    Text(
        stringResource(R.string.library_continue),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.xs, bottom = Spacing.sm),
    )
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        reading.forEach { book ->
            val progress = library.progress(book)
            Surface(onClick = { onOpen(book) }, shape = AppShape.lg, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("%${(progress * 100).toInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.xl))
}

/** Uygulama amblemi: ton dairesinde kalkan (gizli modda kapalı göz). */
@Composable
private fun Emblem(private: Boolean) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (private) Icons.Rounded.VisibilityOff else Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun SiteSection(
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
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.xs, bottom = Spacing.sm),
    )
    val shown = sites.take(8)
    TileGrid(columns = 4, count = shown.size) { index, modifier ->
        SiteTile(shown[index], removeLabel, onOpen, onOpenInNewTab, onRemove, modifier)
    }
    Spacer(Modifier.height(Spacing.lg))
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
        Tile(
            label = site.title,
            leading = { Favicon(site.url, size = 28.dp) },
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menu = true
            },
            onClick = { onOpen(site.url) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_open_new_tab)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                onClick = { menu = false; onOpenInNewTab(site.url) },
            )
            DropdownMenuItem(
                text = { Text(removeLabel) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { menu = false; onRemove(site) },
            )
        }
    }
}

private val previewSites = listOf(
    HomeSite("https://tr.wikipedia.org/", "Vikipedi"),
    HomeSite("https://www.youtube.com/", "YouTube"),
    HomeSite("https://example.com/", "Example"),
)

@Preview(name = "Ana sayfa – koyu", showBackground = true, heightDp = 720, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Ana sayfa – açık", showBackground = true, heightDp = 720)
@Preview(name = "Ana sayfa – büyük yazı", showBackground = true, heightDp = 720, fontScale = 2f)
@Composable
private fun HomePreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM) {
        HomeScreen(false, previewSites, previewSites.take(2), {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Ana sayfa – gizli", showBackground = true, heightDp = 720)
@Composable
private fun HomePrivatePreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM, private = true) {
        HomeScreen(true, emptyList(), emptyList(), {}, {}, {}, {}, {}, {})
    }
}
