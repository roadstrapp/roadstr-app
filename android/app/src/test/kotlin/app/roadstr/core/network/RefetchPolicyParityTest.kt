package app.roadstr.core.network

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefetchPolicyParityTest {
    private val policy = RefetchPolicy(750.0, Duration.ofMinutes(15))
    private val here = GeoPoint(45.0, 9.0)
    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private fun north(meters: Double) =
        here.copy(latitude = here.latitude + meters / GeoMath.metresPerDegree)

    @Test
    fun `empty or unsuccessful cache is due`() {
        assertTrue(policy.isDue(null, here, null, now))
        assertTrue(policy.isDue(here, here, null, now))
    }

    @Test
    fun `movement and age thresholds are strict`() {
        assertFalse(policy.isDue(here, north(500.0), now, now))
        assertFalse(policy.isDue(here, north(749.0), now, now))
        assertTrue(policy.isDue(here, north(760.0), now, now))
        assertFalse(policy.isDue(here, here, now, now.plus(Duration.ofMinutes(15))))
        assertTrue(policy.isDue(here, here, now, now.plus(Duration.ofMinutes(16))))
    }
}
