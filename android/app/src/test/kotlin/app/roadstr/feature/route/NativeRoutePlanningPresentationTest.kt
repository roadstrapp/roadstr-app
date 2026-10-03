package app.roadstr.feature.route

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.RoutingRouteAvoidance
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeRouteOverlaySession
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRoutePlanningPresentationTest {
    @Test
    fun `wire transport modes preserve four profiles with driving fallback`() {
        assertEquals(
            NativeRouteTransportMode.entries,
            NativeRouteTransportMode.entries.map { NativeRouteTransportMode.fromWire(it.wireValue) },
        )
        assertEquals(NativeRouteTransportMode.Driving, NativeRouteTransportMode.fromWire("unknown"))
        assertEquals(NativeRouteTransportMode.Driving, NativeRouteTransportMode.fromWire(null))
    }

    @Test
    fun `cards mirror Flutter duration distance and badge priority`() {
        val cards = NativeRoutePlanningPresenter.alternatives(
            listOf(
                candidate(route(distance = 12_340.0, duration = 3_630.0)),
                candidate(route(avoidance = RoutingRouteAvoidance.HighwayAndTollFree)),
                candidate(route(avoidance = RoutingRouteAvoidance.MinimizedHighwaysAndTolls)),
                candidate(route(avoidance = RoutingRouteAvoidance.OffRoadAvoided)),
            ),
            imperial = false,
        )

        assertEquals(61, cards[0].durationMinutes)
        assertEquals(1, cards[0].durationHours)
        assertEquals(1, cards[0].durationMinuteRemainder)
        assertEquals("12.3 km", cards[0].distanceLabel)
        assertEquals(NativeRouteBadge.Fastest, cards[0].badge)
        assertEquals(NativeRouteBadge.AvoidHighwaysAndTolls, cards[1].badge)
        assertEquals(NativeRouteBadge.UnavoidableHighwayOrToll, cards[2].badge)
        assertEquals(NativeRouteBadge.AvoidUnpavedRoads, cards[3].badge)
    }

    @Test
    fun `imperial route cards reuse exact shared distance thresholds`() {
        val cards = NativeRoutePlanningPresenter.alternatives(
            listOf(candidate(route(distance = 1_609.344))),
            imperial = true,
        )

        assertEquals("1.0 mi", cards.single().distanceLabel)
    }

    @Test
    fun `preview clock rounds provider seconds like Flutter`() {
        val (departure, arrival) = NativeRoutePlanningPresenter.departureAndArrival(
            route(duration = 3_630.5),
            LocalDateTime.of(2024, 1, 1, 23, 30),
        )

        assertEquals("23:30", departure)
        assertEquals("00:30", arrival)
    }

    @Test
    fun `preview conditions are normalized capped and bounded`() {
        val values = NativeRoutePlanningPresenter.normalizeConditions(
            listOf(
                NativeRouteConditionPresentation("  Accident\u0000 ", "  blocked lane  "),
                NativeRouteConditionPresentation("", "ignored"),
                NativeRouteConditionPresentation("Camera", null),
                NativeRouteConditionPresentation("Bump", "x".repeat(800)),
                NativeRouteConditionPresentation("Traffic", null),
            ),
        )

        assertEquals(3, values.size)
        assertEquals("Accident", values[0].categoryLabel)
        assertEquals("blocked lane", values[0].comment)
        assertEquals(500, values[2].comment?.length)
    }

    @Test
    fun `malformed routes and restriction vectors fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeRoutePlanningPresenter.alternatives(
                listOf(candidate(route(distance = Double.NaN))),
                false,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRoutePlanningPresenter.alternatives(
                listOf(candidate(route(), restrictions = listOf(false))),
                false,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRoutePlanningPresenter.alternatives(emptyList(), false)
        }
    }

    @Test
    fun `planner preserves destination and inserts intermediate stops before it`() {
        val session = session()
        assertTrue(session.showPlanner(1, "My location", "Rome", hasGps = true))

        assertTrue(session.addStop(1))
        assertTrue(session.updateStop(1, 0, "Florence"))
        val state = session.state.value

        assertEquals(listOf("Florence", "Rome"), state.stops.map { it.query })
        assertTrue(state.canCalculate)
        assertEquals(0, state.activeStopIndex)
    }

    @Test
    fun `planner bounds five stops and never removes the final destination`() {
        val session = session()
        session.showPlanner(1, "Origin", "Destination")

        repeat(4) { assertTrue(session.addStop(1)) }
        assertEquals(5, session.state.value.stops.size)
        assertFalse(session.addStop(1))
        repeat(4) { assertTrue(session.removeStop(1, 0)) }
        assertEquals(1, session.state.value.stops.size)
        assertFalse(session.removeStop(1, 0))
    }

    @Test
    fun `planner reorder changes journey order while preserving stable stop ids`() {
        val session = session()
        session.showPlanner(1, "Origin", "Destination")
        session.addStop(1)
        session.updateStop(1, 0, "Stop")
        val before = session.state.value.stops

        assertTrue(session.reorderStop(1, 0, 1))
        val after = session.state.value.stops
        assertEquals(before[0].id, after[1].id)
        assertEquals(listOf("Destination", "Stop"), after.map { it.query })
        assertFalse(session.reorderStop(1, 1, 1))
    }

    @Test
    fun `route request requires a complete planner and fences stale generations`() {
        val session = session()
        session.showPlanner(1, "", "Destination")
        assertFalse(session.beginRouteRequest(2))
        session.updateOrigin(1, "Origin")

        assertTrue(session.beginRouteRequest(2))
        assertEquals(NativeRoutePlanningStatus.Loading, session.state.value.status)
        assertFalse(session.updateOrigin(1, "stale"))
        assertTrue(session.failRouteRequest(2))
        assertEquals(NativeRoutePlanningStatus.Planner, session.state.value.status)
    }

    @Test
    fun `alternatives synchronize selection with the map overlay`() {
        val overlay = NativeRouteOverlaySession(0xFF71_58E2L)
        val session = session(overlay)
        readyPlanner(session)
        val routes = listOf(candidate(route(latitude = 45.0)), candidate(route(latitude = 46.0)))

        assertTrue(session.submitAlternatives(2, routes, selectedIndex = 0, destinationLabel = " Milan "))
        assertEquals(2, overlay.state.value.alternativeCount)
        assertEquals(0, overlay.state.value.selectedAlternativeIndex)
        assertTrue(session.selectAlternative(2, 1))
        assertEquals(1, session.state.value.selectedIndex)
        assertEquals(1, overlay.state.value.selectedAlternativeIndex)
        assertEquals("Milan", session.state.value.destinationLabel)
    }

    @Test
    fun `map tap selection updates the same route card state`() {
        val overlay = NativeRouteOverlaySession(0xFF71_58E2L)
        val session = session(overlay)
        readyPlanner(session)
        session.submitAlternatives(
            2,
            listOf(candidate(route(latitude = 45.0)), candidate(route(latitude = 46.0))),
        )

        assertTrue(session.selectAlternativeAt(2, NativeMapPoint(46.0, 9.0)))
        assertEquals(1, session.state.value.selectedIndex)
        assertFalse(session.selectAlternativeAt(1, NativeMapPoint(45.0, 9.0)))
    }

    @Test
    fun `avoidance state is driving-only and replacement safe`() {
        val session = session()
        readyAlternatives(session)

        assertTrue(session.setAvoidanceState(2, enabled = true, loading = true))
        assertFalse(session.setAvoidanceState(2, enabled = true, loading = true))
        assertTrue(session.selectMode(2, NativeRouteTransportMode.Walking))
        assertFalse(session.state.value.avoidanceEnabled)
        assertFalse(session.setAvoidanceState(2, enabled = true, loading = false))
    }

    @Test
    fun `confirmation commits selected geometry and emits bounded preview`() {
        val overlay = NativeRouteOverlaySession(0xFF71_58E2L)
        val session = session(overlay)
        readyAlternatives(session)
        session.selectAlternative(2, 1)

        assertTrue(
            session.confirmSelection(
                2,
                NativeRoutePreviewMetadata(
                    destinationLabel = "Milan",
                    trafficStatus = "Moderate traffic",
                    conditions = listOf(NativeRouteConditionPresentation("Accident", "lane closed")),
                    now = LocalDateTime.of(2024, 1, 1, 10, 0),
                ),
            ),
        )

        val state = session.state.value
        assertEquals(NativeRoutePlanningStatus.Preview, state.status)
        assertEquals(1, state.alternatives.size)
        assertEquals("10:00", state.departureLabel)
        assertEquals("10:10", state.arrivalLabel)
        assertEquals("Moderate traffic", state.trafficStatus)
        assertNull(overlay.state.value.selectedAlternativeIndex)
        assertEquals(0, overlay.state.value.alternativeCount)
    }

    @Test
    fun `preview hands one defensive route to navigation without clearing geometry`() {
        val overlay = NativeRouteOverlaySession(0xFF71_58E2L)
        val session = session(overlay)
        readyAlternatives(session)
        assertTrue(session.confirmSelection(2))

        val selected = session.selectedNavigationRoute(2)
        assertEquals(2, selected?.polyline?.size)
        assertTrue(session.beginNavigation(2))
        assertEquals(NativeRoutePlanningStatus.Hidden, session.state.value.status)
        assertFalse(overlay.state.value.snapshot.activeRuns.isEmpty())
        assertNull(session.selectedNavigationRoute(2))
        assertFalse(session.beginNavigation(2))
    }

    @Test
    fun `unit changes reproject cards without replacing route geometry`() {
        val overlay = NativeRouteOverlaySession(0xFF71_58E2L)
        val session = session(overlay)
        readyAlternatives(session, distance = 1_609.344)
        val revision = overlay.state.value.revision

        assertTrue(session.updateUnits(true))
        assertEquals("1.0 mi", session.state.value.alternatives.first().distanceLabel)
        assertEquals(revision, overlay.state.value.revision)
        assertFalse(session.updateUnits(true))
    }

    @Test
    fun `hide clears route and rejects stale callbacks`() {
        val overlay = NativeRouteOverlaySession(0xFF71_58E2L)
        val session = session(overlay)
        readyAlternatives(session)

        assertTrue(session.hide(3))
        assertEquals(NativeRoutePlanningStatus.Hidden, session.state.value.status)
        assertTrue(overlay.state.value.snapshot.activeRuns.isEmpty())
        assertFalse(session.selectAlternative(2, 1))
        assertFalse(session.hide(2))
    }

    private fun readyPlanner(session: NativeRoutePlanningSession) {
        assertTrue(session.showPlanner(1, "Origin", "Destination"))
        assertTrue(session.beginRouteRequest(2))
    }

    private fun readyAlternatives(
        session: NativeRoutePlanningSession,
        distance: Double = 1_000.0,
    ) {
        readyPlanner(session)
        assertTrue(
            session.submitAlternatives(
                2,
                listOf(
                    candidate(route(distance = distance, duration = 300.0, latitude = 45.0)),
                    candidate(route(distance = 2_000.0, duration = 600.0, latitude = 46.0)),
                ),
            ),
        )
    }

    private fun session(
        overlay: NativeRouteOverlaySession = NativeRouteOverlaySession(0xFF71_58E2L),
    ) = NativeRoutePlanningSession(overlay)

    private fun candidate(
        route: RoutingParsedRoute,
        restrictions: List<Boolean> = List(route.polyline.size) { false },
    ) = NativeRoutePlanningCandidate(route, restrictions)

    private fun route(
        distance: Double = 1_000.0,
        duration: Double = 600.0,
        latitude: Double = 45.0,
        avoidance: RoutingRouteAvoidance = RoutingRouteAvoidance.None,
    ) = RoutingParsedRoute(
        polyline = listOf(
            RoutingResponsePoint(latitude, 9.0),
            RoutingResponsePoint(latitude, 9.01),
        ),
        steps = emptyList(),
        totalDistanceM = distance,
        totalDurationS = duration,
        avoidance = avoidance,
    )
}
