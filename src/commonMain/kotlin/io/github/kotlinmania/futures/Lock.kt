// port-lint: source futures-channel/src/lock.rs
package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A mutex which only supports `tryLock`.
 *
 * Used as a fast user-space implementation of a lock that can only have a `tryLock` operation.
 */
@OptIn(ExperimentalAtomicApi::class)
public class Lock<T>(
    private var data: T,
) {
    private val locked = AtomicBoolean(false)

    /**
     * Attempts to acquire this lock, returning whether the lock was acquired or not.
     */
    public fun tryLock(): TryLock<T>? =
        if (locked.compareAndSet(false, true)) {
            TryLock(this)
        } else {
            null
        }

    public fun getData(): T = data

    public fun setData(value: T) {
        data = value
    }

    public fun unlock() {
        locked.store(false)
    }

    public companion object {
        public fun <T> new(data: T): Lock<T> = Lock(data)
    }
}

/**
 * Sentinel representing an acquired lock through which the data can be accessed.
 */
public class TryLock<T>(
    private val lock: Lock<T>,
) {
    public fun get(): T = lock.getData()

    public fun set(value: T) {
        lock.setData(value)
    }

    public fun unlock() {
        lock.unlock()
    }
}
