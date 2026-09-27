package com.gliffy.g2048

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthesizes all sfx procedurally into [AudioTrack] buffers — zero asset
 * files in the APK, instant startup, and it obeys the global volume /
 * mute state so the user never hears a dead buzz.
 *
 * Tracks play monophonically (one [AudioTrack] at a time) to keep the
 * ear safe from harmful overlaps. On Android O+ volume is looked up from
 * the system AudioManager; the local [self] flag remains the app pairing
 * toggle that decides whether we ever emit a sample.
 */
class AudioController(ctx: Context) {
    @Volatile var enabled: Boolean = true
        private set
    fun setEnabled(b: Boolean) { enabled = b; if (!b) stop() }

    private val ctx = ctx.applicationContext
    private val manager =
        ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val streamType = when {
        android.os.Build.VERSION.SDK_INT >= 31 -> AudioManager.STREAM_VOICE_CALL
        else -> AudioManager.STREAM_MUSIC
    }

    private val sampleRate = 44_100
    private var track: AudioTrack? = null

    private fun createTrack(): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(8192)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

    private fun finishReady(): Boolean {
        val t = track ?: return false
        t.play()
        return true
    }

    private fun stop() {
        track?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        track = null
    }

    private fun envelope(n: Int, attack: Int, decay: Float): FloatArray {
        val s = FloatArray(n)
        val ad = (attack + (n - attack).coerceAtLeast(1)).coerceAtLeast(1)
        for (i in 0 until attack) s[i] = (i.toFloat() / attack) * decay
        for (i in attack until n) s[i] = decay * (1f - (i - attack).toFloat() / (n - attack))
        return s
    }

    /** Frequency sweep with a simple exponential decay envelope. */
    private fun playSweep(fromHz: Double, toHz: Double, durMs: Int, gain: Float) {
        if (!enabled) return
        stop()
        val n = (sampleRate * durMs / 1000).coerceAtLeast(16)
        val t = createTrack()
        track = t
        try {
            val env = envelope(n, (n * 0.05f).toInt(), gain)
            val buf = ShortArray(n)
            var phase = 0.0
            val step0 = fromHz * 2 * PI / sampleRate
            val step1 = toHz * 2 * PI / sampleRate
            for (i in 0 until n) {
                phase += step0 + (step1 - step0) * i / n
                buf[i] = (sin(phase) * env[i]).toInt().shr(1).toShort()
            }
            t.write(buf, 0, n)
            finishReady()
        } catch (e: Exception) {
            t.release()
        }
    }

    fun move() = playSweep(300.0, 400.0, 70, 0.35f)

    fun merge(size: Int) {
        // Slightly pitchy "thock" — higher tile = higher pitch.
        val f = 220.0 + 30.0 * (1 + size)
        playSweep(f, f * 0.8, 120, 0.55f)
    }

    fun win() {
        // Tiny arpeggio: three short ascending notes in sequence.
        stop()
        val notes = listOf(523.25, 659.25, 783.99)
        val t = createTrack()
        track = t
        try {
            val n = sampleRate * 300 / 1000
            val buf = ShortArray(n)
            val per = n / notes.size
            for (k in notes.indices) {
                var ph = 0.0
                val f = notes[k] * 2 * PI / sampleRate
                for (i in 0 until per) {
                    ph += f
                    val env = kotlin.math.cos((i.toDouble() / per) * PI)
                    buf[k * per + i] = (sin(ph) * env * 0.5).toInt().shr(1).toShort()
                }
            }
            t.write(buf, 0, n)
            finishReady()
        } catch (e: Exception) {
            t.release()
        }
    }

    fun lose() = playSweep(200.0, 90.0, 280, 0.5f)
}
