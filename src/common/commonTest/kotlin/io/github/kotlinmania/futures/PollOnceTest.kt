package io.github.kotlinmania.futures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PollOnceTest {
    @Test
    fun testPollOncePendingAndReady() {
        var callCount = 0
        val future =
            Future {
                callCount++
                if (callCount == 1) {
                    Poll.Pending
                } else {
                    Poll.Ready("done")
                }
            }

        val cx = TaskContext()
        val pollOnce = poll(future)

        val firstPoll = pollOnce.poll(cx)
        assertIs<Poll.Ready<Poll<String>>>(firstPoll)
        assertEquals(Poll.Pending, firstPoll.value)

        val secondPoll = pollOnce.poll(cx)
        assertIs<Poll.Ready<Poll<String>>>(secondPoll)
        assertEquals(Poll.Ready("done"), secondPoll.value)
    }
}
