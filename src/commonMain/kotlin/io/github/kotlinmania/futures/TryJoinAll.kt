// port-lint: source futures-util/src/future/try_join_all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryJoinAll] function.
 */
@HiddenFromObjC
public class TryJoinAll<T, E>(
    futures: Iterable<Future<Try<T, E>>>,
) : FusedFuture<Try<List<T>, E>> {
    private val elems: List<TryMaybeDone<T, E>> = futures.map { tryMaybeDone(it) }
    private var done: Boolean = false

    override fun isTerminated(): Boolean = done || elems.all { it.isTerminated() }

    override fun poll(context: TaskContext): Poll<Try<List<T>, E>> {
        if (done) {
            error("TryJoinAll polled after completion")
        }

        var hasPending = false
        for (elem in elems) {
            when (val p = elem.poll(context)) {
                is Poll.Pending -> hasPending = true
                is Poll.Ready -> {
                    when (val v = p.value) {
                        is Try.Ok -> {}
                        is Try.Err -> {
                            done = true
                            return Poll.Ready(Try.Err(v.error))
                        }
                    }
                }
            }
        }

        return if (!hasPending) {
            done = true
            val results = elems.map { it.takeOutput()!! }
            Poll.Ready(Try.Ok(results))
        } else {
            Poll.Pending
        }
    }

    public companion object {
        @HiddenFromObjC
        public fun <T, E> fromIter(iter: Iterable<Future<Try<T, E>>>): TryJoinAll<T, E> = tryJoinAll(iter)
    }
}

/**
 * Creates a future which represents either a collection of the results of the futures given or an error.
 */
@HiddenFromObjC
public fun <T, E> tryJoinAll(futures: Iterable<Future<Try<T, E>>>): TryJoinAll<T, E> = TryJoinAll(futures)
