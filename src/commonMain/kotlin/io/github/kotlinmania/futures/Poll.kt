// port-lint: source async_await/poll.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Extracts the successful value of a [Poll.Ready], or returns early with null if [Poll.Pending].
 *
 * Mirrors the upstream ready macro from task poll.
 */
@HiddenFromObjC
public fun <T> ready(poll: Poll<T>): T? =
    when (poll) {
        is Poll.Ready -> poll.value
        Poll.Pending -> null
    }
