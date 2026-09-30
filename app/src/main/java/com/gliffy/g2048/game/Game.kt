package com.gliffy.g2048.game

/** Direction enum. UP/DOWN move vertically; LEFT/RIGHT horizontally. */
enum class Dir { UP, DOWN, LEFT, RIGHT }

/** A single 2048 tile. Mutable position/value: the engine moves it in place */
class Tile(
    val id: Int,
    var row: Int,
    var col: Int,
    var value: Int,
)

data class Slide(val id: Int, val oldRow: Int, val oldCol: Int)

/** What the animation layer needs: the tile(s) the survivor came
 *  from, i.e. the board 'before' the move, as id -> (r,c). */
data class MergeInfo(val survivorId: Int, val id: Int, val oldRow: Int, val oldCol: Int, val value: Int)

typealias MergeInfoList = ArrayList<MergeInfo>

/** Result record for a successful [Game.move]. All lists are plain values. */
class Move(
    val before: Game.State,
    val slid: List<Slide>,
    val mergedInto: Map<Int, Int>, // vanisher id -> survivor id
    val mergeInfoList: MergeInfoList, // full merge metadata (vis + fx)
    val added: Tile,
    val scoreGained: Int,
    val wonNow: Boolean,
    val overNow: Boolean,
)

object Game {

    const val WIN_VALUE = 2048

    class State(
        val size: Int,
        val rng: Rng,
        val daily: Boolean = false,
    ) {
        var score: Int = 0
        var movesMade: Int = 0
        var keepGoing: Boolean = false
        var won: Boolean = false
        val tiles: ArrayList<Tile> = ArrayList()

        /** O(1) cell lookup, kept in sync with [tiles] by [reindex]. */
        private var grid = Array(size) { arrayOfNulls<Tile>(size) }

        /** Rebuild the cell index from [tiles]. Call after any list mutation. */
        fun reindex() {
            grid = Array(size) { arrayOfNulls<Tile>(size) }
            for (t in tiles) if (t.row in 0 until size && t.col in 0 until size)
                grid[t.row][t.col] = t
        }

        fun cellAt(row: Int, col: Int): Tile? =
            if (row in 0 until size && col in 0 until size) grid[row][col] else null

        fun freeCells(): ArrayList<Pair<Int, Int>> {
            val occ = BooleanArray(size * size)
            for (t in tiles) occ[t.row * size + t.col] = true
            val out = ArrayList<Pair<Int, Int>>()
            for (idx in 0 until size * size)
                if (!occ[idx]) out.add(idx / size to idx % size)
            return out
        }

        /** Any like-valued pair of adjacent cells (== mergeable). */
        fun hasStuckPair(): Boolean {
            for (r in 0 until size) for (c in 0 until size) {
                val t = grid[r][c] ?: continue
                if (c + 1 < size && grid[r][c + 1]?.value == t.value) return true
                if (r + 1 < size && grid[r + 1][c]?.value == t.value) return true
            }
            return false
        }

        fun isOver(): Boolean = freeCells().isEmpty() && !hasStuckPair()

        fun copy(): State {
            val s = State(size, Rng(rng.state), daily)
            s.score = score
            s.movesMade = movesMade
            s.keepGoing = keepGoing
            s.won = won
            for (t in tiles) s.tiles.add(Tile(t.id, t.row, t.col, t.value))
            s.reindex()
            return s
        }

        /** size;score;moves;keep;won;daily;rngseed|id:r:c:v,id:r:c:v,...  */
        fun serialize(): String = FORMAT_V1 + serializeBody()

        /** Body without the format tag (kept so old v0 strings still parse). */
        private fun serializeBody(): String {
            val head = listOf(size, score, movesMade,
                if (keepGoing) 1 else 0,
                if (won) 1 else 0,
                if (daily) 1 else 0,
                rng.state,
            ).joinToString(";")
            val body = tiles.joinToString(",") { "${it.id}:${it.row}:${it.col}:${it.value}" }
            return head + "|" + body
        }

        companion object {
            /** Save-format tag. Bump when the layout changes; old tags keep
             *  parsing via their own branch so upgrades never wipe a game. */
            const val FORMAT_V1 = "v1;"

            fun deserialize(s: String): State? {
                return try {
                val body = if (s.startsWith(FORMAT_V1)) s.substring(FORMAT_V1.length) else s
                val parts = body.split("|")
                if (parts.size != 2) return null
                val f = parts[0].split(";")
                if (f.size != 7) return null
                val st = State(f[0].toInt(), Rng(f[6].toLong()), f[5] == "1")
                st.score = f[1].toInt()
                st.movesMade = f[2].toInt()
                st.keepGoing = f[3] == "1"
                st.won = f[4] == "1"
                for (rec in parts[1].split(",")) {
                    if (rec.isEmpty()) continue
                    val g = rec.split(":")
                    if (g.size != 4) return null
                    val t = Tile(g[0].toInt(), g[1].toInt(), g[2].toInt(), g[3].toInt())
                    // Reject out-of-bounds tiles explicitly: the freeCells
                    // count check below would otherwise let a full board
                    // with an OOB tile slip through and corrupt play.
                    if (t.row !in 0 until st.size || t.col !in 0 until st.size) return null
                    st.tiles.add(t)
                }
                if (st.freeCells().size + st.tiles.size != st.size * st.size) return null
                st.reindex()
                st
            } catch (e: Exception) {
                null
            }
            }

            /** New game: empty board with exactly two spawned tiles. */
            fun initial(size: Int, seed: Long, daily: Boolean = false): State {
                val st = State(size, Rng(seed), daily)
                st.spawnOne()
                st.spawnOne()
                return st
            }
        }

        internal fun spawnOne(): Tile? {
            val free = freeCells()
            if (free.isEmpty()) return null
            val (r, c) = free[rng.nextInt(free.size)]
            val value = if (rng.nextInt(10) == 0) 4 else 2
            val m = tiles.maxOfOrNull { it.id } ?: 0
            val t = Tile(m + 1, r, c, value)
            tiles.add(t)
            grid[r][c] = t
            return t
        }
    }

    /**
     * Attempt [dir] on [st]. Mutates [st] in place only on success; returns
     * the [Move] record or null (state untouched) when no tile can move/merge.
     */
    fun move(st: State, dir: Dir): Move? {
        val n = st.size
        val horizontal = dir in setOf(Dir.LEFT, Dir.RIGHT)
        val toLow = dir == Dir.LEFT || dir == Dir.UP

        val before = st.copy()
        val slid = ArrayList<Slide>()
        val mergedInto = HashMap<Int, Int>()
        val mlist = MergeInfoList()
        val gone = ArrayList<Tile>()
        var gained = 0
        var any = false

        for (line in 0 until n) {
            // walk cells of this line, leading edge first.
            val packed = ArrayList<Tile>()
            val packedMerged = ArrayList<Boolean>()
            for (i in 0 until n) {
                val side = if (toLow) i else (n - 1 - i)
                val r = if (horizontal) line else side
                val c = if (horizontal) side else line
                val t = st.cellAt(r, c) ?: continue
                // target pack cell for the next survivor.
                val k = packed.size // index in pack order, 0 = leading
                val tr = if (horizontal) line else (if (toLow) k else n - 1 - k)
                val tc = if (horizontal) (if (toLow) k else n - 1 - k) else line
                val last = packed.lastOrNull()
                if (last != null && !packedMerged.last() && last.value == t.value) {
                    last.value *= 2
                    gained += last.value
                    mergedInto[t.id] = last.id
                    mlist.add(MergeInfo(last.id, t.id, t.row, t.col, last.value / 2))
                    slid.add(Slide(t.id, t.row, t.col))
                    any = true
                    packedMerged[packedMerged.size - 1] = true
                    gone.add(t)
                } else {
                    if (t.row != tr || t.col != tc) {
                        slid.add(Slide(t.id, t.row, t.col))
                        any = true
                    }
                    t.row = tr
                    t.col = tc
                    packed.add(t)
                    packedMerged.add(false)
                }
            }
        }
        if (!any) return null

        st.tiles.removeAll(gone.toHashSet())
        st.reindex()
        st.score += gained
        st.movesMade += 1
        // Latch the win BEFORE spawning: if this move both reaches 2048 and
        // fills the board (spawn returns null), the win must still be
        // reported and saved instead of being swallowed by the game-over
        // early-return below.
        val wonNow = !before.won && st.tiles.any { it.value >= WIN_VALUE }
        if (wonNow) st.won = true
        val added = st.spawnOne()
            ?: return Move(before, slid, mergedInto, mlist,
                Tile(-1, -1, -1, 0), gained, wonNow, st.isOver())
        return Move(before, slid, mergedInto, mlist, added, gained, wonNow, st.isOver())
    }
}
