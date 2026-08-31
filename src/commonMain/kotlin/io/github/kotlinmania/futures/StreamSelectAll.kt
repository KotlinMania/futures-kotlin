// port-lint: source futures-util/src/stream/select_all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * An unbounded set of streams.
 *
 * This combinator provides the ability to maintain a set of streams and drive them all to completion.
 */
@HiddenFromObjC
public class SelectAllStream<T>(
    private val streams: MutableList<Stream<T>> = mutableListOf(),
) : FusedStream<T> {
    private var terminated: Boolean = false

    public fun push(stream: Stream<T>) {
        streams.add(stream)
        terminated = false
    }

    public fun size(): Int = streams.size

    public fun len(): Int = streams.size

    public fun isEmpty(): Boolean = streams.isEmpty()

    public fun clear() {
        streams.clear()
        terminated = false
    }

    public fun iter(): List<Stream<T>> = streams.toList()

    override fun isTerminated(): Boolean = terminated

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        var i = 0
        while (i < streams.size) {
            val stream = streams[i]
            when (val p = stream.pollNext(context)) {
                is Poll.Pending -> {
                    i++
                }
                is Poll.Ready -> {
                    return when (val y = p.value) {
                        is Yield.Value -> {
                            // Cycle the stream to the end to ensure fair scheduling
                            streams.removeAt(i)
                            streams.add(stream)
                            Poll.ready(Yield.value(y.value))
                        }
                        Yield.End -> {
                            streams.removeAt(i)
                            if (streams.isEmpty()) {
                                terminated = true
                                Poll.ready(Yield.end())
                            } else {
                                continue
                            }
                        }
                    }
                }
            }
        }
        return if (streams.isEmpty()) {
            terminated = true
            Poll.ready(Yield.end())
        } else {
            Poll.pending()
        }
    }
}

/**
 * Convert an iterable of streams into a [Stream] of results from the streams.
 */
@HiddenFromObjC
public fun <T> streamSelectAll(streams: Iterable<Stream<T>>): SelectAllStream<T> {
    val set = SelectAllStream<T>()
    for (st in streams) {
        set.push(st)
    }
    return set
}
