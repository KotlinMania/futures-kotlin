// port-lint: source futures-core/src/stream.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * An owned dynamically typed [Stream] for use in cases where you can't
 * statically type your result or need to add some indirection.
 */
public typealias BoxStream<T> = Stream<T>

/**
 * `BoxStream`, but without the thread-safety requirement.
 */
public typealias LocalBoxStream<T> = Stream<T>

/**
 * A stream of values produced asynchronously.
 *
 * If [Future] is an asynchronous version of a single value, then [Stream] is
 * an asynchronous version of an iterator: a sequence of value-producing
 * events that occur asynchronously to the caller.
 *
 * The interface is modeled after [Future], but allows [pollNext] to be called
 * even after a value has been produced, yielding [Yield.End] once the stream has
 * been fully exhausted.
 */
@HiddenFromObjC
public interface Stream<out T> {
    /**
     * Attempt to pull out the next value of this stream, registering the
     * current task for wakeup if the value is not yet available, and returning
     * [Yield.End] if the stream is exhausted.
     *
     * # Return value
     *
     * There are several possible return values, each indicating a distinct
     * stream state:
     *
     * - [Poll.Pending] means that this stream's next value is not ready
     *   yet. Implementations will ensure that the current task will be notified
     *   when the next value may be ready.
     *
     * - `Poll.Ready(Yield.Value(val))` means that the stream has successfully
     *   produced a value, `val`, and may produce further values on subsequent
     *   [pollNext] calls.
     *
     * - `Poll.Ready(Yield.End)` means that the stream has terminated, and
     *   [pollNext] should not be invoked again.
     */
    public fun pollNext(context: TaskContext): Poll<Yield<T>>

    /**
     * Returns the bounds on the remaining length of the stream.
     *
     * Specifically, [sizeHint] returns a [SizeHint] where [SizeHint.lower]
     * is the lower bound, and [SizeHint.upper] is the upper bound.
     *
     * A `null` upper bound means that either there is no known upper bound, or the
     * upper bound is larger than [Int.MAX_VALUE].
     *
     * # Implementation notes
     *
     * It is not enforced that a stream implementation yields the declared
     * number of elements. A buggy stream may yield less than the lower bound
     * or more than the upper bound of elements.
     *
     * [sizeHint] is primarily intended to be used for optimizations such as
     * reserving space for the elements of the stream, but must not be
     * trusted to e.g., omit bounds checks.
     *
     * The default implementation returns `SizeHint(0, null)` which is correct for any
     * stream.
     */
    public fun sizeHint(): SizeHint = DEFAULT_SIZE_HINT

    private companion object {
        private val DEFAULT_SIZE_HINT = SizeHint(lower = 0, upper = null)
    }
}

/**
 * Either a yielded value from a [Stream] or the end-of-stream sentinel.
 *
 * Models upstream `Option<Item>`: [Value] corresponds to `Some(item)` and
 * [End] corresponds to `None`. A dedicated sealed type is used (rather than
 * a nullable Kotlin type) so that streams whose item type is itself nullable
 * remain unambiguous.
 *
 * Hidden from Swift Export: generic sealed family with a generic subclass
 * triggers gap #4 + gap #3.
 */
@HiddenFromObjC
public sealed interface Yield<out T> {
    @HiddenFromObjC
    public data class Value<out T>(
        public val value: T,
    ) : Yield<T>

    @HiddenFromObjC
    public data object End : Yield<Nothing>

    public companion object {
        @HiddenFromObjC
        public fun <T> value(value: T): Yield<T> = Value(value)

        @HiddenFromObjC
        public fun <T> end(): Yield<T> = End
    }
}

/**
 * Bounds on the remaining length of a [Stream]: `lower` is a guaranteed
 * lower bound, `upper` is the upper bound or `null` when unknown / larger
 * than [Int.MAX_VALUE].
 *
 * Named record class rather than `Pair<Int, Int?>` to keep the public API
 * strongly typed across the Swift Export boundary (see project goal in
 * `AGENTS.md` §4).
 */
public data class SizeHint(
    public val lower: Int,
    public val upper: Int?,
)

/**
 * A stream which tracks whether or not the underlying stream
 * should no longer be polled.
 *
 * `isTerminated` will return `true` if a future should no longer be polled.
 * Usually, this state occurs after `pollNext` (or `tryPollNext`) returned
 * `Poll.Ready(Yield.End)`. However, `isTerminated` may also return `true` if a
 * stream has become inactive and can no longer make progress and should be
 * ignored or dropped rather than being polled again.
 */
@HiddenFromObjC
public interface FusedStream<out T> : Stream<T> {
    /**
     * Returns `true` if the stream should no longer be polled.
     */
    public fun isTerminated(): Boolean
}

/**
 * A convenience for streams that return [Try] values that includes
 * a variety of adapters tailored to such futures.
 */
@HiddenFromObjC
public interface TryStream<out T, out E> : Stream<Try<T, E>> {
    /**
     * Poll this `TryStream` as if it were a `Stream`.
     *
     * This method is a stopgap for a compiler limitation that prevents us from
     * directly inheriting from the `Stream` trait; in the future it won't be
     * needed.
     */
    public fun tryPollNext(context: TaskContext): Poll<Yield<Try<T, E>>> =
        pollNext(context)
}

/**
 * View a stream yielding [Try] as a [TryStream].
 *
 * Mirrors upstream's blanket `impl<S, T, E> TryStream for S where S: Stream<Item = Result<T, E>>`.
 */
@HiddenFromObjC
public fun <T, E> Stream<Try<T, E>>.asTryStream(): TryStream<T, E> {
    val delegate = this
    return object : TryStream<T, E> {
        override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> =
            delegate.pollNext(context)

        override fun sizeHint(): SizeHint = delegate.sizeHint()
    }
}

/**
 * Fold over a [Yield]: handle a yielded value or the end-of-stream sentinel.
 */
@HiddenFromObjC
public inline fun <T, R> Yield<T>.fold(onValue: (T) -> R, onEnd: () -> R): R =
    when (this) {
        is Yield.Value -> onValue(value)
        Yield.End -> onEnd()
    }

/**
 * Return the yielded value, or `null` if the stream has ended.
 */
@HiddenFromObjC
public fun <T> Yield<T>.valueOrNull(): T? =
    when (this) {
        is Yield.Value -> value
        Yield.End -> null
    }

/**
 * Type alias for Stream Item type.
 */
public typealias Item = Any?

/**
 * Sealed marker trait for TryStream implementation constraints.
 */
@HiddenFromObjC
public sealed interface TryStreamSealed
