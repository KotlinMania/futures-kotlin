// port-lint: source futures-util/src/stream/stream/cycle.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [cycle] method.
 */
@HiddenFromObjC
public class Cycle<T>(
    private val factory: () -> Stream<T>,
) : FusedStream<T> {
    private var stream: Stream<T> = factory()

    public companion object {
        internal fun <T> new(factory: () -> Stream<T>): Cycle<T> = Cycle(factory)
    }

    override fun isTerminated(): Boolean {
        val hint = sizeHint()
        return hint.lower == 0 && hint.upper == 0
    }

    override fun pollNext(context: TaskContext): Poll<Yield<T>> =
        when (val p = stream.pollNext(context)) {
            is Poll.Ready -> {
                when (p.value) {
                    is Yield.Value -> p
                    Yield.End -> {
                        stream = factory()
                        stream.pollNext(context)
                    }
                }
            }
            Poll.Pending -> Poll.Pending
        }

    override fun sizeHint(): SizeHint {
        val orig = factory().sizeHint()
        return when {
            orig.lower == 0 && orig.upper == 0 -> orig
            orig.lower == 0 -> SizeHint(0, null)
            else -> SizeHint(Int.MAX_VALUE, null)
        }
    }
}

/**
 * Repeats a stream endlessly by creating a new stream each time the previous stream ends.
 */
@HiddenFromObjC
public fun <T> (() -> Stream<T>).cycle(): Cycle<T> = Cycle.new(this)
