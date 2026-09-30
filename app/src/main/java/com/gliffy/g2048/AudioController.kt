package com.gliffy.g2048

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
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
 *
 * Every blocking audio operation (track creation, PCM write, play) is
 * dispatched to a single dedicated [worker] thread so the UI thread is
 * never stalled by an audio-stream open — the usual cause of first-frame
 * jank and ANR-style slowdowns. Playback is serialized on that thread so
 * we keep at most one live track and a clean stop() is always race-free.
 */
class AudioController(ctx: Context) {
    @Volatile var enabled: Boolean = true
        private set
    fun setEnabled(b: Boolean) { enabled = b; if (!b) stop() }

    private val ctx = ctx.applicationContext
    private val manager =
        ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // Game sfx always ride STREAM_MUSIC: STREAM_VOICE_CALL is tied to call
    // volume (often near-silent outside a call) and conflicts with in-call
    // audio. The AudioAttributes usage below is what modern Android routes
    // by; the legacy streamType only matters on pre-O devices.
    private val streamType = AudioManager.STREAM_MUSIC

    private val sampleRate = 44_100

    // All access to [track] is guarded by the monitor (this). The public
    // [stop] and the serialized worker both go through it, so there is no
    // window where a dead track is released twice or a stale one is kept.
    private var track: AudioTrack? = null
        private set

    // Single background worker; daemon so it never blocks process exit.
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "G2048-audio").apply { isDaemon = true }
    }

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

    /** Caller must hold the monitor ([this]). */
    private fun releaseCurrentLocked() {
        track?.let {
            try { it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        track = null
    }

    /** Stop and release any live track. Safe to call from any thread. */
    @Synchronized
    fun stop() = releaseCurrentLocked()

    private fun envelope(n: Int, attack: Int, decay: Float): FloatArray {
        val s = FloatArray(n)
        val ad = (attack + (n - attack).coerceAtLeast(1)).coerceAtLeast(1)
        for (i in 0 until attack) s[i] = (i.toFloat() / attack) * decay
        for (i in attack until n) s[i] = decay * (1f - (i - attack).toFloat() / (n - attack))
        return s
    }

    /**
     * Run blocking audio work off-thread. [work] must return the freshly
     * created track (or null to cancel); the monitor swap that retires the
     * previous track, installs the new one and starts it happens inside the
     * single serialized block so start ordering is always well-defined.
     */
    private fun startTrack(work: () -> AudioTrack?) {
        if (!enabled) return
        worker.execute {
            val t = try { work() } catch (e: Exception) { null } ?: return@execute
            synchronized(this) {
                if (!enabled) { t.release(); return@synchronized }
                releaseCurrentLocked()
                track = t
                try { t.play() } catch (e: Exception) { t.release(); track = null }
            }
        }
    }

    /** Frequency sweep with a simple exponential decay envelope. */
    private fun playSweep(fromHz: Double, toHz: Double, durMs: Int, gain: Float) {
        if (!enabled) return
        val n = (sampleRate * durMs / 1000).coerceAtLeast(16)
        val env = envelope(n, (n * 0.05f).toInt(), gain)
        val buf = ShortArray(n)
        var phase = 0.0
        val step0 = fromHz * 2 * PI / sampleRate
        val step1 = toHz * 2 * PI / sampleRate
        for (i in 0 until n) {
            phase += step0 + (step1 - step0) * i / n
            // -> short value
            buf[i] = Math.round(sin(phase) * env[i] * Short.MAX_VALUE).toShort()
        }
        startTrack {
            val t = createTrack()
            t.write(buf, 0, n)   // blocking: this is why it must be off-thread
            t
        }
    }

    fun move() = playSweep(300.0, 400.0, 70, 0.35f)

    fun merge(size: Int) {
        // Slightly pitchy "thock" — higher tile = higher pitch.
        val f = 220.0 + 30.0 * (1 + size)
        playSweep(f, f * 0.8, 120, 0.55f)
    }

    fun win() {
        // Tiny arpeggio: three short ascending notes in one buffer.
        startTrack {
            val t = createTrack()
            val n = sampleRate * 300 / 1000
            val buf = ShortArray(n)
            val notes = listOf(523.25, 659.25, 783.99)
            val per = n / notes.size
            for (k in notes.indices) {
                var ph = 0.0
                val f = notes[k] * 2 * PI / sampleRate
                for (i in 0 until per) {
                    ph += f
                    val env = kotlin.math.cos((i.toDouble() / per) * PI)
                    buf[k * per + i] =
                        Math.round(sin(ph) * env * 0.5 * Short.MAX_VALUE).toShort()
                }
            }
            t.write(buf, 0, n)
            t
        }
    }

    fun lose() = playSweep(200.0, 90.0, 280, 0.5f)
}
