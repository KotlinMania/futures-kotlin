// port-lint: tests futures/tests/ready_queue.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.oneshot.Canceled
import io.github.kotlinmania.futures.channel.oneshot.oneshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReadyQueueTest {
    @Test
    fun basicUsage() {
        val queue = FuturesUnordered<Try<String, Canceled>>()
        val (tx1, rx1) = oneshot<String>()
        val (tx2, rx2) = oneshot<String>()
        val (tx3, rx3) = oneshot<String>()

        queue.push(rx1)
        queue.push(rx2)
        queue.push(rx3)

        val cx = TaskContext()
        assertTrue(queue.pollNext(cx) is Poll.Pending)

        tx2.send("hello")

        val p1 = queue.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<String, Canceled>>>>(p1)
        assertEquals(Yield.value(Try.ok("hello")), p1.value)
        assertTrue(queue.pollNext(cx) is Poll.Pending)

        tx1.send("world")
        tx3.send("world2")

        val p2 = queue.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<String, Canceled>>>>(p2)
        assertEquals(Yield.value(Try.ok("world")), p2.value)

        val p3 = queue.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<String, Canceled>>>>(p3)
        assertEquals(Yield.value(Try.ok("world2")), p3.value)

        val p4 = queue.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<String, Canceled>>>>(p4)
        assertEquals(Yield.end(), p4.value)
    }

    @Test
    fun resolvingErrors() {
        val queue = FuturesUnordered<Try<String, Canceled>>()
        val (tx1, rx1) = oneshot<String>()
        val (tx2, rx2) = oneshot<String>()
        val (tx3, rx3) = oneshot<String>()

        queue.push(rx1)
        queue.push(rx2)
        queue.push(rx3)

        val cx = TaskContext()
        assertTrue(queue.pollNext(cx) is Poll.Pending)

        tx2.close()

        val p1 = queue.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<String, Canceled>>>>(p1)
        assertEquals(Yield.value(Try.err(Canceled)), p1.value)
        assertTrue(queue.pollNext(cx) is Poll.Pending)

        tx1.close()
        tx3.send("world2")

        val p2 = queue.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<String, Canceled>>>>(p2)
        assertEquals(Yield.value(Try.err(Canceled)), p2.value)

        val p3 = queue.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<String, Canceled>>>>(p3)
        assertEquals(Yield.value(Try.ok("world2")), p3.value)

        val p4 = queue.pollNext(cx)
        assertIs<Poll.Ready<Yield<Try<String, Canceled>>>>(p4)
        assertEquals(Yield.end(), p4.value)
    }

    // dropping_ready_queue not ported: Rust Drop destructor semantics on FuturesUnordered (Kotlin GC has no Drop lifecycle)

    @Test
    fun stress() {
        for (i in 0 until 5) {
            val queue = FuturesUnordered<Try<Int, Canceled>>()
            val n = (i % 5) + 1
            val senders = mutableListOf<io.github.kotlinmania.futures.channel.oneshot.Sender<Int>>()

            for (num in 0 until n) {
                val (tx, rx) = oneshot<Int>()
                queue.push(rx)
                senders.add(tx)
            }

            for ((idx, tx) in senders.withIndex()) {
                tx.send(idx)
            }

            val cx = TaskContext()
            val received = mutableListOf<Int>()
            while (true) {
                val p = queue.pollNext(cx)
                if (p is Poll.Ready) {
                    when (val y = p.value) {
                        is Yield.Value -> received.add((y.value as Try.Ok).value)
                        Yield.End -> break
                    }
                }
            }
            assertEquals(n, received.size)
            received.sort()
            for ((idx, x) in received.withIndex()) {
                assertEquals(idx, x)
            }
        }
    }
}
