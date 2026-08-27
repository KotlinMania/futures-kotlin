// port-lint: source futures-util/src/future/future/remote_handle.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.oneshot.Receiver
import io.github.kotlinmania.futures.channel.oneshot.Sender
import io.github.kotlinmania.futures.channel.oneshot.channel
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.HiddenFromObjC

/**
 * The handle to a remote future returned by [remoteHandle].
 */
@HiddenFromObjC
public class RemoteHandle<T> internal constructor(
    private val rx: Receiver<Result<T>>,
    private val keepRunning: AtomicBoolean,
) : Future<T> {
    /**
     * Drops this handle without canceling the underlying future.
     */
    public fun forget() {
        keepRunning.store(true)
    }

    override fun poll(context: TaskContext): Poll<T> {
        return when (val p = rx.poll(context)) {
            is Poll.Ready -> {
                when (val result = p.value) {
                    is Try.Ok -> {
                        val innerResult = result.value
                        if (innerResult.isSuccess) {
                            Poll.Ready(innerResult.getOrThrow())
                        } else {
                            throw innerResult.exceptionOrNull() ?: RuntimeException("Remote future failed")
                        }
                    }
                    is Try.Err -> throw RuntimeException("Oneshot sender was dropped")
                }
            }
            Poll.Pending -> Poll.Pending
        }
    }
}

/**
 * A future which sends its output to the corresponding [RemoteHandle].
 */
@HiddenFromObjC
public class Remote<T> internal constructor(
    private var tx: Sender<Result<T>>?,
    private val keepRunning: AtomicBoolean,
    private val future: Future<Result<T>>,
) : Future<Unit> {
    override fun poll(context: TaskContext): Poll<Unit> {
        val currentTx = tx ?: return Poll.Ready(Unit)
        if (currentTx.pollCanceled(context) is Poll.Ready && !keepRunning.load()) {
            return Poll.Ready(Unit)
        }

        return when (val p = future.poll(context)) {
            is Poll.Ready -> {
                val sender = tx
                tx = null
                sender?.send(p.value)
                Poll.Ready(Unit)
            }
            Poll.Pending -> Poll.Pending
        }
    }
}

/**
 * Turns a future into a pair of a [Remote] future and a [RemoteHandle].
 */
@HiddenFromObjC
public fun <T> Future<T>.remoteHandle(): Pair<Remote<T>, RemoteHandle<T>> {
    val (tx, rx) = channel<Result<T>>()
    val keepRunning = AtomicBoolean(false)
    val wrapped = Remote(
        tx = tx,
        keepRunning = keepRunning,
        future = this.catchUnwind(),
    )
    val handle = RemoteHandle(rx = rx, keepRunning = keepRunning)
    return Pair(wrapped, handle)
}
