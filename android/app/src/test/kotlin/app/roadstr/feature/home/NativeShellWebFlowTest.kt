package app.roadstr.feature.home

import app.roadstr.core.discovery.DiscoveryNotice
import app.roadstr.core.discovery.DiscoveryOutcome
import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.discovery.RankedPlace
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.SearchArea
import app.roadstr.core.discovery.web.EndpointRejection
import app.roadstr.core.discovery.web.SearxngCapability
import app.roadstr.core.discovery.web.WebDiscoveryMode
import app.roadstr.core.discovery.web.WebDiscoveryOutcome
import app.roadstr.core.discovery.web.WebDiscoverySettings
import app.roadstr.core.discovery.web.WebResult
import app.roadstr.core.discovery.web.WebSearchContext
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.time.OpenState
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.route.NativeRoutePlanningSession
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.search.NativeSearchSession
import app.roadstr.feature.search.NativeSearchWeb
import app.roadstr.feature.search.NativeWebProblem
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeShellWebFlowTest {
    private val gps = SearchResponsePoint(45.0, 9.0)
    private val own = WebDiscoverySettings(mode = WebDiscoveryMode.ASK, endpointText = "https://search.example.org")
    private val steaks = "bistecche di manzo a Trieste"
    private val pharmacy = "farmacia vicino a me"

    private class Gateway : NativeShellJourneyGateway {
        var discovery: DiscoveryOutcome = DiscoveryOutcome.NotApplicable
        var web: WebDiscoveryOutcome = WebDiscoveryOutcome.Disabled
        var webFails = false
        val webContexts = mutableListOf<WebSearchContext>()

        override suspend fun search(
            query: String,
            near: SearchResponsePoint?,
            languageCode: String,
            onPartial: (List<SearchResult>) -> Unit,
        ): List<SearchResult> = emptyList()

        override suspend fun routes(
            origin: SearchResponsePoint,
            destination: SearchResponsePoint,
            via: List<SearchResponsePoint>,
            mode: NativeRouteTransportMode,
            languageCode: String,
            avoidHighwaysAndTolls: Boolean,
            avoidUnpavedRoads: Boolean,
        ): List<RoutingParsedRoute> = emptyList()

        override suspend fun discover(request: app.roadstr.core.discovery.DiscoveryRequest) = discovery

        override suspend fun webSearch(context: WebSearchContext): WebDiscoveryOutcome {
            webContexts += context
            if (webFails) throw IOException("down")
            return web
        }
    }

    private class Harness(var settings: WebDiscoverySettings) {
        val gateway = Gateway()
        val search = NativeSearchSession()
        val coordinator = NativeShellJourneyCoordinator(
            gateway = gateway,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = search,
            routeSession = NativeRoutePlanningSession(NativeRouteOverlaySession(0xFF71_58E2L)),
            languageCode = "it",
            webSettings = { settings },
        )
        val web get() = search.state.value.web
    }

    private fun result(rank: Int) = WebResult(
        title = "Menu $rank",
        url = URI("https://menu$rank.example.com/carta"),
        host = "menu$rank.example.com",
        snippet = "Bistecche",
        engines = listOf("duckduckgo"),
        rank = rank,
    )

    private fun results() = WebDiscoveryOutcome.Results(listOf(result(1), result(2)), "search.example.org", emptyList())

    private fun searched(h: Harness, text: String) {
        h.coordinator.openSearch(nearbyEnabled = true)
        h.coordinator.submitSearch(text, gps)
    }

    private fun sparseFound() = DiscoveryOutcome.Found(
        listOf(
            RankedPlace(
                RoadstrPlace(
                    id = "a", osm = null, name = "Verde", category = PlaceCategory.RESTAURANT,
                    position = GeoPoint(45.001, 9.001), address = null, distanceMeters = 100.0,
                    openingHours = null, phone = null, website = null, cuisine = null, tags = emptyMap(),
                    sources = setOf(PlaceSource.OPEN_STREET_MAP),
                ),
                score = 0.9,
                open = OpenState.UNKNOWN,
            ),
        ),
        setOf(DiscoveryNotice.FEW_TAGGED),
        SearchArea.Circle(GeoPoint(45.0, 9.0), 5_000),
    )

    @Test
    fun `web search is invisible while it is off`() {
        val h = Harness(WebDiscoverySettings())
        searched(h, steaks)
        assertEquals(NativeSearchWeb.Hidden, h.web)
        assertTrue(h.gateway.webContexts.isEmpty())
    }

    @Test
    fun `without a usable instance nothing is offered`() {
        val plainHttp = own.copy(endpointText = "http://search.example.org")
        val h = Harness(plainHttp)
        searched(h, steaks)
        assertEquals(NativeSearchWeb.Hidden, h.web)
        assertEquals(NativeSearchWeb.Hidden, Harness(own.copy(endpointText = "")).also { searched(it, steaks) }.web)
    }

    @Test
    fun `an ordinary search only offers the web and asks before sending when set to ask`() {
        val h = Harness(own)
        searched(h, pharmacy)
        assertTrue(h.web is NativeSearchWeb.Offer)

        assertTrue(h.coordinator.searchWeb())
        val consent = h.web as NativeSearchWeb.Consent
        assertEquals("search.example.org", consent.host)
        assertTrue(h.gateway.webContexts.isEmpty())

        h.gateway.web = results()
        assertTrue(h.coordinator.confirmWeb())
        val shown = h.web as NativeSearchWeb.Results
        assertEquals(listOf("Menu 1", "Menu 2"), shown.rows.map { it.title })
        assertEquals("https://menu1.example.com/carta", shown.rows.first().url)
        assertEquals(1, h.gateway.webContexts.size)
    }

    @Test
    fun `leftover words ask at once and declining goes back to the offer`() {
        val h = Harness(own)
        searched(h, steaks)
        assertTrue(h.web is NativeSearchWeb.Consent)
        assertTrue(h.gateway.webContexts.isEmpty())

        assertTrue(h.coordinator.declineWeb())
        assertTrue(h.web is NativeSearchWeb.Offer)
        assertTrue(h.gateway.webContexts.isEmpty())
    }

    @Test
    fun `when set to on, leftover words search without asking`() {
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.web = results()
        searched(h, steaks)
        assertTrue(h.web is NativeSearchWeb.Results)
        assertEquals(1, h.gateway.webContexts.size)
    }

    @Test
    fun `when set to on, tapping the offer searches without a second question`() {
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.web = results()
        searched(h, pharmacy)
        assertTrue(h.web is NativeSearchWeb.Offer)

        assertTrue(h.coordinator.searchWeb())
        assertTrue(h.web is NativeSearchWeb.Results)
    }

    @Test
    fun `a sparsely tagged answer suggests the web even without leftover words`() {
        val h = Harness(own)
        h.gateway.discovery = sparseFound()
        searched(h, pharmacy)
        assertTrue(h.web is NativeSearchWeb.Consent)
    }

    @Test
    fun `the search sends the words and the position, never a street address`() {
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.web = results()
        searched(h, steaks)
        val context = h.gateway.webContexts.single()
        assertEquals("it", context.languageCode)
        assertEquals(GeoPoint(45.0, 9.0), context.device)
    }

    @Test
    fun `the question says when the name of the town goes along`() {
        val near = Harness(own)
        searched(near, pharmacy)
        near.coordinator.searchWeb()
        assertTrue((near.web as NativeSearchWeb.Consent).addsTown)

        val named = Harness(own)
        searched(named, steaks)
        assertFalse((named.web as NativeSearchWeb.Consent).addsTown)
    }

    @Test
    fun `without a position no town is added and the question does not claim it`() {
        val h = Harness(own)
        h.coordinator.openSearch(nearbyEnabled = false)
        h.coordinator.submitSearch(pharmacy, null)
        h.coordinator.searchWeb()
        assertFalse((h.web as NativeSearchWeb.Consent).addsTown)
    }

    @Test
    fun `each way the instance can fail is told apart`() {
        val cases = listOf(
            WebDiscoveryOutcome.Incompatible(SearxngCapability.JsonDisabled) to NativeWebProblem.JsonDisabled,
            WebDiscoveryOutcome.Incompatible(SearxngCapability.NotSearxng) to NativeWebProblem.NotSearxng,
            WebDiscoveryOutcome.Incompatible(SearxngCapability.NoEngineList) to NativeWebProblem.NoEngineList,
            WebDiscoveryOutcome.RateLimited(30) to NativeWebProblem.RateLimited,
            WebDiscoveryOutcome.Rejected(EndpointRejection.INVALID) to NativeWebProblem.Rejected,
            WebDiscoveryOutcome.Failed to NativeWebProblem.Unreachable,
        )
        for ((outcome, problem) in cases) {
            val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
            h.gateway.web = outcome
            searched(h, steaks)
            assertEquals(NativeSearchWeb.Unavailable(problem), h.web)
        }
    }

    @Test
    fun `an exception from the provider reads as unreachable`() {
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.webFails = true
        searched(h, steaks)
        assertEquals(NativeSearchWeb.Unavailable(NativeWebProblem.Unreachable), h.web)
    }

    @Test
    fun `a provider that says disabled leaves the list without a web part`() {
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.web = WebDiscoveryOutcome.Disabled
        searched(h, steaks)
        assertEquals(NativeSearchWeb.Hidden, h.web)
    }

    @Test
    fun `a new search forgets a pending web question`() {
        val h = Harness(own)
        searched(h, steaks)
        assertTrue(h.web is NativeSearchWeb.Consent)
        h.coordinator.dismissSearch()
        assertFalse(h.coordinator.confirmWeb())
        assertTrue(h.gateway.webContexts.isEmpty())
    }

    @Test
    fun `the settings are read again at every search`() {
        val h = Harness(WebDiscoverySettings())
        searched(h, steaks)
        assertEquals(NativeSearchWeb.Hidden, h.web)
        h.settings = own
        searched(h, steaks)
        assertTrue(h.web is NativeSearchWeb.Consent)
    }
}
