// port-lint: source stream/stream/catch_unwind.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [catchUnwind] method.
 */
@HiddenFromObjC
public class StreamCatchUnwind<T>(
    private val stream: Stream<T>,
) : FusedStream<Result<T>> {
    private var caughtUnwind: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>): StreamCatchUnwind<T> = StreamCatchUnwind(stream)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream

    override fun isTerminated(): Boolean =
        caughtUnwind || (stream as? FusedStream<*>)?.isTerminated() == true

    override fun pollNext(context: TaskContext): Poll<Yield<Result<T>>> {
        if (caughtUnwind) {
            return Poll.Ready(Yield.End)
        }
        return try {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> Poll.Ready(Yield.Value(Result.success(y.value)))
                        Yield.End -> Poll.Ready(Yield.End)
                    }
                }
                Poll.Pending -> Poll.Pending
            }
        } catch (t: Throwable) {
            caughtUnwind = true
            Poll.Ready(Yield.Value(Result.failure(t)))
        }
    }

    override fun sizeHint(): SizeHint {
        if (caughtUnwind) {
            return SizeHint(0, 0)
        }
        return stream.sizeHint()
    }
}

/**
 * Wraps a stream to catch any unwinding panics / exceptions while polling.
 */
@HiddenFromObjC
public fun <T> Stream<T>.catchUnwind(): StreamCatchUnwind<T> = StreamCatchUnwind.new(this)
