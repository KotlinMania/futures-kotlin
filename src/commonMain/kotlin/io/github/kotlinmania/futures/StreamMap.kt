// port-lint: source futures-util/src/stream/stream/map.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [map] method.
 */
@HiddenFromObjC
public class StreamMap<T, R>(
    private val stream: Stream<T>,
    private val transform: (T) -> R,
) : FusedStream<R> {
    public companion object {
        internal fun <T, R> new(stream: Stream<T>, transform: (T) -> R): StreamMap<T, R> = StreamMap(stream, transform)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun pollNext(context: TaskContext): Poll<Yield<R>> =
        when (val p = stream.pollNext(context)) {
            is Poll.Ready -> {
                when (val y = p.value) {
                    is Yield.Value -> Poll.Ready(Yield.Value(transform(y.value)))
                    Yield.End -> Poll.Ready(Yield.End)
                }
            }
            Poll.Pending -> Poll.Pending
        }

    override fun sizeHint(): SizeHint = stream.sizeHint()
}

/**
 * Maps this stream's items to a different type.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.map(transform: (T) -> R): StreamMap<T, R> = StreamMap.new(this, transform)
