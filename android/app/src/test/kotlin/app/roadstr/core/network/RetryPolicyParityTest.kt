package app.roadstr.core.network

import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyParityTest {
    @Test
    fun `failure and status classification match retry semantics`() {
        assertTrue(NetworkFailureClassifier.classify(SocketTimeoutException()) is TransientFailure)
        assertTrue(NetworkFailureClassifier.classify(SocketException()) is TransientFailure)
        assertTrue(
            NetworkFailureClassifier.classify(MalformedResponseException("bad json")) is PermanentFailure,
        )
        assertNull(NetworkFailureClassifier.classifyStatus(204))
        assertTrue(NetworkFailureClassifier.classifyStatus(429) is TransientFailure)
        assertTrue(NetworkFailureClassifier.classifyStatus(503) is TransientFailure)
        assertTrue(NetworkFailureClassifier.classifyStatus(404) is PermanentFailure)

        val failure = NetworkFailureClassifier.classifyStatus(503, host = "api.example.org")!!
        assertTrue(failure.toString().contains("api.example.org"))
        assertTrue(!failure.toString().contains('?'))
    }

    @Test
    fun `retry after seconds and dates are bounded`() {
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val seconds = NetworkFailureClassifier.classifyStatus(
            429,
            retryAfterHeader = "3",
            now = now,
        ) as TransientFailure
        assertEquals(Duration.ofSeconds(3), seconds.retryAfter)
        val date = NetworkFailureClassifier.classifyStatus(
            503,
            retryAfterHeader = "Mon, 01 Jan 2024 00:00:20 GMT",
            now = now,
        ) as TransientFailure
        assertEquals(Duration.ofSeconds(20), date.retryAfter)
        val absurd = NetworkFailureClassifier.classifyStatus(
            429,
            retryAfterHeader = "3600",
            now = now,
        ) as TransientFailure
        assertNull(absurd.retryAfter)
    }

    @Test
    fun `backoff doubles and stops at the ceiling`() {
        val policy = RetryPolicy(
            attempts = 6,
            baseDelay = Duration.ofMillis(100),
            maxDelay = Duration.ofMillis(500),
        )
        assertEquals(Duration.ofMillis(100), policy.delayBefore(2))
        assertEquals(Duration.ofMillis(200), policy.delayBefore(3))
        assertEquals(Duration.ofMillis(400), policy.delayBefore(4))
        assertEquals(Duration.ofMillis(500), policy.delayBefore(5))
        assertEquals(Duration.ofMillis(500), policy.delayBefore(9))
        assertEquals(
            Duration.ofMillis(500),
            policy.delayBefore(2, retryAfter = Duration.ofSeconds(2)),
        )
        assertEquals(1, RetryPolicy.None.attempts)
    }
}
