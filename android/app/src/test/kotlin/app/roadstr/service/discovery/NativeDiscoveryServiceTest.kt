package app.roadstr.service.discovery

import app.roadstr.core.discovery.DiscoveryNotice
import app.roadstr.core.discovery.DiscoveryOutcome
import app.roadstr.core.discovery.DiscoveryRequest
import app.roadstr.core.discovery.NaturalQueryParser
import app.roadstr.core.discovery.SearchArea
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeTransport(
    val handler: (SearchProviderRequest) -> NativeHttpResponse,
) : NativeSearchHttpTransport {
    val requests = mutableListOf<SearchProviderRequest>()

    val overpass get() = requests.filter { it.uri.startsWith("https://m") }
    val nominatim get() = requests.filter { it.uri.contains("nominatim") }

    override suspend fun execute(
        request: SearchProviderRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse {
        requests += request
        return handler(request)
    }
}

private fun ok(body: String) = NativeHttpResponse(200, "OK", emptyMap(), body.toByteArray())

private fun status(code: Int) = NativeHttpResponse(code, "x", emptyMap(), ByteArray(0))

private fun node(id: Int, lat: Double, name: String, extra: String = "") =
    """{"type":"node","id":$id,"lat":$lat,"lon":9.0,"tags":{"amenity":"restaurant","name":"$name"$extra}}"""

private fun elements(vararg items: String) = """{"elements":[${items.joinToString(",")}]}"""

private const val FLORENCE = """[{"lat":"43.77","lon":"11.25","display_name":"Firenze, Toscana",
  "osm_type":"relation","osm_id":41485,"category":"boundary","type":"administrative","importance":0.7,
  "boundingbox":["43.72","43.83","11.15","11.33"],"namedetails":{"name":"Firenze","name:en":"Florence"}}]"""

class NativeDiscoveryServiceTest {
    private val parser = NaturalQueryParser()
    private val here = GeoPoint(45.0, 9.0)
    private val now = LocalDateTime.of(2026, 10, 5, 12, 0)

    private fun request(
        text: String,
        locale: String = "en",
        device: GeoPoint? = here,
        destination: GeoPoint? = null,
        route: List<GeoPoint> = emptyList(),
    ) = DiscoveryRequest(parser.interpret(text, locale), device, null, destination, locale, now, route)

    private fun service(transport: FakeTransport, mirrors: List<String> = listOf("https://m1/api", "https://m2/api")) =
        NativeDiscoveryService(
            transport = transport,
            overpassMirrors = mirrors,
            nominatimPacer = HostPacer(0, now = { 0L }, pause = { }),
        )

    private fun found(outcome: DiscoveryOutcome) = outcome as DiscoveryOutcome.Found

    @Test
    fun `a name or an address is left to the classic search without a request`() = runBlocking {
        val transport = FakeTransport { ok("{}") }
        assertEquals(DiscoveryOutcome.NotApplicable, service(transport).discover(request("Via Roma 12 Milano")))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `a search near me is one overpass request sorted by distance`() = runBlocking {
        val transport = FakeTransport {
            ok(elements(node(1, 45.02, "Far"), node(2, 45.001, "Near"), node(3, 45.01, "Mid")))
        }
        val outcome = found(service(transport).discover(request("restaurant near me")))
        assertEquals(listOf("Near", "Mid", "Far"), outcome.places.map { it.place.name })
        assertEquals(1, transport.requests.size)
        assertTrue(transport.overpass.single().body!!.contains("amenity"))
        assertTrue(transport.nominatim.isEmpty())
    }

    @Test
    fun `a named place is looked up once and searched by area`() = runBlocking {
        val transport = FakeTransport { call ->
            if (call.uri.contains("nominatim")) ok(FLORENCE) else ok(elements(node(1, 43.77, "Tavola"), node(2, 43.78, "Verde"), node(3, 43.76, "Foglia")))
        }
        val outcome = found(service(transport).discover(request("vegan restaurant in Florence")))
        assertEquals(1, transport.nominatim.size)
        assertTrue(transport.overpass.single().body!!.contains("area%283600041485%29") || transport.overpass.single().body!!.contains("area(3600041485)") || transport.overpass.single().body!!.contains("3600041485"))
        assertEquals(3, outcome.places.size)
        assertTrue(outcome.area is SearchArea.AdminArea)
    }

    @Test
    fun `a place that cannot be located leaves the search to the classic one`() = runBlocking {
        val transport = FakeTransport { ok("""[{"lat":"1","lon":"1","display_name":"Via Nowhere","osm_type":"way","osm_id":9,"category":"highway","type":"residential","namedetails":{"name":"Via Nowhere"}}]""") }
        assertEquals(DiscoveryOutcome.NotApplicable, service(transport).discover(request("pizza in Nowhere")))
        assertTrue(transport.overpass.isEmpty())
    }

    @Test
    fun `an empty administrative area falls back to its box`() = runBlocking {
        var overpassCalls = 0
        val transport = FakeTransport { call ->
            if (call.uri.contains("nominatim")) {
                ok(FLORENCE)
            } else {
                overpassCalls++
                if (overpassCalls == 1) ok("""{"elements":[]}""") else ok(elements(node(1, 43.77, "A"), node(2, 43.78, "B"), node(3, 43.79, "C")))
            }
        }
        val outcome = found(service(transport).discover(request("restaurant in Florence")))
        assertEquals(2, transport.overpass.size)
        assertTrue(DiscoveryNotice.AREA_FALLBACK in outcome.notices)
    }

    @Test
    fun `few results around the user widen the search once`() = runBlocking {
        var calls = 0
        val transport = FakeTransport {
            calls++
            if (calls == 1) ok(elements(node(1, 45.0, "Only"))) else ok(elements(node(1, 45.0, "Only"), node(2, 45.05, "Two"), node(3, 45.06, "Three")))
        }
        val outcome = found(service(transport).discover(request("restaurant near me")))
        assertEquals(2, transport.overpass.size)
        assertTrue(DiscoveryNotice.WIDENED in outcome.notices)
        assertEquals(3, outcome.places.size)
    }

    @Test
    fun `a sparse tag is said out loud`() = runBlocking {
        val transport = FakeTransport { ok(elements(node(1, 45.001, "Vegan Spot", ",\"diet:vegan\":\"only\""))) }
        val outcome = found(service(transport).discover(request("vegan restaurant near me")))
        assertTrue(DiscoveryNotice.FEW_TAGGED in outcome.notices)
    }

    @Test
    fun `nothing found is empty, not an error`() = runBlocking {
        val transport = FakeTransport { ok("""{"elements":[]}""") }
        assertTrue(service(transport).discover(request("restaurant near me")) is DiscoveryOutcome.Empty)
    }

    @Test
    fun `a failing mirror hands over to the next one`() = runBlocking {
        val transport = FakeTransport { call ->
            if (call.uri.startsWith("https://m1")) status(504) else ok(elements(node(1, 45.0, "A"), node(2, 45.01, "B"), node(3, 45.02, "C")))
        }
        val outcome = found(service(transport).discover(request("restaurant near me")))
        assertEquals(3, outcome.places.size)
        assertEquals(listOf("https://m1/api", "https://m2/api"), transport.overpass.map { it.uri })
    }

    @Test
    fun `a busy server answering 200 with a remark counts as a failure`() = runBlocking {
        val transport = FakeTransport { call ->
            if (call.uri.startsWith("https://m1")) ok("""{"elements":[],"remark":"runtime error: timeout"}""") else ok(elements(node(1, 45.0, "A"), node(2, 45.01, "B"), node(3, 45.02, "C")))
        }
        assertEquals(3, found(service(transport).discover(request("restaurant near me"))).places.size)
    }

    @Test
    fun `when every mirror fails the classic search takes over`() = runBlocking {
        val transport = FakeTransport { status(503) }
        assertEquals(DiscoveryOutcome.NotApplicable, service(transport).discover(request("restaurant near me")))
        assertEquals(2, transport.overpass.size)
    }

    @Test
    fun `a network exception is treated like a failed mirror`() = runBlocking {
        val transport = FakeTransport { throw IOException("down") }
        assertEquals(DiscoveryOutcome.NotApplicable, service(transport).discover(request("restaurant near me")))
    }

    @Test
    fun `the same search twice is answered from memory`() = runBlocking {
        val transport = FakeTransport { ok(elements(node(1, 45.0, "A"), node(2, 45.01, "B"), node(3, 45.02, "C"))) }
        val service = service(transport)
        service.discover(request("restaurant near me"))
        service.discover(request("restaurant near me"))
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `the nearest station becomes the reference for a parking search`() = runBlocking {
        val transport = FakeTransport { call ->
            val body = call.body.orEmpty()
            if (body.contains("railway")) {
                ok("""{"elements":[{"type":"node","id":9,"lat":45.01,"lon":9.01,"tags":{"railway":"station","name":"Central"}}]}""")
            } else {
                ok("""{"elements":[{"type":"node","id":1,"lat":45.0101,"lon":9.0101,"tags":{"amenity":"parking"}},
                  {"type":"node","id":2,"lat":45.0102,"lon":9.0102,"tags":{"amenity":"parking"}},
                  {"type":"node","id":3,"lat":45.0103,"lon":9.0103,"tags":{"amenity":"parking"}}]}""")
            }
        }
        val outcome = found(service(transport).discover(request("parking near the station")))
        assertEquals(2, transport.overpass.size)
        assertEquals(45.01, outcome.area.center.latitude, 1e-9)
        assertEquals(3, outcome.places.size)
    }

    @Test
    fun `a named reference place is geocoded`() = runBlocking {
        val transport = FakeTransport { call ->
            if (call.uri.contains("nominatim")) {
                ok("""[{"lat":"40.78","lon":"-73.96","display_name":"Central Park, NYC","osm_type":"relation","osm_id":1,"category":"leisure","type":"park","namedetails":{"name":"Central Park"}}]""")
            } else {
                ok(elements("""{"type":"node","id":1,"lat":40.7801,"lon":-73.9601,"tags":{"amenity":"pharmacy","name":"Rx"}}""", """{"type":"node","id":2,"lat":40.781,"lon":-73.961,"tags":{"amenity":"pharmacy","name":"Rx2"}}""", """{"type":"node","id":3,"lat":40.782,"lon":-73.962,"tags":{"amenity":"pharmacy","name":"Rx3"}}"""))
            }
        }
        val outcome = found(service(transport).discover(request("pharmacy near Central Park")))
        assertEquals(40.78, outcome.area.center.latitude, 1e-9)
    }

    @Test
    fun `the destination is the centre for a search near it`() = runBlocking {
        val destination = GeoPoint(46.0, 10.0)
        val transport = FakeTransport { ok(elements(node(1, 46.0, "A"), node(2, 46.001, "B"), node(3, 46.002, "C"))) }
        val outcome = found(service(transport).discover(request("restaurant near my destination", destination = destination)))
        assertEquals(46.0, outcome.area.center.latitude, 0.0)
        assertTrue(transport.overpass.single().body!!.contains("46.000000"))
    }

    @Test
    fun `along the route without a route says so and stays around the user`() = runBlocking {
        val transport = FakeTransport { ok(elements(node(1, 45.0, "A"), node(2, 45.01, "B"), node(3, 45.02, "C"))) }
        val outcome = found(service(transport).discover(request("restaurant along the route")))
        assertTrue(DiscoveryNotice.ROUTE_UNSUPPORTED in outcome.notices)
        assertTrue(outcome.area is SearchArea.Circle)
    }

    @Test
    fun `along the route with a route is one polyline query ahead of the user`() = runBlocking {
        val road = (0..300).map { GeoPoint(45.0 + it * 0.001, 9.0) }
        val transport = FakeTransport { ok(elements(node(1, 45.05, "Near"), node(2, 45.1, "Far"), node(3, 45.2, "Farther"))) }
        val outcome = found(service(transport).discover(request("restaurant along the route", route = road)))
        assertTrue(DiscoveryNotice.ROUTE_AHEAD in outcome.notices)
        assertTrue(DiscoveryNotice.ROUTE_UNSUPPORTED !in outcome.notices)
        val corridor = outcome.area as SearchArea.Corridor
        assertTrue(corridor.points.size <= SearchArea.MAX_CORRIDOR_VERTICES)
        val body = java.net.URLDecoder.decode(transport.overpass.single().body!!.removePrefix("data="), "UTF-8")
        assertTrue(body, body.contains("(around:1000,45.000000,9.000000,"))
        assertTrue(body, body.endsWith(");out tags center 80;"))
        assertEquals(listOf("Near", "Far", "Farther"), outcome.places.map { it.place.name })
        assertTrue(outcome.places.first().place.distanceMeters!! < outcome.places.last().place.distanceMeters!!)
    }

    @Test
    fun `a route the user is far from is not searched`() = runBlocking {
        val road = (0..300).map { GeoPoint(47.0 + it * 0.001, 12.0) }
        val transport = FakeTransport { ok(elements(node(1, 45.0, "A"), node(2, 45.01, "B"), node(3, 45.02, "C"))) }
        val outcome = found(service(transport).discover(request("restaurant along the route", route = road)))
        assertTrue(DiscoveryNotice.ROUTE_UNSUPPORTED in outcome.notices)
    }

    @Test
    fun `without a position a search around the user cannot run`() = runBlocking {
        val transport = FakeTransport { ok("{}") }
        assertEquals(DiscoveryOutcome.NotApplicable, service(transport).discover(request("restaurant near me", device = null)))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `a trailing city word is tried as a place, a non place word is not`() = runBlocking {
        val transport = FakeTransport { call ->
            if (call.uri.contains("nominatim")) ok(FLORENCE) else ok(elements(node(1, 43.77, "A"), node(2, 43.78, "B"), node(3, 43.79, "C")))
        }
        val city = found(service(transport).discover(request("cinema Florence")))
        assertTrue(city.area is SearchArea.AdminArea)
        val other = FakeTransport { call ->
            if (call.uri.contains("nominatim")) ok("[]") else ok(elements(node(1, 45.0, "A"), node(2, 45.01, "B"), node(3, 45.02, "C")))
        }
        val around = found(service(other).discover(request("pizzeria da Mario")))
        assertTrue(around.area is SearchArea.Circle)
    }

    @Test
    fun `a longer reading is tried first and the shorter one next`() = runBlocking {
        val calls = mutableListOf<String>()
        val transport = FakeTransport { call ->
            if (call.uri.contains("nominatim")) {
                calls += call.uri
                if (call.uri.contains("teglia")) ok("[]") else ok("""[{"lat":"41.9","lon":"12.5","display_name":"Roma, Lazio","osm_type":"relation","osm_id":41485,"category":"boundary","type":"administrative","namedetails":{"name":"Roma"}}]""")
            } else {
                ok(elements(node(1, 41.9, "A"), node(2, 41.91, "B"), node(3, 41.92, "C")))
            }
        }
        val outcome = found(service(transport).discover(request("pizza in teglia a Roma", locale = "it")))
        assertEquals(2, calls.size)
        assertTrue(outcome.area is SearchArea.AdminArea)
    }

    @Test
    fun `cancellation reaches the caller`() {
        val transport = FakeTransport { throw CancellationException("cancelled") }
        try {
            runBlocking { service(transport).discover(request("restaurant near me")) }
            fail("expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    @Test
    fun `requests carry no cookies and the geocoder is told who is asking`() = runBlocking {
        val transport = FakeTransport { call ->
            if (call.uri.contains("nominatim")) ok(FLORENCE) else ok(elements(node(1, 43.77, "A"), node(2, 43.78, "B"), node(3, 43.79, "C")))
        }
        service(transport).discover(request("restaurant in Florence"))
        assertTrue(transport.nominatim.single().headers.getValue("User-Agent").contains("roadstr"))
        assertTrue(transport.requests.none { it.headers.keys.any { key -> key.equals("Cookie", true) } })
    }
}
