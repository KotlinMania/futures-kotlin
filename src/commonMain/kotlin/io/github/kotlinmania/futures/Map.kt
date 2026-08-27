// port-lint: source futures-util/src/future/future/map.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Internal Map future.
 */
@HiddenFromObjC
public class Map<T, R>(
    private var future: Future<T>?,
    private var f: ((T) -> R)?,
) : FusedFuture<R> {
    override fun isTerminated(): Boolean = future == null

    override fun poll(context: TaskContext): Poll<R> {
        val fut = future ?: error("Map must not be polled after it returned `Poll::Ready`")
        val func = f ?: error("Map must not be polled after it returned `Poll::Ready`")
        return when (val p = fut.poll(context)) {
            is Poll.Ready -> {
                future = null
                f = null
                Poll.Ready(func(p.value))
            }
            is Poll.Pending -> Poll.Pending
        }
    }

    public companion object {
        internal fun <T, R> new(future: Future<T>, f: (T) -> R): Map<T, R> =
            Map(future, f)
    }
}
