// port-lint: source stream/stream/all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [all] method.
 */
@HiddenFromObjC
public class StreamAll<T>(
    private val stream: Stream<T>,
    private val predicate: (T) -> Boolean,
) : FusedFuture<Boolean> {
    private var done = false

    public companion object {
        internal fun <T> new(stream: Stream<T>, predicate: (T) -> Boolean): StreamAll<T> = StreamAll(stream, predicate)
    }

    override fun isTerminated(): Boolean = done

    override fun poll(context: TaskContext): Poll<Boolean> {
        if (done) throw RuntimeException("All polled after completion")
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            if (!predicate(y.value)) {
                                done = true
                                return Poll.Ready(false)
                            }
                        }
                        Yield.End -> {
                            done = true
                            return Poll.Ready(true)
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }
}

/**
 * Tests if every element of the stream matches a predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.all(predicate: (T) -> Boolean): StreamAll<T> = StreamAll.new(this, predicate)

/**
 * Tests if every element of the stream matches a predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.forAll(predicate: (T) -> Boolean): StreamAll<T> = StreamAll.new(this, predicate)
