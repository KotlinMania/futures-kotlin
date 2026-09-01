// port-lint: source future/option.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A future representing a value which may or may not be present.
 */
@HiddenFromObjC
public class OptionFuture<out T>(
    private var inner: Future<T>?,
) : FusedFuture<T?> {
    private var terminated = inner == null

    override fun isTerminated(): Boolean {
        if (terminated) return true
        return (inner as? FusedFuture<*>)?.isTerminated() ?: false
    }

    override fun poll(context: TaskContext): Poll<T?> {
        val f = inner ?: return Poll.Ready(null)
        return when (val p = f.poll(context)) {
            is Poll.Ready -> {
                terminated = true
                inner = null
                Poll.Ready(p.value)
            }
            is Poll.Pending -> Poll.Pending
        }
    }
}

/**
 * Converts a nullable [Future] into an [OptionFuture].
 */
@HiddenFromObjC
public fun <T> Future<T>?.intoOptionFuture(): OptionFuture<T> = OptionFuture(this)
