package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.runtime.collectAsState
import com.gtarayici.browser.search.SearchShortcuts
import com.gtarayici.browser.search.SearchSuggestions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.search.SearchEngine
import com.gtarayici.browser.search.TextMatch
import com.gtarayici.browser.search.UrlInput
import kotlinx.coroutines.delay
import mozilla.components.support.ktx.util.URLStringUtils

/** Öneri satırı: dokununca adres açılır ya da ([tabId] varsa) o sekmeye geçilir; [fill] metni giriş alanına koyar. */
private data class Suggestion(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val target: String,
    val tabId: String? = null,
    val fill: String? = null,
)

/**
 * Adres/arama giriş ekranı. Giriş alanı başparmağa yakın olsun diye altta durur;
 * öneriler (yer imleri ve geçmiş) hemen üstünde listelenir.
 */
@Composable
fun SearchScreen(
    initialUrl: String,
    private: Boolean,
    engine: SearchEngine,
    onSubmit: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    onScanQr: () -> Unit = {},
    /** Açık sekmeler (aynı profil ve aynı gizlilik): "Sekmeye geç" önerileri için. */
    openTabs: List<mozilla.components.browser.state.state.TabSessionState> = emptyList(),
    currentTabId: String? = null,
    onSwitchTab: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val db = context.components.database
    val profileId = context.components.profiles.activeProfile.id
    var value by remember {
        mutableStateOf(TextFieldValue(initialUrl, selection = TextRange(0, initialUrl.length)))
    }
    var suggestions by remember { mutableStateOf(emptyList<Suggestion>()) }
    val remoteEnabled by context.components.settings.searchSuggestions.collectAsState()
    val client = context.components.client
    val switchLabel = stringResource(R.string.search_switch_to_tab)
    val resources = androidx.compose.ui.platform.LocalResources.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(value.text) {
        val query = value.text.trim()
        if (query.isEmpty() || query == initialUrl) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(120)
        val list = mutableListOf<Suggestion>()
        val shortcut = SearchShortcuts.resolve(query)
        if (shortcut != null) {
            val (site, text) = shortcut
            list += Suggestion(Icons.Filled.Search, text, resources.getString(R.string.search_on_site, site.title), site.url(text))
        }
        if (!query.contains(' ') && URLStringUtils.isURLLike(query)) {
            list += Suggestion(Icons.Filled.Language, query, null, UrlInput.toLoadableUrl(query, engine))
        }
        if (shortcut == null) list += Suggestion(Icons.Filled.Search, query, engine.title, engine.searchUrl(query))
        openTabs.asSequence()
            .filter { it.id != currentTabId && TextMatch.matches(query, it.content.title, it.content.url) }
            .take(3)
            .forEach { tab ->
                list += Suggestion(
                    Icons.Filled.Tab,
                    tab.content.title.ifBlank { UrlInput.displayUrl(tab.content.url) },
                    "$switchLabel · ${UrlInput.displayUrl(tab.content.url)}",
                    tab.content.url,
                    tabId = tab.id,
                )
            }
        if (!private) {
            db.bookmarks().list(profileId).asSequence()
                .filter { TextMatch.matches(query, it.title, it.url) }.take(4).forEach {
                    if (list.none { s -> s.target == it.url }) {
                        list += Suggestion(Icons.Filled.Bookmark, it.title, UrlInput.displayUrl(it.url), it.url)
                    }
                }
            db.history().candidates(profileId).asSequence()
                .filter { TextMatch.matches(query, it.title, it.url) }.take(6).forEach { h ->
                if (list.none { it.target == h.url }) {
                    list += Suggestion(Icons.Filled.History, h.title.ifBlank { h.url }, UrlInput.displayUrl(h.url), h.url)
                }
            }
        }
        suggestions = list
        // Arama motorunun önerileri ağdan gelir; yerel öneriler beklemeden gösterilir, bunlar sonra eklenir.
        if (!private && remoteEnabled && shortcut == null && query.length >= 2 && !query.contains("://")) {
            delay(150)
            val remote = withContext(Dispatchers.IO) { SearchSuggestions.fetch(client, engine, query) }
            if (remote.isNotEmpty()) {
                val searchIndex = list.indexOfFirst { it.target == engine.searchUrl(query) }.coerceAtLeast(0)
                val extra = remote.map { Suggestion(Icons.Filled.Search, it, null, engine.searchUrl(it), fill = it) }
                suggestions = list.toMutableList().apply { addAll(searchIndex + 1, extra) }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
                items(suggestions) { s ->
                    ListRow(
                        leading = { Icon(s.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        headline = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supporting = s.subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        trailing = s.fill?.let { text ->
                            {
                                IconButton(onClick = {
                                    val filled = "$text "
                                    value = TextFieldValue(filled, selection = TextRange(filled.length))
                                }) {
                                    Icon(Icons.Filled.NorthWest, stringResource(R.string.search_fill))
                                }
                            }
                        },
                        modifier = Modifier.clickable { s.tabId?.let(onSwitchTab) ?: onOpenUrl(s.target) },
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(if (private) R.string.private_search_hint else R.string.search_hint))
                    },
                    trailingIcon = {
                        if (value.text.isNotEmpty()) {
                            IconButton(onClick = { value = TextFieldValue("") }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.action_clear))
                            }
                        } else {
                            IconButton(onClick = onScanQr) {
                                Icon(Icons.Filled.QrCodeScanner, stringResource(R.string.menu_scan_qr))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onGo = {
                        val text = value.text
                        if (text.isBlank()) return@KeyboardActions
                        // "yt kedi" gibi kısayollar doğrudan o sitede arar.
                        SearchShortcuts.resolve(text)?.let { (site, query) -> onOpenUrl(site.url(query)) } ?: onSubmit(text)
                    }),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
