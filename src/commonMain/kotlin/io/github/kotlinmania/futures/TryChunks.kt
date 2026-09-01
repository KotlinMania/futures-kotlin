// port-lint: source stream/try_stream/try_chunks.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Error indicating that while a chunk was being collected the inner stream produced an error.
 *
 * Contains all items that were collected before an error occurred, and the stream error itself.
 */
@HiddenFromObjC
public data class TryChunksError<out T, out E>(
    public val items: List<T>,
    public val error: E,
) {
    override fun toString(): String = "TryChunksError(items=$items, error=$error)"
}

/**
 * Stream for the [tryChunks] method.
 */
@HiddenFromObjC
public class TryChunks<T, E>(
    private val stream: Stream<Try<T, E>>,
    private val capacity: Int,
) : FusedStream<Try<List<T>, TryChunksError<T, E>>> {
    private val items = mutableListOf<T>()
    private var isStreamTerminated: Boolean = false

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    public companion object {
        internal fun <T, E> new(stream: Stream<Try<T, E>>, capacity: Int): TryChunks<T, E> =
            TryChunks(stream, capacity)
    }

    private fun takeItems(): List<T> {
        val copy = items.toList()
        items.clear()
        return copy
    }

    override fun isTerminated(): Boolean = isStreamTerminated && items.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<Try<List<T>, TryChunksError<T, E>>>> {
        while (true) {
            when (val pollResult = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = pollResult.value) {
                        is Yield.Value -> {
                            when (val item = y.value) {
                                is Try.Ok -> {
                                    items.add(item.value)
                                    if (items.size >= capacity) {
                                        return Poll.Ready(Yield.Value(Try.Ok(takeItems())))
                                    }
                                }
                                is Try.Err -> {
                                    return Poll.Ready(Yield.Value(Try.Err(TryChunksError(takeItems(), item.error))))
                                }
                            }
                        }
                        Yield.End -> {
                            isStreamTerminated = true
                            return if (items.isNotEmpty()) {
                                Poll.Ready(Yield.Value(Try.Ok(takeItems())))
                            } else {
                                Poll.Ready(Yield.End)
                            }
                        }
                    }
                }
                Poll.Pending -> {
                    return Poll.Pending
                }
            }
        }
    }

    override fun sizeHint(): SizeHint {
        val chunkLen = if (items.isNotEmpty()) 1 else 0
        val hint = stream.sizeHint()
        val lower = (hint.lower / capacity).coerceAtMost(Int.MAX_VALUE - chunkLen) + chunkLen
        val upper = hint.upper?.let { (it / capacity).coerceAtMost(Int.MAX_VALUE - chunkLen) + chunkLen }
        return SizeHint(lower, upper)
    }
}

/**
 * An adapter for chunks of at most `capacity` items on a [TryStream].
 */
@HiddenFromObjC
public fun <T, E> TryStream<T, E>.tryChunks(capacity: Int): TryChunks<T, E> =
    TryChunks.new(this, capacity)
