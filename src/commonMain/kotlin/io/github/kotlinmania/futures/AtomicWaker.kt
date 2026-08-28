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
 * Sometimes the task interested in a given event will change over time.
 * An [AtomicWaker] can coordinate concurrent notifications with the consumer
 * potentially "updating" the underlying task to wake up. This is useful in
 * scenarios where a computation completes in another thread and wants to
 * notify the consumer, but the consumer is in the process of being migrated to
 * a new logical task.
 *
 * Consumers should call [register] before checking the result of a computation
 * and producers should call [wake] after producing the computation (this
 * differs from the usual `thread::park` pattern). It is also permitted for
 * [wake] to be called **before** [register]. This results in a no-op.
 *
 * A single [AtomicWaker] may be reused for any number of calls to [register] or
 * [wake].
 *
 * # Memory ordering
 *
 * Calling [register] "acquires" all memory "released" by calls to [wake]
 * before the call to [register]. Later calls to [wake] will wake the
 * registered waker (on contention this wake might be triggered in [register]).
 *
 * For concurrent calls to [register] (should be avoided) the ordering is only
 * guaranteed for the winning call.
 */
@OptIn(ExperimentalAtomicApi::class)
public class AtomicWaker {
    private val state = AtomicInt(WAITING)
    private val wakerCell = AtomicReference<Waker?>(null)

    /**
     * Registers the waker to be notified on calls to [wake].
     *
     * The new task will take place of any previous tasks that were registered
     * by previous calls to [register]. Any calls to [wake] that happen after
     * a call to [register] (as defined by the memory ordering rules), will
     * notify the [register] caller's task and deregister the waker from future
     * notifications. Because of this, callers should ensure [register] gets
     * invoked with a new [Waker] **each** time they require a wakeup.
     *
     * It is safe to call [register] with multiple other threads concurrently
     * calling [wake]. This will result in the [register] caller's current
     * task being notified once.
     *
     * This function is safe to call concurrently, but this is generally a bad
     * idea. Concurrent calls to [register] will attempt to register different
     * tasks to be notified. One of the callers will win and have its task set,
     * but there is no guarantee as to which caller will succeed.
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
     * Calls `wake` on the last [Waker] passed to [register].
     *
     * If [register] has not been called yet, then this does nothing.
     */
    public fun wake() {
        take()?.wakeByRef()
    }

    /**
     * Returns the last [Waker] passed to [register], so that the user can wake it.
     *
     * Sometimes, just waking the [AtomicWaker] is not fine grained enough. This allows the user
     * to take the waker and then wake it separately, rather than performing both steps in one
     * atomic action.
     *
     * If a waker has not been registered, this returns `null`.
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

    /** Formats this [AtomicWaker] for debugging. */
    public fun fmt(builder: StringBuilder) {
        builder.append("AtomicWaker")
    }

    override fun toString(): String = "AtomicWaker"

    public companion object {
        /** Create an [AtomicWaker]. */
        public fun new(): AtomicWaker = AtomicWaker()

        /** Create a default [AtomicWaker]. */
        public fun default(): AtomicWaker = AtomicWaker()
    }
}
