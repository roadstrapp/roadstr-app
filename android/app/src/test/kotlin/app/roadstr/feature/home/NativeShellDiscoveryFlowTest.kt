package app.roadstr.feature.home

import app.roadstr.core.discovery.DiscoveryNotice
import app.roadstr.core.discovery.DiscoveryOutcome
import app.roadstr.core.discovery.DiscoveryRequest
import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.discovery.RankedPlace
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.SearchArea
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.time.OpenState
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.route.NativeRoutePlanningSession
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.search.NativeSearchNearbyCategory
import app.roadstr.feature.search.NativeSearchNotice
import app.roadstr.feature.search.NativeSearchSession
import app.roadstr.feature.search.NativeSearchUiStatus
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeShellDiscoveryFlowTest {
    private val gps = SearchResponsePoint(45.0, 9.0)

    private class Gateway : NativeShellJourneyGateway {
        var outcome: DiscoveryOutcome = DiscoveryOutcome.NotApplicable
        var classic: List<SearchResult> = emptyList()
        var failDiscovery = false
        val searches = mutableListOf<String>()
        var lastRequest: DiscoveryRequest? = null

        override suspend fun search(
            query: String,
            near: SearchResponsePoint?,
            languageCode: String,
            onPartial: (List<SearchResult>) -> Unit,
        ): List<SearchResult> {
            searches += query
            return classic
        }

        override suspend fun routes(
            origin: SearchResponsePoint,
            destination: SearchResponsePoint,
            via: List<SearchResponsePoint>,
            mode: NativeRouteTransportMode,
            languageCode: String,
            avoidHighwaysAndTolls: Boolean,
            avoidUnpavedRoads: Boolean,
        ): List<RoutingParsedRoute> = emptyList()

        override suspend fun discover(request: DiscoveryRequest): DiscoveryOutcome {
            lastRequest = request
            if (failDiscovery) throw IOException("down")
            return outcome
        }
    }

    private class Harness(
        val gateway: Gateway = Gateway(),
        languageCode: String = "en",
        destination: SearchResponsePoint? = null,
        route: List<GeoPoint> = emptyList(),
    ) {
        val search = NativeSearchSession()
        val pins = mutableListOf<Pair<Long, List<RankedPlace>>>()
        val coordinator = NativeShellJourneyCoordinator(
            gateway = gateway,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = search,
            routeSession = NativeRoutePlanningSession(NativeRouteOverlaySession(0xFF71_58E2L)),
            languageCode = languageCode,
            destination = { destination },
            onDiscovery = { revision, places -> pins += revision to places },
            routeAhead = { route },
        )
    }

    private fun place(id: String, name: String, category: PlaceCategory = PlaceCategory.RESTAURANT) =
        RankedPlace(
            RoadstrPlace(
                id = id, osm = null, name = name, category = category,
                position = GeoPoint(45.001, 9.001), address = "Via Uno 3", distanceMeters = 120.0,
                openingHours = null, phone = null, website = null, cuisine = null, tags = emptyMap(),
                sources = setOf(PlaceSource.OPEN_STREET_MAP),
            ),
            score = 0.9,
            open = OpenState.UNKNOWN,
        )

    private fun found(vararg places: RankedPlace, notices: Set<DiscoveryNotice> = emptySet()) =
        DiscoveryOutcome.Found(places.toList(), notices, SearchArea.Circle(GeoPoint(45.0, 9.0), 5_000))

    @Test
    fun `a place search shows discovery rows and never runs the classic search`() {
        val h = Harness(languageCode = "it")
        h.gateway.outcome = found(place("a", "Verde"), notices = setOf(DiscoveryNotice.FEW_TAGGED))
        h.coordinator.openSearch(nearbyEnabled = true)

        assertTrue(h.coordinator.submitSearch("ristorante vegano vicino a me", gps))

        val state = h.search.state.value
        assertEquals(NativeSearchUiStatus.Results, state.status)
        assertEquals("Verde", state.results.single().title)
        assertEquals("Ristorante · Via Uno 3", state.results.single().subtitle)
        assertEquals("🍽️", state.results.single().emoji)
        assertEquals(NativeSearchNotice.FewTagged, state.notice)
        assertTrue(h.gateway.searches.isEmpty())
        assertEquals(listOf("a"), h.pins.last().second.map { it.place.id })
    }

    @Test
    fun `the request carries the interface language, the position and the destination`() {
        val destination = SearchResponsePoint(46.0, 10.0)
        val h = Harness(languageCode = "it", destination = destination)
        h.coordinator.openSearch(nearbyEnabled = false)
        h.coordinator.submitSearch("farmacia aperta vicino a me", gps)

        val request = h.gateway.lastRequest!!
        assertEquals("it", request.languageCode)
        assertTrue(request.query.openNow)
        assertEquals(GeoPoint(45.0, 9.0), request.device)
        assertEquals(GeoPoint(46.0, 10.0), request.destination)
    }

    @Test
    fun `the route still ahead goes along with the request`() {
        val route = listOf(GeoPoint(45.0, 9.0), GeoPoint(45.1, 9.0))
        val h = Harness(languageCode = "en", route = route)
        h.coordinator.openSearch(nearbyEnabled = true)
        h.coordinator.submitSearch("restaurant along the route", gps)
        assertEquals(route, h.gateway.lastRequest!!.route)
    }

    @Test
    fun `a name is searched the classic way without the near me words`() {
        val h = Harness(languageCode = "it")
        h.coordinator.openSearch(nearbyEnabled = false)
        h.coordinator.submitSearch("Esselunga vicino a me", gps)
        assertEquals(listOf("Esselunga"), h.gateway.searches)
    }

    @Test
    fun `an empty discovery falls back to the classic search and shows its results`() {
        val h = Harness()
        h.gateway.outcome = DiscoveryOutcome.Empty(setOf(DiscoveryNotice.FEW_TAGGED))
        h.gateway.classic = listOf(
            SearchResult("Classic, Town", "Classic", SearchResponsePoint(45.1, 9.1)),
        )
        h.coordinator.openSearch(nearbyEnabled = false)
        h.coordinator.submitSearch("vegan restaurant near me", gps)

        assertEquals(listOf("vegan restaurant"), h.gateway.searches)
        assertEquals("Classic", h.search.state.value.results.single().title)
        assertNull(h.search.state.value.notice)
    }

    @Test
    fun `when both are empty the notice explains why`() {
        val h = Harness()
        h.gateway.outcome = DiscoveryOutcome.Empty(setOf(DiscoveryNotice.FEW_TAGGED))
        h.coordinator.openSearch(nearbyEnabled = false)
        h.coordinator.submitSearch("vegan restaurant near me", gps)

        assertTrue(h.search.state.value.results.isEmpty())
        assertEquals(NativeSearchNotice.FewTagged, h.search.state.value.notice)
    }

    @Test
    fun `a discovery failure never stops the search`() {
        val h = Harness()
        h.gateway.failDiscovery = true
        h.gateway.classic = listOf(SearchResult("Classic", "Classic", SearchResponsePoint(45.1, 9.1)))
        h.coordinator.openSearch(nearbyEnabled = false)
        h.coordinator.submitSearch("pharmacy near me", gps)
        assertEquals(1, h.search.state.value.results.size)
    }

    @Test
    fun `the nearby chips go through discovery too`() {
        val h = Harness()
        h.gateway.outcome = found(place("a", "Rx", PlaceCategory.PHARMACY))
        h.coordinator.openSearch(nearbyEnabled = true)
        h.coordinator.submitNearby(NativeSearchNearbyCategory.Pharmacy, gps)

        assertEquals(listOf(PlaceCategory.PHARMACY), h.gateway.lastRequest!!.query.categories)
        assertEquals(NativeSearchUiStatus.Results, h.search.state.value.status)
    }

    @Test
    fun `opening the search or starting a new one clears the pins`() {
        val h = Harness()
        h.gateway.outcome = found(place("a", "Verde"))
        h.coordinator.openSearch(nearbyEnabled = false)
        h.coordinator.submitSearch("restaurant near me", gps)
        assertEquals(1, h.pins.last().second.size)

        h.coordinator.openSearch(nearbyEnabled = false)
        assertTrue(h.pins.last().second.isEmpty())

        h.coordinator.submitSearch("restaurant near me", gps)
        assertEquals(1, h.pins.last().second.size)
        h.gateway.outcome = DiscoveryOutcome.NotApplicable
        h.coordinator.submitSearch("restaurant near me", gps)
        assertTrue(h.pins.last().second.isEmpty())
    }
}
