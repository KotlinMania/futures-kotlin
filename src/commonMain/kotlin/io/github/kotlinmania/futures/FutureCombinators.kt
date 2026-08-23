// port-lint: source futures-util/src/future/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Creates a future that is immediately ready with a value.
 */
@HiddenFromObjC
public fun <T> ready(value: T): Future<T> = Future { Poll.Ready(value) }

/**
 * Creates a future that never completes (always pending).
 */
@HiddenFromObjC
public fun <T> pending(): Future<T> = Future { Poll.Pending }

/**
 * Creates a new future wrapping around a function returning [Poll].
 */
@HiddenFromObjC
public fun <T> pollFn(block: (TaskContext) -> Poll<T>): Future<T> =
    object : Future<T> {
        override fun poll(context: TaskContext): Poll<T> = block(context)
    }

/**
 * Joins the result of two futures, waiting for them both to complete.
 */
@HiddenFromObjC
public fun <A, B> join(futureA: Future<A>, futureB: Future<B>): Future<Pair<A, B>> {
    var resA: A? = null
    var doneA = false
    var resB: B? = null
    var doneB = false

    return object : Future<Pair<A, B>> {
        @Suppress("UNCHECKED_CAST")
        override fun poll(context: TaskContext): Poll<Pair<A, B>> {
            if (!doneA) {
                when (val p = futureA.poll(context)) {
                    is Poll.Ready -> {
                        resA = p.value
                        doneA = true
                    }
                    is Poll.Pending -> {}
                }
            }
            if (!doneB) {
                when (val p = futureB.poll(context)) {
                    is Poll.Ready -> {
                        resB = p.value
                        doneB = true
                    }
                    is Poll.Pending -> {}
                }
            }

            return if (doneA && doneB) {
                Poll.Ready(Pair(resA as A, resB as B))
            } else {
                Poll.Pending
            }
        }
    }
}

/**
 * Joins the result of three futures, waiting for all three to complete.
 */
@HiddenFromObjC
public fun <A, B, C> join3(
    futureA: Future<A>,
    futureB: Future<B>,
    futureC: Future<C>,
): Future<Triple<A, B, C>> {
    var resA: A? = null
    var doneA = false
    var resB: B? = null
    var doneB = false
    var resC: C? = null
    var doneC = false

    return object : Future<Triple<A, B, C>> {
        @Suppress("UNCHECKED_CAST")
        override fun poll(context: TaskContext): Poll<Triple<A, B, C>> {
            if (!doneA) {
                when (val p = futureA.poll(context)) {
                    is Poll.Ready -> {
                        resA = p.value
                        doneA = true
                    }
                    is Poll.Pending -> {}
                }
            }
            if (!doneB) {
                when (val p = futureB.poll(context)) {
                    is Poll.Ready -> {
                        resB = p.value
                        doneB = true
                    }
                    is Poll.Pending -> {}
                }
            }
            if (!doneC) {
                when (val p = futureC.poll(context)) {
                    is Poll.Ready -> {
                        resC = p.value
                        doneC = true
                    }
                    is Poll.Pending -> {}
                }
            }

            return if (doneA && doneB && doneC) {
                Poll.Ready(Triple(resA as A, resB as B, resC as C))
            } else {
                Poll.Pending
            }
        }
    }
}

/**
 * Joins a collection of futures, resolving to a list of results when all complete.
 */
@HiddenFromObjC
public fun <T> joinAll(futures: List<Future<T>>): Future<List<T>> {
    val results = arrayOfNulls<Any?>(futures.size)
    val done = BooleanArray(futures.size)
    var completedCount = 0

    return object : Future<List<T>> {
        @Suppress("UNCHECKED_CAST")
        override fun poll(context: TaskContext): Poll<List<T>> {
            for (i in futures.indices) {
                if (!done[i]) {
                    when (val p = futures[i].poll(context)) {
                        is Poll.Ready -> {
                            results[i] = p.value
                            done[i] = true
                            completedCount++
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
                Poll.Ready(list)
            } else {
                Poll.Pending
            }
        }
    }
}

/**
 * Maps this future's output to a different value.
 */
@HiddenFromObjC
public fun <T, R> Future<T>.map(transform: (T) -> R): Future<R> {
    val source = this
    return object : Future<R> {
        override fun poll(context: TaskContext): Poll<R> {
            return when (val p = source.poll(context)) {
                is Poll.Ready -> Poll.Ready(transform(p.value))
                is Poll.Pending -> Poll.Pending
            }
        }
    }
}
