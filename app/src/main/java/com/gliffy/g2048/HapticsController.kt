package com.gliffy.g2048

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.Build
import android.view.HapticFeedbackConstants

/**
 * Thin wrapper over the system vibrator with a built-in "enabled" flag so
 * callers can fire haptics with zero context lookup. [disabled] mode is a
 * no-op, reading `Settings.System` is never touched (caller owns the pref).
 */
class HapticsController(private val context: Context) {

    private val vibrator: android.os.Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }

    @Volatile var enabled: Boolean = true
        set(value) {
            field = value
            if (!value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                vibrator?.cancel()
            }
        }

    private fun buzz(pattern: LongArray, repeat: Int = -1) {
        if (!enabled || vibrator == null) return
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } catch (_: Exception) {
            // A restrictive device policy (work profile, accessibility
            // override, AOSP build without VIBRATE) may deny the call —
            // haptics are cosmetic; never take the game down over them.
        }
    }

    /** Swipe-move tick: one short pulse. */
    fun move() = buzz(longArrayOf(0, 8))

    /** Merge thock: two-pulse. */
    fun merge() = buzz(longArrayOf(0, 14, 16, 10))

    /** Win: cheerful triplet. */
    fun win() = buzz(longArrayOf(0, 40, 40, 40, 40, 60))

    /** Game-over: low double. */
    fun lose() = buzz(longArrayOf(0, 80, 60, 60))

    /** UI tap. */
    fun tap() = buzz(longArrayOf(0, 5))
}
