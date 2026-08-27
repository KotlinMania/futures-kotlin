// port-lint: source futures-util/src/stream/stream/into_future.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [intoFuture] method.
 */
@HiddenFromObjC
public class StreamFuture<St : Stream<T>, T>(
    private var stream: St?,
) : FusedFuture<Pair<T?, St>> {
    public fun getRef(): St? = stream

    public fun getMut(): St? = stream

    public fun intoInner(): St? = stream

    override fun isTerminated(): Boolean = stream == null

    override fun poll(context: TaskContext): Poll<Pair<T?, St>> {
        val s = stream ?: error("polling StreamFuture twice")
        return when (val next = s.pollNext(context)) {
            is Poll.Pending -> Poll.Pending
            is Poll.Ready -> {
                val st = stream!!
                stream = null
                val item = when (val value = next.value) {
                    is Yield.Value -> value.item
                    is Yield.End -> null
                }
                Poll.Ready(item to st)
            }
        }
    }
}

/**
 * Converts a stream into a future that resolves to `(item, stream)`.
 */
@HiddenFromObjC
public fun <St : Stream<T>, T> St.intoFuture(): StreamFuture<St, T> = StreamFuture(this)
