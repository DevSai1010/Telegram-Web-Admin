package com.msma.tgpanel.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.msma.tgpanel.App
import com.msma.tgpanel.ConnState
import com.msma.tgpanel.R
import com.msma.tgpanel.core.avatarHue
import com.msma.tgpanel.core.hslToArgb

@Composable
fun appOf(): App = LocalContext.current.applicationContext as App

/** Round avatar: the contact's Telegram photo when there is one, otherwise a tonal colour + initial (same hue as the web panel). */
@Composable
fun Avatar(id: String, title: String, size: Dp, modifier: Modifier = Modifier) {
    val app = appOf()
    val ctx = LocalContext.current
    val bg = Color(hslToArgb(avatarHue(id), 0.42f, 0.38f))
    val initial = title.trim().take(1).uppercase().ifEmpty { "?" }
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(initial, color = Color.White.copy(alpha = 0.96f), fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp)
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(app.api.photoUrl(id))
                .addHeader("Authorization", "Bearer " + app.prefs.token).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Telegram-style connection strip: invisible while fully connected. */
@Composable
fun ConnectionBanner(state: ConnState, modifier: Modifier = Modifier) {
    val label = when (state) {
        ConnState.Connecting -> R.string.conn_connecting
        ConnState.WaitingNetwork -> R.string.conn_waiting
        ConnState.Updating -> R.string.conn_updating
        ConnState.Connected -> null
    }
    AnimatedVisibility(
        visible = label != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(label ?: R.string.conn_connecting),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "Today" / "Yesterday" / a date — localised by the system for fa / en / ru. */
@Composable
fun dayLabel(ts: Long): String {
    val ctx = LocalContext.current
    val today = stringResource(R.string.today)
    val yesterday = stringResource(R.string.yesterday)
    return when {
        DateUtils.isToday(ts) -> today
        DateUtils.isToday(ts + DateUtils.DAY_IN_MILLIS) -> yesterday
        else -> DateUtils.formatDateTime(ctx, ts, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
    }
}
