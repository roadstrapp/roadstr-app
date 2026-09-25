package app.roadstr.core.navigation

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingFilterParityTest {
    private val start = GeoPoint(45.0703, 7.6869)
    private fun north(from: GeoPoint, meters: Double) =
        from.copy(latitude = from.latitude + meters / GeoMath.metresPerDegree)

    @Test
    fun `motion uses hysteresis and expires stale samples`() {
        var now = 1_000L
        val filter = HeadingFilter { now }
        assertFalse(filter.isMoving)
        assertFalse(filter.updateMotion(5.0))
        assertTrue(filter.updateMotion(5.5))
        assertTrue(filter.updateMotion(4.0))
        assertTrue(filter.updateMotion(2.0))
        assertFalse(filter.updateMotion(1.9))
        assertTrue(filter.updateMotion(50.0))
        filter.reset()
        assertTrue(filter.isMoving)
        now += 6_001
        assertFalse(filter.isMoving)
    }

    @Test
    fun `provider fallback and reliable travel baseline match Dart`() {
        val filter = HeadingFilter()
        assertEquals(
            42.0,
            filter.resolve(90.0, start, north(start, 50.0), 1.0, 5.0, 42.0, true),
            0.0,
        )
        assertEquals(
            90.0,
            filter.resolve(90.0, start, north(start, 2.0), 30.0, 5.0, 0.0, true),
            0.0,
        )
        assertFalse(HeadingFilter.usesTravelHeading(Double.NaN))
        assertFalse(HeadingFilter.hasReliableMovement(start, north(start, 4.0), 5.0))
        assertTrue(HeadingFilter.hasReliableMovement(start, north(start, 9.0), 5.0))
        assertTrue(HeadingFilter.hasReliableMovement(start, north(start, 9.0), Double.NaN))
    }

    @Test
    fun `one reversal is held and a second agreeing fix is accepted`() {
        val filter = HeadingFilter()
        assertEquals(
            180.0,
            filter.resolve(180.0, start, north(start, 40.0), 50.0, 5.0, null, true),
            0.0,
        )
        assertEquals(
            0.0,
            filter.resolve(180.0, north(start, 40.0), north(start, 80.0), 50.0, 5.0, null, true),
            0.5,
        )
    }

    @Test
    fun `route veto and tangent smoothing preserve trusted direction`() {
        val veto = HeadingFilter()
        repeat(4) {
            assertEquals(
                180.0,
                veto.resolve(
                    180.0,
                    start,
                    north(start, 40.0),
                    50.0,
                    5.0,
                    null,
                    true,
                ) { RouteLocalBearing(5.0, 180.0) },
                0.0,
            )
        }

        val smoothing = HeadingFilter()
        val result = smoothing.resolve(
            10.0,
            start,
            north(start, 40.0),
            50.0,
            5.0,
            null,
            true,
        ) { RouteLocalBearing(5.0, 20.0) }
        assertEquals(7.0, result, 0.5)
    }
}
