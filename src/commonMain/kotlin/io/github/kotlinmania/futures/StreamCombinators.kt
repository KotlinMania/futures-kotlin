// port-lint: source futures-util/src/stream/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Converts an [Iterable] into a [Stream] that produces all its items.
 */
@HiddenFromObjC
public fun <T> Iterable<T>.asStream(): Iter<T> = streamIter(this)

/**
 * Calls a closure on each element of the stream, passing the value through unchanged.
 */
@HiddenFromObjC
public fun <T> Stream<T>.inspect(action: (T) -> Unit): Stream<T> =
    map { item ->
        action(item)
        item
    }

/**
 * Maps each item to a stream and flattens the resulting streams.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.flatMap(transform: (T) -> Stream<R>): Stream<R> =
    map(transform).flatten()

internal fun <T, S : Stream<T>> assertStream(stream: S): S = stream
