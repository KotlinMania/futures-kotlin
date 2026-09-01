// port-lint: source stream/try_stream/try_buffered.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [tryBuffered] method.
 */
@HiddenFromObjC
public class TryBuffered<T, E>(
    private val stream: Stream<Try<Future<Try<T, E>>, E>>,
    private val capacity: Int,
) : FusedStream<Try<T, E>> {
    private val inProgressQueue = mutableListOf<Future<Try<T, E>>>()
    private var streamDone: Boolean = false

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    public companion object {
        internal fun <T, E> new(
            stream: Stream<Try<Future<Try<T, E>>, E>>,
            capacity: Int,
        ): TryBuffered<T, E> = TryBuffered(stream, capacity)
    }

    override fun isTerminated(): Boolean = streamDone && inProgressQueue.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> {
        while (!streamDone && inProgressQueue.size < capacity) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            when (val item = y.value) {
                                is Try.Ok -> inProgressQueue.add(item.value)
                                is Try.Err -> return Poll.Ready(Yield.Value(Try.Err(item.error)))
                            }
                        }
                        Yield.End -> streamDone = true
                    }
                }
                Poll.Pending -> break
            }
        }

        if (inProgressQueue.isNotEmpty()) {
            when (val p = inProgressQueue.first().poll(context)) {
                is Poll.Ready -> {
                    inProgressQueue.removeAt(0)
                    return Poll.Ready(Yield.Value(p.value))
                }
                Poll.Pending -> return Poll.Pending
            }
        }

        return if (streamDone && inProgressQueue.isEmpty()) {
            Poll.Ready(Yield.End)
        } else {
            Poll.Pending
        }
    }

    override fun sizeHint(): SizeHint {
        val qLen = inProgressQueue.size
        val hint = stream.sizeHint()
        val lower = (hint.lower.toLong() + qLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val upper = hint.upper?.let { (it.toLong() + qLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        return SizeHint(lower, upper)
    }
}

/**
 * An adapter for caching the output of a stream of try-futures, running up to `capacity` futures concurrently in order.
 */
@HiddenFromObjC
public fun <T, E> TryStream<Future<Try<T, E>>, E>.tryBuffered(capacity: Int): TryBuffered<T, E> =
    TryBuffered.new(this, capacity)
