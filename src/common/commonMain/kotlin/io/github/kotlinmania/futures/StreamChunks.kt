// port-lint: source stream/stream/chunks.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [chunks] method.
 */
@HiddenFromObjC
public class Chunks<T>(
    stream: Stream<T>,
    private val capacity: Int,
) : FusedStream<List<T>> {
    private val items = mutableListOf<T>()
    private val stream: StreamFuse<T> = stream.fuse()

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    public interface Item

    public interface Error

    public companion object {
        public fun <T> new(stream: Stream<T>, capacity: Int): Chunks<T> = Chunks(stream, capacity)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream.getRef()

    /**
     * Acquires a mutable reference to the underlying stream.
     */
    public fun getMut(): Stream<T> = stream.getRef()

    /**
     * Acquires a pinned mutable reference to the underlying stream.
     */
    public fun getPinMut(): Stream<T> = stream.getRef()

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream.intoInner()

    public fun fmt(): String = "Chunks"

    override fun toString(): String = fmt()

    private fun take(): List<T> {
        val chunk = items.toList()
        items.clear()
        return chunk
    }

    override fun isTerminated(): Boolean = stream.isTerminated() && items.isEmpty()

    override fun pollNext(context: TaskContext): Poll<Yield<List<T>>> {
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            items.add(y.value)
                            if (items.size >= capacity) {
                                return Poll.Ready(Yield.Value(take()))
                            }
                        }
                        Yield.End -> {
                            return if (items.isNotEmpty()) {
                                Poll.Ready(Yield.Value(take()))
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
        val lower = minOf(Int.MAX_VALUE.toLong(), (hint.lower / capacity).toLong() + chunkLen).toInt()
        val upper = hint.upper?.let { if (it > Int.MAX_VALUE - chunkLen) null else it + chunkLen }
        return SizeHint(lower, upper)
    }
}

/**
 * An adaptor for chunks of elements in a stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.chunks(capacity: Int): Chunks<T> = Chunks.new(this, capacity)
