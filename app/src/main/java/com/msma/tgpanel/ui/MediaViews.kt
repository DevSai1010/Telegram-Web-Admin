package com.msma.tgpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.msma.tgpanel.R

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')
}

/** Voice message: play / pause + seek bar. Only one plays at a time (shared [com.msma.tgpanel.data.MediaHolder]). */
@Composable
fun VoiceBar(fileId: String) {
    val media = appOf().media
    val current by media.currentFileId.collectAsStateWithLifecycle()
    val playing by media.isPlaying.collectAsStateWithLifecycle()
    val pos by media.positionMs.collectAsStateWithLifecycle()
    val dur by media.durationMs.collectAsStateWithLifecycle()
    val active = current == fileId
    val frac = if (active && dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f

    Row(Modifier.widthIn(min = 230.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = { media.toggle(fileId) }, modifier = Modifier.size(40.dp)) {
            Icon(
                if (active && playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (active && playing) R.string.pause else R.string.play),
            )
        }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Slider(
                value = frac,
                onValueChange = { if (active) media.seekToFraction(it) },
                enabled = active && dur > 0,
            )
            Text(
                if (active) clock(pos) + (if (dur > 0) " / " + clock(dur) else "") else stringResource(R.string.media_voice),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Video / GIF: a tappable tile that opens the full-screen player. GIFs are muted MP4s on Telegram, so they loop without a controller. */
@Composable
fun VideoTile(fileId: String, label: String, loop: Boolean) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.18f))
            .clickable { open = true }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayCircle, stringResource(R.string.play), Modifier.size(34.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 10.dp))
    }
    if (open) VideoDialog(fileId, loop) { open = false }
}

@Composable
fun VideoDialog(fileId: String, loop: Boolean, onDismiss: () -> Unit) {
    val app = appOf()
    val exo = remember {
        app.media.newPlayer().apply {
            setMediaItem(MediaItem.fromUri(app.api.mediaUrl(fileId)))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            if (loop) volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        app.media.pause()                               // a playing voice message makes way for the video
        onDispose { exo.release() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = exo; useController = !loop } },
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White)
            }
        }
    }
}
