// port-lint: source sink/flush.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures.sink

import io.github.kotlinmania.futures.Future
import io.github.kotlinmania.futures.Poll
import io.github.kotlinmania.futures.Sink
import io.github.kotlinmania.futures.SinkOutcome
import io.github.kotlinmania.futures.TaskContext
import io.github.kotlinmania.futures.Try
import kotlin.native.HiddenFromObjC

/**
 * Future for the [flush] method.
 */
@HiddenFromObjC
public class Flush<in Item, out E>(
    private val sink: Sink<Item, E>,
) : Future<Try<Unit, E>> {
    override fun poll(context: TaskContext): Poll<Try<Unit, E>> =
        when (val p = sink.pollFlush(context)) {
            is Poll.Pending -> Poll.pending()
            is Poll.Ready -> {
                when (val outcome = p.value) {
                    is SinkOutcome.Err -> Poll.ready(Try.err(outcome.error))
                    SinkOutcome.Ready -> Poll.ready(Try.ok(Unit))
                }
            }
        }

    public companion object {
        public fun <Item, E> new(sink: Sink<Item, E>): Flush<Item, E> = Flush(sink)
    }
}

/**
 * Flush the sink, processing all pending items.
 */
@HiddenFromObjC
public fun <Item, E> Sink<Item, E>.flush(): Flush<Item, E> =
    Flush(this)
