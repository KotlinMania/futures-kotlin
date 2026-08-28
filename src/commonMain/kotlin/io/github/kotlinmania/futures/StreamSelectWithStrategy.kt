// port-lint: source futures-util/src/stream/select_with_strategy.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Type to tell [SelectWithStrategy] which stream to poll next.
 */
@HiddenFromObjC
public enum class PollNext {
    /** Poll the first stream. */
    Left,

    /** Poll the second stream. */
    Right;

    /** Toggle the value and return the other one. */
    public fun toggle(): PollNext = other()

    /** Returns the other side. */
    public fun other(): PollNext =
        when (this) {
            Left -> Right
            Right -> Left
        }

    public companion object {
        /** Default side is [Left]. */
        public fun default(): PollNext = Left
    }
}

/**
 * Internal state for [SelectWithStrategy].
 */
internal enum class InternalState {
    Start,
    LeftFinished,
    RightFinished,
    BothFinished;

    fun finish(ps: PollNext): InternalState =
        when (this) {
            Start ->
                when (ps) {
                    PollNext.Left -> LeftFinished
                    PollNext.Right -> RightFinished
                }
            LeftFinished ->
                when (ps) {
                    PollNext.Right -> BothFinished
                    else -> this
                }
            RightFinished ->
                when (ps) {
                    PollNext.Left -> BothFinished
                    else -> this
                }
            BothFinished -> BothFinished
        }
}

/**
 * Stream for the [selectWithStrategy] function.
 */
@HiddenFromObjC
public class SelectWithStrategy<T, St1 : Stream<T>, St2 : Stream<T>, State>(
    private val stream1: St1,
    private val stream2: St2,
    private var state: State,
    private val clos: (State) -> PollNext,
) : FusedStream<T> {
    private var internalState: InternalState = InternalState.Start

    /**
     * Acquires references to the underlying streams that this combinator is pulling from.
     */
    public fun getRef(): Pair<St1, St2> = Pair(stream1, stream2)

    /**
     * Acquires mutable references to the underlying streams that this combinator is pulling from.
     */
    public fun getMut(): Pair<St1, St2> = Pair(stream1, stream2)

    /**
     * Acquires pinned mutable references to the underlying streams that this combinator is pulling from.
     */
    public fun getPinMut(): Pair<St1, St2> = Pair(stream1, stream2)

    /**
     * Consumes this combinator, returning the underlying streams.
     */
    public fun intoInner(): Pair<St1, St2> = Pair(stream1, stream2)

    override fun isTerminated(): Boolean = internalState == InternalState.BothFinished

    private fun pollSide(side: PollNext, context: TaskContext): Poll<Yield<T>> =
        when (side) {
            PollNext.Left -> stream1.pollNext(context)
            PollNext.Right -> stream2.pollNext(context)
        }

    private fun pollInner(side: PollNext, context: TaskContext): Poll<Yield<T>> {
        val firstDone =
            when (val p = pollSide(side, context)) {
                is Poll.Ready ->
                    when (p.value) {
                        is Yield.Value -> return p
                        Yield.End -> {
                            internalState = internalState.finish(side)
                            true
                        }
                    }
                Poll.Pending -> false
            }

        val other = side.other()
        return when (val p = pollSide(other, context)) {
            is Poll.Ready ->
                when (p.value) {
                    is Yield.Value -> p
                    Yield.End -> {
                        internalState = internalState.finish(other)
                        if (firstDone) {
                            Poll.Ready(Yield.End)
                        } else {
                            Poll.Pending
                        }
                    }
                }
            Poll.Pending -> Poll.Pending
        }
    }

    override fun pollNext(context: TaskContext): Poll<Yield<T>> =
        when (internalState) {
            InternalState.Start -> {
                val nextSide = clos(state)
                pollInner(nextSide, context)
            }
            InternalState.LeftFinished ->
                when (val p = stream2.pollNext(context)) {
                    is Poll.Ready ->
                        when (p.value) {
                            is Yield.Value -> p
                            Yield.End -> {
                                internalState = InternalState.BothFinished
                                Poll.Ready(Yield.End)
                            }
                        }
                    Poll.Pending -> Poll.Pending
                }
            InternalState.RightFinished ->
                when (val p = stream1.pollNext(context)) {
                    is Poll.Ready ->
                        when (p.value) {
                            is Yield.Value -> p
                            Yield.End -> {
                                internalState = InternalState.BothFinished
                                Poll.Ready(Yield.End)
                            }
                        }
                    Poll.Pending -> Poll.Pending
                }
            InternalState.BothFinished -> Poll.Ready(Yield.End)
        }

    /** Format string representation. */
    public fun fmt(): String = "SelectWithStrategy(stream1=$stream1, stream2=$stream2, state=$state)"

    override fun toString(): String = fmt()

    override fun sizeHint(): SizeHint {
        if (internalState == InternalState.BothFinished) return SizeHint(0, 0)
        val hint1 = stream1.sizeHint()
        val hint2 = stream2.sizeHint()
        val lower = (hint1.lower.toLong() + hint2.lower.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val upper =
            if (hint1.upper != null && hint2.upper != null) {
                (hint1.upper.toLong() + hint2.upper.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            } else {
                null
            }
        return SizeHint(lower, upper)
    }

    public companion object {
        internal fun <T, St1 : Stream<T>, St2 : Stream<T>, State> new(
            stream1: St1,
            stream2: St2,
            initialState: State,
            which: (State) -> PollNext,
        ): SelectWithStrategy<T, St1, St2, State> =
            SelectWithStrategy(stream1, stream2, initialState, which)
    }
}

/**
 * Pull items from both streams according to a strategy function.
 */
@HiddenFromObjC
public fun <T, St1 : Stream<T>, St2 : Stream<T>, State> selectWithStrategy(
    stream1: St1,
    stream2: St2,
    initialState: State,
    which: (State) -> PollNext,
): SelectWithStrategy<T, St1, St2, State> =
    SelectWithStrategy.new(stream1, stream2, initialState, which)

/**
 * Pull items from both streams according to a stateless strategy function.
 */
@HiddenFromObjC
public fun <T, St1 : Stream<T>, St2 : Stream<T>> selectWithStrategy(
    stream1: St1,
    stream2: St2,
    which: () -> PollNext,
): SelectWithStrategy<T, St1, St2, Unit> =
    SelectWithStrategy.new(stream1, stream2, Unit) { which() }
