@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Sink for the [unfoldSink] function.
 */
public typealias SinkUnfold<T, Item, E> = io.github.kotlinmania.futures.sink.Unfold<T, Item, E>

/**
 * Create a sink from a function which processes one item at a time.
 */
@HiddenFromObjC
public fun <T, Item, E> unfoldSink(
    init: T,
    function: (T, Item) -> Future<Try<T, E>>,
): io.github.kotlinmania.futures.sink.Unfold<T, Item, E> = io.github.kotlinmania.futures.sink.unfold(init, function)
