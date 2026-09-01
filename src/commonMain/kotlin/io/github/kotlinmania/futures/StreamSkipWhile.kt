// port-lint: source stream/stream/skip_while.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [skipWhile] method.
 */
@HiddenFromObjC
public class SkipWhile<T>(
    private val stream: Stream<T>,
    private val predicate: (T) -> Boolean,
) : FusedStream<T> {
    private var doneSkipping: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>, predicate: (T) -> Boolean): SkipWhile<T> =
            SkipWhile(stream, predicate)
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
        (stream as? FusedStream<*>)?.isTerminated() ?: false

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        if (doneSkipping) {
            return stream.pollNext(context)
        }
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            if (!predicate(y.value)) {
                                doneSkipping = true
                                return Poll.Ready(Yield.Value(y.value))
                            }
                        }
                        Yield.End -> {
                            doneSkipping = true
                            return Poll.Ready(Yield.End)
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }

    override fun sizeHint(): SizeHint {
        if (doneSkipping) return stream.sizeHint()
        val (_, upper) = stream.sizeHint()
        return SizeHint(0, upper)
    }
}

/**
 * Skips elements in the stream until the predicate returns false.
 */
@HiddenFromObjC
public fun <T> Stream<T>.skipWhile(predicate: (T) -> Boolean): SkipWhile<T> =
    SkipWhile.new(this, predicate)
