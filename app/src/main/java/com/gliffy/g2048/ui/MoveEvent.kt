package com.gliffy.g2048.ui

import com.gliffy.g2048.game.Dir
import com.gliffy.g2048.game.Tile

/**
 * Emitted by [GameStateMachine] after every successful move (including
 * first-frame events) so the Compose layer can play fx. All positions are
 * in TILE units (post-screen-space conversion happens in the canvas).
 */
data class MoveEvent(
    val seq: Long,
    val dir: Dir,
    /** per tile id: (oldX, oldY) of the pre-move position */
    val from: Map<Int, Pair<Float, Float>>,
    /** which tiles were absorbed (vanishing) */
    val vanishing: Set<Int>,
    /** id -> id the vanisher slides INTO (also its target) */
    val mergedInto: Map<Int, Int>,
    val added: Tile?,
    val gained: Int,
    val won: Boolean,
    val over: Boolean,
)
