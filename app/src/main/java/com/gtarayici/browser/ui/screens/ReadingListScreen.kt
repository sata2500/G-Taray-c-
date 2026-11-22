package com.gtarayici.browser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.reading.Article
import com.gtarayici.browser.reading.ArticleBlock
import com.gtarayici.browser.ui.components.Favicon
import com.gtarayici.browser.ui.components.SpeakerBar
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.designsystem.ListRow
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.feedback.UiText
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/** "Sonra oku" listesi ve kaydedilen makalenin internetsiz okunduğu görünüm. */
@Composable
fun ReadingListScreen(onOpenUrl: (String) -> Unit, onSpeak: (Article, Int) -> Unit, onBack: () -> Unit) {
    val components = LocalContext.current.components
    val list = components.readingList
    val items by list.items.collectAsState()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = openId != null) { openId = null }

    val current = openId
    if (current != null) {
        val article by produceState<Article?>(null, current) { value = list.load(current) }
        ArticleView(article, onOpenUrl, onSpeak, onBack = { openId = null })
        return
    }

    OverlayScaffold(title = stringResource(R.string.reading_list), onBack = onBack) { modifier ->
        if (items.isEmpty()) {
            EmptyState(stringResource(R.string.reading_empty), modifier, icon = Icons.Rounded.AutoStories)
        } else {
            LazyColumn(modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    ListRow(
                        leading = { Favicon(item.url) },
                        headline = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supporting = {
                            Text(
                                "${item.site} · ${pluralStringResource(R.plurals.reading_minutes, item.minutes, item.minutes)} · " +
                                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.savedAt)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailing = {
                            IconButton(onClick = {
                                scope.launch {
                                    val removed = list.delete(item.id) ?: return@launch
                                    components.messenger.undoable(UiText.res(R.string.reading_deleted), "okuma-sil-${item.id}") {
                                        scope.launch { list.restore(removed) }
                                    }
                                }
                            }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                        },
                        modifier = Modifier.clickable { openId = item.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleView(article: Article?, onOpenUrl: (String) -> Unit, onSpeak: (Article, Int) -> Unit, onBack: () -> Unit) {
    val components = LocalContext.current.components
    var scale by rememberSaveable { mutableStateOf(1f) }
    OverlayScaffold(
        title = article?.site.orEmpty(),
        onBack = onBack,
        actions = {
            if (article != null) {
                IconButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(0.8f) }) { Icon(Icons.Rounded.TextDecrease, stringResource(R.string.site_text_smaller)) }
                IconButton(onClick = { scale = (scale + 0.1f).coerceAtMost(1.8f) }) { Icon(Icons.Rounded.TextIncrease, stringResource(R.string.site_text_larger)) }
                IconButton(onClick = { onSpeak(article, 0) }) { Icon(Icons.Rounded.RecordVoiceOver, stringResource(R.string.menu_read_aloud)) }
                IconButton(onClick = { onOpenUrl(article.url) }) { Icon(Icons.Rounded.OpenInBrowser, stringResource(R.string.reading_open_original)) }
            }
        },
    ) { modifier ->
        if (article == null) return@OverlayScaffold
        Column(modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
                item {
                    Text(
                        article.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize * scale,
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                    )
                }
                itemsIndexed(article.blocks) { index, block ->
                    // Paragrafa dokununca oradan sesli okunur (başlık 0. sırada olduğu için +1).
                    Text(
                        if (block.kind == ArticleBlock.Kind.ITEM) "•  ${block.text}" else block.text,
                        fontSize = (if (block.kind == ArticleBlock.Kind.HEADING) 20.sp else 17.sp) * scale,
                        lineHeight = 1.5.em,
                        fontWeight = if (block.kind == ArticleBlock.Kind.HEADING) FontWeight.SemiBold else null,
                        fontStyle = if (block.kind == ArticleBlock.Kind.QUOTE) FontStyle.Italic else null,
                        color = if (block.kind == ArticleBlock.Kind.QUOTE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpeak(article, index + 1) }
                            .padding(vertical = if (block.kind == ArticleBlock.Kind.HEADING) 12.dp else 6.dp),
                    )
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
            SpeakerBar(components.speaker)
        }
    }
}
