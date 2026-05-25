// port-lint: source futures-core/src/stream.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A stream of values produced asynchronously.
 *
 * If [Future] is an asynchronous version of a single value, then [Stream] is
 * the asynchronous version of an iterator: a sequence of value-producing
 * events that occur asynchronously to the caller.
 *
 * [pollNext] may be called even after a value has been produced. The stream
 * is exhausted when [Yield.End] is returned; calling [pollNext] again after
 * exhaustion is not required to behave sensibly.
 *
 * Hidden from Swift Export: depends on the generic [Poll] and [Yield]
 * carriers whose bridges would emit `KotlinStdlib.kt` unchecked-cast
 * warnings that fail under `allWarningsAsErrors`. See
 * `SWIFT_EXPORT_ROLLOUT.md` gap #3.
 */
@HiddenFromObjC
public interface Stream<out T> {
    /**
     * Pull out the next value of this stream, registering the current task
     * for wakeup if no value is yet available, and yielding [Yield.End] once
     * the stream is exhausted.
     */
    public fun pollNext(context: TaskContext): Poll<Yield<T>>

    /**
     * Returns the bounds on the remaining length of the stream as
     * `(lower, upper)`.
     *
     * The default returns `(0, null)`, which is always correct for any
     * stream. Implementations may override to support reservation
     * optimizations downstream, but the value must not be relied on for
     * memory-safety decisions.
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
    public data class Value<out T>(public val value: T) : Yield<T>

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
public data class SizeHint(public val lower: Int, public val upper: Int?)

/**
 * A stream which tracks whether or not it should no longer be polled.
 *
 * [isTerminated] returning `true` typically follows [Yield.End] from
 * [pollNext], but may also indicate a stream that has become inactive for
 * other reasons and should be dropped rather than polled further.
 */
@HiddenFromObjC
public interface FusedStream<out T> : Stream<T> {
    public fun isTerminated(): Boolean
}

/**
 * Convenience view of a [Stream] yielding [Try] values, mirroring
 * `futures::stream::TryStream` upstream.
 *
 * Hidden from Swift Export: same generic-sealed bridge concerns as [Stream].
 */
@HiddenFromObjC
public interface TryStream<out T, out E> : Stream<Try<T, E>> {
    /**
     * Poll this [TryStream] as if it were a [Stream]. The default delegates
     * to [pollNext]; provided so that callers operating against the
     * try-shaped API surface have a name that signals the result type.
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
