package com.gliffy.g2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Engine correctness tests. The RNG (SplitMix64) is deterministic across
 * platforms, so seeding makes these full replay checks of the move/merge
 * rules independent of spawn luck.
 */
class GameEngineTest {

    private fun fresh(size: Int, seed: Long) = Game.State.initial(size, seed)

    private fun place(st: Game.State, id: Int, r: Int, c: Int, v: Int) {
        st.tiles.removeIf { it.id == id }
        val t = Tile(id, r, c, v)
        st.tiles += t
        st.reindex()
    }

    private fun board(st: Game.State): String =
        (0 until st.size).joinToString("\n") { r ->
            (0 until st.size).joinToString(" ") { c ->
                st.cellAt(r, c)?.value.toString().padStart(3) ?: " · "
            }
        }

    @Test
    fun initialBoardHasTwoTiles() {
        val st = fresh(4, 1L)
        assertEquals(2, st.tiles.size)
        assertTrue(st.tiles.all { it.value == 2 || it.value == 4 })
    }

    @Test
    fun basicMergeSemantics() {
        val st = fresh(4, 1L)
        //  2 2 · 4
        //  · · · ·
        //  · 2 · ·
        //  · · · 4
        // UP: col0 unchanged; col1: 2+2=4 at (0,1); col3: 4+4=8 at (0,3).
        st.tiles.clear(); st.reindex()
        place(st, 1, 0, 0, 2); place(st, 2, 0, 1, 2)
        place(st, 3, 0, 3, 4); place(st, 4, 2, 1, 2); place(st, 5, 3, 3, 4)

        val m = Game.move(st, Dir.UP)
        assertNotNull(m)
        System.err.println("BASIC_BOARD:\n" + board(st) + " score=" + st.score + " tiles=" + st.tiles.size)
        assertEquals(2, st.cellAt(0, 0)?.value)
        assertEquals(4, st.cellAt(0, 1)?.value)
        assertEquals(8, st.cellAt(0, 3)?.value)
        assertEquals(12, st.score)
        assertEquals(1, st.movesMade)
        assertEquals(4, st.tiles.size) // 3 survivors + 1 spawn
    }

    @Test
    fun chainDoesNotReMergeSameTurn() {
        val st = fresh(4, 8L)
        // col0: 2,2,4 top to bottom -> UP should give 4,4 (not 16).
        st.tiles.clear(); st.reindex()
        place(st, 1, 0, 0, 2); place(st, 2, 1, 0, 2); place(st, 3, 2, 0, 4)

        val m = Game.move(st, Dir.UP)
        assertNotNull(m)
        System.err.println("CHAIN_BOARD:\n" + board(st) + " score=" + st.score)
        assertEquals(4, st.cellAt(0, 0)?.value)
        assertEquals(4, st.cellAt(1, 0)?.value)
        assertEquals(4, st.score)
    }

    @Test
    fun mergeRequiresSameValueAndReducerRule() {
        //  2 2 2 ·   -> LEFT ->  4 2 · ·
        val st = fresh(4, 9L)
        st.tiles.clear(); st.reindex()
        place(st, 1, 0, 0, 2); place(st, 2, 0, 1, 2); place(st, 3, 0, 2, 2)

        val m = Game.move(st, Dir.LEFT)
        assertNotNull(m)
        assertEquals(4, st.cellAt(0, 0)?.value)
        assertEquals(2, st.cellAt(0, 1)?.value)
        assertNull(st.cellAt(0, 2))
        assertEquals(4, st.score)
    }

    @Test
    fun illegalMoveLeavesStateIntact() {
        val st = Game.State(4, Rng(42L))
        // Canonical maximally packed 2048 board: no 4 equal neighbors.
        st.tiles += Tile(1, 0, 0, 2); st.tiles += Tile(2, 0, 1, 4); st.tiles += Tile(3, 0, 2, 2); st.tiles += Tile(4, 0, 3, 4)
        st.tiles += Tile(5, 1, 0, 4); st.tiles += Tile(6, 1, 1, 2); st.tiles += Tile(7, 1, 2, 4); st.tiles += Tile(8, 1, 3, 2)
        st.tiles += Tile(9, 2, 0, 2); st.tiles += Tile(10, 2, 1, 4); st.tiles += Tile(11, 2, 2, 2); st.tiles += Tile(12, 2, 3, 4)
        st.tiles += Tile(13, 3, 0, 4); st.tiles += Tile(14, 3, 1, 2); st.tiles += Tile(15, 3, 2, 4); st.tiles += Tile(16, 3, 3, 2)
        st.reindex()
        val before = st.serialize()
        for (d in Dir.values()) assertNull("move $d on full board", Game.move(st, d))
        assertEquals(before, st.serialize())
    }

    @Test
    fun legalMovesAdvanceScoreAndMooovesMade() {
        val st = fresh(4, 7L)
        var legal = 0
        for (i in 0 until 30) {
            val d = Dir.values()[i % 4]
            if (Game.move(st, d) != null) legal++
        }
        assertTrue(legal > 0)
        assertEquals(legal, st.movesMade)
    }

    @Test
    fun everyLegalMoveSpawnsExactlyOneTile() {
        val st = fresh(4, 11L)
        val before = st.tiles.size
        for (i in 0 until 200) {
            val d = Dir.values()[i % 4]
            val m = Game.move(st, d) ?: continue
            assertEquals("spawn must be non-null before game over, board=\n${board(st)}",
                1, m.added?.let { 1 } ?: 0)
            if (m.overNow) break
        }
        assertTrue(st.tiles.size >= before)
    }

    @Test
    fun winLatchesOnceAndStopsReflagging() {
        val st = Game.State(2, Rng(3L))
        st.tiles.clear(); st.reindex()
        place(st, 1, 0, 0, 1024); place(st, 2, 0, 1, 1024)

        val m = Game.move(st, Dir.LEFT)
        assertNotNull(m);
        val mv=m!!
        assertTrue(mv.wonNow)
        assertEquals(2048, st.cellAt(0, 0)?.value)

        st.keepGoing = true
        val m2 = Game.move(st, Dir.UP)
        val m3 = Game.move(st, Dir.DOWN)
        val m4 = Game.move(st, Dir.RIGHT)
        val all = listOf(m2, m3, m4)
        var next: Move? = null
        for (mc in all) if (mc != null) { next = mc; break }
        assertEquals(true, next != null)
        assertTrue("wonNow must be false after keepGoing latches `won`", !(next!!.wonNow))
    }

    @Test
    fun gameOverDetectsFullStuckBoard() {
        val st = Game.State(2, Rng(123L))
        st.tiles.clear(); st.reindex()
        // 2 4
        // 4 2  -> full and unmovable.
        place(st, 1, 0, 0, 2); place(st, 2, 0, 1, 4)
        place(st, 3, 1, 0, 4); place(st, 4, 1, 1, 2)
        assertTrue(st.isOver())
        for (d in Dir.values()) assertNull(Game.move(st, d))
    }

    @Test
    fun winOnMoveThatAlsoEndsGameStillReportsWin() {
        // 2x2: [1024 1024 / 2 4] -> LEFT merges row0 to 2048; the merge frees
        // one cell, the spawn refills it, and the board is full and stuck.
        // The win must still latch and be reported (the old code swallowed it
        // in the game-over early-return before the win latch ran).
        val st = Game.State(2, Rng(7L))
        st.tiles.clear(); st.reindex()
        place(st, 1, 0, 0, 1024); place(st, 2, 0, 1, 1024)
        place(st, 3, 1, 0, 2); place(st, 4, 1, 1, 4)

        val m = Game.move(st, Dir.LEFT)
        assertNotNull(m)
        val mv = m!!
        assertTrue("win must be reported even when the move ends the game", mv.wonNow)
        assertTrue("overNow must be reported", mv.overNow)
        assertTrue("won must latch in state", st.won)
        assertEquals(4, st.tiles.size)
        assertTrue("board full and stuck", st.isOver())
        // serialize round-trip keeps the won flag
        val st2 = Game.State.deserialize(st.serialize())!!
        assertTrue(st2.won)
    }

    @Test
    fun serializeRoundtrip() {
        val st = fresh(4, 21L)
        for (d in listOf(Dir.LEFT, Dir.UP, Dir.DOWN, Dir.LEFT))
            Game.move(st, d)
        val ser = st.serialize()
        System.err.println("SER: " + ser)
        val st2 = Game.State.deserialize(ser)!!
        assertEquals(ser, st2.serialize())
    }

    @Test
    fun oldFormatSaveStringStillParses() {
        // v0 strings (no "v1;" tag) written by older releases must load.
        val st = fresh(4, 33L)
        Game.move(st, Dir.LEFT)
        val v0 = st.serialize().removePrefix(Game.State.FORMAT_V1)
        val st2 = Game.State.deserialize(v0)
        assertNotNull(st2)
        assertEquals(st.score, st2!!.score)
        assertEquals(st.tiles.map { it.value }.sorted(), st2.tiles.map { it.value }.sorted())
    }

    @Test
    fun gridIndexStaysInSyncWithMoves() {
        // cellAt must agree with a linear scan after every move (guards the
        // reindex() calls in move/copy/deserialize).
        val st = fresh(4, 99L)
        for (i in 0 until 150) {
            Game.move(st, Dir.values()[i % 4]) ?: continue
            for (r in 0 until st.size) for (c in 0 until st.size) {
                val viaIndex = st.cellAt(r, c)
                val viaScan = st.tiles.firstOrNull { it.row == r && it.col == c }
                if (viaIndex === null || viaScan === null) {
                    assertEquals(viaIndex, viaScan)
                } else {
                    assertSame("grid and scan disagree at ($r,$c)", viaIndex, viaScan)
                }
            }
            if (st.isOver()) break
        }
    }

    @Test
    fun verticalAndHorizontalMerges() {
        val st1 = Game.State(4, Rng(5L))
        st1.tiles.clear()
        place(st1, 1, 0, 0, 2); place(st1, 2, 2, 0, 2)
        val m1 = Game.move(st1, Dir.DOWN)
        assertNotNull(m1)
        assertEquals(4, st1.cellAt(3, 0)?.value)
        assertEquals(4, st1.score)

        val st2 = Game.State(4, Rng(5L))
        st2.tiles.clear()
        place(st2, 1, 0, 0, 2); place(st2, 2, 0, 3, 2)
        val m2 = Game.move(st2, Dir.LEFT)
        assertNotNull(m2)
        assertEquals(4, st2.cellAt(0, 0)?.value)
    }

    @Test
    fun dailySeedIsDeterministicAcrossCalls() {
        val seedA = dailySeed(YearMonthDay(2026, 9, 27))
        val seedB = dailySeed(YearMonthDay(2026, 9, 27))
        assertEquals(seedA, seedB)

        val s1 = Game.State.initial(4, seedA, daily = true)
        val s2 = Game.State.initial(4, seedA, daily = true)
        // Same seed, same size -> identical first two spawns.
        assertEquals(
            s1.tiles.map { "${it.row}:${it.col}:${it.value}" }.sorted(),
            s2.tiles.map { "${it.row}:${it.col}:${it.value}" }.sorted(),
        )
    }
}
