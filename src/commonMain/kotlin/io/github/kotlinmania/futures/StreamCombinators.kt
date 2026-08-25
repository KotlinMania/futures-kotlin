// port-lint: source futures-util/src/stream/mod.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future that collects all remaining items of a [Stream] into a [List].
 */
@HiddenFromObjC
public fun <T> Stream<T>.collect(): Future<List<T>> {
    val stream = this
    val collected = mutableListOf<T>()
    return object : Future<List<T>> {
        override fun poll(context: TaskContext): Poll<List<T>> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> collected.add(y.value)
                            Yield.End -> return Poll.ready(collected.toList())
                        }
                    }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }
    }
}

/**
 * Future that produces the next item from the [Stream], or null when exhausted.
 */
@HiddenFromObjC
public fun <T> Stream<T>.next(): Future<T?> {
    val stream = this
    return object : Future<T?> {
        override fun poll(context: TaskContext): Poll<T?> =
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> Poll.ready(p.value.valueOrNull())
                Poll.Pending -> Poll.pending()
            }
    }
}

/**
 * Returns a new [Stream] that takes up to [n] items from this stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.take(n: Int): Stream<T> {
    val stream = this
    var remaining = maxOf(0, n)
    return object : Stream<T>, FusedStream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            if (remaining <= 0) {
                return Poll.ready(Yield.end())
            }
            return when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            remaining--
                            Poll.ready(y)
                        }
                        Yield.End -> {
                            remaining = 0
                            Poll.ready(Yield.end())
                        }
                    }
                }
                Poll.Pending -> Poll.pending()
            }
        }

        override fun isTerminated(): Boolean = remaining <= 0

        override fun sizeHint(): SizeHint {
            val inner = stream.sizeHint()
            val lower = minOf(remaining, inner.lower)
            val upper = if (inner.upper != null) minOf(remaining, inner.upper) else remaining
            return SizeHint(lower = lower, upper = upper)
        }
    }
}

/**
 * Maps this stream's items to a different value.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.map(transform: (T) -> R): Stream<R> {
    val stream = this
    return object : Stream<R> {
        override fun pollNext(context: TaskContext): Poll<Yield<R>> =
            when (val p = stream.pollNext(context)) {
                is Poll.Ready ->
                    when (val y = p.value) {
                        is Yield.Value -> Poll.ready(Yield.value(transform(y.value)))
                        Yield.End -> Poll.ready(Yield.end())
                    }
                Poll.Pending -> Poll.pending()
            }

        override fun sizeHint(): SizeHint = stream.sizeHint()
    }
}

/**
 * Filters this stream according to the given predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.filter(predicate: (T) -> Boolean): Stream<T> {
    val stream = this
    return object : Stream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready ->
                        when (val y = p.value) {
                            is Yield.Value -> {
                                if (predicate(y.value)) {
                                    return Poll.ready(y)
                                }
                            }
                            Yield.End -> return Poll.ready(Yield.end())
                        }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }

        override fun sizeHint(): SizeHint = SizeHint(0, stream.sizeHint().upper)
    }
}

/**
 * Filters and maps this stream's items using the given partial transform.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.filterMap(transform: (T) -> R?): Stream<R> {
    val stream = this
    return object : Stream<R> {
        override fun pollNext(context: TaskContext): Poll<Yield<R>> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready ->
                        when (val y = p.value) {
                            is Yield.Value -> {
                                val mapped = transform(y.value)
                                if (mapped != null) {
                                    return Poll.ready(Yield.value(mapped))
                                }
                            }
                            Yield.End -> return Poll.ready(Yield.end())
                        }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }

        override fun sizeHint(): SizeHint = SizeHint(0, stream.sizeHint().upper)
    }
}

/**
 * Folds every element into an accumulator by applying an operation, returning a [Future] of the final result.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.fold(initial: R, operation: (R, T) -> R): Future<R> {
    val stream = this
    var acc = initial
    return object : Future<R> {
        override fun poll(context: TaskContext): Poll<R> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready ->
                        when (val y = p.value) {
                            is Yield.Value -> acc = operation(acc, y.value)
                            Yield.End -> return Poll.ready(acc)
                        }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }
    }
}

/**
 * Counts the number of items yielded by this stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.count(): Future<Int> {
    val stream = this
    var count = 0
    return object : Future<Int> {
        override fun poll(context: TaskContext): Poll<Int> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready ->
                        when (p.value) {
                            is Yield.Value -> count++
                            Yield.End -> return Poll.ready(count)
                        }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }
    }
}

/**
 * Tests whether every element of the stream matches the given predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.forAll(predicate: (T) -> Boolean): Future<Boolean> {
    val stream = this
    return object : Future<Boolean> {
        override fun poll(context: TaskContext): Poll<Boolean> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready ->
                        when (val y = p.value) {
                            is Yield.Value -> {
                                if (!predicate(y.value)) {
                                    return Poll.ready(false)
                                }
                            }
                            Yield.End -> return Poll.ready(true)
                        }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }
    }
}

/**
 * Tests whether any element of the stream matches the given predicate.
 */
@HiddenFromObjC
public fun <T> Stream<T>.any(predicate: (T) -> Boolean): Future<Boolean> {
    val stream = this
    return object : Future<Boolean> {
        override fun poll(context: TaskContext): Poll<Boolean> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready ->
                        when (val y = p.value) {
                            is Yield.Value -> {
                                if (predicate(y.value)) {
                                    return Poll.ready(true)
                                }
                            }
                            Yield.End -> return Poll.ready(false)
                        }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }
    }
}

/**
 * Gives the current iteration count as well as the next value.
 */
@HiddenFromObjC
public fun <T> Stream<T>.enumerate(): Stream<IndexedValue<T>> {
    val stream = this
    var index = 0
    return object : Stream<IndexedValue<T>> {
        override fun pollNext(context: TaskContext): Poll<Yield<IndexedValue<T>>> =
            when (val p = stream.pollNext(context)) {
                is Poll.Ready ->
                    when (val y = p.value) {
                        is Yield.Value -> Poll.ready(Yield.value(IndexedValue(index++, y.value)))
                        Yield.End -> Poll.ready(Yield.end())
                    }
                Poll.Pending -> Poll.pending()
            }

        override fun sizeHint(): SizeHint = stream.sizeHint()
    }
}

/**
 * Skips the first [n] elements of the stream.
 */
@HiddenFromObjC
public fun <T> Stream<T>.skip(n: Int): Stream<T> {
    val stream = this
    var remaining = maxOf(0, n)
    return object : Stream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            while (remaining > 0) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready ->
                        when (p.value) {
                            is Yield.Value -> remaining--
                            Yield.End -> return Poll.ready(Yield.end())
                        }
                    Poll.Pending -> return Poll.pending()
                }
            }
            return stream.pollNext(context)
        }

        override fun sizeHint(): SizeHint {
            val inner = stream.sizeHint()
            val lower = maxOf(0, inner.lower - remaining)
            val upper = inner.upper?.let { maxOf(0, it - remaining) }
            return SizeHint(lower, upper)
        }
    }
}

/**
 * Skips elements of the stream while the predicate holds true.
 */
@HiddenFromObjC
public fun <T> Stream<T>.skipWhile(predicate: (T) -> Boolean): Stream<T> {
    val stream = this
    var skipping = true
    return object : Stream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            while (skipping) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready ->
                        when (val y = p.value) {
                            is Yield.Value -> {
                                if (!predicate(y.value)) {
                                    skipping = false
                                    return Poll.ready(y)
                                }
                            }
                            Yield.End -> return Poll.ready(Yield.end())
                        }
                    Poll.Pending -> return Poll.pending()
                }
            }
            return stream.pollNext(context)
        }

        override fun sizeHint(): SizeHint = SizeHint(0, stream.sizeHint().upper)
    }
}

/**
 * Takes elements of the stream while the predicate holds true.
 */
@HiddenFromObjC
public fun <T> Stream<T>.takeWhile(predicate: (T) -> Boolean): Stream<T> {
    val stream = this
    var done = false
    return object : Stream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            if (done) return Poll.ready(Yield.end())
            return when (val p = stream.pollNext(context)) {
                is Poll.Ready ->
                    when (val y = p.value) {
                        is Yield.Value -> {
                            if (predicate(y.value)) {
                                Poll.ready(y)
                            } else {
                                done = true
                                Poll.ready(Yield.end())
                            }
                        }
                        Yield.End -> {
                            done = true
                            Poll.ready(Yield.end())
                        }
                    }
                Poll.Pending -> Poll.pending()
            }
        }

        override fun sizeHint(): SizeHint = SizeHint(0, stream.sizeHint().upper)
    }
}

/**
 * Chains two streams together, yielding elements from this stream and then from [other].
 */
@HiddenFromObjC
public fun <T> Stream<T>.chain(other: Stream<T>): Stream<T> {
    val first = this
    val second = other
    var firstExhausted = false
    return object : Stream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            if (!firstExhausted) {
                when (val p = first.pollNext(context)) {
                    is Poll.Ready ->
                        when (val y = p.value) {
                            is Yield.Value -> return Poll.ready(y)
                            Yield.End -> firstExhausted = true
                        }
                    Poll.Pending -> return Poll.pending()
                }
            }
            return second.pollNext(context)
        }

        override fun sizeHint(): SizeHint {
            val a = first.sizeHint()
            val b = second.sizeHint()
            val lower = if (firstExhausted) b.lower else a.lower + b.lower
            val upper =
                if (firstExhausted) {
                    b.upper
                } else if (a.upper != null && b.upper != null) {
                    a.upper + b.upper
                } else {
                    null
                }
            return SizeHint(lower, upper)
        }
    }
}

/**
 * Combines two streams into a stream of pairs, terminating when either stream is exhausted.
 */
@HiddenFromObjC
public fun <T, U> Stream<T>.zip(other: Stream<U>): Stream<Pair<T, U>> {
    val first = this
    val second = other
    var queuedFirst: T? = null
    var hasFirst = false
    var queuedSecond: U? = null
    var hasSecond = false
    var exhausted = false

    return object : Stream<Pair<T, U>> {
        override fun pollNext(context: TaskContext): Poll<Yield<Pair<T, U>>> {
            if (exhausted) return Poll.ready(Yield.end())

            if (!hasFirst) {
                when (val p = first.pollNext(context)) {
                    is Poll.Ready ->
                        when (val y = p.value) {
                            is Yield.Value -> {
                                queuedFirst = y.value
                                hasFirst = true
                            }
                            Yield.End -> {
                                exhausted = true
                                return Poll.ready(Yield.end())
                            }
                        }
                    Poll.Pending -> {}
                }
            }

            if (!hasSecond) {
                when (val p = second.pollNext(context)) {
                    is Poll.Ready ->
                        when (val y = p.value) {
                            is Yield.Value -> {
                                queuedSecond = y.value
                                hasSecond = true
                            }
                            Yield.End -> {
                                exhausted = true
                                return Poll.ready(Yield.end())
                            }
                        }
                    Poll.Pending -> {}
                }
            }

            if (hasFirst && hasSecond) {
                @Suppress("UNCHECKED_CAST")
                val item = Pair(queuedFirst as T, queuedSecond as U)
                queuedFirst = null
                hasFirst = false
                queuedSecond = null
                hasSecond = false
                return Poll.ready(Yield.value(item))
            }

            return Poll.pending()
        }

        override fun sizeHint(): SizeHint {
            val a = first.sizeHint()
            val b = second.sizeHint()
            val lower = minOf(a.lower, b.lower)
            val upper =
                when {
                    a.upper != null && b.upper != null -> minOf(a.upper, b.upper)
                    a.upper != null -> a.upper
                    b.upper != null -> b.upper
                    else -> null
                }
            return SizeHint(lower, upper)
        }
    }
}

/**
 * Converts an [Iterable] into a [Stream] that produces all its items.
 */
@HiddenFromObjC
public fun <T> Iterable<T>.asStream(): Iter<T> = streamIter(this)

/**
 * Flattens a stream of streams into a single stream.
 */
@HiddenFromObjC
public fun <T> Stream<Stream<T>>.flatten(): Stream<T> {
    val outer = this
    var currentInner: Stream<T>? = null
    var outerExhausted = false

    return object : Stream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            while (true) {
                val inner = currentInner
                if (inner != null) {
                    when (val p = inner.pollNext(context)) {
                        is Poll.Ready -> {
                            when (val y = p.value) {
                                is Yield.Value -> return Poll.ready(y)
                                Yield.End -> currentInner = null
                            }
                        }
                        Poll.Pending -> return Poll.pending()
                    }
                } else if (!outerExhausted) {
                    when (val p = outer.pollNext(context)) {
                        is Poll.Ready -> {
                            when (val y = p.value) {
                                is Yield.Value -> currentInner = y.value
                                Yield.End -> {
                                    outerExhausted = true
                                    return Poll.ready(Yield.end())
                                }
                            }
                        }
                        Poll.Pending -> return Poll.pending()
                    }
                } else {
                    return Poll.ready(Yield.end())
                }
            }
        }
    }
}

/**
 * Collects a stream of pairs into two separate lists.
 */
@HiddenFromObjC
public fun <A, B> Stream<Pair<A, B>>.unzip(): Future<Pair<List<A>, List<B>>> {
    val stream = this
    val first = mutableListOf<A>()
    val second = mutableListOf<B>()

    return object : Future<Pair<List<A>, List<B>>> {
        override fun poll(context: TaskContext): Poll<Pair<List<A>, List<B>>> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> {
                                first.add(y.value.first)
                                second.add(y.value.second)
                            }
                            Yield.End -> return Poll.ready(Pair(first.toList(), second.toList()))
                        }
                    }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }
    }
}

/**
 * Calls a closure on each element of the stream, passing the value through unchanged.
 */
@HiddenFromObjC
public fun <T> Stream<T>.inspect(action: (T) -> Unit): Stream<T> {
    val stream = this
    return object : Stream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> =
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            action(y.value)
                            Poll.ready(y)
                        }
                        Yield.End -> Poll.ready(Yield.end())
                    }
                }
                Poll.Pending -> Poll.pending()
            }

        override fun sizeHint(): SizeHint = stream.sizeHint()
    }
}

/**
 * An iterator adaptor that applies a stateful function across items in the stream.
 */
@HiddenFromObjC
public fun <T, S, R> Stream<T>.scan(initial: S, transform: (S, T) -> Pair<S, R>?): Stream<R> {
    val stream = this
    var state = initial
    var done = false

    return object : Stream<R> {
        override fun pollNext(context: TaskContext): Poll<Yield<R>> {
            if (done) return Poll.ready(Yield.end())
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> {
                                val result = transform(state, y.value)
                                if (result != null) {
                                    state = result.first
                                    return Poll.ready(Yield.value(result.second))
                                } else {
                                    done = true
                                    return Poll.ready(Yield.end())
                                }
                            }
                            Yield.End -> {
                                done = true
                                return Poll.ready(Yield.end())
                            }
                        }
                    }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }
    }
}

/**
 * Runs the stream to completion, calling the provided closure for each item.
 */
@HiddenFromObjC
public fun <T> Stream<T>.forEach(action: (T) -> Unit): Future<Unit> {
    val stream = this
    return object : Future<Unit> {
        override fun poll(context: TaskContext): Poll<Unit> {
            while (true) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> action(y.value)
                            Yield.End -> return Poll.ready(Unit)
                        }
                    }
                    Poll.Pending -> return Poll.pending()
                }
            }
        }
    }
}

/**
 * Batches elements into fixed-size chunks.
 */
@HiddenFromObjC
public fun <T> Stream<T>.chunks(size: Int): Stream<List<T>> {
    require(size > 0) { "chunk size must be greater than 0" }
    val stream = this
    val buffer = mutableListOf<T>()
    var streamExhausted = false

    return object : Stream<List<T>> {
        override fun pollNext(context: TaskContext): Poll<Yield<List<T>>> {
            if (streamExhausted) return Poll.ready(Yield.end())
            while (buffer.size < size) {
                when (val p = stream.pollNext(context)) {
                    is Poll.Ready -> {
                        when (val y = p.value) {
                            is Yield.Value -> buffer.add(y.value)
                            Yield.End -> {
                                streamExhausted = true
                                break
                            }
                        }
                    }
                    Poll.Pending -> return Poll.pending()
                }
            }
            return if (buffer.isNotEmpty()) {
                val chunk = buffer.toList()
                buffer.clear()
                Poll.ready(Yield.value(chunk))
            } else {
                Poll.ready(Yield.end())
            }
        }

        override fun sizeHint(): SizeHint {
            val inner = stream.sizeHint()
            val lower = inner.lower / size
            val upper = inner.upper?.let { (it + size - 1) / size }
            return SizeHint(lower, upper)
        }
    }
}

/**
 * Yields elements from the stream until [stopFuture] completes.
 */
@HiddenFromObjC
public fun <T> Stream<T>.takeUntil(stopFuture: Future<*>): Stream<T> {
    val stream = this
    var stopped = false

    return object : Stream<T> {
        override fun pollNext(context: TaskContext): Poll<Yield<T>> {
            if (stopped) return Poll.ready(Yield.end())
            when (stopFuture.poll(context)) {
                is Poll.Ready -> {
                    stopped = true
                    return Poll.ready(Yield.end())
                }
                Poll.Pending -> {}
            }
            return when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (p.value) {
                        is Yield.Value -> Poll.ready(p.value)
                        Yield.End -> {
                            stopped = true
                            Poll.ready(Yield.end())
                        }
                    }
                }
                Poll.Pending -> Poll.pending()
            }
        }
    }
}

/**
 * Transforms each element into a [Future] and yields the resolved values in order.
 */
@HiddenFromObjC
public fun <T, R> Stream<T>.then(transform: (T) -> Future<R>): Stream<R> {
    val stream = this
    var activeFuture: Future<R>? = null

    return object : Stream<R> {
        override fun pollNext(context: TaskContext): Poll<Yield<R>> {
            while (true) {
                val fut = activeFuture
                if (fut != null) {
                    when (val p = fut.poll(context)) {
                        is Poll.Ready -> {
                            activeFuture = null
                            return Poll.ready(Yield.value(p.value))
                        }
                        Poll.Pending -> return Poll.pending()
                    }
                } else {
                    when (val p = stream.pollNext(context)) {
                        is Poll.Ready -> {
                            when (val y = p.value) {
                                is Yield.Value -> activeFuture = transform(y.value)
                                Yield.End -> return Poll.ready(Yield.end())
                            }
                        }
                        Poll.Pending -> return Poll.pending()
                    }
                }
            }
        }
    }
}
