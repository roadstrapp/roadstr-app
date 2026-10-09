package app.roadstr.feature.home

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.navigation.NativeNavigationRerouteRequest
import app.roadstr.feature.route.NativeRoutePlanningSession
import app.roadstr.feature.route.NativeRoutePlanningStatus
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.search.NativeSearchFavorite
import app.roadstr.feature.search.NativeSearchSession
import app.roadstr.feature.search.NativeSearchUiStatus
import app.roadstr.feature.savedroute.NativeSavedRouteProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeShellJourneyCoordinatorTest {
    @Test
    fun `save reopen and start uses stored geometry without a routing call`() {
        val gateway = FakeGateway()
        val firstPlanner = NativeRoutePlanningSession(NativeRouteOverlaySession(0xFF71_58E2L))
        val first = NativeShellJourneyCoordinator(
            gateway = gateway,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = NativeSearchSession(),
            routeSession = firstPlanner,
            languageCode = "en",
        )
        val gps = SearchResponsePoint(51.5074, -0.1278)
        assertTrue(first.selectDestination("Edinburgh", SearchResponsePoint(55.9533, -3.1883), gps, "My location"))
        assertTrue(first.calculateRoute(firstPlanner.state.value, gps, "My location"))
        val saved = first.buildSavedRoute(
            name = "Northern trip",
            providerId = "osrm",
            engineId = "osrm",
            avoidUnpavedRoads = false,
            nowEpochMillis = 1_800_000_000_000,
        )!!
        val reopened = NativeSavedRouteProtocol.decode(
            NativeSavedRouteProtocol.encode(listOf(saved)),
        ).single()
        first.close()

        val secondPlanner = NativeRoutePlanningSession(NativeRouteOverlaySession(0xFF71_58E2L))
        val second = NativeShellJourneyCoordinator(
            gateway = gateway,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = NativeSearchSession(),
            routeSession = secondPlanner,
            languageCode = "en",
        )
        val routeCallsBeforeOpen = gateway.routeCalls

        assertTrue(second.openSavedRoute(reopened))
        assertEquals(NativeRoutePlanningStatus.Preview, secondPlanner.state.value.status)
        assertTrue(secondPlanner.selectedNavigationRoute(secondPlanner.state.value.revision) != null)
        assertEquals(SearchResponsePoint(55.9533, -3.1883), second.navigationDestination(secondPlanner.state.value.revision))
        assertEquals(routeCallsBeforeOpen, gateway.routeCalls)
        second.close()
    }

    @Test
    fun `editing unchanged saved stops recalculates their exact coordinates`() {
        val gateway = FakeGateway()
        val planner = NativeRoutePlanningSession(NativeRouteOverlaySession(0xFF71_58E2L))
        val coordinator = NativeShellJourneyCoordinator(
            gateway = gateway,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = NativeSearchSession(),
            routeSession = planner,
        )
        val route = NativeSavedRouteProtocol.create(
            id = "saved",
            name = "Saved",
            createdAtEpochMillis = 1,
            nowEpochMillis = 1,
            stops = listOf(
                app.roadstr.feature.savedroute.NativeSavedRouteStop(
                    "Start",
                    RoutingResponsePoint(40.4168, -3.7038),
                ),
                app.roadstr.feature.savedroute.NativeSavedRouteStop(
                    "Finish",
                    RoutingResponsePoint(38.7223, -9.1393),
                ),
            ),
            preferences = app.roadstr.feature.savedroute.NativeSavedRoutePreferences("driving"),
            providerId = "osrm",
            engineId = "osrm",
            route = RoutingParsedRoute(
                polyline = listOf(
                    RoutingResponsePoint(40.4168, -3.7038),
                    RoutingResponsePoint(38.7223, -9.1393),
                ),
                steps = emptyList(),
                totalDistanceM = 625_000.0,
                totalDurationS = 21_600.0,
            ),
        )

        assertTrue(coordinator.editSavedRoute(route, hasGps = false))
        assertTrue(coordinator.calculateRoute(planner.state.value, null, "My location"))

        assertEquals(0, gateway.searchCalls)
        assertEquals(SearchResponsePoint(40.4168, -3.7038), gateway.lastOrigin)
        assertEquals(SearchResponsePoint(38.7223, -9.1393), gateway.lastDestination)
        coordinator.close()
    }

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
        val routeRevision = planner.state.value.revision
        assertTrue(planner.confirmSelection(routeRevision))
        assertEquals(
            SearchResponsePoint(45.0703, 7.6869),
            coordinator.navigationDestination(routeRevision),
        )
        coordinator.close()
    }

    @Test
    fun `saved places are listed when the search opens and narrowed by what is typed`() {
        val search = NativeSearchSession()
        val planner = NativeRoutePlanningSession(NativeRouteOverlaySession(0xFF71_58E2L))
        val saved = listOf(
            NativeSearchFavorite("Casa", "Via Roma 1, Torino", SearchResponsePoint(45.0, 7.0)),
            NativeSearchFavorite("Lavoro", "Corso Francia 9, Torino", SearchResponsePoint(45.1, 7.1)),
        )
        val coordinator = NativeShellJourneyCoordinator(
            gateway = FakeGateway(),
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = search,
            routeSession = planner,
            languageCode = "it",
            favorites = { saved },
        )

        assertTrue(coordinator.openSearch(nearbyEnabled = false))
        assertEquals(listOf("Casa", "Lavoro"), search.state.value.favorites.map { it.label })

        // Label or address, any case.
        coordinator.updateSearchQuery("francia")
        assertEquals(listOf("Lavoro"), search.state.value.favorites.map { it.label })
        coordinator.updateSearchQuery("torino")
        assertEquals(2, search.state.value.favorites.size)
        coordinator.updateSearchQuery("nessuno")
        assertTrue(search.state.value.favorites.isEmpty())
        coordinator.close()
    }

    @Test
    fun `a failing saved-places read never keeps the search from opening`() {
        val search = NativeSearchSession()
        val coordinator = NativeShellJourneyCoordinator(
            gateway = FakeGateway(),
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = search,
            routeSession = NativeRoutePlanningSession(NativeRouteOverlaySession(0xFF71_58E2L)),
            languageCode = "it",
            favorites = { error("storage unavailable") },
        )

        assertTrue(coordinator.openSearch(nearbyEnabled = false))
        assertTrue(search.state.value.favorites.isEmpty())
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

    @Test
    fun `active reroute forwards motion context and returns one parsed route`() {
        val gateway = FakeGateway()
        val coordinator = NativeShellJourneyCoordinator(
            gateway = gateway,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = NativeSearchSession(),
            routeSession = NativeRoutePlanningSession(
                NativeRouteOverlaySession(0xFF71_58E2L),
            ),
            languageCode = "IT",
        )
        var acceptedSequence: Long? = null
        var acceptedRoute: RoutingParsedRoute? = null
        var failedSequence: Long? = null
        val request = NativeNavigationRerouteRequest(
            sequence = 9,
            revision = 4,
            origin = NativeMapPoint(45.0, 7.0),
            destination = NativeMapPoint(46.0, 8.0),
            mode = NativeRouteTransportMode.Driving,
            speedKilometresPerHour = 42.0,
            headingDegrees = 181.0,
            straightLineDistanceMeters = 2_000.0,
        )

        assertTrue(
            coordinator.reroute(
                request = request,
                onSuccess = { sequence, route ->
                    acceptedSequence = sequence
                    acceptedRoute = route
                },
                onFailure = { failedSequence = it },
            ),
        )

        assertEquals(9L, acceptedSequence)
        assertTrue(acceptedRoute != null)
        assertNull(failedSequence)
        assertEquals(1, gateway.rerouteCalls)
        assertEquals(42.0, gateway.lastSpeed, 0.0)
        assertEquals(181.0, gateway.lastHeading!!, 0.0)
        assertEquals("it", gateway.lastLanguage)
        coordinator.close()
    }

    private class FakeGateway : NativeShellJourneyGateway {
        var searchCalls = 0
        var routeCalls = 0
        var rerouteCalls = 0
        var lastMode: NativeRouteTransportMode? = null
        var lastLanguage: String? = null
        var lastSpeed = 0.0
        var lastHeading: Double? = null
        var lastOrigin: SearchResponsePoint? = null
        var lastDestination: SearchResponsePoint? = null

        override suspend fun search(
            query: String,
            near: SearchResponsePoint?,
            languageCode: String,
            onPartial: (List<SearchResult>) -> Unit,
        ): List<SearchResult> {
            searchCalls += 1
            return listOf(
                SearchResult(
                    displayName = "Torino, Piemonte, Italia",
                    shortName = "Torino",
                    position = SearchResponsePoint(45.0703, 7.6869),
                ),
            )
        }

        override suspend fun routes(
            origin: SearchResponsePoint,
            destination: SearchResponsePoint,
            via: List<SearchResponsePoint>,
            mode: NativeRouteTransportMode,
            languageCode: String,
            avoidHighwaysAndTolls: Boolean,
            avoidUnpavedRoads: Boolean,
        ): List<RoutingParsedRoute> {
            routeCalls += 1
            lastOrigin = origin
            lastDestination = destination
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

        override suspend fun reroute(
            origin: SearchResponsePoint,
            destination: SearchResponsePoint,
            mode: NativeRouteTransportMode,
            languageCode: String,
            speedKilometresPerHour: Double,
            headingDegrees: Double?,
            straightLineDistanceMeters: Double,
            avoidUnpavedRoads: Boolean,
        ): List<RoutingParsedRoute> {
            rerouteCalls += 1
            lastMode = mode
            lastLanguage = languageCode
            lastSpeed = speedKilometresPerHour
            lastHeading = headingDegrees
            return routes(
                origin,
                destination,
                emptyList(),
                mode,
                languageCode,
                avoidHighwaysAndTolls = false,
                avoidUnpavedRoads = avoidUnpavedRoads,
            )
        }
    }
}
