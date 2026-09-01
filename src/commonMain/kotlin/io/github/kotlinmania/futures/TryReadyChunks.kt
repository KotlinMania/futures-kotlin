// port-lint: source stream/try_stream/try_ready_chunks.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Error indicating that while a ready chunk was being collected the inner stream produced an error.
 *
 * Contains all items that were collected before an error occurred, and the stream error itself.
 */
@HiddenFromObjC
public data class TryReadyChunksError<out T, out E>(
    public val items: List<T>,
    public val error: E,
) {
    override fun toString(): String = "TryReadyChunksError(items=$items, error=$error)"
}

/**
 * Stream for the [tryReadyChunks] method.
 */
@HiddenFromObjC
public class TryReadyChunks<T, E>(
    private val stream: Stream<Try<T, E>>,
    private val capacity: Int,
) : FusedStream<Try<List<T>, TryReadyChunksError<T, E>>> {
    private var isStreamTerminated: Boolean = false

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    public companion object {
        internal fun <T, E> new(stream: Stream<Try<T, E>>, capacity: Int): TryReadyChunks<T, E> =
            TryReadyChunks(stream, capacity)
    }

    override fun isTerminated(): Boolean = isStreamTerminated

    override fun pollNext(context: TaskContext): Poll<Yield<Try<List<T>, TryReadyChunksError<T, E>>>> {
        val items = mutableListOf<T>()

        while (true) {
            when (val pollResult = stream.pollNext(context)) {
                Poll.Pending -> {
                    return if (items.isEmpty()) {
                        Poll.Pending
                    } else {
                        Poll.Ready(Yield.Value(Try.Ok(items)))
                    }
                }
                is Poll.Ready -> {
                    when (val y = pollResult.value) {
                        is Yield.Value -> {
                            when (val item = y.value) {
                                is Try.Ok -> {
                                    items.add(item.value)
                                    if (items.size >= capacity) {
                                        return Poll.Ready(Yield.Value(Try.Ok(items)))
                                    }
                                }
                                is Try.Err -> {
                                    return Poll.Ready(Yield.Value(Try.Err(TryReadyChunksError(items, item.error))))
                                }
                            }
                        }
                        Yield.End -> {
                            isStreamTerminated = true
                            return if (items.isNotEmpty()) {
                                Poll.Ready(Yield.Value(Try.Ok(items)))
                            } else {
                                Poll.Ready(Yield.End)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun sizeHint(): SizeHint {
        val hint = stream.sizeHint()
        val lower = hint.lower / capacity
        val upper = hint.upper?.let { it / capacity }
        return SizeHint(lower, upper)
    }
}

/**
 * An adapter for chunks of at most `capacity` items on a [TryStream] that are currently ready.
 */
@HiddenFromObjC
public fun <T, E> TryStream<T, E>.tryReadyChunks(capacity: Int): TryReadyChunks<T, E> =
    TryReadyChunks.new(this, capacity)
