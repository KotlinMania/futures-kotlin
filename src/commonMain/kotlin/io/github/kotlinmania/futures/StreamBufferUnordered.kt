// port-lint: source stream/stream/buffer_unordered.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [bufferUnordered] method.
 */
@HiddenFromObjC
public class BufferUnordered<T>(
    private val stream: Stream<Future<T>>,
    private val capacity: Int,
) : FusedStream<T> {
    private val inFlight = mutableListOf<Future<T>>()
    private var streamDone: Boolean = false

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    public companion object {
        internal fun <T> new(stream: Stream<Future<T>>, capacity: Int): BufferUnordered<T> =
            BufferUnordered(stream, capacity)
    }

    override fun isTerminated(): Boolean = streamDone && inFlight.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        while (!streamDone && inFlight.size < capacity) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> inFlight.add(y.value)
                        Yield.End -> streamDone = true
                    }
                }
                Poll.Pending -> break
            }
        }

        var i = 0
        while (i < inFlight.size) {
            when (val p = inFlight[i].poll(context)) {
                is Poll.Ready -> {
                    inFlight.removeAt(i)
                    return Poll.Ready(Yield.Value(p.value))
                }
                Poll.Pending -> i++
            }
        }

        return if (streamDone && inFlight.isEmpty()) {
            Poll.Ready(Yield.End)
        } else {
            Poll.Pending
        }
    }

    override fun sizeHint(): SizeHint {
        val flightLen = inFlight.size
        val hint = stream.sizeHint()
        val lower = (hint.lower.toLong() + flightLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val upper = hint.upper?.let { (it.toLong() + flightLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        return SizeHint(lower, upper)
    }
}

/**
 * Runs up to [capacity] futures concurrently and yields their results in order of completion.
 */
@HiddenFromObjC
public fun <T> Stream<Future<T>>.bufferUnordered(capacity: Int): BufferUnordered<T> =
    BufferUnordered.new(this, capacity)
