// port-lint: source stream/try_stream/try_buffer_unordered.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [tryBufferUnordered] method.
 */
@HiddenFromObjC
public class TryBufferUnordered<T, E>(
    stream: Stream<Try<Future<Try<T, E>>, E>>,
    private val capacity: Int,
) : FusedStream<Try<T, E>> {
    private val stream: StreamFuse<Try<Future<Try<T, E>>, E>> = stream.fuse()
    private val inProgressQueue = FuturesUnordered<Try<T, E>>()

    init {
        require(capacity >= 0) { "capacity must not be negative" }
    }

    public companion object {
        internal fun <T, E> new(
            stream: Stream<Try<Future<Try<T, E>>, E>>,
            capacity: Int,
        ): TryBufferUnordered<T, E> = TryBufferUnordered(stream, capacity)
    }

    override fun isTerminated(): Boolean = stream.isDone() && inProgressQueue.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> {
        // Fill the queue first, propagating input-stream errors immediately.
        while (inProgressQueue.len() < capacity) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            when (val item = y.value) {
                                is Try.Ok -> {
                                    inProgressQueue.push(item.value)
                                }

                                is Try.Err -> {
                                    return Poll.Ready(Yield.Value(Try.Err(item.error)))
                                }
                            }
                        }

                        Yield.End -> {
                            break
                        }
                    }
                }

                Poll.Pending -> {
                    break
                }
            }
        }

        when (val result = inProgressQueue.pollNext(context)) {
            is Poll.Ready -> {
                if (result.value is Yield.Value) return result
            }

            Poll.Pending -> {
                return Poll.Pending
            }
        }

        // An empty queue is not terminal while the input can supply more futures.
        return if (stream.isDone()) Poll.Ready(Yield.End) else Poll.Pending
    }
}

/**
 * An adapter for caching the output of a stream of try-futures, running up to `capacity` futures concurrently in completion order.
 */
@HiddenFromObjC
public fun <T, E> TryStream<Future<Try<T, E>>, E>.tryBufferUnordered(capacity: Int): TryBufferUnordered<T, E> =
    TryBufferUnordered.new(this, capacity)
