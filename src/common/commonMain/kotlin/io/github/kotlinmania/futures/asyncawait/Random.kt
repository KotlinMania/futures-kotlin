// port-lint: source async_await/random.rs
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.kotlinmania.futures.asyncawait

import kotlin.concurrent.atomics.AtomicLong

private val prngCounter = AtomicLong(0L)
private val rngState = AtomicLong(0L)

/**
 * Randomly shuffles [slice] in place using Fisher-Yates shuffle and the internal xorshift* PRNG.
 */
public fun <T> shuffle(slice: MutableList<T>) {
    for (i in (slice.size - 1) downTo 1) {
        val j = genIndex(i + 1)
        val tmp = slice[i]
        slice[i] = slice[j]
        slice[j] = tmp
    }
}

/**
 * Returns a uniformly bounded pseudo-random index in `0 until n`.
 */
internal fun genIndex(n: Int): Int {
    require(n > 0) { "genIndex bound must be positive" }
    return (random() % n.toULong()).toInt()
}

private fun prngSeed(): ULong {
    var seed = 0uL
    while (seed == 0uL) {
        val n = prngCounter.fetchAndAdd(1L).toULong() + 1uL
        // SplitMix64 mixing guarantees a non-zero, well-distributed seed.
        var z = n + 0x9e37_79b9_7f4a_7c15uL
        z = (z xor (z shr 30)) * 0xbf58_476d_1ce4_e5b9uL
        z = (z xor (z shr 27)) * 0x94d0_49bb_1331_11ebuL
        seed = z xor (z shr 31)
    }
    return seed
}

/**
 * Pseudorandom number generator based on xorshift*.
 */
internal fun random(): ULong {
    while (true) {
        val current = rngState.load().toULong()
        var x = if (current == 0uL) prngSeed() else current
        x = x xor (x shr 12)
        x = x xor (x shl 25)
        x = x xor (x shr 27)
        if (rngState.compareAndSet(current.toLong(), x.toLong())) {
            return x * 0x2545_f491_4f6c_dd1duL
        }
    }
}
