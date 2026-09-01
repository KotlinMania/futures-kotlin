// port-lint: source stream/futures_ordered.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

internal class OrderWrapper<T>(
    val data: T,
    val index: Long,
) : Comparable<OrderWrapper<T>> {
    override fun equals(other: Any?): Boolean =
        other is OrderWrapper<*> && this.index == other.index

    override fun hashCode(): Int = index.hashCode()

    override fun compareTo(other: OrderWrapper<T>): Int =
        index.compareTo(other.index)

    override fun toString(): String = "OrderWrapper(data=$data, index=$index)"
}

private class MinHeap<T : Comparable<T>> {
    private val heap = ArrayList<T>()
    val size: Int get() = heap.size
    val isEmpty: Boolean get() = heap.isEmpty()

    fun peek(): T? = heap.firstOrNull()

    fun push(item: T) {
        heap.add(item)
        siftUp(heap.size - 1)
    }

    fun pop(): T? {
        if (heap.isEmpty()) return null
        val top = heap[0]
        val last = heap.removeAt(heap.size - 1)
        if (heap.isNotEmpty()) {
            heap[0] = last
            siftDown(0)
        }
        return top
    }

    fun clear() {
        heap.clear()
    }

    private fun siftUp(index: Int) {
        var child = index
        while (child > 0) {
            val parent = (child - 1) / 2
            if (heap[child] < heap[parent]) {
                val temp = heap[child]
                heap[child] = heap[parent]
                heap[parent] = temp
                child = parent
            } else {
                break
            }
        }
    }

    private fun siftDown(index: Int) {
        var parent = index
        while (true) {
            val left = 2 * parent + 1
            val right = 2 * parent + 2
            var smallest = parent

            if (left < heap.size && heap[left] < heap[smallest]) {
                smallest = left
            }
            if (right < heap.size && heap[right] < heap[smallest]) {
                smallest = right
            }
            if (smallest != parent) {
                val temp = heap[parent]
                heap[parent] = heap[smallest]
                heap[smallest] = temp
                parent = smallest
            } else {
                break
            }
        }
    }
}

/**
 * An unbounded queue of futures.
 *
 * Imposes a FIFO order on top of the set of futures. While futures in the set will
 * race to completion in parallel, results will only be returned in the order their
 * originating futures were added to the queue.
 */
@HiddenFromObjC
public class FuturesOrdered<T> :
    FusedStream<T>,
    Iterable<Future<T>> {
    private val inProgressQueue = FuturesUnordered<OrderWrapper<T>>()
    private val queuedOutputs = MinHeap<OrderWrapper<T>>()
    private var nextIncomingIndex: Long = 0L
    private var nextOutgoingIndex: Long = 0L
    private val inFlightFutures = mutableListOf<Future<T>>()

    public constructor()

    public constructor(futures: Iterable<Future<T>>) {
        for (fut in futures) {
            pushBack(fut)
        }
    }

    /**
     * Pushes a future to the back of the queue.
     */
    public fun pushBack(future: Future<T>) {
        val index = nextIncomingIndex
        nextIncomingIndex++
        inFlightFutures.add(future)
        val wrapped =
            object : Future<OrderWrapper<T>> {
                override fun poll(context: TaskContext): Poll<OrderWrapper<T>> =
                    when (val p = future.poll(context)) {
                        is Poll.Ready -> Poll.ready(OrderWrapper(p.value, index))
                        Poll.Pending -> Poll.pending()
                    }
            }
        inProgressQueue.push(wrapped)
    }

    /**
     * Push a future into the queue (alias for [pushBack]).
     */
    public fun push(future: Future<T>) {
        pushBack(future)
    }

    /**
     * Extends this [FuturesOrdered] with the contents of an [Iterable] of futures.
     */
    public fun extend(futures: Iterable<Future<T>>) {
        for (fut in futures) {
            pushBack(fut)
        }
    }

    /**
     * Pushes a future to the front of the queue.
     */
    public fun pushFront(future: Future<T>) {
        nextOutgoingIndex--
        val index = nextOutgoingIndex
        inFlightFutures.add(0, future)
        val wrapped =
            object : Future<OrderWrapper<T>> {
                override fun poll(context: TaskContext): Poll<OrderWrapper<T>> =
                    when (val p = future.poll(context)) {
                        is Poll.Ready -> Poll.ready(OrderWrapper(p.value, index))
                        Poll.Pending -> Poll.pending()
                    }
            }
        inProgressQueue.push(wrapped)
    }

    /**
     * Returns the total number of in-flight futures.
     */
    public fun len(): Int = inProgressQueue.len() + queuedOutputs.size

    /**
     * Returns the total number of in-flight futures.
     */
    public val size: Int get() = len()

    /**
     * Returns true if the queue contains no futures.
     */
    public fun isEmpty(): Boolean = inProgressQueue.isEmpty() && queuedOutputs.isEmpty

    /**
     * Returns true if the queue contains one or more futures.
     */
    public fun isNotEmpty(): Boolean = !isEmpty()

    /**
     * Clears the queue, removing all futures and outputs.
     */
    public fun clear() {
        inProgressQueue.clear()
        queuedOutputs.clear()
        inFlightFutures.clear()
        nextIncomingIndex = 0L
        nextOutgoingIndex = 0L
    }

    override fun isTerminated(): Boolean =
        inProgressQueue.isTerminated() && queuedOutputs.isEmpty

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        val nextOutput = queuedOutputs.peek()
        if (nextOutput != null && nextOutput.index == nextOutgoingIndex) {
            nextOutgoingIndex++
            queuedOutputs.pop()
            return Poll.ready(Yield.value(nextOutput.data))
        }

        while (true) {
            when (val p = inProgressQueue.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            val output = y.value
                            if (output.index == nextOutgoingIndex) {
                                nextOutgoingIndex++
                                return Poll.ready(Yield.value(output.data))
                            } else {
                                queuedOutputs.push(output)
                            }
                        }
                        Yield.End -> {
                            val queued = queuedOutputs.peek()
                            if (queued != null && queued.index == nextOutgoingIndex) {
                                nextOutgoingIndex++
                                queuedOutputs.pop()
                                return Poll.ready(Yield.value(queued.data))
                            }
                            return if (queuedOutputs.isEmpty) {
                                Poll.ready(Yield.end())
                            } else {
                                Poll.pending()
                            }
                        }
                    }
                }
                Poll.Pending -> {
                    val queued = queuedOutputs.peek()
                    if (queued != null && queued.index == nextOutgoingIndex) {
                        nextOutgoingIndex++
                        queuedOutputs.pop()
                        return Poll.ready(Yield.value(queued.data))
                    }
                    return Poll.pending()
                }
            }
        }
    }

    override fun sizeHint(): SizeHint {
        val count = len()
        return SizeHint(lower = count, upper = count)
    }

    override fun iterator(): Iterator<Future<T>> = inFlightFutures.iterator()

    override fun toString(): String = "FuturesOrdered { ... }"

    public companion object {
        public fun <T> new(): FuturesOrdered<T> = FuturesOrdered()

        public fun <T> default(): FuturesOrdered<T> = FuturesOrdered()

        public fun <T> fromIterable(futures: Iterable<Future<T>>): FuturesOrdered<T> =
            FuturesOrdered(futures)

        public fun <T> fromIter(futures: Iterable<Future<T>>): FuturesOrdered<T> =
            FuturesOrdered(futures)
    }
}

/**
 * Creates a [FuturesOrdered] from an [Iterable] of futures.
 */
@HiddenFromObjC
public fun <T> futuresOrdered(futures: Iterable<Future<T>>): FuturesOrdered<T> =
    FuturesOrdered.fromIterable(futures)

/**
 * Collects an [Iterable] of futures into a [FuturesOrdered].
 */
@HiddenFromObjC
public fun <T> Iterable<Future<T>>.collectFuturesOrdered(): FuturesOrdered<T> =
    FuturesOrdered.fromIterable(this)
