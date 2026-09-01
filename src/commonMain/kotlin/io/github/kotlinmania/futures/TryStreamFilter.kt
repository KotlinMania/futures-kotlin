// port-lint: source futures-util/src/stream/try_stream/try_filter.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [tryFilter] method.
 */
@HiddenFromObjC
public class TryFilter<T, E>(
    private val stream: TryStream<T, E>,
    private val predicate: (T) -> Future<Boolean>,
) : FusedStream<Try<T, E>> {
    private var pendingFuture: Future<Boolean>? = null
    private var pendingItem: T? = null

    public companion object {
        internal fun <T, E> new(
            stream: TryStream<T, E>,
            predicate: (T) -> Future<Boolean>,
        ): TryFilter<T, E> = TryFilter(stream, predicate)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): TryStream<T, E> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): TryStream<T, E> = stream

    override fun isTerminated(): Boolean =
        pendingFuture == null && ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> {
        while (true) {
            val fut = pendingFuture
            if (fut != null) {
                when (val p = fut.poll(context)) {
                    is Poll.Ready -> {
                        pendingFuture = null
                        val item = pendingItem
                        pendingItem = null
                        if (p.value) {
                            @Suppress("UNCHECKED_CAST")
                            return Poll.Ready(Yield.Value(Try.Ok(item as T)))
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            } else {
                when (val p = stream.tryPollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> {
                                when (val res = y.value) {
                                    is Try.Ok -> {
                                        pendingItem = res.value
                                        pendingFuture = predicate(res.value)
                                    }
                                    is Try.Err -> return Poll.Ready(Yield.Value(Try.Err(res.error)))
                                }
                            }
                            Yield.End -> return Poll.Ready(Yield.End)
                        }
                    }
                    Poll.Pending -> return Poll.Pending
                }
            }
        }
    }

    override fun sizeHint(): SizeHint {
        val pendingLen = if (pendingFuture != null) 1 else 0
        val hint = stream.sizeHint()
        val upper = hint.upper?.let { (it.toLong() + pendingLen).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        return SizeHint(0, upper)
    }
}
