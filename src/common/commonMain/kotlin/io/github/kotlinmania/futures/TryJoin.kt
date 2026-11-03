// port-lint: source future/try_join.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [tryJoin] function.
 */
@HiddenFromObjC
public class TryJoin<Fut1, Fut2, E>(
    future1: Future<Try<Fut1, E>>,
    future2: Future<Try<Fut2, E>>,
) : FusedFuture<Try<Pair<Fut1, Fut2>, E>> {
    private val a: TryMaybeDone<Fut1, E> = tryMaybeDone(future1)
    private val b: TryMaybeDone<Fut2, E> = tryMaybeDone(future2)

    override fun isTerminated(): Boolean = a.isTerminated() && b.isTerminated()

    override fun poll(context: TaskContext): Poll<Try<Pair<Fut1, Fut2>, E>> {
        var allDone = true

        when (val p = a.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = b.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        return if (allDone) {
            Poll.Ready(Try.Ok(Pair(a.takeOutput()!!, b.takeOutput()!!)))
        } else {
            Poll.Pending
        }
    }
}

/**
 * Future for the [tryJoin3] function.
 */
@HiddenFromObjC
public class TryJoin3<Fut1, Fut2, Fut3, E>(
    future1: Future<Try<Fut1, E>>,
    future2: Future<Try<Fut2, E>>,
    future3: Future<Try<Fut3, E>>,
) : FusedFuture<Try<Triple<Fut1, Fut2, Fut3>, E>> {
    private val a: TryMaybeDone<Fut1, E> = tryMaybeDone(future1)
    private val b: TryMaybeDone<Fut2, E> = tryMaybeDone(future2)
    private val c: TryMaybeDone<Fut3, E> = tryMaybeDone(future3)

    override fun isTerminated(): Boolean = a.isTerminated() && b.isTerminated() && c.isTerminated()

    override fun poll(context: TaskContext): Poll<Try<Triple<Fut1, Fut2, Fut3>, E>> {
        var allDone = true

        when (val p = a.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = b.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = c.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        return if (allDone) {
            Poll.Ready(Triple(a.takeOutput()!!, b.takeOutput()!!, c.takeOutput()!!).let { Try.Ok(it) })
        } else {
            Poll.Pending
        }
    }
}

/**
 * Future for the [tryJoin4] function.
 */
@HiddenFromObjC
public class TryJoin4<Fut1, Fut2, Fut3, Fut4, E>(
    future1: Future<Try<Fut1, E>>,
    future2: Future<Try<Fut2, E>>,
    future3: Future<Try<Fut3, E>>,
    future4: Future<Try<Fut4, E>>,
) : FusedFuture<Try<Tuple4<Fut1, Fut2, Fut3, Fut4>, E>> {
    private val a: TryMaybeDone<Fut1, E> = tryMaybeDone(future1)
    private val b: TryMaybeDone<Fut2, E> = tryMaybeDone(future2)
    private val c: TryMaybeDone<Fut3, E> = tryMaybeDone(future3)
    private val d: TryMaybeDone<Fut4, E> = tryMaybeDone(future4)

    override fun isTerminated(): Boolean =
        a.isTerminated() && b.isTerminated() && c.isTerminated() && d.isTerminated()

    override fun poll(context: TaskContext): Poll<Try<Tuple4<Fut1, Fut2, Fut3, Fut4>, E>> {
        var allDone = true

        when (val p = a.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = b.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = c.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = d.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        return if (allDone) {
            Poll.Ready(Try.Ok(Tuple4(a.takeOutput()!!, b.takeOutput()!!, c.takeOutput()!!, d.takeOutput()!!)))
        } else {
            Poll.Pending
        }
    }
}

/**
 * Future for the [tryJoin5] function.
 */
@HiddenFromObjC
public class TryJoin5<Fut1, Fut2, Fut3, Fut4, Fut5, E>(
    future1: Future<Try<Fut1, E>>,
    future2: Future<Try<Fut2, E>>,
    future3: Future<Try<Fut3, E>>,
    future4: Future<Try<Fut4, E>>,
    future5: Future<Try<Fut5, E>>,
) : FusedFuture<Try<Tuple5<Fut1, Fut2, Fut3, Fut4, Fut5>, E>> {
    private val a: TryMaybeDone<Fut1, E> = tryMaybeDone(future1)
    private val b: TryMaybeDone<Fut2, E> = tryMaybeDone(future2)
    private val c: TryMaybeDone<Fut3, E> = tryMaybeDone(future3)
    private val d: TryMaybeDone<Fut4, E> = tryMaybeDone(future4)
    private val e: TryMaybeDone<Fut5, E> = tryMaybeDone(future5)

    override fun isTerminated(): Boolean =
        a.isTerminated() && b.isTerminated() && c.isTerminated() && d.isTerminated() && e.isTerminated()

    override fun poll(context: TaskContext): Poll<Try<Tuple5<Fut1, Fut2, Fut3, Fut4, Fut5>, E>> {
        var allDone = true

        when (val p = a.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = b.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = c.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = d.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        when (val p = e.poll(context)) {
            is Poll.Ready -> {
                when (val v = p.value) {
                    is Try.Err -> return Poll.Ready(Try.Err(v.error))
                    is Try.Ok -> {}
                }
            }
            is Poll.Pending -> allDone = false
        }

        return if (allDone) {
            Poll.Ready(Try.Ok(Tuple5(a.takeOutput()!!, b.takeOutput()!!, c.takeOutput()!!, d.takeOutput()!!, e.takeOutput()!!)))
        } else {
            Poll.Pending
        }
    }
}

/**
 * Joins two try-futures, returning a future that resolves to [Try.Ok] of the pair
 * if both succeed, or [Try.Err] immediately if either fails.
 */
@HiddenFromObjC
public fun <A, B, E> tryJoin(
    futureA: Future<Try<A, E>>,
    futureB: Future<Try<B, E>>,
): TryJoin<A, B, E> = TryJoin(futureA, futureB)

/**
 * Joins three try-futures, returning a future that resolves to [Try.Ok] of the triple
 * if all succeed, or [Try.Err] immediately if any fails.
 */
@HiddenFromObjC
public fun <A, B, C, E> tryJoin3(
    futureA: Future<Try<A, E>>,
    futureB: Future<Try<B, E>>,
    futureC: Future<Try<C, E>>,
): TryJoin3<A, B, C, E> = TryJoin3(futureA, futureB, futureC)

/**
 * Joins four try-futures, returning a future that resolves to [Try.Ok] of the 4-tuple
 * if all succeed, or [Try.Err] immediately if any fails.
 */
@HiddenFromObjC
public fun <A, B, C, D, E> tryJoin4(
    futureA: Future<Try<A, E>>,
    futureB: Future<Try<B, E>>,
    futureC: Future<Try<C, E>>,
    futureD: Future<Try<D, E>>,
): TryJoin4<A, B, C, D, E> = TryJoin4(futureA, futureB, futureC, futureD)

/**
 * Joins five try-futures, returning a future that resolves to [Try.Ok] of the 5-tuple
 * if all succeed, or [Try.Err] immediately if any fails.
 */
@HiddenFromObjC
public fun <A, B, C, D, E5, Err> tryJoin5(
    futureA: Future<Try<A, Err>>,
    futureB: Future<Try<B, Err>>,
    futureC: Future<Try<C, Err>>,
    futureD: Future<Try<D, Err>>,
    futureE: Future<Try<E5, Err>>,
): TryJoin5<A, B, C, D, E5, Err> = TryJoin5(futureA, futureB, futureC, futureD, futureE)

/**
 * Joins a list of try-futures, returning a future that resolves to [Try.Ok] of the list
 * if all succeed, or [Try.Err] immediately if any fails.
 */
@HiddenFromObjC
public fun <T, E> tryJoinAll(futures: List<Future<Try<T, E>>>): Future<Try<List<T>, E>> {
    val results = arrayOfNulls<Any?>(futures.size)
    val done = BooleanArray(futures.size)
    var completedCount = 0
    var error: E? = null
    var errored = false

    return object : Future<Try<List<T>, E>> {
        @Suppress("UNCHECKED_CAST")
        override fun poll(context: TaskContext): Poll<Try<List<T>, E>> {
            if (errored) {
                return Poll.Ready(Try.Err(error as E))
            }

            for (i in futures.indices) {
                if (!done[i]) {
                    when (val p = futures[i].poll(context)) {
                        is Poll.Ready -> {
                            when (val v = p.value) {
                                is Try.Ok -> {
                                    results[i] = v.value
                                    done[i] = true
                                    completedCount++
                                }
                                is Try.Err -> {
                                    error = v.error
                                    errored = true
                                    return Poll.Ready(Try.Err(v.error))
                                }
                            }
                        }
                        is Poll.Pending -> {}
                    }
                }
            }

            return if (completedCount == futures.size) {
                val list = ArrayList<T>(futures.size)
                for (item in results) {
                    list.add(item as T)
                }
                Poll.Ready(Try.Ok(list))
            } else {
                Poll.Pending
            }
        }
    }
}
