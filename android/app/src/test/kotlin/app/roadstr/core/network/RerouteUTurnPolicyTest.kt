package app.roadstr.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RerouteUTurnPolicyTest {
    // Heading north (0 degrees); a degree of latitude is about 111 km, so 0.001 is about 111 m.
    private fun route(
        vararg latitudes: Double,
        steps: List<RoutingResponseStep> = emptyList(),
    ) = RoutingParsedRoute(
        polyline = latitudes.map { RoutingResponsePoint(it, 9.0) },
        steps = steps,
        totalDistanceM = 5_000.0,
        totalDurationS = 300.0,
    )

    private fun step(modifier: String, distance: Double) = RoutingResponseStep(
        instruction = "",
        direction = "turn",
        modifier = modifier,
        distanceM = distance,
        location = RoutingResponsePoint(45.0, 9.0),
    )

    @Test
    fun `osm tags decide when they are known, whatever the speed`() {
        assertTrue(RerouteUTurnPolicy.forbidsUTurn(true, 40.0, null))
        assertTrue(RerouteUTurnPolicy.forbidsUTurn(true, 0.0, 30))
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(false, 130.0, 50))
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(false, 130.0, null))
    }

    @Test
    fun `without tags, over 90 and over the osm limit forbids`() {
        assertTrue(RerouteUTurnPolicy.forbidsUTurn(null, 95.0, 90))
        assertTrue(RerouteUTurnPolicy.forbidsUTurn(null, 150.0, 130))
        // Over 90 but within the limit, or limit not exceeded: no ban from this rule.
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(null, 100.0, 130))
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(null, 130.0, 130))
        // Not over 90 even when over a low limit.
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(null, 85.0, 50))
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(null, 90.0, 50))
    }

    @Test
    fun `the fallback threshold is the country's rural limit`() {
        // Italy: 90, the model. Elsewhere the same rule with the local number.
        assertEquals(90, RerouteUTurnPolicy.fallbackSpeedKmh("IT"))
        assertEquals(100, RerouteUTurnPolicy.fallbackSpeedKmh("DE"))
        assertEquals(80, RerouteUTurnPolicy.fallbackSpeedKmh("FR"))
        assertEquals(97, RerouteUTurnPolicy.fallbackSpeedKmh("GB"))
        assertEquals(100, RerouteUTurnPolicy.fallbackSpeedKmh("au"))
        assertEquals(60, RerouteUTurnPolicy.fallbackSpeedKmh("JP"))
        assertEquals(70, RerouteUTurnPolicy.fallbackSpeedKmh("SE"))
    }

    @Test
    fun `united states are told apart by state, the rest by country`() {
        assertEquals(97, RerouteUTurnPolicy.fallbackSpeedKmh("US", "TX"))
        assertEquals(129, RerouteUTurnPolicy.fallbackSpeedKmh("US", "NV"))
        assertEquals(89, RerouteUTurnPolicy.fallbackSpeedKmh("US", null))
        assertEquals(89, RerouteUTurnPolicy.fallbackSpeedKmh("US", "XX"))
        // Canada has only provinces in the wiki: their most common value.
        assertEquals(80, RerouteUTurnPolicy.fallbackSpeedKmh("CA", "02"))
    }

    @Test
    fun `unknown country gets the world median, tiny rural limits get a floor`() {
        assertEquals(90, RerouteUTurnPolicy.fallbackSpeedKmh(null))
        assertEquals(90, RerouteUTurnPolicy.fallbackSpeedKmh("ZZ"))
        assertEquals(RerouteUTurnPolicy.MIN_FALLBACK_SPEED_KMH, RerouteUTurnPolicy.fallbackSpeedKmh("SG"))
        assertEquals(RerouteUTurnPolicy.MIN_FALLBACK_SPEED_KMH, RerouteUTurnPolicy.fallbackSpeedKmh("US", "DC"))
    }

    @Test
    fun `the threshold passed in is the one compared against`() {
        // Germany: over 100 and over the limit.
        assertTrue(RerouteUTurnPolicy.forbidsUTurn(null, 105.0, 100, thresholdKmh = 100))
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(null, 95.0, 50, thresholdKmh = 100))
        // Japan: 60 is the threshold.
        assertTrue(RerouteUTurnPolicy.forbidsUTurn(null, 70.0, null, thresholdKmh = 60))
    }

    @Test
    fun `without tags or limit, over 90 forbids`() {
        assertTrue(RerouteUTurnPolicy.forbidsUTurn(null, 90.1, null))
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(null, 90.0, null))
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(null, 20.0, null))
        assertFalse(RerouteUTurnPolicy.forbidsUTurn(null, Double.NaN, null))
    }

    @Test
    fun `a first leg pointing back against the heading is a reversal`() {
        val backwards = route(45.0, 44.998, 44.99)
        val forwards = route(45.0, 45.002, 45.01)
        assertTrue(RerouteUTurnPolicy.reversesDirection(backwards, 0.0))
        assertFalse(RerouteUTurnPolicy.reversesDirection(forwards, 0.0))
    }

    @Test
    fun `a U-turn step close to the start is a reversal, a distant one is not`() {
        val soon = route(45.0, 45.01, steps = listOf(step("uturn", 400.0), step("left", 1_000.0)))
        val later = route(
            45.0, 45.01,
            steps = listOf(step("straight", 2_500.0), step("uturn", 400.0)),
        )
        assertTrue(RerouteUTurnPolicy.reversesDirection(soon, 0.0))
        assertFalse(RerouteUTurnPolicy.reversesDirection(later, 0.0))
    }

    @Test
    fun `without a heading only an explicit U-turn step counts`() {
        val backwards = route(45.0, 44.998, 44.99)
        val uTurn = route(45.0, 45.01, steps = listOf(step("uturn", 300.0)))
        assertFalse(RerouteUTurnPolicy.reversesDirection(backwards, null))
        assertTrue(RerouteUTurnPolicy.reversesDirection(uTurn, null))
        assertFalse(RerouteUTurnPolicy.reversesDirection(backwards, Double.NaN))
    }

    @Test
    fun `reversals are dropped and the rest kept`() {
        val good = route(45.0, 45.01)
        val bad = route(45.0, 44.99)
        assertEquals(listOf(good), RerouteUTurnPolicy.withoutReversals(listOf(bad, good), 0.0))
        assertTrue(RerouteUTurnPolicy.withoutReversals(listOf(bad), 0.0).isEmpty())
    }
}
