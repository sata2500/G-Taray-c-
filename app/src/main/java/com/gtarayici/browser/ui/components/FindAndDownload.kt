package com.gtarayici.browser.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.youtube.YouTubeVideo
import mozilla.components.browser.state.selector.selectedTab

/** Sayfada metin arama çubuğu. */
@Composable
fun FindInPageBar(onClose: () -> Unit) {
    val components = LocalContext.current.components
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results by components.store.observe { state ->
        state.selectedTab?.content?.findResults?.lastOrNull()?.let { it.activeMatchOrdinal + 1 to it.numberOfMatches }
    }
    val session = { components.store.state.selectedTab?.engineState?.engineSession }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isEmpty()) session()?.clearFindMatches() else session()?.findAll(it)
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.find_hint)) },
                suffix = { results?.let { (current, total) -> if (query.isNotEmpty()) Text("$current/$total") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { session()?.findNext(true) }),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            IconButton(onClick = { session()?.findNext(false) }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.find_previous)) }
            IconButton(onClick = { session()?.findNext(true) }) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.find_next)) }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
        }
    }
}

/** YouTube videosu için biçim/kalite seçme sayfası. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeDownloadSheet(pageUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val downloader = context.components.youtube
    var video by remember { mutableStateOf<YouTubeVideo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pageUrl) {
        downloader.fetch(pageUrl).onSuccess { video = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
    }

    // Tek "Geri" basışı pencereyi kapatsın (yarı açık ara durum yok).
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = com.gtarayici.browser.ui.designsystem.rememberFullSheetState()) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            Text(
                video?.title ?: stringResource(R.string.yt_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val current = video
            when {
                error != null -> Text(
                    stringResource(R.string.yt_error, error.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
                current == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.yt_loading), modifier = Modifier.padding(top = 12.dp))
                    }
                }
                else -> {
                    current.options.forEach { option ->
                        ListRow(
                            leading = { Icon(if (option.videoUrl != null) Icons.Filled.Movie else Icons.Filled.AudioFile, null) },
                            headline = { Text(option.label) },
                            supporting = { Text(option.detail) },
                            modifier = Modifier.clickable {
                                downloader.start(current, option)
                                Toast.makeText(context, R.string.yt_started, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                        )
                    }
                    Text(
                        stringResource(R.string.yt_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
