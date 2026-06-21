// port-lint: source futures-core/src/future.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A single eventual value produced by an asynchronous computation.
 *
 * Hidden from Swift Export: depends on the generic `Poll<T>` carrier whose
 * bridge would emit `KotlinStdlib.kt` unchecked-cast warnings that fail
 * under `allWarningsAsErrors`. See `SWIFT_EXPORT_ROLLOUT.md` gap #3.
 */
@HiddenFromObjC
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
@HiddenFromObjC
public interface FusedFuture<out T> : Future<T> {
    /**
     * Returns true when the underlying future should no longer be polled.
     */
    public fun isTerminated(): Boolean
}

/**
 * Result value used by [TryFuture] to preserve success and failure payloads.
 *
 * Hidden from Swift Export: generic sealed family with generic subclasses
 * triggers gap #4 (Swift cannot reach `Try.Ok` / `Try.Err` via `as?`) and
 * gap #3 (unchecked-cast bridges on the generic parameters).
 */
@HiddenFromObjC
public sealed interface Try<out T, out E> {
    @HiddenFromObjC
    public data class Ok<out T>(
        public val value: T,
    ) : Try<T, Nothing>

    @HiddenFromObjC
    public data class Err<out E>(
        public val error: E,
    ) : Try<Nothing, E>

    public companion object {
        @HiddenFromObjC
        public fun <T> ok(value: T): Try<T, Nothing> = Ok(value)

        @HiddenFromObjC
        public fun <E> err(error: E): Try<Nothing, E> = Err(error)
    }
}

/**
 * Convenience for futures that return [Try] values and includes adapters
 * tailored to those futures.
 */
@HiddenFromObjC
public interface TryFuture<out T, out E> : Future<Try<T, E>> {
    /**
     * Poll this [TryFuture] as if it were a [Future].
     */
    public fun tryPoll(context: TaskContext): Poll<Try<T, E>> = poll(context)
}

/**
 * View a future yielding [Try] as a [TryFuture].
 */
@HiddenFromObjC
public fun <T, E> Future<Try<T, E>>.asTryFuture(): TryFuture<T, E> {
    val delegate = this
    return object : TryFuture<T, E> {
        override fun poll(context: TaskContext): Poll<Try<T, E>> =
            delegate.poll(context)
    }
}
