// port-lint: source futures-util/src/unfold_state.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * State used for stream and sink unfolds.
 */
@HiddenFromObjC
public sealed class UnfoldState<out T, out R> {
    @HiddenFromObjC
    public class Value<T>(
        public val value: T,
    ) : UnfoldState<T, Nothing>()

    @HiddenFromObjC
    public class InProgress<R>(
        public val future: R,
    ) : UnfoldState<Nothing, R>()

    @HiddenFromObjC
    public object Empty : UnfoldState<Nothing, Nothing>()

    public fun futureOrNull(): R? =
        when (this) {
            is InProgress -> future
            else -> null
        }

    public fun valueOrNull(): T? =
        when (this) {
            is Value -> value
            else -> null
        }

    public fun projectFuture(): R? = futureOrNull()

    public fun takeValue(): T? = valueOrNull()
}
