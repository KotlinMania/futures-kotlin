// port-lint: source futures-util/src/future/join.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * 4-element tuple for combinators.
 */
@HiddenFromObjC
public data class Tuple4<out A, out B, out C, out D>(
    public val first: A,
    public val second: B,
    public val third: C,
    public val fourth: D,
)

/**
 * 5-element tuple for combinators.
 */
@HiddenFromObjC
public data class Tuple5<out A, out B, out C, out D, out E>(
    public val first: A,
    public val second: B,
    public val third: C,
    public val fourth: D,
    public val fifth: E,
)

/**
 * Future for the [join] function.
 */
@HiddenFromObjC
public class Join<Fut1, Fut2>(
    future1: Future<Fut1>,
    future2: Future<Fut2>,
) : FusedFuture<Pair<Fut1, Fut2>> {
    private val a: MaybeDone<Fut1> = maybeDone(future1)
    private val b: MaybeDone<Fut2> = maybeDone(future2)

    override fun isTerminated(): Boolean = a.isTerminated() && b.isTerminated()

    override fun poll(context: TaskContext): Poll<Pair<Fut1, Fut2>> {
        var allDone = a.poll(context) is Poll.Ready
        allDone = (b.poll(context) is Poll.Ready) && allDone

        return if (allDone) {
            Poll.Ready(Pair(a.takeOutput()!!, b.takeOutput()!!))
        } else {
            Poll.Pending
        }
    }
}

/**
 * Future for the [join3] function.
 */
@HiddenFromObjC
public class Join3<Fut1, Fut2, Fut3>(
    future1: Future<Fut1>,
    future2: Future<Fut2>,
    future3: Future<Fut3>,
) : FusedFuture<Triple<Fut1, Fut2, Fut3>> {
    private val a: MaybeDone<Fut1> = maybeDone(future1)
    private val b: MaybeDone<Fut2> = maybeDone(future2)
    private val c: MaybeDone<Fut3> = maybeDone(future3)

    override fun isTerminated(): Boolean = a.isTerminated() && b.isTerminated() && c.isTerminated()

    override fun poll(context: TaskContext): Poll<Triple<Fut1, Fut2, Fut3>> {
        var allDone = a.poll(context) is Poll.Ready
        allDone = (b.poll(context) is Poll.Ready) && allDone
        allDone = (c.poll(context) is Poll.Ready) && allDone

        return if (allDone) {
            Poll.Ready(Triple(a.takeOutput()!!, b.takeOutput()!!, c.takeOutput()!!))
        } else {
            Poll.Pending
        }
    }
}

/**
 * Future for the [join4] function.
 */
@HiddenFromObjC
public class Join4<Fut1, Fut2, Fut3, Fut4>(
    future1: Future<Fut1>,
    future2: Future<Fut2>,
    future3: Future<Fut3>,
    future4: Future<Fut4>,
) : FusedFuture<Tuple4<Fut1, Fut2, Fut3, Fut4>> {
    private val a: MaybeDone<Fut1> = maybeDone(future1)
    private val b: MaybeDone<Fut2> = maybeDone(future2)
    private val c: MaybeDone<Fut3> = maybeDone(future3)
    private val d: MaybeDone<Fut4> = maybeDone(future4)

    override fun isTerminated(): Boolean =
        a.isTerminated() && b.isTerminated() && c.isTerminated() && d.isTerminated()

    override fun poll(context: TaskContext): Poll<Tuple4<Fut1, Fut2, Fut3, Fut4>> {
        var allDone = a.poll(context) is Poll.Ready
        allDone = (b.poll(context) is Poll.Ready) && allDone
        allDone = (c.poll(context) is Poll.Ready) && allDone
        allDone = (d.poll(context) is Poll.Ready) && allDone

        return if (allDone) {
            Poll.Ready(Tuple4(a.takeOutput()!!, b.takeOutput()!!, c.takeOutput()!!, d.takeOutput()!!))
        } else {
            Poll.Pending
        }
    }
}

/**
 * Future for the [join5] function.
 */
@HiddenFromObjC
public class Join5<Fut1, Fut2, Fut3, Fut4, Fut5>(
    future1: Future<Fut1>,
    future2: Future<Fut2>,
    future3: Future<Fut3>,
    future4: Future<Fut4>,
    future5: Future<Fut5>,
) : FusedFuture<Tuple5<Fut1, Fut2, Fut3, Fut4, Fut5>> {
    private val a: MaybeDone<Fut1> = maybeDone(future1)
    private val b: MaybeDone<Fut2> = maybeDone(future2)
    private val c: MaybeDone<Fut3> = maybeDone(future3)
    private val d: MaybeDone<Fut4> = maybeDone(future4)
    private val e: MaybeDone<Fut5> = maybeDone(future5)

    override fun isTerminated(): Boolean =
        a.isTerminated() && b.isTerminated() && c.isTerminated() && d.isTerminated() && e.isTerminated()

    override fun poll(context: TaskContext): Poll<Tuple5<Fut1, Fut2, Fut3, Fut4, Fut5>> {
        var allDone = a.poll(context) is Poll.Ready
        allDone = (b.poll(context) is Poll.Ready) && allDone
        allDone = (c.poll(context) is Poll.Ready) && allDone
        allDone = (d.poll(context) is Poll.Ready) && allDone
        allDone = (e.poll(context) is Poll.Ready) && allDone

        return if (allDone) {
            Poll.Ready(Tuple5(a.takeOutput()!!, b.takeOutput()!!, c.takeOutput()!!, d.takeOutput()!!, e.takeOutput()!!))
        } else {
            Poll.Pending
        }
    }
}

/**
 * Joins the result of two futures, waiting for them both to complete.
 */
@HiddenFromObjC
public fun <A, B> join(future1: Future<A>, future2: Future<B>): Join<A, B> =
    Join(future1, future2)

/**
 * Joins the result of three futures, waiting for all three to complete.
 */
@HiddenFromObjC
public fun <A, B, C> join3(
    future1: Future<A>,
    future2: Future<B>,
    future3: Future<C>,
): Join3<A, B, C> = Join3(future1, future2, future3)

/**
 * Joins the result of four futures, waiting for all four to complete.
 */
@HiddenFromObjC
public fun <A, B, C, D> join4(
    future1: Future<A>,
    future2: Future<B>,
    future3: Future<C>,
    future4: Future<D>,
): Join4<A, B, C, D> = Join4(future1, future2, future3, future4)

/**
 * Joins the result of five futures, waiting for all five to complete.
 */
@HiddenFromObjC
public fun <A, B, C, D, E> join5(
    future1: Future<A>,
    future2: Future<B>,
    future3: Future<C>,
    future4: Future<D>,
    future5: Future<E>,
): Join5<A, B, C, D, E> = Join5(future1, future2, future3, future4, future5)
