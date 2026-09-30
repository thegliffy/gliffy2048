package com.gliffy.g2048.ui

import com.gliffy.g2048.AudioController
import com.gliffy.g2048.HapticsController
import com.gliffy.g2048.Prefs
import com.gliffy.g2048.game.Dir
import com.gliffy.g2048.game.Game
import com.gliffy.g2048.game.GameMode
import com.gliffy.g2048.game.Rng
import com.gliffy.g2048.game.YearMonthDay
import com.gliffy.g2048.game.dailySeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random

/** FX for a fresh board: tiles pop in with a stagger. */
data class SpawnEvent(val seq: Long)

/**
 * Snapshot handed to Compose. `state` is the live (mutable) engine state;
 * [revision] changes on every mutation so the board always re-evaluates.
 */
data class UiSnap(
    val state: Game.State,
    val key: String,
    val modeName: String,
    val moveEv: MoveEvent?,
    val spawnEv: SpawnEvent?,
    val revision: Long,
    val canUndo: Boolean,
    /** True until the board animation of the last revision settles, then the
     *  win/lose overlay may appear (so the final move visibly lands first). */
    val animSettled: Boolean,
    val themeMode: Int,
    val soundOn: Boolean,
    val hapticsOn: Boolean,
)

/**
 * Owns [Game.State] plus persistence and the FX buses (haptics + synth audio).
 * All access from the main thread: Compose collects [ui] via
 * collectAsStateWithLifecycle and the activity saves on pause.
 */
class GameMachine(
    private val prefs: Prefs,
    private val haptics: HapticsController,
    private val audio: AudioController,
) {
    init {
        // Let MainActivity flush live state on onStop().
        com.gliffy.g2048.Game2048App.instance.liveMachine = this
    }
    private val UNDO_CAP = 40

    private val undo = ArrayDeque<Game.State>()
    private var seq = 0L
    private var rev = 0L

    private var live: Game.State = Game.State(4, Rng(0L))
    private var moveEv: MoveEvent? = null
    private var spawnSeq = 0L
    private var settled = true

    private val _ui = MutableStateFlow<UiSnap>(UiSnap(
        state = Game.State(4, Rng(0L)),
        key = "classic-4",
        modeName = "2048",
        moveEv = null, spawnEv = null, revision = 0L,
        canUndo = false, animSettled = true, themeMode = 0,
        soundOn = true, hapticsOn = true,
    ))
    val ui: StateFlow<UiSnap> = _ui

    init { preset() }

    // ---------- public API ----------

    fun tryMove(dir: Dir) {
        if (live.isOver()) return
        val m = Game.move(live, dir) ?: return
        // Undo snapshot: pre-move state INCLUDING the pre-move RNG stream,
        // so restoring reproduces the exact spawn that would have followed.
        undo.addLast(m.before)
        if (undo.size > UNDO_CAP) undo.removeFirst()

        if (m.scoreGained > 0) {
            audio.merge(m.scoreGained)
            haptics.merge()
        } else {
            audio.move()
            haptics.move()
        }

        moveEv = MoveEvent(
            seq = ++seq,
            dir = dir,
            // (oldX, oldY) tile units for every tile that changed cell.
            from = m.slid.associate { it.id to (it.oldCol.toFloat() to it.oldRow.toFloat()) },
            vanishing = m.mergedInto.keys,
            mergedInto = m.mergedInto,
            added = m.added,
            gained = m.scoreGained,
            won = m.wonNow,
            over = m.overNow,
        )

        if (m.wonNow) {
            audio.win(); haptics.win()
        }
        if (m.overNow) {
            audio.lose(); haptics.lose()
        }

        prefs.setBest(modeKey(), live.score)
        persist()
        bump()
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        live = prev
        moveEv = null
        settled = false
        persist(); bump()
    }
    fun uiTap() { haptics.tap() }

    /** Explicitly choose the mode for the next board. */
    fun newGame(asDaily: Boolean) {
        startFresh(asDaily)
    }

    /** Pick the board size for the next fresh game; immediately fresh board.
     *  Returns false if a game is in progress and the caller must confirm
     *  first (changing size restarts the game). */
    fun chooseSize(size: Int, force: Boolean = false): Boolean {
        if (size == live.size) return true
        if (!force && live.movesMade > 0 && !live.isOver()) return false
        prefs.startSize = size.coerceIn(3, 6)
        startFresh(prefs.daily)
        return true
    }

    fun setSound(on: Boolean) { prefs.soundOn = on; audio.setEnabled(on) }
    fun setHaptics(on: Boolean) { prefs.hapticsOn = on; haptics.enabled = on }
    fun setThemeMode(m: Int) { prefs.themeMode = m; bump() }

    /** "Won, keep going" — latch and keep playing. */
    fun continuePlaying() {
        live.keepGoing = true
        settled = false
        bump()
    }

    /** True while a Daily game saved on an earlier UTC day is not resumable. */
    fun dailyDateLabel(): String {
        val d = LocalDateTime.now(ZoneOffset.UTC)
        val dates = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return "%s %d".format(dates[d.monthValue - 1], d.dayOfMonth)
    }

    // ---------- internals ----------

    private fun modeKey(): String {
        val m = GameMode.bySize(live.size)
        return if (live.daily) "daily-${m.size}" else "classic-${m.size}"
    }

    private fun preset() {
        haptics.enabled = prefs.hapticsOn
        audio.setEnabled(prefs.soundOn)
        val saved = prefs.liveState?.let { Game.State.deserialize(it) }
        if (saved == null) {
            startFresh(prefs.daily)
            return
        }
        // A daily board only resumes if it was created today (UTC).
        if (saved.daily && prefs.lastDailyDate != todayUtc()) {
            startFresh(true)
            return
        }
        live = saved
        settled = false // pop-in entrance on cold start of a resumed board
        // Stale chained-undo stack after death: discard history.
        undo.clear()
        persist()
        bump()
    }

    private fun startFresh(daily: Boolean) {
        val size = prefs.startSize
        prefs.daily = daily
        val dt = LocalDateTime.now(ZoneOffset.UTC)
        val dateStr = "%04d-%02d-%02d".format(dt.year, dt.monthValue, dt.dayOfMonth)
        val seed = if (daily) dailySeed(YearMonthDay(dt.year, dt.monthValue, dt.dayOfMonth))
        else Random.nextLong()
        live = Game.State.initial(size, seed, daily)
        prefs.lastDailyDate = if (daily) dateStr else null
        prefs.dailySeedKey = if (daily) seed else 0L
        undo.clear()
        moveEv = null
        spawnSeq++
        settled = false
        persist()
        bump()
    }

    fun onPauseSave() { persist() }
    private fun persist() {
        prefs.liveState = live.serialize()
    }

    private fun bump() {
        _ui.value = snap()
    }

    private fun snap(): UiSnap = UiSnap(
        state = live,
        key = modeKey(),
        modeName = if (live.daily) "Daily — ${dailyDateLabel()}"
                   else GameMode.bySize(live.size).displayName,
        moveEv = moveEv,
        spawnEv = if (settled) null else SpawnEvent(spawnSeq),
        revision = ++rev,
        canUndo = undo.isNotEmpty(),
        animSettled = settled,
        themeMode = prefs.themeMode,
        soundOn = prefs.soundOn,
        hapticsOn = prefs.hapticsOn,
    )

    fun onAnimSettled() {
        if (settled) return
        settled = true
        bump()
    }

    private fun todayUtc(): String {
        val d = LocalDateTime.now(ZoneOffset.UTC)
        return "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)
    }
}
