// port-lint: source futures-util/src/stream/stream/split.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Error indicating two split halves did not originate from the same stream.
 */
@HiddenFromObjC
public class SplitReuniteError(
    public val sink: SplitSink<*, *, *>,
    public val stream: SplitStream<*, *>,
) : Exception("tried to reunite SplitSink and SplitStream that don't form a pair")

/**
 * A [Stream] part of the split pair.
 */
@HiddenFromObjC
public class SplitStream<S : Stream<Item>, Item> internal constructor(
    internal val lock: BiLock<S>,
) : Stream<Item> {
    public fun isPairOf(other: SplitSink<*, *, *>): Boolean =
        @Suppress("UNCHECKED_CAST")
        lock.isPairOf(other.lock as BiLock<S>)

    public fun reunite(other: SplitSink<*, *, *>): Result<S> {
        @Suppress("UNCHECKED_CAST")
        val res = lock.reunite(other.lock as BiLock<S>)
        return if (res.isSuccess) {
            Result.success(res.getOrThrow())
        } else {
            Result.failure(SplitReuniteError(other, this))
        }
    }

    override fun pollNext(context: TaskContext): Poll<Yield<Item>> =
        when (val lockPoll = lock.pollLock(context)) {
            is Poll.Ready -> {
                val guard = lockPoll.value
                try {
                    guard.get().pollNext(context)
                } finally {
                    guard.unlock()
                }
            }
            Poll.Pending -> Poll.pending()
        }
}

/**
 * A [Sink] part of the split pair.
 */
@HiddenFromObjC
public class SplitSink<S : Sink<Item, E>, Item, E> internal constructor(
    internal val lock: BiLock<S>,
) : Sink<Item, E> {
    private var slot: Item? = null
    private var hasSlot: Boolean = false

    public fun isPairOf(other: SplitStream<*, *>): Boolean =
        @Suppress("UNCHECKED_CAST")
        lock.isPairOf(other.lock as BiLock<S>)

    public fun reunite(other: SplitStream<*, *>): Result<S> {
        @Suppress("UNCHECKED_CAST")
        val res = lock.reunite(other.lock as BiLock<S>)
        return if (res.isSuccess) {
            Result.success(res.getOrThrow())
        } else {
            Result.failure(SplitReuniteError(this, other))
        }
    }

    override fun pollReady(context: TaskContext): Poll<SinkOutcome<E>> {
        if (!hasSlot) {
            return Poll.ready(SinkOutcome.ready())
        }
        return when (val lockPoll = lock.pollLock(context)) {
            is Poll.Ready -> {
                val guard = lockPoll.value
                try {
                    val sink = guard.get()
                    val readyPoll = sink.pollReady(context)
                    if (readyPoll is Poll.Ready) {
                        when (val outcome = readyPoll.value) {
                            SinkOutcome.Ready -> {
                                @Suppress("UNCHECKED_CAST")
                                val item = slot as Item
                                slot = null
                                hasSlot = false
                                val sendOutcome = sink.startSend(item)
                                Poll.ready(sendOutcome)
                            }
                            is SinkOutcome.Err -> Poll.ready(outcome)
                        }
                    } else {
                        Poll.pending()
                    }
                } finally {
                    guard.unlock()
                }
            }
            Poll.Pending -> Poll.pending()
        }
    }

    override fun startSend(item: Item): SinkOutcome<E> {
        if (hasSlot) {
            throw IllegalStateException("SplitSink cannot accept another item before the previous item is flushed")
        }
        slot = item
        hasSlot = true
        return SinkOutcome.ready()
    }

    override fun pollFlush(context: TaskContext): Poll<SinkOutcome<E>> =
        when (val lockPoll = lock.pollLock(context)) {
            is Poll.Ready -> {
                val guard = lockPoll.value
                try {
                    val sink = guard.get()
                    if (hasSlot) {
                        val readyPoll = sink.pollReady(context)
                        if (readyPoll is Poll.Ready) {
                            when (val outcome = readyPoll.value) {
                                SinkOutcome.Ready -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val item = slot as Item
                                    slot = null
                                    hasSlot = false
                                    val sendOutcome = sink.startSend(item)
                                    if (sendOutcome is SinkOutcome.Err) {
                                        Poll.ready(sendOutcome)
                                    } else {
                                        sink.pollFlush(context)
                                    }
                                }
                                is SinkOutcome.Err -> Poll.ready(outcome)
                            }
                        } else {
                            Poll.pending()
                        }
                    } else {
                        sink.pollFlush(context)
                    }
                } finally {
                    guard.unlock()
                }
            }
            Poll.Pending -> Poll.pending()
        }

    override fun pollClose(context: TaskContext): Poll<SinkOutcome<E>> =
        when (val lockPoll = lock.pollLock(context)) {
            is Poll.Ready -> {
                val guard = lockPoll.value
                try {
                    val sink = guard.get()
                    if (hasSlot) {
                        val readyPoll = sink.pollReady(context)
                        if (readyPoll is Poll.Ready) {
                            when (val outcome = readyPoll.value) {
                                SinkOutcome.Ready -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val item = slot as Item
                                    slot = null
                                    hasSlot = false
                                    val sendOutcome = sink.startSend(item)
                                    if (sendOutcome is SinkOutcome.Err) {
                                        Poll.ready(sendOutcome)
                                    } else {
                                        sink.pollClose(context)
                                    }
                                }
                                is SinkOutcome.Err -> Poll.ready(outcome)
                            }
                        } else {
                            Poll.pending()
                        }
                    } else {
                        sink.pollClose(context)
                    }
                } finally {
                    guard.unlock()
                }
            }
            Poll.Pending -> Poll.pending()
        }
}

/**
 * Splits a stream that also implements [Sink] into separate [SplitStream] and [SplitSink] halves.
 */
@HiddenFromObjC
public fun <S, Item, E> S.split(): Pair<SplitSink<S, Item, E>, SplitStream<S, Item>>
    where S : Stream<Item>, S : Sink<Item, E> {
    val (lock1, lock2) = BiLock.new(this)
    return Pair(SplitSink(lock1), SplitStream(lock2))
}
