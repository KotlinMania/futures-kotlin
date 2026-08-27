// port-lint: source futures-util/src/stream/stream/buffered.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [buffered] method.
 */
@HiddenFromObjC
public class Buffered<T>(
    private val stream: Stream<Future<T>>,
    private val capacity: Int,
) : FusedStream<T> {
    private val queue = mutableListOf<Future<T>>()
    private var streamDone: Boolean = false

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    public companion object {
        internal fun <T> new(stream: Stream<Future<T>>, capacity: Int): Buffered<T> =
            Buffered(stream, capacity)
    }

    override fun isTerminated(): Boolean = streamDone && queue.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        while (!streamDone && queue.size < capacity) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> queue.add(y.value)
                        Yield.End -> streamDone = true
                    }
                }
                Poll.Pending -> break
            }
        }

        if (queue.isNotEmpty()) {
            when (val p = queue.first().poll(context)) {
                is Poll.Ready -> {
                    queue.removeAt(0)
                    return Poll.Ready(Yield.Value(p.value))
                }
                Poll.Pending -> {}
            }
        }

        return if (streamDone && queue.isEmpty()) {
            Poll.Ready(Yield.End)
        } else {
            Poll.Pending
        }
    }

    override fun sizeHint(): SizeHint {
        val qLen = queue.size
        val hint = stream.sizeHint()
        val lower = (hint.lower.toLong() + qLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val upper = hint.upper?.let { (it.toLong() + qLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        return SizeHint(lower, upper)
    }
}

/**
 * Runs up to [capacity] futures concurrently and yields their results in order of completion.
 */
@HiddenFromObjC
public fun <T> Stream<Future<T>>.buffered(capacity: Int): Buffered<T> = Buffered.new(this, capacity)
