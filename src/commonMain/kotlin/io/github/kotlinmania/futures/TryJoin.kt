// port-lint: source futures-util/src/future/try_join.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Joins two try-futures, returning a future that resolves to [Try.Ok] of the pair
 * if both succeed, or [Try.Err] immediately if either fails.
 */
@HiddenFromObjC
public fun <A, B, E> tryJoin(
    futureA: Future<Try<A, E>>,
    futureB: Future<Try<B, E>>,
): Future<Try<Pair<A, B>, E>> {
    var resA: A? = null
    var doneA = false
    var resB: B? = null
    var doneB = false
    var error: E? = null
    var errored = false

    return object : Future<Try<Pair<A, B>, E>> {
        @Suppress("UNCHECKED_CAST")
        override fun poll(context: TaskContext): Poll<Try<Pair<A, B>, E>> {
            if (errored) {
                return Poll.Ready(Try.Err(error as E))
            }

            if (!doneA) {
                when (val p = futureA.poll(context)) {
                    is Poll.Ready -> {
                        when (val v = p.value) {
                            is Try.Ok -> {
                                resA = v.value
                                doneA = true
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

            if (!doneB) {
                when (val p = futureB.poll(context)) {
                    is Poll.Ready -> {
                        when (val v = p.value) {
                            is Try.Ok -> {
                                resB = v.value
                                doneB = true
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

            return if (doneA && doneB) {
                Poll.Ready(Try.Ok(Pair(resA as A, resB as B)))
            } else {
                Poll.Pending
            }
        }
    }
}

/**
 * Joins three try-futures, returning a future that resolves to [Try.Ok] of the triple
 * if all succeed, or [Try.Err] immediately if any fails.
 */
@HiddenFromObjC
public fun <A, B, C, E> tryJoin3(
    futureA: Future<Try<A, E>>,
    futureB: Future<Try<B, E>>,
    futureC: Future<Try<C, E>>,
): Future<Try<Triple<A, B, C>, E>> {
    var resA: A? = null
    var doneA = false
    var resB: B? = null
    var doneB = false
    var resC: C? = null
    var doneC = false
    var error: E? = null
    var errored = false

    return object : Future<Try<Triple<A, B, C>, E>> {
        @Suppress("UNCHECKED_CAST")
        override fun poll(context: TaskContext): Poll<Try<Triple<A, B, C>, E>> {
            if (errored) {
                return Poll.Ready(Try.Err(error as E))
            }

            if (!doneA) {
                when (val p = futureA.poll(context)) {
                    is Poll.Ready -> {
                        when (val v = p.value) {
                            is Try.Ok -> {
                                resA = v.value
                                doneA = true
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

            if (!doneB) {
                when (val p = futureB.poll(context)) {
                    is Poll.Ready -> {
                        when (val v = p.value) {
                            is Try.Ok -> {
                                resB = v.value
                                doneB = true
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

            if (!doneC) {
                when (val p = futureC.poll(context)) {
                    is Poll.Ready -> {
                        when (val v = p.value) {
                            is Try.Ok -> {
                                resC = v.value
                                doneC = true
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

            return if (doneA && doneB && doneC) {
                Poll.Ready(Try.Ok(Triple(resA as A, resB as B, resC as C)))
            } else {
                Poll.Pending
            }
        }
    }
}

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
