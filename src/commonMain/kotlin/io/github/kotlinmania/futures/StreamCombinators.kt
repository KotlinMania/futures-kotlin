// port-lint: source futures-util/src/stream/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future that collects all remaining items of a [Stream] into a [List].
 */
@HiddenFromObjC
public fun <T> Stream<T>.collect(): Future<List<T>> {
    val stream = this
    val collected = mutableListOf<T>()
    return object : Future<List<T>> {
        override fun poll(context: TaskContext): Poll<List<T>> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> collected.add(y.value)
                            Yield.End -> return Poll.ready(collected.toList())
                        }
                    }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }
    }
}

/**
 * Future that produces the next item from the [Stream], or null when exhausted.
 */
@HiddenFromObjC
public fun <T> Stream<T>.next(): Future<T?> {
    val stream = this
    return object : Future<T?> {
        override fun poll(context: TaskContext): Poll<T?> =
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> Poll.ready(p.value.valueOrNull())
                Poll.Pending -> Poll.pending()
            }
    }
}

/**
 * Returns a new [Stream] that takes up to [n] items from this stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.take(n: Int): Stream<T> {
    val stream = this
    var remaining = maxOf(0, n)
    return object : Stream<T>, FusedStream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            if (remaining <= 0) {
                return Poll.ready(Yield.end())
            }
            return when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            remaining--
                            Poll.ready(y)
                        }
                        Yield.End -> {
                            remaining = 0
                            Poll.ready(Yield.end())
                        }
                    }
                }
                Poll.Pending -> Poll.pending()
            }
        }

        override fun isTerminated(): Boolean = remaining <= 0

        override fun sizeHint(): SizeHint {
            val inner = stream.sizeHint()
            val lower = minOf(remaining, inner.lower)
            val upper = if (inner.upper != null) minOf(remaining, inner.upper) else remaining
            return SizeHint(lower = lower, upper = upper)
        }
    }
}

private enum class SendState {
    NOT_READY,
    READY_TO_START,
    FLUSHING,
    DONE,
}

/**
 * Sends a value into this sink, returning a [Future] that resolves once the item is sent and flushed.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.send(item: Item): Future<Try<Unit, E>> {
    val sink = this
    var state = SendState.NOT_READY

    return object : Future<Try<Unit, E>> {
        override fun poll(context: TaskContext): Poll<Try<Unit, E>> {
            if (state == SendState.NOT_READY) {
                when (val p = sink.pollReady(context)) {
                    is Poll.Ready -> {
                        when (val outcome = p.value) {
                            SinkOutcome.Ready -> state = SendState.READY_TO_START
                            is SinkOutcome.Err -> {
                                state = SendState.DONE
                                return Poll.ready(Try.err(outcome.error))
                            }
                        }
                    }
                    Poll.Pending -> return Poll.pending()
                }
            }

            if (state == SendState.READY_TO_START) {
                when (val outcome = sink.startSend(item)) {
                    SinkOutcome.Ready -> state = SendState.FLUSHING
                    is SinkOutcome.Err -> {
                        state = SendState.DONE
                        return Poll.ready(Try.err(outcome.error))
                    }
                }
            }

            if (state == SendState.FLUSHING) {
                when (val p = sink.pollFlush(context)) {
                    is Poll.Ready -> {
                        when (val outcome = p.value) {
                            SinkOutcome.Ready -> {
                                state = SendState.DONE
                                return Poll.ready(Try.ok(Unit))
                            }
                            is SinkOutcome.Err -> {
                                state = SendState.DONE
                                return Poll.ready(Try.err(outcome.error))
                            }
                        }
                    }
                    Poll.Pending -> return Poll.pending()
                }
            }

            return Poll.ready(Try.ok(Unit))
        }
    }
}

/**
 * Flushes this sink, returning a [Future] that resolves once all buffered items are flushed.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.flush(): Future<Try<Unit, E>> {
    val sink = this
    return object : Future<Try<Unit, E>> {
        override fun poll(context: TaskContext): Poll<Try<Unit, E>> =
            when (val p = sink.pollFlush(context)) {
                is Poll.Ready -> {
                    when (val outcome = p.value) {
                        SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                        is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                    }
                }
                Poll.Pending -> Poll.pending()
            }
    }
}

/**
 * Closes this sink, returning a [Future] that resolves once the sink is closed.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.close(): Future<Try<Unit, E>> {
    val sink = this
    return object : Future<Try<Unit, E>> {
        override fun poll(context: TaskContext): Poll<Try<Unit, E>> =
            when (val p = sink.pollClose(context)) {
                is Poll.Ready -> {
                    when (val outcome = p.value) {
                        SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                        is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                    }
                }
                Poll.Pending -> Poll.pending()
            }
    }
}
