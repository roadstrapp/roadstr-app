package app.roadstr.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteProgressParityTest {
    private val start = GeoPoint(45.0, 9.0)
    private fun north(from: GeoPoint, meters: Double) =
        GeoPoint(from.latitude + meters / GeoMath.metresPerDegree, from.longitude)

    @Test
    fun `cumulative and nearest indexes preserve empty and vertex contracts`() {
        val line = listOf(start, north(start, 100.0), north(start, 200.0), north(start, 300.0))
        assertEquals(listOf(0.0, 100.0, 200.0, 300.0), RouteProgress.cumulativeDistances(line).map { it.roundToHalf() })
        assertEquals(2, RouteProgress.nearestIndex(line, north(start, 195.0)))
        assertEquals(0, RouteProgress.nearestIndex(emptyList(), start))
        assertEquals(emptyList<Double>(), RouteProgress.cumulativeDistances(emptyList()))
    }

    @Test
    fun `near search stays on the hinted pass of a loop`() {
        val outbound = (0..100).map { north(start, it * 10.0) }
        val loop = outbound + outbound.asReversed().drop(1)
        val position = north(start, 500.0)

        assertEquals(50, RouteProgress.nearestIndex(loop, position))
        assertEquals(150, RouteProgress.nearestIndexNear(loop, position, hint = 148))
        assertEquals(50, RouteProgress.nearestIndexNear(loop, position, hint = 48))
    }

    @Test
    fun `ordered step matching keeps the final point at the end of a loop`() {
        val outbound = (0..100).map { north(start, it * 10.0) }
        val loop = outbound + outbound.asReversed().drop(1)
        val steps = listOf(loop.first(), loop[100], loop.last())

        assertEquals(listOf(0, 100, loop.lastIndex), RouteProgress.nearestIndicesAlong(loop, steps))
        assertEquals(listOf(0), RouteProgress.nearestIndicesAlong(emptyList(), listOf(start)))
    }

    private fun Double.roundToHalf(): Double = kotlin.math.round(this * 2.0) / 2.0
}
