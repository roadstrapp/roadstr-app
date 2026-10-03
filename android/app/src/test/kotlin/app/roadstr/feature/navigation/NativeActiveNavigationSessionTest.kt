package app.roadstr.feature.navigation

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.RoutingResponseStep
import app.roadstr.core.network.RoutingSpeedLimitEntry
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeRouteCandidate
import app.roadstr.feature.map.NativeRouteOverlaySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeActiveNavigationSessionTest {
    @Test
    fun `gps fixes advance committed geometry and live maneuver summary together`() {
        val route = route()
        val overlay = preparedOverlay(route, revision = 4)
        val hud = NativeNavigationHudSession()
        val session = NativeActiveNavigationSession(hud, overlay)

        assertTrue(session.start(4, route, "Now"))
        assertEquals(NativeNavigationHudStatus.Active, hud.state.value.status)
        assertEquals(1, hud.state.value.current?.index)

        assertTrue(
            session.submitFix(
                sequence = 1,
                point = route.polyline[1].toMapPoint(),
                speedMetersPerSecond = 10.0,
                altitudeMeters = 240.0,
            ),
        )

        assertEquals(2, hud.state.value.current?.index)
        assertEquals(36, hud.state.value.speed)
        assertEquals(30, hud.state.value.speedLimit)
        assertTrue(hud.state.value.overSpeedLimit)
        assertTrue(session.state.value.progressMeters > 90.0)
        assertTrue(overlay.state.value.progressMeters > 90.0)

        val progress = session.state.value.progressMeters
        assertTrue(
            session.submitFix(
                sequence = 2,
                point = route.polyline.first().toMapPoint(),
                speedMetersPerSecond = 5.0,
                altitudeMeters = 230.0,
            ),
        )
        assertEquals(progress, session.state.value.progressMeters, 0.000001)
        assertEquals(progress, overlay.state.value.progressMeters, 0.000001)
    }

    @Test
    fun `voice state stale fixes and stop remain revision fenced`() {
        val route = route()
        val overlay = preparedOverlay(route, revision = 7)
        val hud = NativeNavigationHudSession()
        val session = NativeActiveNavigationSession(hud, overlay)

        assertTrue(session.start(7, route, "Now"))
        assertTrue(session.toggleVoice(7))
        assertTrue(hud.state.value.voiceMuted)
        assertFalse(session.toggleVoice(6))
        assertTrue(
            session.submitFix(
                sequence = 3,
                point = route.polyline.first().toMapPoint(),
                speedMetersPerSecond = 0.0,
                altitudeMeters = 0.0,
            ),
        )
        assertFalse(
            session.submitFix(
                sequence = 3,
                point = route.polyline[1].toMapPoint(),
                speedMetersPerSecond = 0.0,
                altitudeMeters = 0.0,
            ),
        )
        assertFalse(session.stop(6))
        assertTrue(session.stop(7))
        assertFalse(session.state.value.active)
        assertEquals(NativeNavigationHudStatus.Hidden, hud.state.value.status)
        assertTrue(overlay.state.value.snapshot.activeRuns.isEmpty())
        assertFalse(session.stop(7))
    }

    private fun preparedOverlay(
        route: RoutingParsedRoute,
        revision: Long,
    ) = NativeRouteOverlaySession(0xFF71_58E2L).also { overlay ->
        assertTrue(
            overlay.submitAlternatives(
                revision = revision,
                candidates = listOf(
                    NativeRouteCandidate(
                        points = route.polyline.map { point -> point.toMapPoint() },
                        restricted = List(route.polyline.size) { false },
                    ),
                ),
                selectedIndex = 0,
            ),
        )
        assertTrue(overlay.commitSelectedAlternative(revision))
    }

    private fun route(): RoutingParsedRoute {
        val points = listOf(point(0.0), point(100.0), point(200.0))
        return RoutingParsedRoute(
            polyline = points,
            steps = listOf(
                step("Depart", "depart", points[0]),
                step("Turn right", "turn", points[1]),
                step("Arrive", "arrive", points[2]),
            ),
            totalDistanceM = 200.0,
            totalDurationS = 40.0,
            speedLimits = listOf(
                RoutingSpeedLimitEntry(0.0, 50),
                RoutingSpeedLimitEntry(80.0, 30),
            ),
        )
    }

    private fun point(northMeters: Double) = RoutingResponsePoint(
        latitude = northMeters / 111_320.0,
        longitude = 0.0,
    )

    private fun step(
        instruction: String,
        direction: String,
        location: RoutingResponsePoint,
    ) = RoutingResponseStep(
        instruction = instruction,
        direction = direction,
        distanceM = 100.0,
        location = location,
    )

    private fun RoutingResponsePoint.toMapPoint() = NativeMapPoint(latitude, longitude)
}
