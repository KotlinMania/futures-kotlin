// port-lint: tests futures/tests/sink_fanout.rs
package io.github.kotlinmania.futures

import io.github.kotlinmania.futures.channel.mpsc.channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SinkFanoutTest {
    @Test
    fun itWorks() {
        val (tx1, rx1) = channel<Int>(1)
        val (tx2, rx2) = channel<Int>(2)
        val tx = tx1.fanout(tx2)
        val cx = TaskContext()

        for (i in 0 until 10) {
            val pollR = tx.pollReady(cx)
            assertIs<Poll.Ready<SinkOutcome<Nothing>>>(pollR)
            val sendR = tx.startSend(i)
            assertEquals(SinkOutcome.ready(), sendR)

            val recv1 = rx1.pollNext(cx)
            assertIs<Poll.Ready<Yield<Int>>>(recv1)
            assertEquals(Yield.value(i), recv1.value)

            val recv2 = rx2.pollNext(cx)
            assertIs<Poll.Ready<Yield<Int>>>(recv2)
            assertEquals(Yield.value(i), recv2.value)
        }

        val flushPoll = tx.pollFlush(cx)
        assertIs<Poll.Ready<SinkOutcome<Nothing>>>(flushPoll)
    }
}
