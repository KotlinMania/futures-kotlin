// port-lint: source stream/once.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A stream which emits single element and then EOF.
 */
@HiddenFromObjC
public class Once<T>(
    private var future: Future<T>?,
) : FusedStream<T> {
    override fun isTerminated(): Boolean = future == null

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        val fut = future ?: return Poll.ready(Yield.end())
        return when (val p = fut.poll(context)) {
            is Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                future = null
                Poll.ready(Yield.value(p.value))
            }
        }
    }

    override fun sizeHint(): SizeHint =
        if (future != null) SizeHint(1, 1) else SizeHint(0, 0)
}

/**
 * Creates a stream of a single element.
 */
@HiddenFromObjC
public fun <T> streamOnce(future: Future<T>): Once<T> = Once(future)
