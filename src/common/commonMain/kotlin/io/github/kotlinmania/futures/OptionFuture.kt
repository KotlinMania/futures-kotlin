// port-lint: source future/option.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * A future representing a value which may or may not be present.
 */
@HiddenFromObjC
public class OptionFuture<out T>(
    private val inner: Future<T>?,
) : FusedFuture<T?> {
    override fun isTerminated(): Boolean =
        inner == null || (inner as? FusedFuture<*>)?.isTerminated() == true

    override fun poll(context: TaskContext): Poll<T?> {
        val f = inner ?: return Poll.Ready(null)
        return when (val p = f.poll(context)) {
            is Poll.Ready -> Poll.Ready(p.value)
            is Poll.Pending -> Poll.Pending
        }
    }
}

/**
 * Converts a nullable [Future] into an [OptionFuture].
 */
@HiddenFromObjC
public fun <T> Future<T>?.intoOptionFuture(): OptionFuture<T> = OptionFuture(this)
