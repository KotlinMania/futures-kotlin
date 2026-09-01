// port-lint: source future/select_all.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [selectAll] function.
 */
@HiddenFromObjC
public class SelectAll<Fut>(
    futures: Iterable<Future<Fut>>,
) : Future<Triple<Fut, Int, List<Future<Fut>>>> {
    private val inner: MutableList<Future<Fut>> = futures.toMutableList()

    init {
        require(inner.isNotEmpty()) { "iterator provided to selectAll was empty" }
    }

    /**
     * Consumes this combinator, returning the underlying futures.
     */
    public fun intoInner(): List<Future<Fut>> = inner.toList()

    override fun poll(context: TaskContext): Poll<Triple<Fut, Int, List<Future<Fut>>>> {
        for (i in inner.indices) {
            when (val p = inner[i].poll(context)) {
                is Poll.Ready -> {
                    val readyValue = p.value
                    inner.removeAt(i)
                    val rest = inner.toList()
                    return Poll.Ready(Triple(readyValue, i, rest))
                }
                is Poll.Pending -> {}
            }
        }
        return Poll.Pending
    }

    public companion object {
        @HiddenFromObjC
        public fun <Fut> fromIter(iter: Iterable<Future<Fut>>): SelectAll<Fut> = selectAll(iter)
    }
}

/**
 * Creates a new future which will select over a list of futures.
 */
@HiddenFromObjC
public fun <Fut> selectAll(futures: Iterable<Future<Fut>>): SelectAll<Fut> = SelectAll(futures)
