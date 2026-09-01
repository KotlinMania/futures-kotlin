// port-lint: source stream/try_stream/try_buffer_unordered.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [tryBufferUnordered] method.
 */
@HiddenFromObjC
public class TryBufferUnordered<T, E>(
    private val stream: Stream<Try<Future<Try<T, E>>, E>>,
    private val capacity: Int,
) : FusedStream<Try<T, E>> {
    private val inFlight = mutableListOf<Future<Try<T, E>>>()
    private var streamDone: Boolean = false

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    public companion object {
        internal fun <T, E> new(
            stream: Stream<Try<Future<Try<T, E>>, E>>,
            capacity: Int,
        ): TryBufferUnordered<T, E> = TryBufferUnordered(stream, capacity)
    }

    override fun isTerminated(): Boolean = streamDone && inFlight.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> {
        while (!streamDone && inFlight.size < capacity) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            when (val item = y.value) {
                                is Try.Ok -> inFlight.add(item.value)
                                is Try.Err -> return Poll.Ready(Yield.Value(Try.Err(item.error)))
                            }
                        }
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
 * An adapter for caching the output of a stream of try-futures, running up to `capacity` futures concurrently in completion order.
 */
@HiddenFromObjC
public fun <T, E> TryStream<Future<Try<T, E>>, E>.tryBufferUnordered(capacity: Int): TryBufferUnordered<T, E> =
    TryBufferUnordered.new(this, capacity)
