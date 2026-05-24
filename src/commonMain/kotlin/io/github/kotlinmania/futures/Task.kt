// port-lint: source futures-core/src/task/mod.rs
package io.github.kotlinmania.futures

/**
 * Result of polling an asynchronous computation.
 */
public sealed interface Poll<out T> {
    /**
     * The computation produced a value.
     */
    public data class Ready<out T>(public val value: T) : Poll<T>

    /**
     * The computation is not ready yet.
     */
    public data object Pending : Poll<Nothing>

    public companion object {
        public fun <T> ready(value: T): Poll<T> = Ready(value)

        public fun <T> pending(): Poll<T> = Pending
    }
}

/**
 * Notification handle for a task that may be polled again.
 */
public fun interface Waker {
    public fun wakeByRef()
}

/**
 * Polling context passed to futures, streams, and sinks.
 */
public class TaskContext(public val waker: Waker = Waker {}) {
    public fun wakeByRef() {
        waker.wakeByRef()
    }
}

public inline fun <T, R> Poll<T>.fold(onReady: (T) -> R, onPending: () -> R): R =
    when (this) {
        is Poll.Ready -> onReady(value)
        Poll.Pending -> onPending()
    }

public fun <T> Poll<T>.readyOrNull(): T? =
    when (this) {
        is Poll.Ready -> value
        Poll.Pending -> null
    }

