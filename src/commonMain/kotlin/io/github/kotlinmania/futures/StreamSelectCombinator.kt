// port-lint: source stream/select.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [streamSelect] round-robin selection function.
 */
@HiddenFromObjC
public class StreamSelectCombinator<T>(
    private val inner: SelectWithStrategy<T, Stream<T>, Stream<T>, Array<PollNext>>,
) : FusedStream<T> {
    public companion object {
        internal fun <T> new(stream1: Stream<T>, stream2: Stream<T>): StreamSelectCombinator<T> {
            val state = arrayOf(PollNext.Left)
            val inner = selectWithStrategy(stream1, stream2, state) { s ->
                val current = s[0]
                s[0] = current.toggle()
                current
            }
            return StreamSelectCombinator(inner)
        }
    }

    override fun isTerminated(): Boolean = inner.isTerminated()

    override fun pollNext(context: TaskContext): Poll<Yield<T>> = inner.pollNext(context)

    override fun sizeHint(): SizeHint = inner.sizeHint()
}

/**
 * Pulls items from both streams in a round-robin alternating fashion.
 */
@HiddenFromObjC
public fun <T> streamSelect(stream1: Stream<T>, stream2: Stream<T>): StreamSelectCombinator<T> =
    StreamSelectCombinator.new(stream1, stream2)
