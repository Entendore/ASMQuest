package com.example.assemblygame.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.example.assemblygame.game.Beep

/**
 * Synthesizes short sine-wave beeps and plays them through AudioTrack.
 * (ToneGenerator only offers preset tones, so arbitrary pitches are generated as PCM —
 * fully offline, no permissions.) Each beep gets a short-lived track; overlaps are fine.
 */
object BeepPlayer {

    private const val SAMPLE_RATE = 22050
    private const val MAX_AUTOPLAY_MS = 8000L
    private val handler = Handler(Looper.getMainLooper())

    /** Plays one beep immediately. */
    fun play(pitchHz: Int, durationMs: Int) {
        val freq = pitchHz.coerceIn(100, 2000)
        val ms = durationMs.coerceIn(50, 2000)
        val sampleCount = SAMPLE_RATE * ms / 1000
        val pcm = ShortArray(sampleCount)
        val env = minOf(SAMPLE_RATE * 8 / 1000, sampleCount / 4).coerceAtLeast(1)
        for (i in 0 until sampleCount) {
            var amp = 0.6
            if (i < env) amp *= i.toDouble() / env                      // attack (no click)
            val toEnd = sampleCount - 1 - i
            if (toEnd < env) amp *= toEnd.toDouble() / env              // release (no click)
            val angle = 2.0 * Math.PI * freq * i / SAMPLE_RATE
            pcm[i] = (Math.sin(angle) * amp * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        handler.postDelayed({
            try { track.stop() } catch (_: Exception) {}
            try { track.release() } catch (_: Exception) {}
        }, ms + 150L)
    }

    /** Schedules a melody note-after-note. Returns the total scheduled length in ms (capped). */
    fun playSequence(beeps: List<Beep>): Long {
        var at = 0L
        for (b in beeps) {
            if (at > MAX_AUTOPLAY_MS) break
            handler.postDelayed({ play(b.pitchHz, b.durationMs) }, at)
            at += b.durationMs + 30L
        }
        return at
    }
}
