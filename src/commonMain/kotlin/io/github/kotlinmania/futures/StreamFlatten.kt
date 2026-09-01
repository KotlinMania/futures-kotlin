// port-lint: source stream/stream/flatten.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [flatten] method.
 */
@HiddenFromObjC
public class Flatten<T>(
    private val stream: Stream<Stream<T>>,
) : FusedStream<T> {
    private var nextStream: Stream<T>? = null

    public companion object {
        internal fun <T> new(stream: Stream<Stream<T>>): Flatten<T> = Flatten(stream)
    }

    override fun isTerminated(): Boolean =
        nextStream == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        while (true) {
            val cur = nextStream
            if (cur != null) {
                when (val p = cur.pollNext(context)) {
                    is Poll.Ready -> {
                        when (p.value) {
                            is Yield.Value -> return p
                            Yield.End -> nextStream = null
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            } else {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> nextStream = y.value
                            Yield.End -> return Poll.Ready(Yield.End)
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            }
        }
    }

    override fun sizeHint(): SizeHint = SizeHint(0, null)
}

/**
 * Flattens a stream of streams into a single stream.
 */
@HiddenFromObjC
public fun <T> Stream<Stream<T>>.flatten(): Flatten<T> = Flatten.new(this)
