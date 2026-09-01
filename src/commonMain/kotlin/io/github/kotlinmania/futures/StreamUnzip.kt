// port-lint: source stream/stream/unzip.rs
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package io.github.kotlinmania.futures

import kotlin.native.HiddenFromObjC

/**
 * Future for the [unzip] method.
 */
@HiddenFromObjC
public class Unzip<A, B>(
    private val stream: Stream<Pair<A, B>>,
) : FusedFuture<Pair<List<A>, List<B>>> {
    private val listA = mutableListOf<A>()
    private val listB = mutableListOf<B>()
    private var done: Boolean = false

    public companion object {
        internal fun <A, B> new(stream: Stream<Pair<A, B>>): Unzip<A, B> = Unzip(stream)
    }

    override fun isTerminated(): Boolean = done

    override fun poll(context: TaskContext): Poll<Pair<List<A>, List<B>>> {
        while (true) {
            when (val p = stream.pollNext(context)) {
                is Poll.Ready -> {
                    when (val y = p.value) {
                        is Yield.Value -> {
                            listA.add(y.value.first)
                            listB.add(y.value.second)
                        }
                        Yield.End -> {
                            done = true
                            return Poll.Ready(Pair(listA.toList(), listB.toList()))
                        }
                    }
                }
                Poll.Pending -> return Poll.Pending
            }
        }
    }
}

/**
 * Unzips a stream of pairs into two separate collections.
 */
@HiddenFromObjC
public fun <A, B> Stream<Pair<A, B>>.unzip(): Unzip<A, B> = Unzip.new(this)
