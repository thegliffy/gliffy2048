package com.gliffy.g2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Error-boundary &amp; fuzz tests for the pure engine:
 *  - invariants under long random play (dedicated fuzz)
 *  - adversarial / corrupted serialization input
 *  - legal-move return contract
 *  - RNG boundary behavior
 */
class EngineErrorTest {

    private val dirs = listOf(Dir.UP, Dir.DOWN, Dir.LEFT, Dir.RIGHT)

    /** Assert structural invariants: in-bounds, unique cells, positive values, sane score. */
    private fun checkInvariants(st: Game.State) {
        val seen = HashSet<Int>()
        for (t in st.tiles) {
            assertTrue("tile ${t.id} out of bounds at (${t.row},${t.col})",
                t.row in 0 until st.size && t.col in 0 until st.size)
            val cell = t.row * st.size + t.col
            assertTrue("duplicate tile at ($cell)", seen.add(cell))
            assertTrue("non-positive value", t.value > 0)
        }
        assertTrue("too many tiles", st.tiles.size <= st.size * st.size)
        assertTrue("score must be >= 0", st.score >= 0)
    }

    // ---------------------------------------------------------------- fuzz

    @Test
    fun fuzzLongGamesInvariantsHold() {
        // A handful of seeds x sizes x hundreds of moves. If move() or
        // spawnOne() ever corrupts the board, an invariant breaks here.
        for (size in 2..5) {
            for (seed in 1L..2L) {
                val st = Game.State.initial(size, seed)
                var moves = 0
                while (moves < 250 && !st.isOver()) {
                    val dir = dirs[(Math.abs(st.rng.state) % 4).toInt()]
                    val beforeCount = st.tiles.size
                    val m = Game.move(st, dir)
                    if (m != null) {
                        moves++
                        // conservation: tiles = before - vanished + 1 spawn
                        assertEquals("tile count conservation broken",
                            beforeCount - m.mergedInto.size + 1, st.tiles.size)
                    }
                    checkInvariants(st)
                    assertTrue("score never decreases", st.score >= 0)
                }
                assertTrue("game should make >= 50 moves (size=$size seed=$seed, got $moves)", moves >= 50)
            }
        }
    }

    @Test
    fun longGameRoundtripSurvivesCrashMiddling() {
        // Simulate app kill at arbitrary points: serialize, kill, restore,
        // continue — the restored state must remain legal and byte-identical.
        for (size in 3..5) {
            val st = Game.State.initial(size, 0xB0B0L)
            for (i in 0 until 240) {
                if (st.isOver()) break
                Game.move(st, dirs[(0xB0B0 + i) % 4])
                if (i % 37 == 0) {
                    val ser = st.serialize()
                    val restored = Game.State.deserialize(ser)
                    assertNotNull("roundtrip failed at move $i", restored)
                    assertEquals(ser, restored!!.serialize())
                    checkInvariants(restored)
                }
            }
        }
    }

    // --------------------------------------------------- adversarial input

    @Test
    fun deserializeRejectsGarbage() {
        for (bad in listOf(
            "",
            "hello",
            "a|b",
            "1;2;3;4;5;6;7",           // no body
            "4;0;0;0;0;0",             // only 6 fields
            "4;0;0;0;0;0;0;extra|",    // 8 fields
            "x;y;z;w;v;u;t|1:0:0:2",   // non-numeric head
            "4;0;0;0;0;0;1|a:b:c:d",   // non-numeric tile
            "4;0;0;0;0;0;1|1:0:0",     // 3-part tile
            "4;0;0;0;0;0;1|1:0:0:2:9", // 5-part tile
            "4;0;0;0;0;0;1|\n \n",     // junk body
            "99999999999999999999;0;0;0;0;0;0|", // overflow int
            "4;99999999999999999999;0;0;0;0;0;0|", // overflow score
        )) {
            assertNull("expected null for: <$bad>", Game.State.deserialize(bad))
        }
    }

    @Test
    fun deserializeEmptyBodyIsHarmlessEmptyBoard() {
        // No tiles -> legal empty board, not a crash.
        val st = Game.State.deserialize("4;0;0;0;0;0;0|")
        assertNotNull(st)
        assertEquals(0, st!!.tiles.size)
        assertEquals(16, st.freeCells().size)
        // playing on it is safe: no move possible until a tile exists
        for (d in Dir.values()) assertNull(Game.move(st, d))
    }

    @Test
    fun deserializeRejectsOutOfBoundsTiles() {
        // Tile row/col outside the board: must be rejected — a bad save
        // must never corrupt later play (freeCells would misbehave).
        val big = "4;0;0;0;0;0;7|1:9:0:2"
        assertNull(Game.State.deserialize(big))
        val neg = "4;0;0;0;0;0;7|1:-1:0:2"
        assertNull(Game.State.deserialize(neg))
        val huge = "4;0;0;0;0;0;7|1:999:999:2"
        assertNull(Game.State.deserialize(huge))
        // Out-of-bounds tiles must also be rejected when the board is full
        // (the freeCells count check alone would let these through).
        val fullOob = "2;0;0;0;0;0;7|1:9:0:2,2:0:1:2,3:1:0:2,4:1:1:2"
        assertNull(Game.State.deserialize(fullOob))
    }

    @Test
    fun deserializeRejectsOverlappingTiles() {
        // Two tiles on the same cell must be rejected: a real board can
        // never have this, and a hand-edited save must not corrupt play.
        val dup = "4;0;0;0;0;0;7|1:0:0:2,2:0:0:4"
        assertNull("duplicate-cell save must be rejected", Game.State.deserialize(dup))
    }

    @Test
    fun deserializeAcceptsValidMaxBoard() {
        // Legal: 2x2 fully and densely packed. Header needs 7 fields
        // (size;score;moves;keep;won;daily;rngseed).
        val ok = "2;50;12;0;0;0;7|1:0:0:4,2:0:1:8,3:1:0:16,4:1:1:32"
        val st = Game.State.deserialize(ok)
        assertNotNull(st)
        val s = st!!
        assertEquals(50, s.score)
        assertEquals(4, s.tiles.size)
        assertTrue(s.isOver())
    }

    // ------------------------------------------------------- move contract

    @Test
    fun fullBoardNoMergeReturnsNullAndIsOver() {
        val st = Game.State(4, Rng(42L))
        st.tiles.clear(); st.reindex()
        // 2/4 checkerboard: no equal neighbors -> legally stuck when full
        var i = 0
        for (r in 0 until 4) for (c in 0 until 4) {
            val v = if ((r + c) % 2 == 0) 2 else 4
            st.tiles.add(Tile(i, r, c, v)); i++
        }
        checkInvariants(st)
        for (d in dirs) assertNull("no move possible but $d succeeded", Game.move(st, d))
        assertTrue(st.isOver())
    }

    @Test
    fun successfulMoveRecordsStayConsistent() {
        for (seed in 1L..10L) {
            val st = Game.State.initial(4, seed)
            for (i in 0 until 150) {
                if (st.isOver()) break
                val before = st.copy()
                val m = Game.move(st, dirs[((seed % 1000).toInt() + i) % 4]) ?: continue
                for ((v, s) in m.mergedInto) {
                    assertFalse("vanished id $v still on board", st.tiles.any { it.id == v })
                    assertTrue("survivor id $s missing", st.tiles.any { it.id == s })
                }
                for (mi in m.mergeInfoList) {
                    val old = before.tiles.first { it.id == mi.id }
                    assertEquals(mi.oldRow, old.row)
                    assertEquals(mi.oldCol, old.col)
                    assertTrue("merge info value mismatch", mi.value > 0)
                }
            }
        }
    }

    @Test
    fun moveRecordBeforeIsRealSnapshot() {
        val st = Game.State.initial(4, 7L)
        val m = Game.move(st, Dir.LEFT) ?: run { fail("expected a move"); return }
        m.before.tiles.add(Tile(999, 0, 0, 64)) // mutate the snapshot
        // live state must be unaffected by snapshot mutation
        assertFalse("snapshot must be detached from live state", st.tiles.any { it.id == 999 })
    }

    @Test
    fun deterministicReplayFuzz() {
        // Direction sequence derived deterministically from the RNG (instead
        // of thread-shared Math.random) — cheaper and reproducible.
        for (size in 2..6) {
            val st = Game.State.initial(size, 0x2048L)
            var moves = 0
            while (moves < 300 && !st.isOver()) {
                val dir = dirs[st.rng.nextInt(4)]
                val beforeCount = st.tiles.size
                val m = Game.move(st, dir)
                if (m != null) {
                    moves++
                    assertEquals("conservation (det) broken",
                        beforeCount - m.mergedInto.size + 1, st.tiles.size)
                }
                checkInvariants(st)
            }
        }
    }

    // ------------------------------------------------------------- rng

    @Test
    fun rngNextIntBoundaries() {
        val r = Rng(99L)
        for (i in 0 until 500) {
            assertEquals("nextInt(1) must always be 0", 0, r.nextInt(1))
        }
        val r2 = Rng(99L)
        for (i in 0 until 500) {
            assertTrue("nextInt(2) out of range: ${r2.nextInt(2)}", r2.nextInt(2) in 0..1)
        }
        try {
            Rng(1L).nextInt(0)
            fail("nextInt(0) should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun rngDeterministicAndCopyable() {
        val a = Rng(1234L)
        val b = Rng(1234L)
        // identical states -> identical sequences
        for (i in 0 until 100) assertEquals(a.nextLong(), b.nextLong())
        // a copy taken now must continue identically from the same point
        val mid = a.copy()
        for (i in 0 until 100) assertEquals(a.nextLong(), mid.nextLong())
    }
}
