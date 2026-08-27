// port-lint: source futures-util/src/stream/stream/select_next_some.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [selectNextSome] method.
 */
@HiddenFromObjC
public class SelectNextSome<T>(
    private val stream: Stream<T>,
) : FusedFuture<T> {
    public companion object {
        internal fun <T> new(stream: Stream<T>): SelectNextSome<T> = SelectNextSome(stream)
    }

    override fun isTerminated(): Boolean =
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun poll(context: TaskContext): Poll<T> {
        check(!isTerminated()) { "SelectNextSome polled after terminated" }

        return when (val p = stream.pollNext(context)) {
            is Poll.Ready -> {
                when (val y = p.value) {
                    is Yield.Value -> Poll.Ready(y.value)
                    Yield.End -> {
                        context.waker.wakeByRef()
                        Poll.Pending
                    }
                }
            }
            Poll.Pending -> Poll.Pending
        }
    }
}

/**
 * Creates a future that yields the next element produced by this stream when matched with select.
 */
@HiddenFromObjC
public fun <T> Stream<T>.selectNextSome(): SelectNextSome<T> = SelectNextSome.new(this)
