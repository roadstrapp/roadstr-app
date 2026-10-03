package app.roadstr.feature.navigation

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.RoutingResponseStep
import app.roadstr.core.network.RoutingSpeedLimitEntry
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeRouteCandidate
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.route.NativeRouteTransportMode
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

        assertTrue(session.startNavigation(4, route))
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

        assertTrue(session.startNavigation(7, route))
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

    @Test
    fun `arrival uses true destination distance and clears guidance atomically`() {
        val route = route()
        val overlay = preparedOverlay(route, revision = 11)
        val hud = NativeNavigationHudSession()
        val session = NativeActiveNavigationSession(hud, overlay)
        val destination = point(250.0).toMapPoint()

        assertTrue(session.startNavigation(11, route, destination))
        assertTrue(
            session.submitFix(
                sequence = 1,
                point = point(205.0).toMapPoint(),
                speedMetersPerSecond = 2.0,
                altitudeMeters = 0.0,
                accuracyMeters = 5.0,
            ),
        )
        assertTrue(session.state.value.active)
        assertFalse(session.state.value.arrived)

        assertTrue(
            session.submitFix(
                sequence = 2,
                point = point(325.0).toMapPoint(),
                speedMetersPerSecond = 2.0,
                altitudeMeters = 0.0,
                accuracyMeters = 5.0,
            ),
        )
        assertFalse(session.state.value.active)
        assertTrue(session.state.value.arrived)
        assertEquals(NativeNavigationHudStatus.Hidden, hud.state.value.status)
        assertTrue(overlay.state.value.snapshot.activeRuns.isEmpty())
        assertTrue(session.dismissArrival(11))
        assertFalse(session.state.value.arrived)
    }

    @Test
    fun `hard route deviation emits one bounded reroute and accepts its replacement`() {
        val route = route()
        val overlay = preparedOverlay(route, revision = 15)
        val hud = NativeNavigationHudSession()
        val session = NativeActiveNavigationSession(hud, overlay)
        val destination = point(1_000.0).toMapPoint()

        assertTrue(session.startNavigation(15, route, destination))
        assertTrue(
            session.submitFix(
                sequence = 1,
                point = NativeMapPoint(0.0, 70.0 / 111_320.0),
                speedMetersPerSecond = 10.0,
                altitudeMeters = 100.0,
                accuracyMeters = 5.0,
                headingDegrees = 725.0,
            ),
        )
        val request = requireNotNull(session.state.value.rerouteRequest)
        assertTrue(session.state.value.rerouting)
        assertEquals(36.0, request.speedKilometresPerHour, 0.000001)
        assertEquals(5.0, request.headingDegrees!!, 0.000001)
        assertFalse(session.failReroute(request.sequence + 1))

        assertTrue(session.completeReroute(request.sequence, route))
        assertEquals(16, session.state.value.revision)
        assertFalse(session.state.value.rerouting)
        assertEquals(NativeNavigationHudStatus.Active, hud.state.value.status)
        assertFalse(overlay.state.value.snapshot.activeRuns.isEmpty())
    }

    @Test
    fun `gps progress emits one far and one imminent voice cue per maneuver`() {
        val points = listOf(point(0.0), point(50.0), point(100.0), point(150.0), point(200.0))
        val route = RoutingParsedRoute(
            polyline = points,
            steps = listOf(
                step("Depart", "depart", points[0]),
                step("Turn right", "turn", points[2]),
                step("Arrive", "arrive", points[4]),
            ),
            totalDistanceM = 200.0,
            totalDurationS = 40.0,
            speedLimits = emptyList(),
        )
        val overlay = preparedOverlay(route, revision = 20)
        val session = NativeActiveNavigationSession(NativeNavigationHudSession(), overlay)
        assertTrue(session.startNavigation(20, route))

        assertTrue(
            session.submitFix(
                sequence = 1,
                point = points[0].toMapPoint(),
                speedMetersPerSecond = 10.0,
                altitudeMeters = 0.0,
            ),
        )
        val far = requireNotNull(session.state.value.voiceCue)
        assertEquals("Turn right", far.instruction)
        assertEquals(100, far.distanceMeters)

        assertTrue(
            session.submitFix(
                sequence = 2,
                point = points[1].toMapPoint(),
                speedMetersPerSecond = 10.0,
                altitudeMeters = 0.0,
            ),
        )
        val near = requireNotNull(session.state.value.voiceCue)
        assertTrue(near.sequence > far.sequence)
        assertEquals("Turn right", near.instruction)
        assertEquals(0, near.distanceMeters)

        assertTrue(
            session.submitFix(
                sequence = 3,
                point = points[1].toMapPoint(),
                speedMetersPerSecond = 10.0,
                altitudeMeters = 0.0,
            ),
        )
        assertEquals(near, session.state.value.voiceCue)
    }

    private fun NativeActiveNavigationSession.startNavigation(
        revision: Long,
        route: RoutingParsedRoute,
        destination: NativeMapPoint = route.polyline.last().toMapPoint(),
    ) = start(
        revision = revision,
        route = route,
        destination = destination,
        mode = NativeRouteTransportMode.Driving,
        nowLabel = "Now",
    )

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
