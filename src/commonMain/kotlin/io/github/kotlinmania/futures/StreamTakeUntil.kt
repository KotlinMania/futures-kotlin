// port-lint: source stream/stream/take_until.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [takeUntil] method.
 */
@HiddenFromObjC
public class TakeUntil<T, Fut : Future<*>>(
    private val stream: Stream<T>,
    private var fut: Fut?,
) : FusedStream<T> {
    private var futResult: Any? = null
    private var free: Boolean = false

    public companion object {
        internal fun <T, Fut : Future<*>> new(stream: Stream<T>, fut: Fut): TakeUntil<T, Fut> =
            TakeUntil(stream, fut)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream

    public fun takeFuture(): Fut? {
        if (fut != null) {
            free = true
        }
        val f = fut
        fut = null
        return f
    }

    public fun takeResult(): Any? {
        val res = futResult
        futResult = null
        return res
    }

    public fun isStopped(): Boolean = !free && fut == null

    override fun isTerminated(): Boolean = isStopped()

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        val f = fut
        if (f != null) {
            when (val p = f.poll(context)) {
                is Poll.Ready -> {
                    fut = null
                    futResult = p.value
                }
                Poll.Pending -> {}
            }
        }

        if (!free && fut == null) {
            return Poll.Ready(Yield.End)
        }

        return when (val p = stream.pollNext(context)) {
            is Poll.Ready -> {
                if (p.value is Yield.End) {
                    fut = null
                }
                p
            }
            Poll.Pending -> Poll.Pending
        }
    }

    override fun sizeHint(): SizeHint {
        if (isStopped()) return SizeHint(0, 0)
        return stream.sizeHint()
    }
}

/**
 * Takes elements from this stream until the stopping future resolves.
 */
@HiddenFromObjC
public fun <T, Fut : Future<*>> Stream<T>.takeUntil(future: Fut): TakeUntil<T, Fut> =
    TakeUntil.new(this, future)
