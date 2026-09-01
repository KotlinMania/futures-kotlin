// port-lint: source future/join_all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [joinAll] function.
 */
@HiddenFromObjC
public class JoinAll<F>(
    futures: Iterable<Future<F>>,
) : FusedFuture<List<F>> {
    private val elems: List<MaybeDone<F>> = futures.map { maybeDone(it) }

    override fun isTerminated(): Boolean = elems.all { it.isTerminated() }

    override fun poll(context: TaskContext): Poll<List<F>> {
        var allDone = true
        for (elem in elems) {
            if (elem.poll(context) is Poll.Pending) {
                allDone = false
            }
        }

        return if (allDone) {
            val result = elems.map { it.takeOutput()!! }
            Poll.Ready(result)
        } else {
            Poll.Pending
        }
    }

    public companion object {
        @HiddenFromObjC
        public fun <F> fromIter(iter: Iterable<Future<F>>): JoinAll<F> = joinAll(iter)
    }
}

/**
 * Creates a future which represents a collection of the outputs of the futures given.
 */
@HiddenFromObjC
public fun <F> joinAll(futures: Iterable<Future<F>>): JoinAll<F> = JoinAll(futures)
