// port-lint: source futures-util/src/stream/iter.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Stream for the [streamIter] function.
 */
@HiddenFromObjC
public class Iter<T>(
    private val iterator: Iterator<T>,
) : Stream<T> {
    /**
     * Acquires a reference to the underlying iterator that this stream is pulling from.
     */
    public fun getRef(): Iterator<T> = iterator

    /**
     * Consumes this stream, returning the underlying iterator.
     */
    public fun intoInner(): Iterator<T> = iterator

    override fun pollNext(context: TaskContext): Poll<Yield<T>> =
        if (iterator.hasNext()) {
            Poll.ready(Yield.value(iterator.next()))
        } else {
            Poll.ready(Yield.end())
        }

    override fun sizeHint(): SizeHint = SizeHint(0, null)
}

/**
 * Converts an [Iterable] into a [Stream] which is always ready to yield the next value.
 */
@HiddenFromObjC
public fun <T> streamIter(iterable: Iterable<T>): Iter<T> = Iter(iterable.iterator())

/**
 * Converts an [Iterator] into a [Stream] which is always ready to yield the next value.
 */
@HiddenFromObjC
public fun <T> streamIter(iterator: Iterator<T>): Iter<T> = Iter(iterator)

