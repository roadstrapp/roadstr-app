package app.roadstr.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeRerouteBackoffTest {
    @Test
    fun `consecutive failures wait longer up to a thirty second ceiling`() {
        val backoff = NativeRerouteBackoff()
        val waits = List(6) { backoff.nextDelayMillis() }
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 30_000L, 30_000L, 30_000L), waits)
    }

    @Test
    fun `a success starts the escalation over`() {
        val backoff = NativeRerouteBackoff()
        backoff.nextDelayMillis()
        backoff.nextDelayMillis()
        backoff.reset()
        assertEquals(5_000L, backoff.nextDelayMillis())
    }
}
