package app.roadstr.feature.home

import app.roadstr.core.discovery.DiscoveryNotice
import app.roadstr.core.discovery.DiscoveryOutcome
import app.roadstr.core.discovery.OsmElementType
import app.roadstr.core.discovery.OsmRef
import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.discovery.RankedPlace
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.SearchArea
import app.roadstr.core.discovery.resolve.MatchClass
import app.roadstr.core.discovery.structured.JsonLdPlaceParser
import app.roadstr.core.discovery.structured.WebPageMessage
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
import app.roadstr.feature.search.NativeWebPlaceLink
import app.roadstr.feature.search.NativeSearchWeb
import app.roadstr.feature.search.NativeWebProblem
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        var lookup: (String) -> List<RoadstrPlace> = { emptyList() }
        var lookupFails = false
        val lookups = mutableListOf<String>()

        override suspend fun lookupPlaces(
            name: String,
            locality: String?,
            near: GeoPoint,
            languageCode: String,
        ): List<RoadstrPlace> {
            lookups += name
            if (lookupFails) throw IOException("down")
            return lookup(name)
        }

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
        val pinned = mutableListOf<List<RankedPlace>>()
        val coordinator = NativeShellJourneyCoordinator(
            gateway = gateway,
            scope = CoroutineScope(Dispatchers.Unconfined + Job()),
            searchSession = search,
            routeSession = NativeRoutePlanningSession(NativeRouteOverlaySession(0xFF71_58E2L)),
            languageCode = "it",
            webSettings = { settings },
            onDiscovery = { _, places -> pinned += places },
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

    private fun placeWithSite(id: Long, name: String, site: String?) = RoadstrPlace(
        id = "osm:n:$id", osm = OsmRef(OsmElementType.NODE, id), name = name, category = PlaceCategory.RESTAURANT,
        position = GeoPoint(45.001, 9.001), address = null, distanceMeters = 100.0, openingHours = null,
        phone = null, website = site?.let(::URI), cuisine = null, tags = mapOf("name" to name),
        sources = setOf(PlaceSource.OPEN_STREET_MAP),
    )

    private fun foundWith(place: RoadstrPlace) = DiscoveryOutcome.Found(
        listOf(RankedPlace(place, 0.9, OpenState.UNKNOWN)),
        emptySet(),
        SearchArea.Circle(GeoPoint(45.0, 9.0), 5_000),
    )

    private fun rowsOf(h: Harness) = (h.web as NativeSearchWeb.Results).rows

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
    fun `a result on the place's own website is linked to it and opens it`() {
        val verde = placeWithSite(5, "Trattoria Verde", "https://www.trattoriaverde.it")
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.discovery = foundWith(verde)
        h.gateway.web = WebDiscoveryOutcome.Results(
            listOf(
                WebResult("Trattoria Verde - Menu", URI("https://trattoriaverde.it/menu"), "trattoriaverde.it", "Bistecche", emptyList(), 1),
                WebResult("Ricetta della bistecca", URI("https://ricette.example.org/b"), "ricette.example.org", "", emptyList(), 2),
            ),
            "search.example.org",
            emptyList(),
        )
        searched(h, steaks)
        val rows = rowsOf(h)
        assertEquals(NativeWebPlaceLink.Linked("osm:n:5", "Trattoria Verde"), rows[0].link)
        assertEquals(NativeWebPlaceLink.None, rows[1].link)
        assertEquals(verde, h.coordinator.webPlace("osm:n:5"))
        assertTrue("a place the search already pinned gets no second pin", h.pinned.last().size == 1)
        assertTrue(h.gateway.lookups.size <= 1)
    }

    @Test
    fun `a name on an unrelated page is only a candidate`() {
        val verde = placeWithSite(5, "Trattoria Verde", "https://www.trattoriaverde.it")
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.discovery = foundWith(verde)
        h.gateway.web = WebDiscoveryOutcome.Results(
            listOf(WebResult("Trattoria Verde, Verona - recensioni", URI("https://blog.example.org/v"), "blog.example.org", "", emptyList(), 1)),
            "search.example.org",
            emptyList(),
        )
        searched(h, steaks)
        assertEquals(NativeWebPlaceLink.Candidate("osm:n:5", "Trattoria Verde"), rowsOf(h).single().link)
    }

    @Test
    fun `a place found only by name is pinned when its website agrees`() {
        val verde = placeWithSite(5, "Trattoria Verde", "https://www.trattoriaverde.it")
        val blu = placeWithSite(77, "Osteria Blu", "https://www.osteriablu.it")
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.discovery = foundWith(verde)
        h.gateway.lookup = { listOf(blu) }
        h.gateway.web = WebDiscoveryOutcome.Results(
            listOf(WebResult("Osteria Blu - Menu", URI("https://osteriablu.it/menu"), "osteriablu.it", "", emptyList(), 1)),
            "search.example.org",
            emptyList(),
            locality = "Trieste",
        )
        searched(h, steaks)
        assertEquals(listOf("Osteria Blu"), h.gateway.lookups)
        assertEquals(NativeWebPlaceLink.Linked("osm:n:77", "Osteria Blu"), rowsOf(h).single().link)
        assertEquals(listOf("osm:n:5", "osm:n:77"), h.pinned.last().map { it.place.id })
        assertEquals(blu.copy(confidence = h.pinned.last().last().place.confidence), h.pinned.last().last().place)
    }

    @Test
    fun `a failing name lookup leaves plain web results and no extra pin`() {
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.discovery = foundWith(placeWithSite(5, "Trattoria Verde", null))
        h.gateway.lookupFails = true
        h.gateway.web = WebDiscoveryOutcome.Results(
            listOf(WebResult("Osteria Blu - Menu", URI("https://osteriablu.it/menu"), "osteriablu.it", "", emptyList(), 1)),
            "search.example.org",
            emptyList(),
        )
        searched(h, steaks)
        assertEquals(NativeWebPlaceLink.None, rowsOf(h).single().link)
        assertEquals(1, h.pinned.last().size)
    }

    @Test
    fun `results are plain when there is nowhere to judge the area from`() {
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.web = WebDiscoveryOutcome.Results(
            listOf(WebResult("Osteria Blu - Menu", URI("https://osteriablu.it/menu"), "osteriablu.it", "", emptyList(), 1)),
            "search.example.org",
            emptyList(),
        )
        h.coordinator.openSearch(nearbyEnabled = false)
        h.coordinator.submitSearch(steaks, null)
        assertEquals(NativeWebPlaceLink.None, rowsOf(h).single().link)
        assertTrue(h.gateway.lookups.isEmpty())
    }

    @Test
    fun `a new search forgets the places tied to the old one`() {
        val verde = placeWithSite(5, "Trattoria Verde", "https://www.trattoriaverde.it")
        val h = Harness(own.copy(mode = WebDiscoveryMode.ON))
        h.gateway.discovery = foundWith(verde)
        h.gateway.web = WebDiscoveryOutcome.Results(
            listOf(WebResult("Trattoria Verde - Menu", URI("https://trattoriaverde.it/menu"), "trattoriaverde.it", "", emptyList(), 1)),
            "search.example.org",
            emptyList(),
        )
        searched(h, steaks)
        assertEquals(verde, h.coordinator.webPlace("osm:n:5"))
        h.gateway.discovery = DiscoveryOutcome.NotApplicable
        h.coordinator.submitSearch("altro", gps)
        assertEquals(null, h.coordinator.webPlace("osm:n:5"))
    }

    private fun pageAbout(name: String, latitude: Double, longitude: Double) = WebPageMessage(
        URI("https://www.trattoriaverde.it/menu"),
        listOf("""{"@type":"Restaurant","name":"$name","geo":{"latitude":$latitude,"longitude":$longitude}}"""),
        emptyMap(),
    )

    private fun structuredOf(page: WebPageMessage) = JsonLdPlaceParser.parse(page.jsonLd)

    @Test
    fun `a page is judged against the current search`() {
        val verde = placeWithSite(5, "Trattoria Verde", "https://www.trattoriaverde.it")
        val h = Harness(own)
        h.gateway.discovery = foundWith(verde)
        searched(h, steaks)

        val here = pageAbout("Trattoria Verde", 45.001, 9.001)
        val match = h.coordinator.pagePlace(here, structuredOf(here))!!
        assertEquals(verde, match.place)
        assertEquals(MatchClass.LINKED, match.matchClass)

        val far = pageAbout("Trattoria Verde", 48.85, 2.35)
        assertNull(h.coordinator.pagePlace(far, structuredOf(far)))
    }

    @Test
    fun `without a search there is nothing to judge a page by`() {
        val h = Harness(own)
        val page = pageAbout("Trattoria Verde", 45.001, 9.001)
        assertNull(h.coordinator.pagePlace(page, structuredOf(page)))
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
