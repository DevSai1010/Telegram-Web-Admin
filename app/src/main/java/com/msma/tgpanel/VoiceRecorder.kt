package com.msma.tgpanel

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Records a voice note. OGG/Opus on Android 10+ (what Telegram wants, no conversion); AAC in MP4 before that (the server converts it). */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val recording: Boolean get() = rec != null
    fun elapsedMs(): Long = if (rec == null) 0 else SystemClock.elapsedRealtime() - startedAt

    fun start(): Boolean {
        if (rec != null) return true
        val ogg = Build.VERSION.SDK_INT >= 29
        val f = File(ctx.cacheDir, "voice_" + System.currentTimeMillis() + if (ogg) ".ogg" else ".m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (ogg) {
                r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                r.setAudioSamplingRate(48000)
                r.setAudioEncodingBitRate(32000)
            } else {
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioSamplingRate(44100)
                r.setAudioEncodingBitRate(64000)
            }
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            rec = r; file = f; startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            try { r.release() } catch (e2: Exception) { }
            f.delete()
            false
        }
    }

    /** Finishes and returns the file, or null when it was too short / failed. */
    fun stop(): File? {
        val r = rec ?: return null
        rec = null
        val f = file
        return try {
            r.stop(); r.release(); f
        } catch (e: RuntimeException) {           // stop() throws if nothing meaningful was recorded
            try { r.release() } catch (e2: Exception) { }
            f?.delete()
            null
        }
    }

    fun cancel() {
        val r = rec
        rec = null
        if (r != null) {
            try { r.stop() } catch (e: RuntimeException) { }
            try { r.release() } catch (e: Exception) { }
        }
        file?.delete()
    }
}
