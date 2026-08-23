// port-lint: source futures-core/src/task/__internal/atomic_waker.rs
package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val WAITING: Int = 0
private const val REGISTERING: Int = 0b01
private const val WAKING: Int = 0b10

/**
 * A synchronization primitive for task wakeup.
 *
 * Coordinates concurrent notifications between a producer waking a task and a
 * consumer updating the registered task waker.
 */
@OptIn(ExperimentalAtomicApi::class)
public class AtomicWaker {
    private val state = AtomicInt(WAITING)
    private val wakerCell = AtomicReference<Waker?>(null)

    /**
     * Registers the waker to be notified on calls to [wake].
     */
    public fun register(waker: Waker) {
        val prevState = state.compareAndExchange(WAITING, REGISTERING)
        when (prevState) {
            WAITING -> {
                wakerCell.store(waker)
                val res = state.compareAndExchange(REGISTERING, WAITING)
                if (res != REGISTERING) {
                    val currentWaker = wakerCell.exchange(null)
                    state.store(WAITING)
                    currentWaker?.wakeByRef()
                }
            }
            WAKING -> {
                waker.wakeByRef()
            }
            else -> {
                // Another thread is holding the registering lock; safe to drop.
            }
        }
    }

    /**
     * Calls wake on the last waker passed to [register].
     */
    public fun wake() {
        take()?.wakeByRef()
    }

    /**
     * Returns the last waker passed to [register] so that the caller can wake it.
     */
    public fun take(): Waker? {
        val prevState = state.fetchAndAdd(WAKING)
        return if (prevState == WAITING) {
            val w = wakerCell.exchange(null)
            state.store(WAITING)
            w
        } else {
            null
        }
    }

    public companion object {
        public fun new(): AtomicWaker = AtomicWaker()
    }
}
