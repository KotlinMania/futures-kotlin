// port-lint: tests abortable.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.mpsc.unbounded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AbortableTest {
    @Test
    fun testAbortableCompletesWithoutAbort() {
        val (fut, handle) = abortable(ready(42))
        assertFalse(handle.isAborted())
        val res = fut.poll(TaskContext())
        assertIs<Poll.Ready<Try<Int, Aborted>>>(res)
        val v = res.value
        assertIs<Try.Ok<Int>>(v)
        assertEquals(42, v.value)
        assertFalse(handle.isAborted())
    }

    @Test
    fun testAbortableAborts() {
        val (fut, handle) = abortable(pending<Int>())
        assertFalse(handle.isAborted())

        val p1 = fut.poll(TaskContext())
        assertTrue(p1 is Poll.Pending)

        handle.abort()
        assertTrue(handle.isAborted())

        val p2 = fut.poll(TaskContext())
        assertIs<Poll.Ready<Try<Int, Aborted>>>(p2)
        val v = p2.value
        assertIs<Try.Err<Aborted>>(v)
        assertEquals(Aborted, v.error)
    }

    @Test
    fun testAbortableStreamCompletesWithoutAbort() {
        val (tx, rx) = unbounded<Int>()
        val (stream, handle) = abortable(rx)
        val cx = TaskContext()

        tx.unboundedSend(1)
        tx.unboundedSend(2)
        tx.disconnect()

        val p1 = stream.pollNext(cx)
        assertTrue(p1 is Poll.Ready && p1.value is Yield.Value && p1.value.value == 1)

        val p2 = stream.pollNext(cx)
        assertTrue(p2 is Poll.Ready && p2.value is Yield.Value && p2.value.value == 2)

        val p3 = stream.pollNext(cx)
        assertTrue(p3 is Poll.Ready && p3.value is Yield.End)
        assertFalse(handle.isAborted())
    }

    @Test
    fun testAbortableStreamAborts() {
        val (tx, rx) = unbounded<Int>()
        val (stream, handle) = abortable(rx)
        val cx = TaskContext()

        tx.unboundedSend(1)
        val p1 = stream.pollNext(cx)
        assertTrue(p1 is Poll.Ready && p1.value is Yield.Value && p1.value.value == 1)

        handle.abort()
        assertTrue(handle.isAborted())

        val p2 = stream.pollNext(cx)
        assertIs<Poll.Ready<Yield<Int>>>(p2)
        assertEquals(Yield.end(), p2.value)
        assertTrue(stream.isTerminated())
    }

    @Test
    fun abortableWorks() {
        val (_tx, aRx) = io.github.kotlinmania.futures.channel.oneshot.channel<Unit>()
        val (abortableRx, abortHandle) = abortable(aRx)

        abortHandle.abort()
        assertTrue(abortableRx.isAborted())
        val cx = TaskContext()
        val res = abortableRx.poll(cx)
        assertIs<Poll.Ready<Try<Try<Unit, io.github.kotlinmania.futures.channel.oneshot.Canceled>, Aborted>>>(res)
        val v = res.value
        assertIs<Try.Err<Aborted>>(v)
        assertEquals(Aborted, v.error)
    }

    @Test
    fun abortableResolves() {
        val (tx, aRx) = io.github.kotlinmania.futures.channel.oneshot.channel<Unit>()
        val (abortableRx, _abortHandle) = abortable(aRx)

        tx.send(Unit)
        assertFalse(abortableRx.isAborted())
        val cx = TaskContext()
        val res = abortableRx.poll(cx)
        assertIs<Poll.Ready<Try<Try<Unit, io.github.kotlinmania.futures.channel.oneshot.Canceled>, Aborted>>>(res)
        assertTrue(res.value is Try.Ok)
    }

    @Test
    fun abortableStreamWorks() {
        val (_tx, aRx) = io.github.kotlinmania.futures.channel.mpsc.channel<Unit>(1)
        val (abortableRx, abortHandle) = abortable(aRx)

        abortHandle.abort()
        assertTrue(abortableRx.isAborted())
        val cx = TaskContext()
        assertEquals(Poll.ready(Yield.end()), abortableRx.pollNext(cx))
    }

    @Test
    fun abortableStreamResolves() {
        val (tx, aRx) = io.github.kotlinmania.futures.channel.mpsc.channel<Unit>(1)
        val (abortableRx, _abortHandle) = abortable(aRx)

        tx.startSend(Unit)
        assertFalse(abortableRx.isAborted())
        val cx = TaskContext()
        assertEquals(Poll.ready(Yield.value(Unit)), abortableRx.pollNext(cx))
    }
}
