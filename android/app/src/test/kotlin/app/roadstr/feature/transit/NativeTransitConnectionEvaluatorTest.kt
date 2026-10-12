package app.roadstr.feature.transit

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeTransitConnectionEvaluatorTest {
    private val arrival = Instant.parse("2026-10-25T00:50:00Z")

    @Test
    fun `known transfer distinguishes feasible risk and missed`() {
        assertEquals(
            NativeTransitConnectionStatus.Feasible,
            assess(departureSeconds = 900, walkSeconds = 300).status,
        )
        assertEquals(
            NativeTransitConnectionStatus.AtRisk,
            assess(departureSeconds = 390, walkSeconds = 300).status,
        )
        assertEquals(
            NativeTransitConnectionStatus.Missed,
            assess(departureSeconds = 240, walkSeconds = 300).status,
        )
    }

    @Test
    fun `missing station transfer data reports risk instead of inventing certainty`() {
        val result = NativeTransitConnectionEvaluator.evaluate(
            expectedArrival = arrival,
            expectedDeparture = arrival.minusSeconds(60),
            walkingTransfer = null,
            minimumTransfer = null,
        )
        assertEquals(NativeTransitConnectionStatus.AtRisk, result.status)
        assertEquals(null, result.requiredSeconds)
    }

    @Test
    fun `realtime cancellation and passed realtime departure are missed`() {
        assertEquals(
            NativeTransitConnectionStatus.Missed,
            NativeTransitConnectionEvaluator.evaluate(
                arrival,
                arrival.plusSeconds(600),
                Duration.ofSeconds(120),
                null,
                nextServiceCancelled = true,
                realtime = true,
            ).status,
        )
        assertEquals(
            NativeTransitConnectionStatus.Missed,
            NativeTransitConnectionEvaluator.evaluate(
                arrival,
                arrival.plusSeconds(600),
                Duration.ofSeconds(120),
                null,
                realtime = true,
                now = arrival.plusSeconds(601),
            ).status,
        )
    }

    @Test
    fun `instants handle overnight and daylight saving boundaries without local arithmetic`() {
        val beforeFallback = Instant.parse("2026-10-25T00:55:00Z")
        val afterFallback = Instant.parse("2026-10-25T01:10:00Z")
        val result = NativeTransitConnectionEvaluator.evaluate(
            beforeFallback,
            afterFallback,
            Duration.ofMinutes(5),
            null,
        )
        assertEquals(900L, result.availableSeconds)
        assertEquals(NativeTransitConnectionStatus.Feasible, result.status)
    }

    private fun assess(departureSeconds: Long, walkSeconds: Long) =
        NativeTransitConnectionEvaluator.evaluate(
            expectedArrival = arrival,
            expectedDeparture = arrival.plusSeconds(departureSeconds),
            walkingTransfer = Duration.ofSeconds(walkSeconds),
            minimumTransfer = null,
            safetyMargin = Duration.ofMinutes(2),
        )
}
