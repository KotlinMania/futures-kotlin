// port-lint: tests futures/tests/oneshot.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.oneshot.Canceled
import io.github.kotlinmania.futures.channel.oneshot.oneshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuturesOneshotTest {
    private val context = TaskContext()

    @Test
    fun oneshotSend1() {
        val (tx1, rx1) = oneshot<Int>()
        val sendRes = tx1.send(1)
        assertTrue(sendRes is Try.Ok)

        val pollRes = rx1.poll(context)
        assertTrue(pollRes is Poll.Ready)
        assertEquals(Try.ok(1), pollRes.value)
    }

    @Test
    fun oneshotSend2() {
        val (tx1, rx1) = oneshot<Int>()
        val sendRes = tx1.send(1)
        assertTrue(sendRes is Try.Ok)

        val mapped = rx1.mapOk { it * 2 }
        val pollRes = mapped.poll(context)
        assertTrue(pollRes is Poll.Ready)
        assertEquals(Try.ok(2), pollRes.value)
    }

    @Test
    fun oneshotSend3() {
        val (tx1, rx1) = oneshot<Int>()
        var woke = false
        val cx = TaskContext(Waker { woke = true })

        val mapped = rx1.mapOk { it + 10 }
        assertEquals(Poll.Pending, mapped.poll(cx))

        val sendRes = tx1.send(5)
        assertTrue(sendRes is Try.Ok)
        assertTrue(woke)

        val pollRes = mapped.poll(context)
        assertTrue(pollRes is Poll.Ready)
        assertEquals(Try.ok(15), pollRes.value)
    }

    @Test
    fun oneshotDropTx1() {
        val (tx1, rx1) = oneshot<Int>()
        tx1.close()

        val pollRes = rx1.poll(context)
        assertTrue(pollRes is Poll.Ready)
        assertEquals(Try.err(Canceled), pollRes.value)
    }

    @Test
    fun oneshotDropTx2() {
        val (tx1, rx1) = oneshot<Int>()
        var woke = false
        val cx = TaskContext(Waker { woke = true })

        assertEquals(Poll.Pending, rx1.poll(cx))

        tx1.close()
        assertTrue(woke)

        val pollRes = rx1.poll(context)
        assertTrue(pollRes is Poll.Ready)
        assertEquals(Try.err(Canceled), pollRes.value)
    }

    @Test
    fun oneshotDropRx() {
        val (tx, rx) = oneshot<Int>()
        rx.close()
        val res = tx.send(2)
        assertTrue(res is Try.Err)
        assertEquals(2, res.error)
    }

    @Test
    fun oneshotDebug() {
        val (tx, rx) = oneshot<Int>()
        assertTrue(tx.toString().contains("Sender"))
        assertTrue(rx.toString().contains("Receiver"))
        rx.close()
        assertTrue(tx.isCanceled())

        val (tx2, rx2) = oneshot<Int>()
        tx2.close()
        val pollRes = rx2.poll(context)
        assertTrue(pollRes is Poll.Ready)
        assertTrue(rx2.isTerminated())
    }
}
