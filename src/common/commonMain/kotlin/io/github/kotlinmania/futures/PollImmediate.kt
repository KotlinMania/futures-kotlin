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
    return Future { context ->
        if (polled) error("pollImmediate future polled after completion")
        polled = true
        Poll.Ready(future.poll(context))
    }
}
