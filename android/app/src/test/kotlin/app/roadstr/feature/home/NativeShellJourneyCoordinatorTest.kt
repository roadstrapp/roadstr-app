package app.roadstr.feature.home

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.route.NativeRoutePlanningSession
import app.roadstr.feature.route.NativeRoutePlanningStatus
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.search.NativeSearchSession
import app.roadstr.feature.search.NativeSearchUiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeShellJourneyCoordinatorTest {
    @Test
    fun `live destination flow reaches route alternatives and map geometry`() {
        val gateway = FakeGateway()
        val search = NativeSearchSession()
        val overlay = NativeRouteOverlaySession(0xFF71_58E2L)
        val planner = NativeRoutePlanningSession(overlay)
        val coordinator = NativeShellJourneyCoordinator(
            gateway = gateway,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = search,
            routeSession = planner,
            languageCode = "it",
        )
        val gps = SearchResponsePoint(45.0, 7.0)

        assertTrue(coordinator.openSearch(nearbyEnabled = true))
        assertTrue(coordinator.updateSearchQuery("Torino"))
        assertEquals(NativeSearchUiStatus.Browsing, search.state.value.status)
        assertTrue(coordinator.submitSearch("Torino", gps))
        assertEquals(NativeSearchUiStatus.Results, search.state.value.status)

        val result = search.state.value.results.single()
        assertTrue(coordinator.selectDestination(result, gps, "La mia posizione"))
        assertEquals(NativeRoutePlanningStatus.Planner, planner.state.value.status)
        assertEquals("La mia posizione", planner.state.value.originQuery)

        assertTrue(coordinator.calculateRoute(planner.state.value, gps, "La mia posizione"))
        assertEquals(NativeRoutePlanningStatus.Alternatives, planner.state.value.status)
        assertEquals(1, overlay.state.value.alternativeCount)
        assertEquals(2, overlay.state.value.snapshot.activeRuns.single().points.size)
        assertEquals(NativeRouteTransportMode.Driving, gateway.lastMode)
        assertEquals("it", gateway.lastLanguage)
        coordinator.close()
    }

    @Test
    fun `transit remains local and never leaks into the road gateway`() {
        val gateway = FakeGateway()
        val search = NativeSearchSession()
        val planner = NativeRoutePlanningSession(NativeRouteOverlaySession(0xFF71_58E2L))
        val coordinator = NativeShellJourneyCoordinator(
            gateway = gateway,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = search,
            routeSession = planner,
        )
        val gps = SearchResponsePoint(45.0, 7.0)

        coordinator.openSearch(true)
        coordinator.submitSearch("Torino", gps)
        coordinator.selectDestination(search.state.value.results.single(), gps, "My location")
        planner.selectMode(planner.state.value.revision, NativeRouteTransportMode.Transit)

        assertFalse(coordinator.calculateRoute(planner.state.value, gps, "My location"))
        assertEquals(NativeRoutePlanningStatus.Planner, planner.state.value.status)
        assertEquals(0, gateway.routeCalls)
        coordinator.close()
    }

    private class FakeGateway : NativeShellJourneyGateway {
        var routeCalls = 0
        var lastMode: NativeRouteTransportMode? = null
        var lastLanguage: String? = null

        override suspend fun search(
            query: String,
            near: SearchResponsePoint?,
            languageCode: String,
            onPartial: (List<SearchResult>) -> Unit,
        ): List<SearchResult> = listOf(
            SearchResult(
                displayName = "Torino, Piemonte, Italia",
                shortName = "Torino",
                position = SearchResponsePoint(45.0703, 7.6869),
            ),
        )

        override suspend fun routes(
            origin: SearchResponsePoint,
            destination: SearchResponsePoint,
            via: List<SearchResponsePoint>,
            mode: NativeRouteTransportMode,
            languageCode: String,
        ): List<RoutingParsedRoute> {
            routeCalls += 1
            lastMode = mode
            lastLanguage = languageCode
            return listOf(
                RoutingParsedRoute(
                    polyline = listOf(
                        RoutingResponsePoint(origin.latitude, origin.longitude),
                        RoutingResponsePoint(destination.latitude, destination.longitude),
                    ),
                    steps = emptyList(),
                    totalDistanceM = 10_000.0,
                    totalDurationS = 900.0,
                ),
            )
        }
    }
}
