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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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

private data class Suggestion(val icon: ImageVector, val title: String, val subtitle: String?, val target: String)

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
) {
    val context = LocalContext.current
    val db = context.components.database
    val profileId = context.components.profiles.activeProfile.id
    var value by remember {
        mutableStateOf(TextFieldValue(initialUrl, selection = TextRange(0, initialUrl.length)))
    }
    var suggestions by remember { mutableStateOf(emptyList<Suggestion>()) }
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
        if (!query.contains(' ') && URLStringUtils.isURLLike(query)) {
            list += Suggestion(Icons.Filled.Language, query, null, UrlInput.toLoadableUrl(query, engine))
        }
        list += Suggestion(Icons.Filled.Search, query, engine.title, engine.searchUrl(query))
        if (!private) {
            db.bookmarks().list(profileId).asSequence()
                .filter { TextMatch.matches(query, it.title, it.url) }.take(4).forEach {
                    list += Suggestion(Icons.Filled.Bookmark, it.title, UrlInput.displayUrl(it.url), it.url)
                }
            db.history().candidates(profileId).asSequence()
                .filter { TextMatch.matches(query, it.title, it.url) }.take(8).forEach { h ->
                if (list.none { it.target == h.url }) {
                    list += Suggestion(Icons.Filled.History, h.title.ifBlank { h.url }, UrlInput.displayUrl(h.url), h.url)
                }
            }
        }
        suggestions = list
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
                items(suggestions) { s ->
                    ListItem(
                        leadingContent = { Icon(s.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = s.subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onOpenUrl(s.target) },
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
                    keyboardActions = KeyboardActions(onGo = { if (value.text.isNotBlank()) onSubmit(value.text) }),
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
