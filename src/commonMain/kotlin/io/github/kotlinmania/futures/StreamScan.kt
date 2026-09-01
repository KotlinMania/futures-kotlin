// port-lint: source stream/stream/scan.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [scan] method.
 */
@HiddenFromObjC
public class Scan<T, S, R>(
    private val stream: Stream<T>,
    private var state: S,
    private val operation: (S, T) -> Pair<S, R?>?,
) : FusedStream<R> {
    private var done: Boolean = false

    public interface StateFn

    public interface Item

    public interface Error

    public companion object {
        public fun <T, S, R> new(
            stream: Stream<T>,
            initialState: S,
            operation: (S, T) -> Pair<S, R?>?,
        ): Scan<T, S, R> = Scan(stream, initialState, operation)
    }

    /**
     * Acquires a reference to the underlying stream that this combinator is pulling from.
     */
    public fun getRef(): Stream<T> = stream

    /**
     * Acquires a mutable reference to the underlying stream.
     */
    public fun getMut(): Stream<T> = stream

    /**
     * Acquires a pinned mutable reference to the underlying stream.
     */
    public fun getPinMut(): Stream<T> = stream

    /**
     * Consumes this combinator, returning the underlying stream.
     */
    public fun intoInner(): Stream<T> = stream

    /**
     * Returns whether the stream has finished taking items.
     */
    public fun isDoneTaking(): Boolean = done

    public fun fmt(): String = "Scan"

    override fun toString(): String = fmt()

    override fun isTerminated(): Boolean =
        done || ((stream as? FusedStream<*>)?.isTerminated() ?: false)

    override fun pollNext(context: TaskContext): Poll<Yield<R>> {
        if (done) return Poll.Ready(Yield.End)
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            val next = operation(state, y.value)
                            if (next == null || next.second == null) {
                                done = true
                                return Poll.Ready(Yield.End)
                            }
                            state = next.first
                            return Poll.Ready(Yield.Value(next.second!!))
                        }
                        Yield.End -> {
                            done = true
                            return Poll.Ready(Yield.End)
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }

    override fun sizeHint(): SizeHint {
        if (done) return SizeHint(0, 0)
        val (_, upper) = stream.sizeHint()
        return SizeHint(0, upper)
    }
}

/**
 * Creates a stream that holds state and yields elements as long as the operation produces non-null items.
 */
@HiddenFromObjC
public fun <T, S, R> Stream<T>.scan(
    initialState: S,
    operation: (S, T) -> Pair<S, R?>?,
): Scan<T, S, R> = Scan.new(this, initialState, operation)
