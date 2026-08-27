// port-lint: source futures-util/src/stream/stream/fuse.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [fuse] method.
 */
@HiddenFromObjC
public class StreamFuse<T>(
    private val stream: Stream<T>,
) : FusedStream<T> {
    private var done: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>): StreamFuse<T> = StreamFuse(stream)
    }

    /**
     * Returns whether the underlying stream has finished or not.
     */
    public fun isDone(): Boolean = done

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream

    override fun isTerminated(): Boolean = done

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        if (done) return Poll.Ready(Yield.End)
        return when (val p = stream.pollNext(context)) {
            is Poll.Ready -> {
                when (p.value) {
                    is Yield.Value -> p
                    Yield.End -> {
                        done = true
                        Poll.Ready(Yield.End)
                    }
                }
            }
            Poll.Pending -> Poll.Pending
        }
    }

    override fun sizeHint(): SizeHint {
        if (done) return SizeHint(0, 0)
        return stream.sizeHint()
    }
}

/**
 * Adapts a stream to be fused so that once it ends, it will continue returning [Yield.End].
 */
@HiddenFromObjC
public fun <T> Stream<T>.fuse(): StreamFuse<T> = StreamFuse.new(this)
