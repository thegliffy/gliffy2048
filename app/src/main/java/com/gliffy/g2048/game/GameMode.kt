package com.gliffy.g2048.game

/** Available board shapes. 4x4 is the canonical game; 3x3 (mini) and 6x6 (expert)
 *  are the popular community variants. */
enum class GameMode(val displayName: String, val size: Int) {
    MINI_3("Mini 3×3", 3),
    CLASSIC_4("2048 · 4×4", 4),
    EXPERT_6("Expert 6×6", 6);

    companion object {
        fun bySize(size: Int): GameMode = values().firstOrNull { it.size == size } ?: CLASSIC_4
    }
}
