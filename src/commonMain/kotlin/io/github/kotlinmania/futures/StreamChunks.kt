// port-lint: source futures-util/src/stream/stream/chunks.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [chunks] method.
 */
@HiddenFromObjC
public class Chunks<T>(
    private val stream: Stream<T>,
    private val capacity: Int,
) : FusedStream<List<T>> {
    private val items = mutableListOf<T>()
    private var streamDone = false

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    public companion object {
        internal fun <T> new(stream: Stream<T>, capacity: Int): Chunks<T> = Chunks(stream, capacity)
    }

    override fun isTerminated(): Boolean = streamDone && items.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<List<T>>> {
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            items.add(y.value)
                            if (items.size >= capacity) {
                                val chunk = items.toList()
                                items.clear()
                                return Poll.Ready(Yield.Value(chunk))
                            }
                        }
                        Yield.End -> {
                            streamDone = true
                            return if (items.isNotEmpty()) {
                                val chunk = items.toList()
                                items.clear()
                                Poll.Ready(Yield.Value(chunk))
                            } else {
                                Poll.Ready(Yield.End)
                            }
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }

    override fun sizeHint(): SizeHint {
        val chunkLen = if (items.isNotEmpty()) 1 else 0
        val hint = stream.sizeHint()
        val lower = (hint.lower / capacity) + chunkLen
        val upper = hint.upper?.let { (it / capacity) + chunkLen }
        return SizeHint(lower, upper)
    }
}

/**
 * An adaptor for chunks of elements in a stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.chunks(capacity: Int): Chunks<T> = Chunks.new(this, capacity)
