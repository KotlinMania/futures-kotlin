// port-lint: source futures-test/src/task/wake_counter.rs
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicInt

/**
 * Number of times the waker was awoken.
 *
 * See [newCountWaker] for usage.
 */
public class AwokenCount internal constructor(
    private val inner: WakerInner,
) {
    /**
     * Get the current count.
     */
    public fun get(): Int = inner.count.load()

    override fun equals(other: Any?): Boolean =
        when (other) {
            is AwokenCount -> get() == other.get()
            is Int -> get() == other
            else -> false
        }

    override fun hashCode(): Int = get().hashCode()

    override fun toString(): String = "AwokenCount(${get()})"
}

internal class WakerInner(
    val count: AtomicInt = AtomicInt(0),
) : ArcWake {
    override fun wakeByRef() {
        count.fetchAndAdd(1)
    }
}

/**
 * Create a new [Waker] that counts the number of times it is awoken.
 */
public fun newCountWaker(): Pair<Waker, AwokenCount> {
    val inner = WakerInner()
    return Pair(waker(inner), AwokenCount(inner))
}
