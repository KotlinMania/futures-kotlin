// port-lint: source futures-util/src/future/try_future/try_flatten.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryFlatten] method.
 */
@HiddenFromObjC
public class TryFlattenFuture<out T, out E>(
    future: TryFuture<TryFuture<T, E>, E>,
) : TryFuture<T, E>,
    FusedFuture<Try<T, E>> {
    private sealed interface State<out T, out E> {
        class First<out T, out E>(
            val future: TryFuture<TryFuture<T, E>, E>,
        ) : State<T, E>

        class Second<out T, out E>(
            val future: TryFuture<T, E>,
        ) : State<T, E>

        data object Empty : State<Nothing, Nothing>
    }

    private var state: State<T, E> = State.First(future)

    override fun isTerminated(): Boolean = state is State.Empty

    override fun poll(context: TaskContext): Poll<Try<T, E>> {
        while (true) {
            when (val curr = state) {
                is State.First -> {
                    when (val p = curr.future.tryPoll(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            when (val outcome = p.value) {
                                is Try.Ok -> state = State.Second(outcome.value)
                                is Try.Err -> {
                                    state = State.Empty
                                    return Poll.Ready(Try.err(outcome.error))
                                }
                            }
                        }
                    }
                }
                is State.Second -> {
                    when (val p = curr.future.tryPoll(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            state = State.Empty
                            return p
                        }
                    }
                }
                State.Empty -> error("TryFlatten polled after completion")
            }
        }
    }
}

/**
 * Stream for the [tryFlattenStream] method.
 */
@HiddenFromObjC
public class TryFlattenStream<out T, out E>(
    future: TryFuture<TryStream<T, E>, E>,
) : TryStream<T, E>,
    FusedStream<Try<T, E>> {
    private sealed interface State<out T, out E> {
        class First<out T, out E>(
            val future: TryFuture<TryStream<T, E>, E>,
        ) : State<T, E>

        class Second<out T, out E>(
            val stream: TryStream<T, E>,
        ) : State<T, E>

        data object Empty : State<Nothing, Nothing>
    }

    private var state: State<T, E> = State.First(future)

    override fun isTerminated(): Boolean = state is State.Empty

    override fun sizeHint(): SizeHint =
        when (val curr = state) {
            is State.First -> SizeHint(0, null)
            is State.Second -> curr.stream.sizeHint()
            State.Empty -> SizeHint(0, 0)
        }

    override fun pollNext(context: TaskContext): Poll<Yield<Try<T, E>>> {
        while (true) {
            when (val curr = state) {
                is State.First -> {
                    when (val p = curr.future.tryPoll(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            when (val outcome = p.value) {
                                is Try.Ok -> state = State.Second(outcome.value)
                                is Try.Err -> {
                                    state = State.Empty
                                    return Poll.Ready(Yield.value(Try.err(outcome.error)))
                                }
                            }
                        }
                    }
                }
                is State.Second -> {
                    when (val p = curr.stream.tryPollNext(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            if (p.value is Yield.End) {
                                state = State.Empty
                            }
                            return p
                        }
                    }
                }
                State.Empty -> return Poll.Ready(Yield.end())
            }
        }
    }
}

/**
 * Sink for the [tryFlattenSink] method.
 */
@HiddenFromObjC
public class TryFlattenSink<in Item, out E>(
    future: TryFuture<Sink<Item, E>, E>,
) : Sink<Item, E> {
    private sealed interface State<in Item, out E> {
        class First<Item, E>(
            val future: TryFuture<Sink<Item, E>, E>,
        ) : State<Item, E>

        class Second<Item, E>(
            val sink: Sink<Item, E>,
        ) : State<Item, E>

        data object Empty : State<Any?, Nothing>
    }

    private var state: State<Item, E> = State.First(future)

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E>> {
        while (true) {
            when (val curr = state) {
                is State.First -> {
                    when (val p = curr.future.tryPoll(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            when (val outcome = p.value) {
                                is Try.Ok -> state = State.Second(outcome.value)
                                is Try.Err -> {
                                    state = State.Empty
                                    return Poll.Ready(SinkOutcome.err(outcome.error))
                                }
                            }
                        }
                    }
                }
                is State.Second -> {
                    return curr.sink.pollReady(context)
                }
                State.Empty -> error("pollReady called after eof")
            }
        }
    }

    override fun startSend(item: Item): SinkOutcome<E> =
        when (val curr = state) {
            is State.First -> error("pollReady not called first")
            is State.Second -> curr.sink.startSend(item)
            State.Empty -> error("startSend called after eof")
        }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>> =
        when (val curr = state) {
            is State.First -> Poll.Ready(SinkOutcome.ready())
            is State.Second -> curr.sink.pollFlush(context)
            State.Empty -> error("pollFlush called after eof")
        }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E>> =
        when (val curr = state) {
            is State.Second -> {
                val res = curr.sink.pollClose(context)
                if (res is Poll.Ready) {
                    state = State.Empty
                }
                res
            }
            else -> {
                state = State.Empty
                Poll.Ready(SinkOutcome.ready())
            }
        }
}

/**
 * Flattens the execution of this [TryFuture] when the successful output is itself another [TryFuture].
 */
@HiddenFromObjC
public fun <T, E> TryFuture<TryFuture<T, E>, E>.tryFlatten(): TryFlattenFuture<T, E> =
    TryFlattenFuture(this)

/**
 * Flattens the execution of this [TryFuture] when the successful output is a [TryStream].
 */
@HiddenFromObjC
public fun <T, E> TryFuture<TryStream<T, E>, E>.tryFlattenStream(): TryFlattenStream<T, E> =
    TryFlattenStream(this)

/**
 * Flattens the execution of this [TryFuture] when the successful output is a [Sink].
 */
@HiddenFromObjC
public fun <Item, E> TryFuture<Sink<Item, E>, E>.tryFlattenSink(): TryFlattenSink<Item, E> =
    TryFlattenSink(this)
