// port-lint: source stream/stream/buffer_unordered.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [bufferUnordered] method.
 */
@HiddenFromObjC
public class BufferUnordered<T>(
    stream: Stream<Future<T>>,
    private val capacity: Int,
) : FusedStream<T> {
    private val stream: StreamFuse<Future<T>> = stream.fuse()
    private val queue = FuturesUnordered<T>()

    init {
        require(capacity >= 0) { "capacity must not be negative" }
    }

    public companion object {
        internal fun <T> new(stream: Stream<Future<T>>, capacity: Int): BufferUnordered<T> =
            BufferUnordered(stream, capacity)
    }

    override fun isTerminated(): Boolean = queue.isTerminated() && stream.isTerminated()

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        // Fill the in-progress queue before attempting to yield its next output.
        while (queue.len() < capacity) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> queue.push(y.value)
                        Yield.End -> break
                    }
                }

                Poll.Pending -> break
            }
        }

        when (val result = queue.pollNext(context)) {
            is Poll.Ready -> {
                if (result.value is Yield.Value) return result
            }

            Poll.Pending -> {
                return Poll.Pending
            }
        }

        // An empty queue does not end this stream while more futures may arrive.
        return if (stream.isDone()) Poll.Ready(Yield.End) else Poll.Pending
    }

    override fun sizeHint(): SizeHint {
        val qLen = queue.len()
        val hint = stream.sizeHint()
        val lower = (hint.lower.toLong() + qLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val upper = hint.upper?.let { if (it > Int.MAX_VALUE - qLen) null else it + qLen }
        return SizeHint(lower, upper)
    }
}

/**
 * Runs up to [capacity] futures concurrently and yields their results in order of completion.
 */
@HiddenFromObjC
public fun <T> Stream<Future<T>>.bufferUnordered(capacity: Int): BufferUnordered<T> = BufferUnordered.new(this, capacity)
