package com.gliffy.g2048

import android.content.Context

/**
 * SharedPreferences facade. Names are stable (must never change in a
 * release; old installs keep their data). Json-set for individual guaranteed
 * persistence on [commit]. Lifecycle/format: every value is one of:
 *  - Int (live/board-size/add-on)
 *  - String (board state, best scores keyed by mode: "classic-4", "daily-3", ...)
 *  - Boolean (quietness flags: soundEnabled, hapticsEnabled, dailyConfigured).
 */
class Prefs(context: Context) {
    private val prefs =
        context.getSharedPreferences("g2048", Context.MODE_PRIVATE)

    /** Live game (saved on every successful move + lifecycle pause). */
    var liveState: String?
        get() = prefs.getString(KEY_LIVE_STATE, null)
        set(v) = prefs.edit().putString(KEY_LIVE_STATE, v).apply()

    var liveSeed: String?
        get() = prefs.getString(KEY_LIVE_SEED, null)
        set(v) = prefs.edit().putString(KEY_LIVE_SEED, v).apply()

    /** Best scores keyed by "$mode-$size" (e.g. "classic-4", "daily-6"). */
    fun best(key: String): Int = prefs.getInt(key, 0)
    fun setBest(key: String, value: Int) {
        val cur = prefs.getInt(key, 0)
        if (value > cur) prefs.edit().putInt(key, value).apply()
    }

    var dailySeedKey: Long?
        get() = prefs.getString(KEY_DAILY_SEED, null)?.toLongOrNull()
        set(v) = prefs.edit().putString(KEY_DAILY_SEED, v?.toString()).apply()

    /** Most-recent Daily [com.gliffy.g2048.game.YearMonthDay] as a String yyyy-mm-dd. */
    var lastDailyDate: String?
        get() = prefs.getString(KEY_LAST_DAILY, null)
        set(v) = prefs.edit().putString(KEY_LAST_DAILY, v).apply()

    var hapticsOn: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit().putBoolean("haptics", v).apply()

    var soundOn: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit().putBoolean("sound", v).apply()

    /** Which size the user picked out of 3/4/5/6 (default 4 = canonical). */
    var startSize: Int
        get() = prefs.getInt("startSize", 4)
        set(v) = prefs.edit().putInt("startSize", v).apply()

    var daily: Boolean
        get() = prefs.getBoolean("daily", false)
        set(v) = prefs.edit().putBoolean("daily", v).apply()

    /** Theme: 0=follow-system, 1=light, 2=dark. */
    var themeMode: Int
        get() = prefs.getInt("theme", 0)
        set(v) = prefs.edit().putInt("theme", v).apply()

    /** Count of [com.gliffy.g2048.game.Game.State.serial] round-trips. */
    var movesMadeTotal: Int
        get() = prefs.getInt(KEY_TOTAL_MOVES, 0)
        set(v) = prefs.edit().putInt(KEY_TOTAL_MOVES, v).apply()

    companion object {
        const val KEY_LIVE_STATE = "live"
        const val KEY_DAILY_SEED = "dSeed"
        const val KEY_LAST_DAILY = "dDate"
        const val KEY_TOTAL_MOVES = "tMoves"
        const val KEY_LIVE_SEED = "liveSeed"
    }
}
