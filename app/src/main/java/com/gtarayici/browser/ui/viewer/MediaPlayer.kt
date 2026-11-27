@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.gtarayici.browser.ui.viewer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.text.format.DateUtils
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gtarayici.browser.R
import com.gtarayici.browser.viewer.FileKind
import com.gtarayici.browser.viewer.FileSource
import com.gtarayici.browser.viewer.ReadingPositions
import kotlinx.coroutines.delay
import kotlin.math.abs

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val RESIZE = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to R.string.player_fit,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to R.string.player_fill,
    AspectRatioFrameLayout.RESIZE_MODE_FILL to R.string.player_stretch,
)

/**
 * Video ve ses oynatıcı. Dokununca denetimler görünür/gizlenir; ekranın sol/sağ yarısına çift dokunmak 10 sn geri/ileri
 * sarar; sol yarıda yukarı-aşağı kaydırmak parlaklığı, sağ yarıda sesi, yatay kaydırmak konumu değiştirir. Kaldığı
 * yerden devam eder.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun MediaPlayer(source: FileSource, chrome: Boolean, onToggleChrome: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val activity = context as? Activity
    val positions = remember { ReadingPositions(context) }
    val audioOnly = source.kind == FileKind.AUDIO
    val player = remember(source.key) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(source.dataSourceFactory(context)))
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                setMediaItem(MediaItem.fromUri(source.mediaUri))
                val saved = positions.get(source.key)
                if (saved > 5_000) seekTo(saved)
                prepare()
                playWhenReady = true
            }
    }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loop by remember { mutableStateOf(false) }
    var resize by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var error by remember { mutableStateOf<String?>(null) }
    var artwork by remember { mutableStateOf<Bitmap?>(null) }
    var title by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    var seeking by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                activity?.window?.let { w ->
                    if (isPlaying) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onTracksChanged(t: Tracks) { tracks = t }

            override fun onPlayerError(e: PlaybackException) {
                error = resources.getString(R.string.player_error)
            }

            override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                metadata.artworkData?.let { data -> artwork = BitmapFactory.decodeByteArray(data, 0, data.size) }
                title = metadata.title?.toString() ?: title
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) positions.set(source.key, 0)
            }
        }
        player.addListener(listener)
        onDispose {
            if (player.playbackState != Player.STATE_ENDED) positions.set(source.key, player.currentPosition)
            player.removeListener(listener)
            player.release()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
            delay(250)
            if (playing && position > 0 && position % 5_000 < 250) positions.set(source.key, position)
        }
    }
    LaunchedEffect(chrome, playing) {
        if (chrome && playing && !audioOnly) {
            delay(3_500)
            onToggleChrome()
        }
    }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(900)
            hint = null
        }
    }
    if (audioOnly) {
        val thumb by produceState<Bitmap?>(null, source.key) { value = source.thumbnail() }
        if (artwork == null) artwork = thumb
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!audioOnly) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.resizeMode = RESIZE[resize].first },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AudioArt(artwork, title ?: source.name)
        }

        // Hareketler: dokunma, çift dokunma ile sarma, kaydırma ile parlaklık/ses/konum.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(source.key) {
                    detectTapGestures(
                        onTap = { onToggleChrome() },
                        onDoubleTap = { at ->
                            val forward = at.x > size.width / 2
                            player.seekTo((player.currentPosition + if (forward) 10_000 else -10_000).coerceIn(0, maxOf(duration, 0)))
                            hint = (if (forward) Icons.Rounded.Forward10 else Icons.Rounded.Replay10) to resources.getString(if (forward) R.string.player_forward else R.string.player_back)
                        },
                    )
                }
                .pointerInput(source.key) {
                    var mode = 0 // 1 parlaklık, 2 ses, 3 konum
                    var startPosition = 0L
                    var acc = 0f
                    val audio = context.getSystemService(AudioManager::class.java)
                    detectDragGestures(
                        onDragStart = { at ->
                            mode = 0
                            acc = 0f
                            startPosition = player.currentPosition
                        },
                        onDragEnd = {
                            seeking?.let { player.seekTo(it) }
                            seeking = null
                        },
                        onDrag = { change, drag ->
                            if (mode == 0) {
                                mode = when {
                                    abs(drag.x) > abs(drag.y) -> 3
                                    change.position.x < size.width / 2 -> 1
                                    else -> 2
                                }
                            }
                            change.consume()
                            when (mode) {
                                1 -> activity?.window?.let { w ->
                                    val attrs = w.attributes
                                    val current = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                                    attrs.screenBrightness = (current - drag.y / size.height).coerceIn(0.02f, 1f)
                                    w.attributes = attrs
                                    hint = Icons.Rounded.BrightnessMedium to "%${(attrs.screenBrightness * 100).toInt()}"
                                }
                                2 -> if (audio != null) {
                                    acc -= drag.y
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val step = size.height / (max * 1.5f)
                                    if (abs(acc) >= step) {
                                        val delta = (acc / step).toInt()
                                        acc -= delta * step
                                        val volume = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + delta).coerceIn(0, max)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                        hint = Icons.AutoMirrored.Rounded.VolumeUp to "${volume * 100 / max.coerceAtLeast(1)}%"
                                    }
                                }
                                3 -> if (duration > 0) {
                                    acc += drag.x
                                    val target = (startPosition + (acc / size.width) * minOf(duration, 600_000L)).toLong().coerceIn(0, duration)
                                    seeking = target
                                }
                            }
                        },
                    )
                },
        )

        // Ortadaki bilgi (sarma, parlaklık, ses, sürükleyerek konum).
        val centerText = seeking?.let { time(it) + " / " + time(duration) } ?: hint?.second
        if (centerText != null) {
            Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp), modifier = Modifier.align(Alignment.Center)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    hint?.first?.takeIf { seeking == null }?.let { Icon(it, null, tint = Color.White, modifier = Modifier.padding(end = 8.dp)) }
                    Text(centerText, color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        error?.let {
            Text(it, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
        }

        AnimatedVisibility(chrome || audioOnly || !playing, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(time(seeking ?: position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = if (duration > 0) ((seeking ?: position).toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { seeking = (it * duration).toLong() },
                        onValueChangeFinished = { seeking?.let(player::seekTo); seeking = null },
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(time(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SpeedButton(speed) { speed = it; player.setPlaybackSpeed(it) }
                    IconButton(onClick = { loop = !loop; player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }) {
                        Icon(if (loop) Icons.Rounded.RepeatOn else Icons.Rounded.Repeat, stringResource(R.string.player_loop), tint = Color.White)
                    }
                    IconButton(onClick = { player.seekBack10() }) { Icon(Icons.Rounded.Replay10, stringResource(R.string.player_back), tint = Color.White) }
                    Surface(onClick = { if (player.isPlaying) player.pause() else player.play() }, shape = CircleShape, color = Color.White, modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                stringResource(if (playing) R.string.pip_pause else R.string.pip_play),
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    IconButton(onClick = { player.seekForward10() }) { Icon(Icons.Rounded.Forward10, stringResource(R.string.player_forward), tint = Color.White) }
                    TracksButton(player, tracks)
                    if (!audioOnly) {
                        var resizeMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { resizeMenu = true }) { Icon(Icons.Rounded.AspectRatio, stringResource(R.string.player_resize), tint = Color.White) }
                            DropdownMenu(resizeMenu, onDismissRequest = { resizeMenu = false }) {
                                RESIZE.forEachIndexed { i, (_, label) ->
                                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { resize = i; resizeMenu = false })
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_rotate)) },
                                    leadingIcon = { Icon(Icons.Rounded.ScreenRotation, null) },
                                    onClick = {
                                        resizeMenu = false
                                        activity?.requestedOrientation = if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Player.seekBack10() = seekTo((currentPosition - 10_000).coerceAtLeast(0))
private fun Player.seekForward10() = seekTo((currentPosition + 10_000).coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE))

@Composable
private fun AudioArt(artwork: Bitmap?, title: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp).padding(bottom = 160.dp, top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF2A2A2A), shadowElevation = 12.dp, modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f)) {
            if (artwork != null) {
                Image(artwork.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Audiotrack, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(96.dp)) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SpeedButton(speed: Float, onChange: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.15f)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(speedLabel(speed), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            SPEEDS.forEach { s -> DropdownMenuItem(text = { Text(speedLabel(s)) }, onClick = { onChange(s); open = false }) }
        }
    }
}

private fun speedLabel(speed: Float) = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')) + "×"

/** Altyazı ve ses parçası seçimi (yalnızca seçenek varsa görünür). */
@OptIn(UnstableApi::class)
@Composable
private fun TracksButton(player: ExoPlayer, tracks: Tracks) {
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val audioCount = audio.sumOf { it.length }
    if (text.isEmpty() && audioCount < 2) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.ClosedCaption, stringResource(R.string.player_tracks), tint = Color.White) }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            if (text.isNotEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(R.string.player_subtitles_off)) }, onClick = {
                    open = false
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                })
                text.forEach { group ->
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_subtitle_track, format.label ?: format.language ?: "${i + 1}")) },
                            onClick = {
                                open = false
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                            },
                        )
                    }
                }
            }
            if (audioCount > 1) {
                audio.forEach { group ->
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_audio_track, format.label ?: format.language ?: "${i + 1}")) },
                            onClick = {
                                open = false
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun time(ms: Long): String = DateUtils.formatElapsedTime((ms / 1000).coerceAtLeast(0))
