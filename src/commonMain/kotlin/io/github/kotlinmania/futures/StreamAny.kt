// port-lint: source futures-util/src/stream/stream/any.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [any] method.
 */
@HiddenFromObjC
public class StreamAny<T>(
    private val stream: Stream<T>,
    private val predicate: (T) -> Boolean,
) : FusedFuture<Boolean> {
    private var done = false

    public companion object {
        internal fun <T> new(stream: Stream<T>, predicate: (T) -> Boolean): StreamAny<T> = StreamAny(stream, predicate)
    }

    override fun isTerminated(): Boolean = done

    override fun poll(context: TaskContext): Poll<Boolean> {
        if (done) throw RuntimeException("Any polled after completion")
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            if (predicate(y.value)) {
                                done = true
                                return Poll.Ready(true)
                            }
                        }
                        Yield.End -> {
                            done = true
                            return Poll.Ready(false)
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }
}

/**
 * Tests if any element of the stream matches a predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.any(predicate: (T) -> Boolean): StreamAny<T> = StreamAny.new(this, predicate)
