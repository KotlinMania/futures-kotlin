// port-lint: source futures-util/src/stream/stream/next.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [next] method.
 */
@HiddenFromObjC
public class Next<T>(
    private val stream: Stream<T>,
) : FusedFuture<T?> {
    public companion object {
        internal fun <T> new(stream: Stream<T>): Next<T> = Next(stream)
    }

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun poll(context: TaskContext): Poll<T?> {
        return when (val p = stream.pollNext(context)) {
            is Poll.Ready -> {
                when (val y = p.value) {
                    is Yield.Value -> Poll.Ready(y.value)
                    Yield.End -> Poll.Ready(null)
                }
            }
            Poll.Pending -> Poll.Pending
        }
    }
}

/**
 * Creates a future that resolves to the next item of this stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.next(): Next<T> = Next.new(this)
