package com.gtarayici.browser.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components

/** Yeni sürüm bulunduğunda veya güncelleme indirilirken gösterilen pencere. */
@Composable
fun UpdateDialog() {
    val updater = LocalContext.current.components.updater
    val prompt by updater.prompt.collectAsState()
    val state by updater.state.collectAsState()

    when (val current = state) {
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = { updater.dismissError() },
            title = { Text(stringResource(R.string.update_title_failed)) },
            text = { Text(current.message) },
            confirmButton = { TextButton(onClick = { updater.dismissError() }) { Text(stringResource(R.string.action_ok)) } },
        )
        is UpdateState.Available -> if (prompt) {
            AlertDialog(
                onDismissRequest = { updater.later() },
                title = { Text(stringResource(R.string.update_title, current.release.version)) },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text(
                            stringResource(R.string.update_current, updater.currentVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (current.release.notes.isNotBlank()) {
                            Text(current.release.notes.replace("**", ""), modifier = Modifier.padding(top = 12.dp))
                        }
                        Text(
                            stringResource(R.string.update_size, current.release.apkSize / (1024 * 1024)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { updater.install(current.release) }) { Text(stringResource(R.string.update_install)) }
                },
                dismissButton = { TextButton(onClick = { updater.later() }) { Text(stringResource(R.string.update_later)) } },
            )
        }
        is UpdateState.Downloading -> if (prompt) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_downloading, current.release.version)) },
                text = {
                    Column {
                        LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth())
                        Text("%${(current.progress * 100).toInt()}", modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = {},
            )
        }
        else -> Unit
    }
}
