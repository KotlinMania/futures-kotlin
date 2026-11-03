// port-lint: source future/poll_immediate.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Creates a future that polls [future] once immediately and resolves to [Poll.Ready] containing
 * the resulting [Poll] (either [Poll.Ready] or [Poll.Pending]).
 */
@HiddenFromObjC
public fun <T> pollImmediate(future: Future<T>): Future<Poll<T>> {
    var polled = false
    return object : Future<Poll<T>> {
        override fun poll(context: TaskContext): Poll<Poll<T>> {
            if (polled) error("pollImmediate future polled after completion")
            polled = true
            return Poll.Ready(future.poll(context))
        }
    }
}
