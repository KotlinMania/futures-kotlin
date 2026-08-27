// port-lint: source futures-util/src/future/future/shared.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.HiddenFromObjC

private const val STATE_IDLE = 0
private const val STATE_POLLING = 1
private const val STATE_COMPLETE = 2
private const val STATE_POISONED = 3

/**
 * A future that is cloneable and can be polled in multiple threads / tasks concurrently.
 */
@HiddenFromObjC
public class Shared<T> internal constructor(
    private val inner: SharedInner<T>,
) : FusedFuture<T> {
    private var completed: Boolean = false

    public companion object {
        internal fun <T> new(future: Future<T>): Shared<T> =
            Shared(SharedInner(future))
    }

    /**
     * Returns true if the shared future has completed.
     */
    public fun isDone(): Boolean = inner.state.load() == STATE_COMPLETE

    /**
     * Returns the output value if the future has completed, or null otherwise.
     */
    public fun peek(): T? =
        if (inner.state.load() == STATE_COMPLETE) inner.output else null

    override fun isTerminated(): Boolean = completed

    @Suppress("UNCHECKED_CAST")
    override fun poll(context: TaskContext): Poll<T> {
        if (completed) {
            val out = inner.output
            if (out != null) return Poll.Ready(out)
            throw RuntimeException("Shared future polled again after completion")
        }

        if (inner.state.load() == STATE_COMPLETE) {
            completed = true
            return Poll.Ready(inner.output as T)
        }

        inner.withLock {
            if (inner.state.load() == STATE_COMPLETE) {
                completed = true
                return Poll.Ready(inner.output as T)
            }
            inner.wakersList.add(context.waker)
        }

        if (inner.state.compareAndSet(STATE_IDLE, STATE_POLLING)) {
            try {
                when (val p = inner.future.poll(context)) {
                    is Poll.Ready -> {
                        inner.output = p.value
                        inner.state.store(STATE_COMPLETE)
                        val toWake =
                            inner.withLock {
                                val list = ArrayList(inner.wakersList)
                                inner.wakersList.clear()
                                list
                            }
                        for (w in toWake) {
                            w.wakeByRef()
                        }
                        completed = true
                        return Poll.Ready(p.value)
                    }
                    Poll.Pending -> {
                        inner.state.store(STATE_IDLE)
                        return Poll.Pending
                    }
                }
            } catch (t: Throwable) {
                inner.error = t
                inner.state.store(STATE_POISONED)
                val toWake =
                    inner.withLock {
                        val list = ArrayList(inner.wakersList)
                        inner.wakersList.clear()
                        list
                    }
                for (w in toWake) {
                    w.wakeByRef()
                }
                throw t
            }
        }

        return Poll.Pending
    }

    public fun clone(): Shared<T> = Shared(inner)
}

internal class SharedInner<T>(
    val future: Future<T>,
) {
    val state = AtomicInt(STATE_IDLE)
    var output: T? = null
    var error: Throwable? = null
    val lock = AtomicBoolean(false)
    val wakersList = mutableListOf<Waker>()

    inline fun <R> withLock(block: () -> R): R {
        while (!lock.compareAndSet(false, true)) {
            // spin
        }
        try {
            return block()
        } finally {
            lock.store(false)
        }
    }
}

/**
 * Creates a new [Shared] future.
 */
@HiddenFromObjC
public fun <T> Future<T>.shared(): Shared<T> = Shared.new(this)
