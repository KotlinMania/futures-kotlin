// port-lint: source futures-channel/src/mpsc/queue.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures.channel.mpsc

import kotlin.concurrent.atomics.AtomicReference
import kotlin.native.HiddenFromObjC

/**
 * A result of the [Queue.pop] function.
 */
@HiddenFromObjC
public sealed interface PopResult<out T> {
    /**
     * Some data has been popped.
     */
    public data class Data<T>(public val value: T) : PopResult<T>

    /**
     * The queue is empty.
     */
    public data object Empty : PopResult<Nothing>

    /**
     * The queue is in an inconsistent state.
     */
    public data object Inconsistent : PopResult<Nothing>
}

internal class QueueNode<T>(
    var value: T?,
) {
    val next: AtomicReference<QueueNode<T>?> = AtomicReference(null)
}

/**
 * A mostly lock-free multi-producer, single consumer queue for sending
 * messages between asynchronous tasks.
 */
@HiddenFromObjC
public class Queue<T> {
    private val stub = QueueNode<T>(null)
    private val head: AtomicReference<QueueNode<T>> = AtomicReference(stub)
    private var tail: QueueNode<T> = stub

    /**
     * Pushes a new value onto this queue.
     */
    public fun push(value: T) {
        val node = QueueNode(value)
        val prev = head.exchange(node)
        prev.next.store(node)
    }

    /**
     * Pops some data from this queue.
     */
    public fun pop(): PopResult<T> {
        val currentTail = tail
        val next = currentTail.next.load()

        if (next != null) {
            tail = next
            val ret = next.value
            next.value = null
            return if (ret != null) {
                PopResult.Data(ret)
            } else {
                PopResult.Empty
            }
        }

        return if (head.load() === currentTail) {
            PopResult.Empty
        } else {
            PopResult.Inconsistent
        }
    }

    /**
     * Pops an element similarly to [pop], but spin-waits on inconsistent queue state.
     */
    public fun popSpin(): T? {
        while (true) {
            when (val res = pop()) {
                is PopResult.Empty -> return null
                is PopResult.Data -> return res.value
                is PopResult.Inconsistent -> {
                    // Spin-wait
                }
            }
        }
    }
}
