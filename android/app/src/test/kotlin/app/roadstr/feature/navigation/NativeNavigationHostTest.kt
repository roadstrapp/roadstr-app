package app.roadstr.feature.navigation

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.RoutingResponseStep
import app.roadstr.feature.home.NativeShellGpsFix
import app.roadstr.feature.home.NativeShellGpsPhase
import app.roadstr.feature.home.NativeShellGpsSnapshot
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeRouteCandidate
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.voice.NativeVoiceGateway
import app.roadstr.feature.voice.NativeVoiceGender
import app.roadstr.feature.voice.NativeVoiceRuntimeSnapshot
import app.roadstr.feature.voice.NativeVoiceRuntimeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The host moves a trip along from the GPS feed alone: no composition, no screen. Every effect that used to
 * run only while the shell was drawn (a fix advances the trip, a cue is spoken, an off-route request is
 * answered, the notification follows the manoeuvre) is checked here without one.
 */
class NativeNavigationHostTest {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Unconfined + job)
    private val gps = MutableStateFlow(NativeShellGpsSnapshot(NativeShellGpsPhase.WaitingForFix))
    private val voice = FakeVoice()
    private val platform = FakePlatform()
    private var nextRoute: RoutingParsedRoute? = null
    private var routeRequests = mutableListOf<NativeNavigationRerouteRequest>()
    private var waited = mutableListOf<Long>()
    private var sequence = 0L

    private val host = NativeNavigationHost(
        accentArgb = 0xFF71_58E2L,
        gps = gps,
        voice = voice,
        reroute = { request, _ ->
            routeRequests += request
            nextRoute
        },
        speedLimit = { null },
        settings = { NativeNavigationSettings(imperialUnits = false, avoidUnpavedRoads = false, languageCode = "it") },
        platform = platform,
        scope = scope,
        elapsedRealtimeMillis = { 1_000L },
        wait = { waited += it },
    )

    @After
    fun tearDown() {
        host.close()
        job.cancel()
    }

    @Test
    fun `a trip moves along with the gps feed alone and holds the platform while it lasts`() {
        val route = route()
        start(route, revision = 4)
        assertEquals(1, platform.started)

        fix(route.polyline[1])

        assertTrue(host.navigating)
        assertTrue(host.session.state.value.progressMeters > 90.0)
        assertEquals(1, platform.started)
        assertEquals(0, platform.stopped)
    }

    @Test
    fun `fixes do nothing while no trip is active`() {
        fix(route().polyline[1])

        assertFalse(host.navigating)
        assertEquals(0.0, host.session.state.value.progressMeters, 0.0)
        assertEquals(0, platform.started)
    }

    @Test
    fun `starting a trip applies the fix that was already there`() {
        val route = route()
        fix(route.polyline[1])

        start(route, revision = 6)

        assertTrue(host.session.state.value.progressMeters > 90.0)
    }

    @Test
    fun `each voice cue is spoken once with the person's units`() {
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
        start(route, revision = 8)

        fix(points[0])
        fix(points[1])
        fix(points[1])

        assertEquals(listOf("Turn right" to 100, "Turn right" to 0), voice.maneuvers.map { it.first to it.second })
        assertTrue(voice.maneuvers.all { !it.third })
    }

    @Test
    fun `leaving the route asks for a new one and applies the answer`() {
        val route = route()
        nextRoute = route
        start(route, revision = 10, destination = point(1_000.0).toMapPoint())

        fix(NativeMapPoint(0.0, 70.0 / 111_320.0), accuracy = 5.0)

        assertEquals(1, routeRequests.size)
        assertEquals(11L, host.session.state.value.revision)
        assertFalse(host.session.state.value.rerouting)
    }

    @Test
    fun `a failed reroute waits and then lets the session ask again`() {
        val route = route()
        nextRoute = null
        start(route, revision = 12, destination = point(1_000.0).toMapPoint())

        fix(NativeMapPoint(0.0, 70.0 / 111_320.0), accuracy = 5.0)

        assertEquals(listOf(5_000L), waited)
        assertEquals(12L, host.session.state.value.revision)
        assertFalse(host.session.state.value.rerouting)
        fix(NativeMapPoint(0.0, 75.0 / 111_320.0), accuracy = 5.0)
        assertEquals(2, routeRequests.size)
        assertEquals(listOf(5_000L, 10_000L), waited)
    }

    @Test
    fun `arriving speaks once and the platform is released after the grace period`() {
        val route = route()
        start(route, revision = 14)

        fix(route.polyline.last())

        assertTrue(host.session.state.value.arrived)
        assertFalse(host.navigating)
        assertEquals(1, voice.arrivals)
        assertEquals(listOf(12_000L), waited)
        assertEquals(1, platform.stopped)
        assertEquals(1, platform.cleared)
    }

    @Test
    fun `stopping the trip clears the notification and releases the platform at once`() {
        val route = route()
        start(route, revision = 16)
        fix(route.polyline[1])

        assertTrue(host.session.stop(host.session.state.value.revision))

        assertFalse(host.navigating)
        assertEquals(1, platform.stopped)
        assertEquals(1, platform.cleared)
        assertTrue(waited.isEmpty())
        // A late fix is ignored.
        val progress = host.session.state.value.progressMeters
        fix(route.polyline.last())
        assertEquals(progress, host.session.state.value.progressMeters, 0.0)
    }

    @Test
    fun `the notification follows the next manoeuvre`() {
        val route = route()
        start(route, revision = 18)
        fix(route.polyline[0])

        assertTrue(platform.instructions.isNotEmpty())
        val first = platform.instructions.last()
        assertEquals("Turn right", first.first)

        fix(route.polyline[1])

        assertEquals("Arrive", platform.instructions.last().first)
    }

    @Test
    fun `closing the host during a trip releases the platform`() {
        start(route(), revision = 20)

        host.close()

        assertEquals(1, platform.stopped)
        assertEquals(1, platform.cleared)
    }

    private fun start(
        route: RoutingParsedRoute,
        revision: Long,
        destination: NativeMapPoint = route.polyline.last().toMapPoint(),
    ) {
        assertTrue(
            host.routeSession.submitAlternatives(
                revision = revision,
                candidates = listOf(
                    NativeRouteCandidate(
                        points = route.polyline.map { it.toMapPoint() },
                        restricted = List(route.polyline.size) { false },
                    ),
                ),
                selectedIndex = 0,
            ),
        )
        assertTrue(host.routeSession.commitSelectedAlternative(revision))
        assertTrue(
            host.session.start(
                revision = revision,
                route = route,
                destination = destination,
                mode = NativeRouteTransportMode.Driving,
                nowLabel = "Now",
            ),
        )
    }

    private fun fix(point: RoutingResponsePoint) = fix(point.toMapPoint())

    private fun fix(point: NativeMapPoint, accuracy: Double = 5.0) {
        gps.value = NativeShellGpsSnapshot(
            NativeShellGpsPhase.Active,
            NativeShellGpsFix(
                sequence = ++sequence,
                point = point,
                speedMetersPerSecond = 10.0,
                accuracyMeters = accuracy,
                headingDegrees = null,
                altitudeMeters = 100.0,
                receivedAtElapsedRealtimeMillis = sequence * 500,
            ),
        )
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
            speedLimits = emptyList(),
        )
    }

    private fun point(northMeters: Double) =
        RoutingResponsePoint(latitude = northMeters / 111_320.0, longitude = 0.0)

    private fun step(instruction: String, direction: String, location: RoutingResponsePoint) =
        RoutingResponseStep(instruction = instruction, direction = direction, distanceM = 100.0, location = location)

    private fun RoutingResponsePoint.toMapPoint() = NativeMapPoint(latitude, longitude)

    private class FakePlatform : NativeNavigationPlatform {
        var started = 0
        var stopped = 0
        var cleared = 0
        val instructions = mutableListOf<Pair<String, String>>()

        override fun guidanceStarted() {
            started += 1
        }

        override fun guidanceStopped() {
            stopped += 1
        }

        override fun showInstruction(instruction: String, distance: String) {
            instructions += instruction to distance
        }

        override fun clearInstruction() {
            cleared += 1
        }
    }

    private class FakeVoice : NativeVoiceGateway {
        val maneuvers = mutableListOf<Triple<String, Int, Boolean>>()
        var arrivals = 0
        override val state: StateFlow<NativeVoiceRuntimeSnapshot> =
            MutableStateFlow(NativeVoiceRuntimeSnapshot(NativeVoiceRuntimeStatus.Ready))

        override fun configure(languageCode: String, gender: NativeVoiceGender, speed: Double, volume: Double) = Unit

        override fun downloadAssets() = Unit

        override fun announceStart() = Unit

        override fun announceManeuver(instruction: String, distanceMeters: Int, nowMillis: Long, imperial: Boolean) {
            maneuvers += Triple(instruction, distanceMeters, imperial)
        }

        override fun announceArrival() {
            arrivals += 1
        }

        override fun setMuted(muted: Boolean) = Unit

        override fun stop() = Unit

        override fun close() = Unit
    }
}
