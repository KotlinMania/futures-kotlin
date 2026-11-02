// port-lint: source stream/stream/ready_chunks.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [readyChunks] method.
 */
@HiddenFromObjC
public class ReadyChunks<T>(
    stream: Stream<T>,
    private val capacity: Int,
) : FusedStream<List<T>> {
    private val stream: StreamFuse<T> = stream.fuse()

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    public companion object {
        internal fun <T> new(stream: Stream<T>, capacity: Int): ReadyChunks<T> = ReadyChunks(stream, capacity)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream.getRef()

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream.intoInner()

    override fun isTerminated(): Boolean = stream.isTerminated()

    override fun pollNext(context: TaskContext): Poll<Yield<List<T>>> {
        val items = mutableListOf<T>()
        while (items.size < capacity) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            items.add(y.value)
                        }
                        Yield.End -> {
                            return if (items.isNotEmpty()) {
                                Poll.Ready(Yield.Value(items))
                            } else {
                                Poll.Ready(Yield.End)
                            }
                        }
                    }
                }
                Poll.Pending -> {
                    return if (items.isNotEmpty()) {
                        Poll.Ready(Yield.Value(items))
                    } else {
                        Poll.Pending
                    }
                }
            }
        }
        return Poll.Ready(Yield.Value(items))
    }

    override fun sizeHint(): SizeHint {
        val hint = stream.sizeHint()
        val lower = hint.lower / capacity
        return SizeHint(lower, hint.upper)
    }
}

/**
 * An adaptor for chunks of elements in a stream that are ready without waiting.
 */
@HiddenFromObjC
public fun <T> Stream<T>.readyChunks(capacity: Int): ReadyChunks<T> = ReadyChunks.new(this, capacity)
