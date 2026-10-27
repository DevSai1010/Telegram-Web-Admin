package com.msma.tgpanel.data

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.msma.tgpanel.App
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Voice-message playback (one at a time, shared by every bubble) and a factory for the full-screen video player.
 * Requests go through the app's OkHttp client with the session token as a header — the token never appears in a URL.
 */
class MediaHolder(private val app: App) {
    val currentFileId = MutableStateFlow<String?>(null)
    val isPlaying = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)

    private var player: ExoPlayer? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying.value = playing }
        override fun onPlaybackStateChanged(state: Int) {
            val p = player ?: return
            if (state == Player.STATE_READY) durationMs.value = p.duration.coerceAtLeast(0L)
            if (state == Player.STATE_ENDED) { p.seekTo(0); p.pause(); positionMs.value = 0L; isPlaying.value = false }
        }
        override fun onPlayerError(error: PlaybackException) { isPlaying.value = false; currentFileId.value = null }
    }

    /** A new player that authenticates its requests (used by the video dialog; the caller releases it). */
    fun newPlayer(): ExoPlayer {
        val http = OkHttpDataSource.Factory(app.api.http)
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer " + app.prefs.token))
        return ExoPlayer.Builder(app).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }

    private fun ensure(): ExoPlayer {
        player?.let { return it }
        val p = newPlayer()
        p.addListener(listener)
        player = p
        ticker = app.appScope.launch {
            while (true) {
                delay(250)
                val cur = player ?: continue
                if (cur.isPlaying) positionMs.value = cur.currentPosition
            }
        }
        return p
    }

    /** Play / pause a voice message; starting another one replaces the current. */
    fun toggle(fileId: String) {
        val p = ensure()
        if (currentFileId.value == fileId) {
            if (p.isPlaying) p.pause() else p.play()
            return
        }
        p.setMediaItem(MediaItem.fromUri(app.api.mediaUrl(fileId)))
        currentFileId.value = fileId
        positionMs.value = 0L
        durationMs.value = 0L
        p.prepare()
        p.play()
    }

    fun seekToFraction(f: Float) {
        val p = player ?: return
        val d = durationMs.value
        if (d > 0) { p.seekTo((d * f.coerceIn(0f, 1f)).toLong()); positionMs.value = p.currentPosition }
    }

    fun pause() { player?.pause() }

    /** Called on sign-out: the next player must pick up the next session's token. */
    fun release() {
        ticker?.cancel(); ticker = null
        player?.release(); player = null
        currentFileId.value = null; isPlaying.value = false; positionMs.value = 0L; durationMs.value = 0L
    }
}
