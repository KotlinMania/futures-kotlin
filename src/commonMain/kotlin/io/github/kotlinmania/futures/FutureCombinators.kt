// port-lint: source futures-util/src/future/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Maps this future's output to a different value.
 */
@HiddenFromObjC
public fun <T, R> Future<T>.map(transform: (T) -> R): Future<R> {
    val source = this
    return object : Future<R> {
        override fun poll(context: TaskContext): Poll<R> =
            when (val p = source.poll(context)) {
                is Poll.Ready -> Poll.Ready(transform(p.value))
                is Poll.Pending -> Poll.Pending
            }
    }
}
