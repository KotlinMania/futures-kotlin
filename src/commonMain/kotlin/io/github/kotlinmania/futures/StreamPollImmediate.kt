// port-lint: source futures-util/src/stream/poll_immediate.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [streamPollImmediate] function.
 *
 * It will never return [Poll.Pending].
 */
@HiddenFromObjC
public class StreamPollImmediate<T>(
    private var stream: Stream<T>?,
) : FusedStream<Poll<T>> {
    override fun isTerminated(): Boolean = stream == null

    override fun pollNext(context: TaskContext): Poll<Yield<Poll<T>>> {
        val st = stream ?: return Poll.ready(Yield.end())
        return when (val p = st.pollNext(context)) {
            is Poll.Pending -> Poll.ready(Yield.value(Poll.pending()))
            is Poll.Ready ->
                when (val y = p.value) {
                    is Yield.Value -> Poll.ready(Yield.value(Poll.ready(y.value)))
                    Yield.End -> {
                        stream = null
                        Poll.ready(Yield.end())
                    }
                }
        }
    }

    override fun sizeHint(): SizeHint =
        stream?.sizeHint() ?: SizeHint(0, 0)
}

/**
 * Creates a new stream that always immediately returns [Poll.Ready] when polled.
 */
@HiddenFromObjC
public fun <T> streamPollImmediate(stream: Stream<T>): StreamPollImmediate<T> =
    StreamPollImmediate(stream)
