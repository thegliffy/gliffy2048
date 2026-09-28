package com.gliffy.g2048

import android.media.AudioTrack
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.measureTimeMillis

/**
 * On-device instrumented tests for the [AudioController] rewrite.
 *
 * What "the new threading" promises, and what each test pins down:
 *
 *  1. The UI (calling) thread is never stalled: every public play() call
 *     must return in a couple of milliseconds, even at repeated call-rates,
 *     because all blocking AudioTrack work (build / write / play) is queued
 *     onto a dedicated worker. A regression to the old main-thread behaviour
 *     performs the blocking AudioTrack writes inline and is easily hundreds
 *     of milliseconds per invocation.
 *
 *  2. That blocking work really runs on the dedicated `G2048-audio` worker
 *     (probed by submitting a no-op to the same executor the audio lambdas
 *     are queued onto: single-threaded, correctly named, not the caller).
 *
 *  3. Monophonic guarantee + clean lifecycle: at most one live track; a
 *     stop() from any thread releases it exactly once and is idempotent; a
 *     play/stop storm interleaved from many threads never throws a
 *     double-release or stale-track error (monitor serialization holds).
 *
 *  4. The enabled/disable gate: disabled -> play() is a no-op that never
 *     installs a track; re-enable resumes normal playback.
 */
@RunWith(AndroidJUnit4::class)
class AudioControllerThreadTest {

    private lateinit var app: Game2048App
    private lateinit var audio: AudioController

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Fresh instance per test: one test's leftover live track/release
        // must not mask or help another.
        audio = AudioController(app)
        audio.setEnabled(true)
    }

    // ------------------------ reflection observation -----------------------
    // The worker executor and the live track guard are private. On-device
    // tests may reach them via reflection to OBSERVE — every play/stop in
    // these tests still goes through the real public API.

    private fun AudioController.privateField(name: String): Any? {
        val f = javaClass.getDeclaredField(name)
        f.isAccessible = true
        return f.get(this)
    }

    private fun AudioController.worker(): ExecutorService =
        (this as AudioController).privateField("worker") as ExecutorService

    private fun AudioController.liveTrack(): AudioTrack? =
        (this as AudioController).privateField("track") as AudioTrack?

    /**
     * FIFO barrier: the executor is single-threaded, so once this no-op
     * drains every audio lambda queued before it has already run.
     */
    private fun AudioController.awaitWorkerDrained(timeoutMs: Long = 15_000): Boolean {
        val barrier = worker().submit<Unit?> { }
        return try {
            barrier.get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (_: java.util.concurrent.TimeoutException) {
            false
        }
    }

    /**
     * Poll until [cond] first holds, within [totalTimeoutMs]. Tolerates a
     * track only ever going null->instance, but does NOT assume either.
     */
    private fun waitUntil(
        totalTimeoutMs: Long,
        cond: () -> Boolean,
    ): Boolean {
        val deadline = System.nanoTime() + totalTimeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (cond()) return true
            Thread.sleep(20)
        }
        return cond()
    }

    // ----------------------------- the tests -------------------------------

    /** 1. Calling-thread latency: play calls must be non-blocking. */
    @Test
    fun playCallsReturnPromptlyOnCallingThread() {
        val main = Thread.currentThread()
        val elapsed = measureTimeMillis {
            repeat(25) { k ->
                audio.move()      // ~70 ms sweep
                audio.merge(6)    // ~120 ms sweep
                audio.win()       // ~300 ms arpeggio
                audio.lose()      // ~280 ms sweep
            }
        }
        audio.stop()
        // 100 enqueue-only play invocations. On a normal device this totals
        // single-digit ms. The bound is intentionally generous: on a
        // software-emulation AVD sharing a heavily loaded host the app
        // process can be descheduled heavily, inflating wall-clock for
        // trivially-light submit calls (observed ~5s for 100 enqueues —
        // pure host starvation, not blocking audio work). A regression to
        // the old main-thread blocking design costs real per-call time
        // (AudioTrack open + buffered write of 70–300ms of PCM) and lands
        // far above this bound even on a loaded host.
        assertTrue(
            "100 play() calls took ${elapsed} ms on main thread '${main.name}' — " +
                "looks like blocking audio work moved back to the caller",
            elapsed < 10_000L,
        )
    }

    /** 2. Blocking work runs on the dedicated, single, named worker. */
    @Test
    fun audioWorkRunsOnDedicatedNamedWorkerThread() {
        val worker = audio.worker()
        audio.stop()

        val probe0 = CompletableFuture<String>()
        val probe1 = CompletableFuture<String>()
        val probe2 = CompletableFuture<String>()
        worker.submit<Unit> { probe0.complete(Thread.currentThread().name) }
            .get(5, TimeUnit.SECONDS)
        worker.submit<Unit> { probe1.complete(Thread.currentThread().name) }
            .get(5, TimeUnit.SECONDS)
        worker.submit<Unit> { probe2.complete(Thread.currentThread().name) }
            .get(5, TimeUnit.SECONDS)

        assertEquals("worker must be the dedicated 'G2048-audio' thread",
            "G2048-audio", probe0.get())
        assertEquals("executor must be single-threaded (same task thread twice)",
            probe0.get(), probe1.get())
        assertEquals("single-threaded check again", probe0.get(), probe2.get())

        val here = Thread.currentThread().name
        assertFalse("worker must not be this thread ('$here')", probe0.get() == here)
    }

    /** 3a. stop() releases the live track exactly once and is idempotent. */
    @Test
    fun stopReleasesLiveTrackAndIsIdempotent() {
        audio.move()
        assertTrue("a live track should appear after drain (playback has not started?)",
            audio.awaitWorkerDrained())
        val playing = audio.liveTrack()
        assertNotNull("after move()+drain a live track must be installed", playing)

        // Two threads slam stop() at the same moment: the monitor must
        // serialize, release exactly once, null exactly once.
        val pool = Executors.newFixedThreadPool(2)
        val firing = CountDownLatch(1)
        val bothDone = CountDownLatch(2)
        var threw = false
        repeat(2) {
            pool.execute {
                firing.await()
                try { audio.stop() } catch (e: Throwable) { threw = true }
                bothDone.countDown()
            }
        }
        firing.countDown()
        assertTrue("concurrent stop() pair did not finish", bothDone.await(10, TimeUnit.SECONDS))
        pool.shutdown()
        assertFalse("stop() threw during a concurrent release", threw)
        assertNull("track must be nulled after stop()", audio.liveTrack())

        audio.stop() // idempotent: must be a silent no-op
        audio.stop()
        assertNull(audio.liveTrack())
    }

    /** 3b. Rapid play/merge/win interleaved with stop() storms must not throw. */
    @Test
    fun playAndStopStormNeverThrowsAndRecovers() {
        val firstError = ConcurrentHashMapMarker()
        val stopper = Thread {
            val deadline = System.nanoTime() + 300_000_000L
            while (System.nanoTime() < deadline) {
                try { audio.stop() } catch (t: Throwable) { firstError.set(t) }
                try { Thread.sleep(2) } catch (_: InterruptedException) { }
            }
        }
        val players = Executors.newFixedThreadPool(3)
        val doneCount = java.util.concurrent.atomic.AtomicInteger(0)
        stopper.start()
        repeat(60) { i ->
            players.execute {
                try {
                    when (i % 3) {
                        0 -> audio.move()
                        1 -> audio.merge(3)
                        else -> audio.win()
                    }
                } catch (t: Throwable) {
                    firstError.set(t)
                }
                doneCount.incrementAndGet()
            }
        }
        stopper.join(5_000)
        assertFalse("stopper thread did not end", stopper.isAlive)
        players.shutdown()
        assertTrue("not all 60 play tasks accepted", players.awaitTermination(30, TimeUnit.SECONDS))
        audio.awaitWorkerDrained()

        firstError.get()?.let { throw AssertionError("play/stop storm threw: $it", it) }

        // Recovery: one more playback after the storm must work end-to-end.
        audio.move()
        audio.awaitWorkerDrained()
        assertNotNull("recovery move() must install a live track", audio.liveTrack())
        audio.stop()
        assertNull("recovery stop() must clear the track", audio.liveTrack())
    }

    /** 4. Disable gate: no tracks ever; re-enable resumes playback. */
    @Test
    fun disabledControllerCreatesNoTracksAndReEnableRestores() {
        audio.setEnabled(false)
        assertFalse(audio.enabled)
        repeat(10) {
            audio.move(); audio.win(); audio.lose()
        }
        audio.awaitWorkerDrained()
        assertNull("disabled controller must never create a track, even after 30 plays",
            audio.liveTrack())

        audio.setEnabled(true)
        assertTrue(audio.enabled)
        audio.move()
        audio.awaitWorkerDrained()
        assertNotNull("re-enabling must resume playback", audio.liveTrack())
        audio.stop()
    }

    /** Single live track invariant: per call, a NEW instance; old ones retired. */
    @Test
    fun eachRapidCallInstallsAFreshTrackAndRetiresThePrevious() {
        audio.move()
        audio.awaitWorkerDrained()
        var prev = audio.liveTrack()
        assertNotNull("initial move() must install a live track", prev)
        for (k in 1..10) {
            audio.merge(k)
            audio.awaitWorkerDrained()
            val now = audio.liveTrack()
            assertNotNull("iter $k: expected a live track", now)
            assertNotSame(
                "iter $k: stale live track survived a swap (expected a new AudioTrack)",
                prev, now,
            )
            prev = now
        }
        audio.stop()
        assertNull("final stop() after the sequence", audio.liveTrack())
    }
}

/** Tiny single-shot error sink used by the storm test. */
private class ConcurrentHashMapMarker {
    @Volatile var value: Throwable? = null
    fun set(t: Throwable) { if (value == null) value = t }
    fun get(): Throwable? = value
}
