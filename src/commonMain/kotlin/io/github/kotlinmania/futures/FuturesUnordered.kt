// port-lint: source stream/futures_unordered/mod.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.native.HiddenFromObjC

/**
 * A set of futures which may complete in any order.
 *
 * This structure manages a set of futures. Futures managed by [FuturesUnordered]
 * will only be polled when they generate wake-up notifications.
 */
@HiddenFromObjC
public class FuturesUnordered<T> :
    FusedStream<T>,
    Iterable<Future<T>> {
    private val readyToRunQueue = ArrayDeque<Long>()
    private val tasks = mutableMapOf<Long, TaskNode<T>>()
    private val parentWaker = AtomicWaker()
    private val isTerminatedFlag = AtomicBoolean(false)
    private val nextTaskId = AtomicLong(0L)
    private val lock = Lock.new(Unit)

    private class TaskNode<T>(
        val id: Long,
        var future: Future<T>?,
        val queued: AtomicBoolean,
        val woken: AtomicBoolean,
    ) {
        lateinit var waker: Waker
    }

    public constructor()

    public constructor(futures: Iterable<Future<T>>) {
        for (fut in futures) {
            push(fut)
        }
    }

    private inline fun <R> withLock(block: () -> R): R {
        var slot: TryLock<Unit>? = null
        while (slot == null) {
            slot = lock.tryLock()
        }
        try {
            return block()
        } finally {
            slot.unlock()
        }
    }

    /**
     * Push a future into the set.
     */
    public fun push(future: Future<T>) {
        val id = nextTaskId.fetchAndAdd(1L)
        val queued = AtomicBoolean(true)
        val woken = AtomicBoolean(false)
        val node = TaskNode(id, future, queued, woken)

        node.waker =
            Waker {
                node.woken.store(true)
                if (node.queued.compareAndSet(false, true)) {
                    withLock {
                        readyToRunQueue.add(id)
                    }
                    parentWaker.wake()
                }
            }

        withLock {
            tasks[id] = node
            readyToRunQueue.add(id)
            isTerminatedFlag.store(false)
        }
    }

    /**
     * Extends this [FuturesUnordered] with the contents of an [Iterable] of futures.
     */
    public fun extend(futures: Iterable<Future<T>>) {
        for (fut in futures) {
            push(fut)
        }
    }

    /**
     * Returns the number of futures contained in the set.
     */
    public fun len(): Int = withLock { tasks.size }

    /**
     * Returns the number of futures contained in the set.
     */
    public val size: Int get() = len()

    /**
     * Returns true if the set contains no futures.
     */
    public fun isEmpty(): Boolean = len() == 0

    /**
     * Returns true if the set contains one or more futures.
     */
    public fun isNotEmpty(): Boolean = !isEmpty()

    /**
     * Clears the set, removing all futures.
     */
    public fun clear() {
        withLock {
            tasks.clear()
            readyToRunQueue.clear()
            isTerminatedFlag.store(false)
        }
    }

    override fun isTerminated(): Boolean = isTerminatedFlag.load()

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        parentWaker.register(context.waker)
        var polled = 0
        var yielded = 0
        val initialLen = len()

        while (true) {
            val taskPair =
                withLock {
                    if (readyToRunQueue.isEmpty()) {
                        null
                    } else {
                        val id = readyToRunQueue.removeFirst()
                        val node = tasks[id]
                        if (node != null && node.future != null) {
                            node.queued.store(false)
                            node.woken.store(false)
                            Pair(node, node.future!!)
                        } else {
                            null
                        }
                    }
                }

            if (taskPair == null) {
                break
            }

            val (node, fut) = taskPair
            val taskContext = TaskContext(waker = node.waker)
            val pollResult = fut.poll(taskContext)
            polled++

            when (pollResult) {
                is Poll.Ready -> {
                    withLock {
                        tasks.remove(node.id)
                        node.future = null
                    }
                    return Poll.ready(Yield.value(pollResult.value))
                }
                Poll.Pending -> {
                    if (node.woken.load()) {
                        yielded++
                    }
                    if (yielded >= 2 || polled >= initialLen) {
                        context.wakeByRef()
                        return Poll.pending()
                    }
                }
            }
        }

        val empty = withLock { tasks.isEmpty() }
        return if (empty) {
            isTerminatedFlag.store(true)
            Poll.ready(Yield.end())
        } else {
            Poll.pending()
        }
    }

    override fun sizeHint(): SizeHint {
        val count = len()
        return SizeHint(lower = count, upper = count)
    }

    override fun iterator(): Iterator<Future<T>> {
        val snapshot =
            withLock {
                tasks.values.mapNotNull { it.future }
            }
        return snapshot.iterator()
    }

    override fun toString(): String = "FuturesUnordered(len=${len()})"

    public companion object {
        public fun <T> new(): FuturesUnordered<T> = FuturesUnordered()

        public fun <T> default(): FuturesUnordered<T> = FuturesUnordered()

        public fun <T> fromIterable(futures: Iterable<Future<T>>): FuturesUnordered<T> =
            FuturesUnordered(futures)
    }
}

/**
 * Creates a [FuturesUnordered] from an [Iterable] of futures.
 */
@HiddenFromObjC
public fun <T> futuresUnordered(futures: Iterable<Future<T>>): FuturesUnordered<T> =
    FuturesUnordered.fromIterable(futures)

/**
 * Collects an [Iterable] of futures into a [FuturesUnordered].
 */
@HiddenFromObjC
public fun <T> Iterable<Future<T>>.collectFuturesUnordered(): FuturesUnordered<T> =
    FuturesUnordered.fromIterable(this)
