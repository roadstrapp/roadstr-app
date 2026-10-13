package app.roadstr.feature.transit

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitLeg
import app.roadstr.core.network.TransitMode
import app.roadstr.core.network.TransitScopedId
import app.roadstr.core.network.TransitStop

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

    @Test
    fun `journey infers a conservative platform change when the provider omits transfer metadata`() {
        val first = leg("A", "Central", "1", arrival, arrival)
        val second = leg("Central", "B", "8", arrival.plusSeconds(250), arrival.plusSeconds(850))
        val journey = TransitItinerary(
            850,
            arrival.minusSeconds(600),
            arrival.plusSeconds(850),
            1,
            listOf(first, second),
            TransitScopedId("provider", "journey"),
        )

        val result = NativeTransitConnectionEvaluator.evaluateJourney(journey, 0)

        assertEquals(180L, result.requiredSeconds)
        assertEquals(NativeTransitConnectionStatus.AtRisk, result.status)
    }

    private fun assess(departureSeconds: Long, walkSeconds: Long) =
        NativeTransitConnectionEvaluator.evaluate(
            expectedArrival = arrival,
            expectedDeparture = arrival.plusSeconds(departureSeconds),
            walkingTransfer = Duration.ofSeconds(walkSeconds),
            minimumTransfer = null,
            safetyMargin = Duration.ofMinutes(2),
        )

    private fun leg(from: String, to: String, platform: String, start: Instant, end: Instant) = TransitLeg(
        mode = TransitMode.Rail,
        distanceMeters = null,
        durationSeconds = Duration.between(start, end).seconds.coerceAtLeast(0),
        startTime = start,
        endTime = end,
        fromName = from,
        toName = to,
        routeShortName = "R",
        routeLongName = null,
        agencyName = null,
        headsign = to,
        routeColorArgb = null,
        routeTextColorArgb = null,
        realTime = true,
        geometry = emptyList(),
        from = TransitStop(
            TransitScopedId("provider", from.lowercase()), from, null, "Europe/Rome",
            null, null, null, start, start, null, platform, platform, false, emptyList(),
        ),
        to = TransitStop(
            TransitScopedId("provider", to.lowercase()), to, null, "Europe/Rome",
            end, end, null, null, null, null, platform, platform, false, emptyList(),
        ),
    )
}
