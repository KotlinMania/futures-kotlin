// port-lint: source abortable.rs
@file:OptIn(
    kotlin.experimental.ExperimentalObjCRefinement::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package io.github.kotlinmania.futures

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.native.HiddenFromObjC

/**
 * Indicator that the [Abortable] task was aborted.
 */
@HiddenFromObjC
public object Aborted {
    override fun toString(): String = "Abortable future has been aborted"
}

internal class AbortInner {
    val waker: AtomicWaker = AtomicWaker()
    val aborted: AtomicBoolean = AtomicBoolean(false)
}

/**
 * A registration handle for an [Abortable] task.
 */
@HiddenFromObjC
public class AbortRegistration internal constructor(
    internal val inner: AbortInner,
) {
    /**
     * Create an [AbortHandle] from the given [AbortRegistration].
     */
    public fun handle(): AbortHandle = AbortHandle(inner)
}

/**
 * A handle to an [Abortable] task.
 */
@HiddenFromObjC
public class AbortHandle internal constructor(
    internal val inner: AbortInner,
) {
    /**
     * Abort the [Abortable] stream/future associated with this handle.
     */
    public fun abort() {
        inner.aborted.store(true)
        inner.waker.wake()
    }

    /**
     * Checks whether [abort] was called on any associated [AbortHandle]s.
     */
    public fun isAborted(): Boolean = inner.aborted.load()

    public companion object {
        /**
         * Creates an ([AbortHandle], [AbortRegistration]) pair.
         */
        @HiddenFromObjC
        public fun newPair(): Pair<AbortHandle, AbortRegistration> {
            val inner = AbortInner()
            return Pair(AbortHandle(inner), AbortRegistration(inner))
        }
    }
}

/**
 * A future which can be remotely short-circuited using an [AbortHandle].
 */
@HiddenFromObjC
public class Abortable<T>(
    private val task: Future<T>,
    private val registration: AbortRegistration,
) : Future<Try<T, Aborted>> {
    public interface Output

    public fun isAborted(): Boolean = registration.inner.aborted.load()

    public fun tryPoll(context: TaskContext): Poll<Try<T, Aborted>> = poll(context)

    public fun fmt(): String = "Abortable"

    override fun toString(): String = fmt()

    override fun poll(context: TaskContext): Poll<Try<T, Aborted>> {
        if (isAborted()) {
            return Poll.Ready(Try.Err(Aborted))
        }

        when (val res = task.poll(context)) {
            is Poll.Ready -> return Poll.Ready(Try.Ok(res.value))
            is Poll.Pending -> {}
        }

        registration.inner.waker.register(context.waker)

        if (isAborted()) {
            return Poll.Ready(Try.Err(Aborted))
        }

        return Poll.Pending
    }

    public companion object {
        @HiddenFromObjC
        public fun <T> new(task: Future<T>, reg: AbortRegistration): Abortable<T> = Abortable(task, reg)

        @HiddenFromObjC
        public fun <T> of(task: Future<T>, reg: AbortRegistration): Abortable<T> = Abortable(task, reg)
    }
}

/**
 * A stream which can be remotely short-circuited using an [AbortHandle].
 */
@HiddenFromObjC
public class AbortableStream<T>(
    private val stream: Stream<T>,
    private val registration: AbortRegistration,
) : Stream<T>,
    FusedStream<T> {
    public interface Item

    public fun isAborted(): Boolean = registration.inner.aborted.load()

    public fun fmt(): String = "AbortableStream"

    override fun toString(): String = fmt()

    override fun pollNext(context: TaskContext): Poll<Yield<T>> {
        if (isAborted()) {
            return Poll.ready(Yield.end())
        }

        when (val res = stream.pollNext(context)) {
            is Poll.Ready -> return res
            is Poll.Pending -> {}
        }

        registration.inner.waker.register(context.waker)

        if (isAborted()) {
            return Poll.ready(Yield.end())
        }

        return Poll.pending()
    }

    override fun isTerminated(): Boolean =
        isAborted() || (stream as? FusedStream<*>)?.isTerminated() == true

    override fun sizeHint(): SizeHint {
        if (isAborted()) return SizeHint(0, 0)
        return stream.sizeHint()
    }

    public companion object {
        @HiddenFromObjC
        public fun <T> new(stream: Stream<T>, reg: AbortRegistration): AbortableStream<T> =
            AbortableStream(stream, reg)

        @HiddenFromObjC
        public fun <T> of(stream: Stream<T>, reg: AbortRegistration): AbortableStream<T> =
            AbortableStream(stream, reg)
    }
}

/**
 * Creates a new [Abortable] future and an [AbortHandle] which can be used to stop it.
 */
@HiddenFromObjC
public fun <Fut> abortable(future: Future<Fut>): Pair<Abortable<Fut>, AbortHandle> {
    val (handle, reg) = AbortHandle.newPair()
    val ab = Abortable(future, reg)
    return Pair(ab, handle)
}

/**
 * Creates a new [AbortableStream] and an [AbortHandle] which can be used to stop it.
 */
@HiddenFromObjC
public fun <T> abortable(stream: Stream<T>): Pair<AbortableStream<T>, AbortHandle> {
    val (handle, reg) = AbortHandle.newPair()
    val ab = AbortableStream(stream, reg)
    return Pair(ab, handle)
}

/**
 * Wraps this stream in an [AbortableStream] using the given [registration].
 */
@HiddenFromObjC
public fun <T> Stream<T>.abortable(registration: AbortRegistration): AbortableStream<T> =
    AbortableStream(this, registration)
