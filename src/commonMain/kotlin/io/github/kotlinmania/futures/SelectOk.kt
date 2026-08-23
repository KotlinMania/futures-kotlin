// port-lint: source futures-util/src/future/select_ok.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [selectOk] function.
 */
@HiddenFromObjC
public class SelectOk<Fut, E>(
    futures: Iterable<Future<Try<Fut, E>>>,
) : Future<Try<Pair<Fut, List<Future<Try<Fut, E>>>>, E>> {
    private val inner: MutableList<Future<Try<Fut, E>>> = futures.toMutableList()

    init {
        require(inner.isNotEmpty()) { "iterator provided to selectOk was empty" }
    }

    override fun poll(context: TaskContext): Poll<Try<Pair<Fut, List<Future<Try<Fut, E>>>>, E>> {
        var i = 0
        while (i < inner.size) {
            when (val p = inner[i].poll(context)) {
                is Poll.Ready -> {
                    when (val v = p.value) {
                        is Try.Ok -> {
                            inner.removeAt(i)
                            val rest = inner.toList()
                            return Poll.Ready(Try.Ok(Pair(v.value, rest)))
                        }
                        is Try.Err -> {
                            inner.removeAt(i)
                            if (inner.isEmpty()) {
                                return Poll.Ready(Try.Err(v.error))
                            }
                        }
                    }
                }
                is Poll.Pending -> {
                    i++
                }
            }
        }
        return Poll.Pending
    }

    public companion object {
        @HiddenFromObjC
        public fun <Fut, E> fromIter(iter: Iterable<Future<Try<Fut, E>>>): SelectOk<Fut, E> = selectOk(iter)
    }
}

/**
 * Creates a new future which will select the first successful future over a list of futures.
 */
@HiddenFromObjC
public fun <Fut, E> selectOk(futures: Iterable<Future<Try<Fut, E>>>): SelectOk<Fut, E> = SelectOk(futures)
