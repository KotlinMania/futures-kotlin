// port-lint: source futures-util/src/stream/stream/zip.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.math.min
import kotlin.native.HiddenFromObjC

/**
 * Stream for the [zip] method.
 */
@HiddenFromObjC
public class Zip<A, B>(
    private val stream1: Stream<A>,
    private val stream2: Stream<B>,
) : FusedStream<Pair<A, B>> {
    private var queued1: A? = null
    private var hasQueued1: Boolean = false
    private var queued2: B? = null
    private var hasQueued2: Boolean = false
    private var done: Boolean = false

    public companion object {
        internal fun <A, B> new(stream1: Stream<A>, stream2: Stream<B>): Zip<A, B> = Zip(stream1, stream2)
    }

    /**
     * Acquires references to the underlying streams that this combinator is pulling from.
     */
    public fun getRef(): Pair<Stream<A>, Stream<B>> = Pair(stream1, stream2)

    /**
     * Consumes this combinator, returning the underlying streams.
     */
    public fun intoInner(): Pair<Stream<A>, Stream<B>> = Pair(stream1, stream2)

    override fun isTerminated(): Boolean =
        done || (!hasQueued1 && !hasQueued2 && (((stream1 as? FusedStream<*>)?.isTerminated() ?: false) || ((stream2 as? FusedStream<*>)?.isTerminated() ?: false)))

    @Suppress("UNCHECKED_CAST")
    override fun pollNext(context: TaskContext): Poll<Yield<Pair<A, B>>> {
        if (done) return Poll.Ready(Yield.End)

        if (!hasQueued1) {
            when (val p1 = stream1.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y1 = p1.value) {
                        is Yield.Value -> {
                            queued1 = y1.value
                            hasQueued1 = true
                        }
                        Yield.End -> {
                            done = true
                            return Poll.Ready(Yield.End)
                        }
                    }
                }
                Poll.Pending -> {}
            }
        }

        if (!hasQueued2) {
            when (val p2 = stream2.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y2 = p2.value) {
                        is Yield.Value -> {
                            queued2 = y2.value
                            hasQueued2 = true
                        }
                        Yield.End -> {
                            done = true
                            return Poll.Ready(Yield.End)
                        }
                    }
                }
                Poll.Pending -> {}
            }
        }

        if (hasQueued1 && hasQueued2) {
            val a = queued1 as A
            val b = queued2 as B
            queued1 = null
            hasQueued1 = false
            queued2 = null
            hasQueued2 = false
            return Poll.Ready(Yield.Value(Pair(a, b)))
        }

        return Poll.Pending
    }

    override fun sizeHint(): SizeHint {
        if (done) return SizeHint(0, 0)
        val hint1 = stream1.sizeHint()
        val hint2 = stream2.sizeHint()
        val lower1 = hint1.lower + (if (hasQueued1) 1 else 0)
        val lower2 = hint2.lower + (if (hasQueued2) 1 else 0)
        val lower = min(lower1, lower2)

        val upper1 = hint1.upper?.let { it + (if (hasQueued1) 1 else 0) }
        val upper2 = hint2.upper?.let { it + (if (hasQueued2) 1 else 0) }
        val upper =
            when {
                upper1 != null && upper2 != null -> min(upper1, upper2)
                upper1 != null -> upper1
                upper2 != null -> upper2
                else -> null
            }
        return SizeHint(lower, upper)
    }
}

/**
 * Zips two streams together into a single stream of pairs.
 */
@HiddenFromObjC
public fun <A, B> Stream<A>.zip(other: Stream<B>): Zip<A, B> = Zip.new(this, other)
