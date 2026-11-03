// port-lint: source async_await/poll.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Creates a future which returns the result of polling a future once within the
 * current context.
 */
@HiddenFromObjC
public fun <T> poll(future: Future<T>): PollOnce<T> = PollOnce(future)

/**
 * A future that returns the result of polling an underlying future once.
 */
@HiddenFromObjC
public class PollOnce<T>(
    private val future: Future<T>,
) : Future<Poll<T>> {
    public fun getRef(): Future<T> = future

    public fun intoInner(): Future<T> = future

    override fun poll(context: TaskContext): Poll<Poll<T>> =
        Poll.Ready(future.poll(context))
}
