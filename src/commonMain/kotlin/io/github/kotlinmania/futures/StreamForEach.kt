// port-lint: source stream/stream/for_each.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [forEach] method.
 */
@HiddenFromObjC
public class ForEach<T>(
    private val stream: Stream<T>,
    private val action: (T) -> Unit,
) : FusedFuture<Unit> {
    private var done: Boolean = false

    public companion object {
        internal fun <T> new(stream: Stream<T>, action: (T) -> Unit): ForEach<T> = ForEach(stream, action)
    }

    override fun isTerminated(): Boolean = done

    override fun poll(context: TaskContext): Poll<Unit> {
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> action(y.value)
                        Yield.End -> {
                            done = true
                            return Poll.Ready(Unit)
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }
}

/**
 * Runs the provided action on each element of the stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.forEach(action: (T) -> Unit): ForEach<T> = ForEach.new(this, action)
