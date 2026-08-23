// port-lint: tests futures-channel/tests/oneshot.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.oneshot.oneshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OneshotTest {
    @Test
    fun smokePoll() {
        val (tx, rx) = oneshot<Int>()
        var woke = false
        val cx = TaskContext(Waker { woke = true })

        assertTrue(tx.pollCanceled(cx) is Poll.Pending)
        assertTrue(tx.pollCanceled(cx) is Poll.Pending)

        rx.close()

        assertTrue(tx.pollCanceled(cx) is Poll.Ready)
        assertTrue(tx.pollCanceled(cx) is Poll.Ready)
    }

    @Test
    fun isCanceled() {
        val (tx, rx) = oneshot<Int>()
        assertFalse(tx.isCanceled())
        rx.close()
        assertTrue(tx.isCanceled())
    }

    @Test
    fun cancelNotifies() {
        val (tx, rx) = oneshot<Int>()
        var woke = false
        val cx = TaskContext(Waker { woke = true })

        assertEquals(Poll.Pending, tx.cancellation().poll(cx))
        assertFalse(woke)

        rx.close()
        assertTrue(woke)
        assertEquals(Poll.Ready(Unit), tx.cancellation().poll(cx))
    }

    @Test
    fun close() {
        val (tx, rx) = oneshot<Int>()
        rx.close()
        val cx = TaskContext()

        val pollRes = rx.poll(cx)
        assertTrue(pollRes is Poll.Ready)
        val outcome = pollRes.readyOrNull()
        assertNotNull(outcome)
        assertTrue(outcome is Try.Err)

        assertTrue(tx.pollCanceled(cx) is Poll.Ready)
    }

    @Test
    fun closeWakes() {
        val (tx, rx) = oneshot<Int>()
        var woke = false
        val cx = TaskContext(Waker { woke = true })

        assertEquals(Poll.Pending, tx.cancellation().poll(cx))
        assertFalse(woke)

        rx.close()
        assertTrue(woke)
        assertEquals(Poll.Ready(Unit), tx.cancellation().poll(cx))
    }

    @Test
    fun cancelAfterSenderDropDoesntNotify() {
        val (tx, rx) = oneshot<Int>()
        val cx = TaskContext(Waker { error("Should not wake") })
        assertEquals(Poll.Pending, tx.pollCanceled(cx))
        tx.close()
        rx.close()
    }

    @Test
    fun sendAndRecv() {
        val (tx, rx) = oneshot<Int>()
        val cx = TaskContext()

        assertEquals(Poll.Pending, rx.poll(cx))
        val sendRes = tx.send(123)
        assertTrue(sendRes is Try.Ok)

        val recvRes = rx.poll(cx)
        assertTrue(recvRes is Poll.Ready)
        assertEquals(Try.ok(123), recvRes.readyOrNull())
        assertTrue(rx.isTerminated())
    }

    @Test
    fun sendFailsWhenReceiverClosed() {
        val (tx, rx) = oneshot<Int>()
        rx.close()
        val sendRes = tx.send(999)
        assertTrue(sendRes is Try.Err)
        assertEquals(999, sendRes.error)
    }

    @Test
    fun tryRecvBeforeAndAfterSend() {
        val (tx, rx) = oneshot<String>()
        val initial = rx.tryRecv()
        assertTrue(initial is Try.Ok)
        assertNull(initial.value)

        tx.send("hello")
        tx.close()

        val received = rx.tryRecv()
        assertTrue(received is Try.Ok)
        assertEquals("hello", received.value)
    }

    @Test
    fun isConnectedTo() {
        val (tx1, rx1) = oneshot<Int>()
        val (tx2, rx2) = oneshot<Int>()

        assertTrue(tx1.isConnectedTo(rx1))
        assertTrue(tx2.isConnectedTo(rx2))
        assertFalse(tx1.isConnectedTo(rx2))
        assertFalse(tx2.isConnectedTo(rx1))
    }
}
