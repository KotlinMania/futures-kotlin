// port-lint: source futures-util/src/future/try_select.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

public typealias EitherOk<A, B> = Either<Pair<A, Future<B>>, Pair<B, Future<A>>>
public typealias EitherErr<A, B, E1, E2> = Either<Pair<E1, Future<B>>, Pair<E2, Future<A>>>

/**
 * Future for the [trySelect] function.
 */
@HiddenFromObjC
public class TrySelect<A, B, E1, E2>(
    future1: Future<Try<A, E1>>,
    future2: Future<Try<B, E2>>,
) : Future<Try<Either<Pair<A, Future<Try<B, E2>>>, Pair<B, Future<Try<A, E1>>>>, Either<Pair<E1, Future<Try<B, E2>>>, Pair<E2, Future<Try<A, E1>>>>>> {
    private var aOpt: Future<Try<A, E1>>? = future1
    private var bOpt: Future<Try<B, E2>>? = future2

    override fun poll(
        context: TaskContext,
    ): Poll<Try<Either<Pair<A, Future<Try<B, E2>>>, Pair<B, Future<Try<A, E1>>>>, Either<Pair<E1, Future<Try<B, E2>>>, Pair<E2, Future<Try<A, E1>>>>>> {
        val a = aOpt ?: error("cannot poll TrySelect twice")
        val b = bOpt ?: error("cannot poll TrySelect twice")

        when (val resA = a.poll(context)) {
            is Poll.Ready -> {
                aOpt = null
                bOpt = null
                return when (val v = resA.value) {
                    is Try.Ok -> Poll.Ready(Try.Ok(Either.Left(Pair(v.value, b))))
                    is Try.Err -> Poll.Ready(Try.Err(Either.Left(Pair(v.error, b))))
                }
            }
            is Poll.Pending -> {}
        }

        when (val resB = b.poll(context)) {
            is Poll.Ready -> {
                aOpt = null
                bOpt = null
                return when (val v = resB.value) {
                    is Try.Ok -> Poll.Ready(Try.Ok(Either.Right(Pair(v.value, a))))
                    is Try.Err -> Poll.Ready(Try.Err(Either.Right(Pair(v.error, a))))
                }
            }
            is Poll.Pending -> {}
        }

        return Poll.Pending
    }
}

/**
 * Waits for either one of two differently-typed try-futures to complete.
 */
@HiddenFromObjC
public fun <A, B, E1, E2> trySelect(
    future1: Future<Try<A, E1>>,
    future2: Future<Try<B, E2>>,
): TrySelect<A, B, E1, E2> = TrySelect(future1, future2)
