// port-lint: source future/poll_fn.rs
package io.github.kotlinmania.futures

/**
 * Future for the [pollFn] function.
 */
public class PollFn<T>(
    private val block: (TaskContext) -> Poll<T>,
) : Future<T> {
    override fun poll(context: TaskContext): Poll<T> = block(context)
}

/**
 * Creates a new future wrapping around a function returning [Poll].
 */
public fun <T> pollFn(block: (TaskContext) -> Poll<T>): PollFn<T> = PollFn(block)
