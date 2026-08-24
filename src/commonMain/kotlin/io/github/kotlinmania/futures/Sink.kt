// port-lint: source futures-sink/src/sink.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A value into which other values can be sent asynchronously.
 *
 * Basic examples include the sending side of channels, sockets, and pipes.
 * On top of these "primitive" sinks it is typical to layer additional
 * functionality such as buffering.
 *
 * Sending is asynchronous in the sense that a value may not be sent in its
 * entirety immediately; values are sent in a two-phase way: first by
 * initiating a send (after [pollReady] reports [SinkOutcome.Ready]), then by
 * polling for completion through [pollFlush]. This mirrors buffered writing
 * in synchronous code where writes succeed immediately but are flushed later.
 *
 * As with [Future] and [Stream], the [Sink] trait is built from a few core
 * required methods, on top of which higher-level combinators can be layered.
 *
 * Hidden from Swift Export: depends on the generic [Poll] and [SinkOutcome]
 * carriers whose bridges would emit `KotlinStdlib.kt` unchecked-cast
 * warnings that fail under `allWarningsAsErrors`. See
 * `SWIFT_EXPORT_ROLLOUT.md` gap #3.
 */
@HiddenFromObjC
public interface Sink<in Item, out E> {
    /**
     * Attempt to prepare the [Sink] to receive a value.
     *
     * This method must return [Poll.Ready] with [SinkOutcome.Ready] prior to
     * each call to [startSend]. If [Poll.Pending] is returned the current
     * task is registered to be notified (via [TaskContext.wakeByRef]) when
     * [pollReady] should be called again.
     *
     * In most cases, if the sink encounters an error, it will permanently
     * be unable to receive items.
     */
    public fun pollReady(context: TaskContext): Poll<SinkOutcome<E>>

    /**
     * Begin the process of sending [item] to the sink.
     *
     * Each call to this function must be preceded by a successful call to
     * [pollReady] which returned [SinkOutcome.Ready]. This method only
     * *begins* the send process; if the sink employs buffering, the item is
     * not fully processed until the buffer is flushed via [pollFlush] or
     * [pollClose].
     *
     * In most cases, if the sink encounters an error, it will permanently
     * be unable to receive items.
     */
    public fun startSend(item: Item): SinkOutcome<E>

    /**
     * Flush any remaining output from this sink.
     *
     * Returns [Poll.Ready] with [SinkOutcome.Ready] when no buffered items
     * remain; at that point all previous values sent via [startSend] have
     * been flushed. Returns [Poll.Pending] when there is more work left to
     * do, in which case the current task is scheduled (via
     * [TaskContext.wakeByRef]) to wake up when [pollFlush] should be called
     * again.
     */
    public fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>>

    /**
     * Flush any remaining output and close this sink.
     *
     * Returns [Poll.Ready] with [SinkOutcome.Ready] when no buffered items
     * remain and the sink has been successfully closed. Returns
     * [Poll.Pending] when there is more work left to do, in which case the
     * current task is scheduled (via [TaskContext.wakeByRef]) to wake up
     * when [pollClose] should be called again.
     *
     * If this function encounters an error, the sink should be considered
     * to have failed permanently, and no more [Sink] methods should be
     * called.
     */
    public fun pollClose(context: TaskContext): Poll<SinkOutcome<E>>
}

/**
 * Result of a [Sink] operation that either completes successfully ([Ready])
 * or surfaces an error payload ([Err]).
 *
 * Models upstream `Result<(), Self::Error>`. A dedicated sealed type is used
 * rather than [kotlin.Result] or [Try] of `Unit` so the public Swift Export
 * surface does not pull in `KotlinStdlib.kt` bridges for `Throwable.getStackTrace()`
 * (see `SWIFT_EXPORT_ROLLOUT.md` gap "Throwable / Result / stdlib-collection
 * from public API").
 *
 * Hidden from Swift Export: generic sealed family triggers Swift Export
 * gap #4 (Swift cannot reach `SinkOutcome.Err` via `as?`) and gap #3
 * (unchecked-cast bridges on the generic parameter).
 */
@HiddenFromObjC
public sealed interface SinkOutcome<out E> {
    /**
     * The operation completed successfully.
     */
    @HiddenFromObjC
    public data object Ready : SinkOutcome<Nothing>

    /**
     * The operation failed; [error] carries the sink's error payload.
     */
    @HiddenFromObjC
    public data class Err<out E>(
        public val error: E,
    ) : SinkOutcome<E>

    public companion object {
        @HiddenFromObjC
        public fun <E> ready(): SinkOutcome<E> = Ready

        @HiddenFromObjC
        public fun <E> err(error: E): SinkOutcome<E> = Err(error)
    }
}

/**
 * Fold over a [SinkOutcome]: handle the ready case or the error case.
 */
@HiddenFromObjC
public inline fun <E, R> SinkOutcome<E>.fold(onReady: () -> R, onErr: (E) -> R): R =
    when (this) {
        SinkOutcome.Ready -> onReady()
        is SinkOutcome.Err -> onErr(error)
    }

/**
 * Return the error payload, or `null` when the outcome is [SinkOutcome.Ready].
 */
@HiddenFromObjC
public fun <E> SinkOutcome<E>.errorOrNull(): E? =
    when (this) {
        SinkOutcome.Ready -> null
        is SinkOutcome.Err -> error
    }

/**
 * View a [MutableList] as a [Sink] that always accepts items immediately.
 *
 * Mirrors upstream's `impl<T> Sink<T> for alloc::vec::Vec<T>`: every poll
 * is ready, every send appends, and the sink never errors. The [E] type
 * parameter is fixed to [Nothing] because the operation is infallible —
 * the analogue of Rust's `core::convert::Infallible` used as the
 * upstream `Error` type.
 */
@HiddenFromObjC
public fun <T> MutableList<T>.asSink(): Sink<T, Nothing> {
    val backing = this
    return object : Sink<T, Nothing> {
        override fun pollReady(context: TaskContext): Poll<SinkOutcome<Nothing>> =
            Poll.ready(SinkOutcome.ready())

        override fun startSend(item: T): SinkOutcome<Nothing> {
            backing.add(item)
            return SinkOutcome.ready()
        }

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<Nothing>> =
            Poll.ready(SinkOutcome.ready())

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<Nothing>> =
            Poll.ready(SinkOutcome.ready())
    }
}

/**
 * View an [ArrayDeque] as a [Sink] that always accepts items immediately,
 * appending to the back of the deque.
 *
 * Mirrors upstream's `impl<T> Sink<T> for alloc::collections::VecDeque<T>`.
 */
@HiddenFromObjC
public fun <T> ArrayDeque<T>.asSink(): Sink<T, Nothing> {
    val backing = this
    return object : Sink<T, Nothing> {
        override fun pollReady(context: TaskContext): Poll<SinkOutcome<Nothing>> =
            Poll.ready(SinkOutcome.ready())

        override fun startSend(item: T): SinkOutcome<Nothing> {
            backing.addLast(item)
            return SinkOutcome.ready()
        }

        override fun pollFlush(context: TaskContext): Poll<SinkOutcome<Nothing>> =
            Poll.ready(SinkOutcome.ready())

        override fun pollClose(context: TaskContext): Poll<SinkOutcome<Nothing>> =
            Poll.ready(SinkOutcome.ready())
    }
}

// Upstream additionally provides adapter implementations for mutable references,
// pinned dereferences, and boxed sinks. In Kotlin, objects are already reference-
// counted and freely re-referenceable, so a Sink instance is directly reusable
// without additional wrappers.
