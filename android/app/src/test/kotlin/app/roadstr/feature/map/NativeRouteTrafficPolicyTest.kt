package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRouteTrafficPolicyTest {
    private val accent = 0xFF8B5CF6

    @Test
    fun `empty route or jam feed produces no traffic segments`() {
        assertTrue(NativeRouteTrafficPolicy.segments(emptyList(), listOf(pointAtMeters(0.0))).isEmpty())
        assertTrue(NativeRouteTrafficPolicy.segments(routeAt(0.0, 500.0), emptyList()).isEmpty())
    }

    @Test
    fun `contiguous jam run includes both visual continuity boundaries`() {
        val route = routeAt(0.0, 500.0, 1_000.0, 1_500.0)

        val segments = NativeRouteTrafficPolicy.segments(route, listOf(pointAtMeters(750.0)))

        assertEquals(listOf(route), segments)
    }

    @Test
    fun `separate jam runs share the intervening route boundary`() {
        val route = routeAt(0.0, 1_000.0, 2_000.0, 3_000.0, 4_000.0)

        val segments = NativeRouteTrafficPolicy.segments(
            route,
            listOf(pointAtMeters(1_000.0), pointAtMeters(3_000.0)),
        )

        assertEquals(listOf(route.subList(0, 3), route.subList(2, 5)), segments)
    }

    @Test
    fun `strict 400 metre gate preserves latlong2 rounded Vincenty behavior`() {
        val jam = pointAtMeters(0.0)
        val included = pointAtMeters(399.4)
        val excluded = pointAtMeters(399.6)

        assertEquals(1, NativeRouteTrafficPolicy.segments(listOf(included, included), listOf(jam)).size)
        assertTrue(NativeRouteTrafficPolicy.segments(listOf(excluded, excluded), listOf(jam)).isEmpty())
    }

    @Test
    fun `traffic input rejects invalid coordinates and oversized caches`() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteTrafficPolicy.normalizeJamPoints(listOf(NativeMapPoint(Double.NaN, 9.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteTrafficPolicy.normalizeJamPoints(
                List(NativeRouteTrafficPolicy.MAX_TRAFFIC_JAMS + 1) { pointAtMeters(it.toDouble()) },
            )
        }
    }

    @Test
    fun `distance check budget rejects pathological cross products`() {
        assertTrue(NativeRouteTrafficPolicy.withinDistanceBudget(500, 2_000))
        assertFalse(NativeRouteTrafficPolicy.withinDistanceBudget(501, 2_000))
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteTrafficPolicy.segments(
                List(501) { pointAtMeters(it.toDouble()) },
                List(2_000) { pointAtMeters(it.toDouble()) },
            )
        }
    }

    @Test
    fun `compiler emits deterministic traffic GeoJSON and counts its points`() {
        val route = routeAt(0.0, 500.0)
        val traffic = routeAt(250.0, 500.0, 750.0)
        val payload = NativeRouteOverlayCompiler.compile(
            NativeRouteOverlaySnapshot(
                activeRuns = listOf(NativeRouteRun(route, restricted = false)),
                completedPoints = emptyList(),
                accentArgb = accent,
                trafficSegments = listOf(traffic),
            ),
        )

        assertEquals(1, payload.trafficFeatureCount)
        assertEquals(5, payload.pointCount)
        assertTrue(payload.trafficGeoJson.contains("[${traffic[0].longitude},0]"))
        assertTrue(payload.trafficGeoJson.indexOf(traffic[0].longitude.toString()) <
            payload.trafficGeoJson.indexOf(traffic[2].longitude.toString()))
    }

    @Test
    fun `compiler rejects singleton and excessive traffic segments`() {
        val line = routeAt(0.0, 10.0)
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteOverlayCompiler.compile(
                NativeRouteOverlaySnapshot(
                    activeRuns = emptyList(),
                    completedPoints = emptyList(),
                    accentArgb = accent,
                    trafficSegments = listOf(listOf(line.first())),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteOverlayCompiler.compile(
                NativeRouteOverlaySnapshot(
                    activeRuns = emptyList(),
                    completedPoints = emptyList(),
                    accentArgb = accent,
                    trafficSegments = List(
                        NativeRouteOverlayCompiler.MAX_TRAFFIC_SEGMENTS + 1,
                    ) { line },
                ),
            )
        }
    }

    @Test
    fun `session retains traffic cache across route arrival and fences stale updates`() {
        val session = NativeRouteOverlaySession(accent)
        val route = routeAt(0.0, 500.0, 1_000.0)

        assertTrue(session.submitTraffic(2, listOf(route[1])))
        assertTrue(session.state.value.snapshot.trafficSegments.isEmpty())
        assertTrue(session.submitRoute(1, route, List(route.size) { false }))
        assertEquals(1, session.state.value.snapshot.trafficSegments.size)
        assertFalse(session.submitTraffic(1, listOf(route.last())))
        assertTrue(session.updateProgress(1, 100.0, cursorRestricted = false))
        assertEquals(1, session.state.value.snapshot.trafficSegments.size)
        assertTrue(session.clearTraffic(3))
        assertFalse(session.submitTraffic(2, listOf(route[1])))
        assertEquals(3L, session.state.value.trafficRevision)
        assertTrue(session.state.value.snapshot.trafficSegments.isEmpty())
    }

    @Test
    fun `alternative selection reprojects retained jams onto selected geometry`() {
        val session = NativeRouteOverlaySession(accent)
        val near = routeAt(0.0, 500.0, 1_000.0)
        val far = near.map { it.copy(longitude = it.longitude + 10.0) }
        session.submitTraffic(1, listOf(near[1]))
        session.submitAlternatives(
            revision = 2,
            candidates = listOf(
                NativeRouteCandidate(near, List(near.size) { false }),
                NativeRouteCandidate(far, List(far.size) { false }),
            ),
            selectedIndex = 1,
        )

        assertTrue(session.state.value.snapshot.trafficSegments.isEmpty())
        assertTrue(session.selectAlternative(2, selectedIndex = 0))
        assertEquals(1, session.state.value.snapshot.trafficSegments.size)
    }

    private fun routeAt(vararg meters: Double): List<NativeMapPoint> =
        meters.map(::pointAtMeters)

    private fun pointAtMeters(meters: Double): NativeMapPoint = NativeMapPoint(
        latitude = 0.0,
        longitude = Math.toDegrees(meters / 6_378_137.0),
    )
}
