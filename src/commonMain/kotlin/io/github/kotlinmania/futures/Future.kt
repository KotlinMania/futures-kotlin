// port-lint: source futures-core/src/future.rs
package io.github.kotlinmania.futures

/**
 * A single eventual value produced by an asynchronous computation.
 */
public fun interface Future<out T> {
    /**
     * Attempt to resolve this future to a final value, registering the current
     * task for wakeup when progress is not yet possible.
     */
    public fun poll(context: TaskContext): Poll<T>
}

/**
 * An owned dynamically typed [Future] for cases where the result cannot be
 * statically typed or needs an extra level of indirection.
 */
public typealias BoxFuture<T> = Future<T>

/**
 * [BoxFuture], but without a cross-thread sending requirement.
 */
public typealias LocalBoxFuture<T> = Future<T>

/**
 * A future that tracks whether its underlying computation should no longer be
 * polled.
 */
public interface FusedFuture<out T> : Future<T> {
    /**
     * Returns true when the underlying future should no longer be polled.
     */
    public fun isTerminated(): Boolean
}

/**
 * Result value used by [TryFuture] to preserve success and failure payloads.
 */
public sealed interface Try<out T, out E> {
    public data class Ok<out T>(public val value: T) : Try<T, Nothing>

    public data class Err<out E>(public val error: E) : Try<Nothing, E>

    public companion object {
        public fun <T> ok(value: T): Try<T, Nothing> = Ok(value)

        public fun <E> err(error: E): Try<Nothing, E> = Err(error)
    }
}

/**
 * Convenience for futures that return [Try] values and includes adapters
 * tailored to those futures.
 */
public interface TryFuture<out T, out E> : Future<Try<T, E>> {
    /**
     * Poll this [TryFuture] as if it were a [Future].
     */
    public fun tryPoll(context: TaskContext): Poll<Try<T, E>> = poll(context)
}

/**
 * View a future yielding [Try] as a [TryFuture].
 */
public fun <T, E> Future<Try<T, E>>.asTryFuture(): TryFuture<T, E> {
    val delegate = this
    return object : TryFuture<T, E> {
        override fun poll(context: TaskContext): Poll<Try<T, E>> =
            delegate.poll(context)
    }
}

