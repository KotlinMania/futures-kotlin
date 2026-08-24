// port-lint: source futures-util/src/lock/bilock.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicReference
import kotlin.native.HiddenFromObjC

private class BiLockInner<T>(
    var value: T,
) {
    val state = AtomicReference<Any?>(null)
}

private val LOCKED_SENTINEL = Any()

/**
 * Error indicating two [BiLock] instances were not two halves of a whole, and thus could not be reunited.
 */
@HiddenFromObjC
public class BiLockReuniteError(
    public val first: BiLock<*>,
    public val second: BiLock<*>,
) : Exception("tried to reunite two BiLocks that don't form a pair")

/**
 * Returned RAII-like guard from the [BiLock.pollLock] method.
 */
@HiddenFromObjC
public class BiLockGuard<T> internal constructor(
    private val bilock: BiLock<T>,
) {
    public fun get(): T = bilock.getValue()

    public fun set(value: T) {
        bilock.setValue(value)
    }

    public fun unlock() {
        bilock.unlock()
    }

    public inline fun <R> withValue(block: (T) -> R): R {
        try {
            return block(get())
        } finally {
            unlock()
        }
    }
}

/**
 * Future returned by [BiLock.lock] which will resolve when the lock is acquired.
 */
@HiddenFromObjC
public class BiLockAcquire<T> internal constructor(
    private val bilock: BiLock<T>,
) : Future<BiLockGuard<T>> {
    override fun poll(context: TaskContext): Poll<BiLockGuard<T>> = bilock.pollLock(context)
}

/**
 * A type of futures-powered synchronization primitive which is a mutex between two possible owners.
 */
@HiddenFromObjC
public class BiLock<T> private constructor(
    private val inner: BiLockInner<T>,
) {
    public companion object {
        /**
         * Creates a new `BiLock` protecting the provided data.
         *
         * Two handles to the lock are returned, and these are the only two handles
         * that will ever be available to the lock.
         */
        public fun <T> new(value: T): Pair<BiLock<T>, BiLock<T>> {
            val inner = BiLockInner(value)
            return Pair(BiLock(inner), BiLock(inner))
        }
    }

    /**
     * Attempt to acquire this lock, returning `Pending` if it can't be acquired.
     */
    public fun pollLock(context: TaskContext): Poll<BiLockGuard<T>> {
        var waker: Waker? = null
        while (true) {
            val prev = inner.state.exchange(LOCKED_SENTINEL)
            when (prev) {
                null -> {
                    return Poll.ready(BiLockGuard(this))
                }
                LOCKED_SENTINEL -> {
                    // Lock held by the other handle
                }
                is Waker -> {
                    waker = prev
                }
            }

            val currentWaker = waker ?: context.waker
            val success = inner.state.compareAndSet(LOCKED_SENTINEL, currentWaker)
            if (success) {
                return Poll.pending()
            }

            val current = inner.state.load()
            if (current == null) {
                continue
            }
        }
    }

    /**
     * Perform a lock acquisition returning a future.
     */
    public fun lock(): BiLockAcquire<T> = BiLockAcquire(this)

    /**
     * Returns true only if the other [BiLock] originated from the same call to [BiLock.new].
     */
    public fun isPairOf(other: BiLock<T>): Boolean = this.inner === other.inner

    /**
     * Attempts to put the two "halves" of a [BiLock] back together and recover the original value.
     */
    public fun reunite(other: BiLock<T>): Result<T> =
        if (isPairOf(other)) {
            Result.success(inner.value)
        } else {
            Result.failure(BiLockReuniteError(this, other))
        }

    internal fun getValue(): T = inner.value

    internal fun setValue(value: T) {
        inner.value = value
    }

    internal fun unlock() {
        val prev = inner.state.exchange(null)
        when (prev) {
            null -> {}
            LOCKED_SENTINEL -> {}
            is Waker -> {
                prev.wakeByRef()
            }
        }
    }
}
