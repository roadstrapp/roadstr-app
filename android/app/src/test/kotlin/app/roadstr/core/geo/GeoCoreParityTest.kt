package app.roadstr.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class GeoCoreParityTest {
    private val latitude = 45.070
    private val longitude = 12.199
    private val north = { meters: Double -> meters / GeoMath.metresPerDegree }
    private val east = { meters: Double ->
        meters / (GeoMath.metresPerDegree * cos(Math.toRadians(latitude)))
    }

    @Test
    fun `projection scales longitude and clamps to segment`() {
        val a = GeoPoint(latitude, longitude)
        val b = GeoPoint(latitude, longitude + east(200.0))
        val point = GeoPoint(latitude + north(40.0), longitude + east(100.0))

        assertEquals(40.0, GeoMath.distanceToSegmentMeters(point, a, b), 0.5)
        assertEquals(0.5, GeoMath.projectOnSegment(point, a, b).t, 0.01)
        assertEquals(
            50.0,
            GeoMath.distanceToSegmentMeters(
                GeoPoint(latitude, longitude + east(250.0)), a, b,
            ),
            0.5,
        )
    }

    @Test
    fun `polygon and bearing match the Dart geometry contract`() {
        val square = listOf(
            GeoPoint(latitude, longitude),
            GeoPoint(latitude, longitude + east(100.0)),
            GeoPoint(latitude + north(100.0), longitude + east(100.0)),
            GeoPoint(latitude + north(100.0), longitude),
        )
        assertTrue(
            GeoMath.pointInPolygon(
                GeoPoint(latitude + north(50.0), longitude + east(50.0)), square,
            ),
        )
        assertFalse(
            GeoMath.pointInPolygon(
                GeoPoint(latitude + north(50.0), longitude + east(150.0)), square,
            ),
        )
        val origin = GeoPoint(latitude, longitude)
        assertEquals(0.0, GeoMath.bearingBetween(origin, GeoPoint(latitude + north(100.0), longitude)), 0.5)
        assertEquals(90.0, GeoMath.bearingBetween(origin, GeoPoint(latitude, longitude + east(100.0))), 0.5)
    }

    @Test
    fun `encoded polyline honors precision and truncation`() {
        val points = EncodedPolyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 0.00001)
        assertEquals(-120.2, points[0].longitude, 0.00001)
        assertEquals(43.252, points[2].latitude, 0.00001)
        assertEquals(-126.453, points[2].longitude, 0.00001)
        // The clipped value contains the first point and only the latitude
        // delta of the next one, so the incomplete second point is discarded.
        assertEquals(1, EncodedPolyline.decode("_p~iF~ps|U_ulL", 5).size)
        assertTrue(EncodedPolyline.decode("", 7).isEmpty())
    }
}
