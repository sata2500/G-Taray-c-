package com.gtarayici.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.reading.Speaker

private val SPEEDS = listOf(0.8f, 1f, 1.25f, 1.5f, 2f)

/** Sesli okuma sürerken araç çubuğunun üstünde duran denetim çubuğu. */
@Composable
fun SpeakerBar(speaker: Speaker, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = speaker.active, modifier = modifier) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { if (speaker.count == 0) 0f else (speaker.index + 1f) / speaker.count },
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
                Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        speaker.title.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val next = SPEEDS[(SPEEDS.indexOfFirst { it >= speaker.rate - 0.01f } + 1) % SPEEDS.size]
                        speaker.setSpeed(next)
                    }) {
                        Text("${speaker.rate.toString().removeSuffix(".0")}×")
                    }
                    IconButton(onClick = { speaker.skip(-1) }) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.speaker_previous)) }
                    FilledIconButton(onClick = { speaker.toggle() }) {
                        Icon(
                            if (speaker.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (speaker.playing) R.string.speaker_pause else R.string.speaker_play),
                        )
                    }
                    IconButton(onClick = { speaker.skip(1) }) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.speaker_next)) }
                    IconButton(onClick = { speaker.close() }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_close)) }
                }
            }
        }
    }
}
