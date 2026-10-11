// port-lint: source futures-test/src/assert.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Asserts that the next poll to the provided stream will return [Poll.Pending].
 */
@HiddenFromObjC
public fun <T> assertStreamPending(stream: Stream<T>, context: TaskContext = noopContext()) {
    val poll = stream.pollNext(context)
    if (poll is Poll.Ready) {
        error("assertion failed: stream is not pending")
    }
}

/**
 * Asserts that the next poll to the provided stream will return [Poll.Ready] with the provided item.
 */
@HiddenFromObjC
public fun <T> assertStreamNext(
    stream: Stream<T>,
    expected: T,
    context: TaskContext = noopContext(),
) {
    when (val poll = stream.pollNext(context)) {
        is Poll.Ready -> {
            when (val y = poll.value) {
                is Yield.Value -> {
                    if (y.value != expected) {
                        error("assertion failed: expected item $expected but got ${y.value}")
                    }
                }
                Yield.End -> {
                    error("assertion failed: expected stream to provide item but stream is at its end")
                }
            }
        }
        Poll.Pending -> {
            error("assertion failed: expected stream to provide item but stream wasn't ready")
        }
    }
}

/**
 * Asserts that the next poll to the provided stream will return an empty [Poll.Ready] signalling completion.
 */
@HiddenFromObjC
public fun <T> assertStreamDone(stream: Stream<T>, context: TaskContext = noopContext()) {
    when (val poll = stream.pollNext(context)) {
        is Poll.Ready -> {
            when (poll.value) {
                is Yield.Value -> {
                    error("assertion failed: expected stream to be done but had more elements")
                }
                Yield.End -> {}
            }
        }
        Poll.Pending -> {
            error("assertion failed: expected stream to be done but was pending")
        }
    }
}
