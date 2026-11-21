package com.gtarayici.browser.ui.screens

import com.gtarayici.browser.ui.feedback.UiText
import android.text.format.DateUtils
import com.gtarayici.browser.data.HistoryEntry
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.search.TextMatch
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.components.Favicon
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = context.components.database.history()
    val profileId = context.components.profiles.active.collectAsState().value.id
    val entries by remember(profileId) { dao.recent(profileId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    OverlayScaffold(
        title = stringResource(R.string.menu_history),
        onBack = onBack,
        actions = {
            if (entries.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.DeleteSweep, stringResource(R.string.history_clear_all))
                }
            }
        },
    ) { modifier ->
        val visible = entries.filter {
            filter.isBlank() || TextMatch.matches(filter, it.title, it.url)
        }
        Column(modifier) {
            if (entries.isNotEmpty()) FilterField(filter) { filter = it }
            if (visible.isEmpty()) {
                EmptyState(
                    stringResource(if (filter.isBlank()) R.string.history_empty else R.string.search_no_results),
                    icon = if (filter.isBlank()) Icons.Rounded.History else Icons.Rounded.SearchOff,
                )
            } else {
                LazyColumn {
                    historyGroups(visible).forEach { (label, group) ->
                        item(key = "day-$label") { DayHeader(stringResource(label)) }
                        items(group, key = { it.url }) { entry ->
                        ListRow(
                            leading = { Favicon(entry.url) },
                            headline = {
                                Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supporting = {
                                Text(
                                    "${UrlInput.host(entry.url)} · " +
                                        DateUtils.formatDateTime(context, entry.lastVisited, DateUtils.FORMAT_SHOW_TIME),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailing = {
                                IconButton(onClick = {
                                    val app = context.components
                                    app.applicationScope.launch(Dispatchers.IO) { dao.delete(profileId, entry.url) }
                                    app.messenger.undoable(UiText.res(R.string.history_entry_deleted), UNDO_HISTORY_KEY) {
                                        app.applicationScope.launch(Dispatchers.IO) { dao.upsert(entry) }
                                    }
                                }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.action_delete))
                                }
                            },
                            modifier = Modifier.clickable { onOpen(entry.url) },
                        )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch(Dispatchers.IO) { dao.clear(profileId) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}


/** Geçmişi "Bugün / Dün / Bu hafta / Bu ay / Daha eski" gruplarına ayırır (sıra korunur). */
internal fun historyGroups(entries: List<HistoryEntry>, now: Long = System.currentTimeMillis()): List<Pair<Int, List<HistoryEntry>>> {
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val today = calendar.timeInMillis
    val day = 24L * 60 * 60 * 1000
    fun bucket(time: Long) = when {
        time >= today -> R.string.history_today
        time >= today - day -> R.string.history_yesterday
        time >= today - 6 * day -> R.string.history_this_week
        time >= today - 29 * day -> R.string.history_this_month
        else -> R.string.history_older
    }
    return entries.groupBy { bucket(it.lastVisited) }.toList()
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}


private const val UNDO_HISTORY_KEY = "geri-al-gecmis"
