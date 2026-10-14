// port-lint: source async_await/poll.rs
package io.github.kotlinmania.futures

/**
 * Extracts the successful value of a [Poll.Ready], or returns early with null if [Poll.Pending].
 *
 * Mirrors the upstream ready macro from task poll.
 */
public fun <T> ready(poll: Poll<T>): T? =
    when (poll) {
        is Poll.Ready -> poll.value
        Poll.Pending -> null
    }

/**
 * Wraps a value into [Poll.Ready].
 */
public fun <T> pollReady(value: T): Poll<T> = Poll.Ready(value)

/**
 * Returns [Poll.Pending].
 */
public fun <T> pollPending(): Poll<T> = Poll.Pending

/**
 * Unwraps the value of [Poll.Ready], throwing an exception if [Poll.Pending].
 */
public fun <T> Poll<T>.unwrap(): T =
    when (this) {
        is Poll.Ready -> value
        Poll.Pending -> throw IllegalStateException("called Poll.unwrap() on a Pending value")
    }
