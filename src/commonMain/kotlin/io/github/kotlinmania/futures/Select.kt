// port-lint: source future/select.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Waits for either one of two differently-typed futures to complete.
 *
 * Resolves to [Either.Left] with `(valueA, futureB)` if [futureA] completes first,
 * or [Either.Right] with `(valueB, futureA)` if [futureB] completes first.
 */
@HiddenFromObjC
public fun <A, B> select(
    futureA: Future<A>,
    futureB: Future<B>,
): Future<Either<Pair<A, Future<B>>, Pair<B, Future<A>>>> {
    var aOpt: Future<A>? = futureA
    var bOpt: Future<B>? = futureB

    return object : Future<Either<Pair<A, Future<B>>, Pair<B, Future<A>>>> {
        override fun poll(context: TaskContext): Poll<Either<Pair<A, Future<B>>, Pair<B, Future<A>>>> {
            val a = aOpt ?: error("cannot poll select twice")
            val b = bOpt ?: error("cannot poll select twice")

            when (val resA = a.poll(context)) {
                is Poll.Ready -> {
                    aOpt = null
                    bOpt = null
                    return Poll.Ready(Either.Left(Pair(resA.value, b)))
                }
                is Poll.Pending -> {}
            }

            when (val resB = b.poll(context)) {
                is Poll.Ready -> {
                    aOpt = null
                    bOpt = null
                    return Poll.Ready(Either.Right(Pair(resB.value, a)))
                }
                is Poll.Pending -> {}
            }

            return Poll.Pending
        }
    }
}

/**
 * Waits for either one of two futures to complete, resolving to the value of the completed future.
 */
@HiddenFromObjC
public fun <A, B> selectValues(
    futureA: Future<A>,
    futureB: Future<B>,
): Future<Either<A, B>> {
    var aOpt: Future<A>? = futureA
    var bOpt: Future<B>? = futureB

    return object : Future<Either<A, B>> {
        override fun poll(context: TaskContext): Poll<Either<A, B>> {
            val a = aOpt ?: error("cannot poll selectValues twice")
            val b = bOpt ?: error("cannot poll selectValues twice")

            when (val resA = a.poll(context)) {
                is Poll.Ready -> {
                    aOpt = null
                    bOpt = null
                    return Poll.Ready(Either.Left(resA.value))
                }
                is Poll.Pending -> {}
            }

            when (val resB = b.poll(context)) {
                is Poll.Ready -> {
                    aOpt = null
                    bOpt = null
                    return Poll.Ready(Either.Right(resB.value))
                }
                is Poll.Pending -> {}
            }

            return Poll.Pending
        }
    }
}
