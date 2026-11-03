// port-lint: source stream/try_stream/into_stream.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [intoStream] method.
 */
@HiddenFromObjC
public class IntoStream<T, E>(
    private val stream: TryStream<T, E>,
) : FusedStream<Try<T, E>> {
    public companion object {
        internal fun <T, E> new(stream: TryStream<T, E>): IntoStream<T, E> = IntoStream(stream)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): TryStream<T, E> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): TryStream<T, E> = stream

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> =
        stream.tryPollNext(context)

    override fun sizeHint(): SizeHint = stream.sizeHint()
}

/**
 * Wraps a [TryStream] into a [Stream] compatible combinator.
 */
@HiddenFromObjC
public fun <T, E> TryStream<T, E>.intoStream(): IntoStream<T, E> = IntoStream.new(this)
