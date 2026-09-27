package com.gliffy.g2048.game

/** Deterministic 64-bit RNG (SplitMix64). Copyable so game states can be
 *  snapshotted for undo and replayed identically for Daily seeds. */
class Rng(var state: Long) {
    fun nextLong(): Long {
        state += java.math.BigInteger("9E3779B97F4A7C15",16).toLong()
        var z = state
        z = (z xor (z ushr 30)) * java.math.BigInteger("BF58476D1CE4E5B9",16).toLong()
        z = (z xor (z ushr 27)) * java.math.BigInteger("94D049BB133111EB",16).toLong()
        return z xor (z ushr 31)
    }

    /** Uniform in [0, toExclusive). */
    fun nextInt(toExclusive: Int): Int {
        require(toExclusive > 0)
        val bits = nextLong() and Long.MAX_VALUE
        return (bits % toExclusive).toInt()
    }

    fun copy() = Rng(state)
}

/** Seed from a UTC calendar date: same seed for the whole world on a given day. */
fun dailySeed(yourDate: YearMonthDay): Long {
    // deterministic from y/m/d without platform deps
    var seed = 0x20482048L
    seed = seed * 100003L + yourDate.year.toLong()
    seed = seed * 100003L + yourDate.month
    seed = seed * 100003L + yourDate.day
    return seed
}

data class YearMonthDay(val year: Int, val month: Int, val day: Int)
