// port-lint: source futures-util/src/future/future/flatten.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [flatten] method.
 */
@HiddenFromObjC
public class FlattenFuture<out T>(
    future: Future<Future<T>>,
) : FusedFuture<T> {
    private sealed interface State<out T> {
        class First<out T>(
            val future: Future<Future<T>>,
        ) : State<T>

        class Second<out T>(
            val future: Future<T>,
        ) : State<T>

        data object Empty : State<Nothing>
    }

    private var state: State<T> = State.First(future)

    override fun isTerminated(): Boolean = state is State.Empty

    override fun poll(context: TaskContext): Poll<T> {
        while (true) {
            when (val curr = state) {
                is State.First -> {
                    when (val p = curr.future.poll(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            state = State.Second(p.value)
                        }
                    }
                }
                is State.Second -> {
                    when (val p = curr.future.poll(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            state = State.Empty
                            return Poll.Ready(p.value)
                        }
                    }
                }
                State.Empty -> error("Flatten polled after completion")
            }
        }
    }
}

/**
 * Stream for the [flattenStream] method.
 */
@HiddenFromObjC
public class FlattenStream<out T>(
    future: Future<Stream<T>>,
) : FusedStream<T> {
    private sealed interface State<out T> {
        class First<out T>(
            val future: Future<Stream<T>>,
        ) : State<T>

        class Second<out T>(
            val stream: Stream<T>,
        ) : State<T>

        data object Empty : State<Nothing>
    }

    private var state: State<T> = State.First(future)

    override fun isTerminated(): Boolean = state is State.Empty

    override fun sizeHint(): SizeHint =
        when (val curr = state) {
            is State.First -> SizeHint(0, null)
            is State.Second -> curr.stream.sizeHint()
            State.Empty -> SizeHint(0, 0)
        }

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        while (true) {
            when (val curr = state) {
                is State.First -> {
                    when (val p = curr.future.poll(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            state = State.Second(p.value)
                        }
                    }
                }
                is State.Second -> {
                    when (val p = curr.stream.pollNext(context)) {
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
 * Sink for the [flattenSink] method.
 */
@HiddenFromObjC
public class FlattenSink<in Item, out E>(
    future: Future<Sink<Item, E>>,
) : Sink<Item, E> {
    private sealed interface State<in Item, out E> {
        class First<Item, E>(
            val future: Future<Sink<Item, E>>,
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
                    when (val p = curr.future.poll(context)) {
                        is Poll.Pending -> return Poll.Pending
                        is Poll.Ready -> {
                            state = State.Second(p.value)
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
 * Flattens the execution of this future when the output is itself another future.
 */
@HiddenFromObjC
public fun <T> Future<Future<T>>.flatten(): FlattenFuture<T> =
    FlattenFuture(this)

/**
 * Flattens the execution of this future when the output is a stream.
 */
@HiddenFromObjC
public fun <T> Future<Stream<T>>.flattenStream(): FlattenStream<T> =
    FlattenStream(this)

/**
 * Flattens the execution of this future when the output is a sink.
 */
@HiddenFromObjC
public fun <Item, E> Future<Sink<Item, E>>.flattenSink(): FlattenSink<Item, E> =
    FlattenSink(this)
