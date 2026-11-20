// port-lint: source async_await/pending.rs
package io.github.kotlinmania.futures.asyncawait

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.TaskContext

private const val PENDING_TEMPLATE: String = "asyncAwait.pendingOnce().await"

/**
 * Yields to the event loop once, matching the upstream `pending!()` macro.
 *
 * This returns the canonical suspend expression marker corresponding to
 * polling [pendingOnce] once and returning [Poll.Pending] before resolving
 * on the subsequent poll.
 */
public fun pending(): String = PENDING_TEMPLATE

/**
 * Creates a future that returns [Poll.Pending] once before resolving with [Unit].
 */
public fun pendingOnce(): PendingOnce = PendingOnce(isReady = false)

/**
 * Future returned by [pendingOnce] that returns [Poll.Pending] on the first poll
 * and [Poll.Ready] with [Unit] on subsequent polls.
 */
public class PendingOnce(
    public var isReady: Boolean = false,
) : Future<Unit> {
    override fun poll(context: TaskContext): Poll<Unit> =
        if (isReady) {
            Poll.Ready(Unit)
        } else {
            isReady = true
            Poll.Pending
        }
}
