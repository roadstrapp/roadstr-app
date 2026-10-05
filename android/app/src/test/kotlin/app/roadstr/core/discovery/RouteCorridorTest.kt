package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCorridorTest {
    // A road heading north along one meridian: a degree of latitude is 111.32 km in GeoMath.
    private fun north(kilometres: Double, lon: Double = 9.0, lat0: Double = 45.0) =
        GeoPoint(lat0 + kilometres * 1_000 / GeoMath.metresPerDegree, lon)

    private fun straight(totalKm: Int, stepKm: Double = 0.5): List<GeoPoint> =
        (0..(totalKm / stepKm).toInt()).map { north(it * stepKm) }

    /** A winding road: a sine wave across a long stretch, with many points. */
    private fun winding(points: Int): List<GeoPoint> = (0 until points).map {
        val t = it.toDouble() / (points - 1)
        GeoPoint(45.0 + t * 0.9, 9.0 + 0.02 * sin(t * 40))
    }

    @Test
    fun `length and a point along the line`() {
        val line = listOf(north(0.0), north(1.0), north(3.0))
        assertEquals(3_000.0, RouteCorridor.lengthMeters(line), 1.0)
        assertEquals(north(2.0).latitude, RouteCorridor.pointAlong(line, 2_000.0).latitude, 1e-6)
        assertEquals(line.last(), RouteCorridor.pointAlong(line, 99_999.0))
        assertEquals(line.first(), RouteCorridor.pointAlong(line, -5.0))
    }

    @Test
    fun `the stretch ahead starts where the driver is and stops at the window`() {
        val route = straight(100)
        val corridor = RouteCorridor.ahead(route, north(10.0), aheadMeters = 30_000.0)!!
        assertEquals(north(10.0).latitude, corridor.points.first().latitude, 1e-5)
        assertEquals(30_000.0, corridor.lengthMeters, 60.0)
        assertEquals(RouteCorridor.DEFAULT_BUFFER_METERS, corridor.bufferMeters)
        assertTrue(corridor.points.size <= SearchArea.MAX_CORRIDOR_VERTICES)
    }

    @Test
    fun `near the end only what is left is searched`() {
        val corridor = RouteCorridor.ahead(straight(100), north(95.0))!!
        assertEquals(5_000.0, corridor.lengthMeters, 60.0)
    }

    @Test
    fun `a position off the road still finds the nearest stretch, a far one finds none`() {
        val route = straight(100)
        assertNotNull(RouteCorridor.ahead(route, GeoPoint(north(10.0).latitude, 9.01)))
        assertNull(RouteCorridor.ahead(route, GeoPoint(north(10.0).latitude, 9.2)))
    }

    @Test
    fun `an unusable route gives no corridor`() {
        assertNull(RouteCorridor.ahead(emptyList(), north(0.0)))
        assertNull(RouteCorridor.ahead(listOf(north(0.0)), north(0.0)))
        assertNull(RouteCorridor.ahead(listOf(north(0.0), GeoPoint(Double.NaN, 9.0)), north(0.0)))
        assertNull(RouteCorridor.ahead(listOf(north(0.0), north(0.1)), north(0.0)))
        assertNull(RouteCorridor.ahead(List(10) { north(0.0) }, north(0.0)))
    }

    @Test
    fun `simplifying keeps the ends, the limit and a stated error bound`() {
        val road = winding(2_000)
        val simplified = RouteCorridor.simplify(road, 24)
        assertTrue(simplified.points.size <= 24)
        assertEquals(road.first(), simplified.points.first())
        assertEquals(road.last(), simplified.points.last())
        val worst = road.maxOf { GeoMath.distanceToPolylineMeters(it, simplified.points) }
        assertTrue("worst $worst vs bound ${simplified.errorBoundMeters}", worst <= simplified.errorBoundMeters + 1.0)
    }

    @Test
    fun `a short line is left alone`() {
        val line = straight(5, 1.0)
        val simplified = RouteCorridor.simplify(line, 24)
        assertEquals(line, simplified.points)
        assertEquals(0.0, simplified.errorBoundMeters, 0.0)
    }

    @Test
    fun `a straight line collapses to its two ends within no error`() {
        val simplified = RouteCorridor.simplify(straight(100), 24)
        assertEquals(2, simplified.points.size)
    }

    @Test
    fun `a very long route is simplified quickly and stays inside the limit`() {
        val big = winding(60_000)
        val started = System.nanoTime()
        val corridor = RouteCorridor.ahead(big, big[100], aheadMeters = 30_000.0)!!
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $millis ms", millis < 3_000)
        assertTrue(corridor.points.size <= SearchArea.MAX_CORRIDOR_VERTICES)
    }

    @Test
    fun `the last resort keeps the limit even for a pathological line`() {
        // A tight spiral: every point matters at small tolerances, so the tolerance grows a long way.
        val spiral = (0 until 5_000).map {
            val angle = it * 0.3
            GeoPoint(45.0 + 0.0001 * it * cos(angle), 9.0 + 0.0001 * it * sin(angle))
        }
        val simplified = RouteCorridor.simplify(spiral, 24)
        assertTrue(simplified.points.size <= 24)
        assertEquals(spiral.first(), simplified.points.first())
    }

    @Test
    fun `a place is judged by the side step and the distance ahead`() {
        val route = straight(20)
        val place = GeoPoint(north(5.0).latitude, 9.0 + 500.0 / (GeoMath.metresPerDegree * cos(Math.toRadians(45.0))))
        val position = RouteCorridor.position(place, route)!!
        assertEquals(500.0, position.offRouteMeters, 5.0)
        assertEquals(5_000.0, position.alongMeters, 20.0)
        assertEquals(1_000.0, position.detourMeters, 10.0)
        assertNull(RouteCorridor.position(place, listOf(north(0.0))))
    }

    @Test
    fun `a corridor describes itself without its points`() {
        val corridor = RouteCorridor.ahead(straight(10), north(0.0))!!
        assertEquals("Corridor(points=${corridor.points.size})", corridor.toString())
        assertEquals(corridor.lengthMeters / 2, GeoMath.distanceMeters(corridor.points.first(), corridor.center), 30.0)
    }
}
