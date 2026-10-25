package app.roadstr.roadtest

import app.roadstr.core.network.RoutingRequestProtocol
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.service.network.NativeHttpRequest
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeTransitHttpTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRoadTestSpeedLimitResolverTest {
    private val point = SearchResponsePoint(41.9028, 12.4964)
    private val requests = mutableListOf<NativeHttpRequest>()
    private var now = 1_000_000L

    private fun resolver(answer: (NativeHttpRequest) -> NativeHttpResponse) =
        NativeRoadTestSpeedLimitResolver(
            transport = NativeTransitHttpTransport { request, _ ->
                requests += request
                answer(request)
            },
            clock = { now },
        )

    @Test
    fun `the posted limit comes from Valhalla without asking Overpass`() = runBlocking {
        val limit = resolver { valhalla(edge(10.8, "secondary", 50), edge(12.3, "secondary", 50)) }
            .resolve(point)

        assertEquals(50, limit)
        assertEquals(listOf(RoutingRequestProtocol.valhallaLocateEndpoint), requests.map { it.uri })
        assertTrue(requests.single().body!!.contains("\"verbose\":true"))
    }

    @Test
    fun `a nearer parking aisle does not hide the road limit`() = runBlocking {
        val limit = resolver { valhalla(edge(3.0, "service_other", 0), edge(9.0, "primary", 30)) }
            .resolve(point)

        assertEquals(30, limit)
    }

    @Test
    fun `unknown, unlimited and disagreeing roads show no limit`() = runBlocking {
        assertNull(resolver { valhalla(edge(5.0, "residential", 0)) }.resolve(point))
        assertNull(resolver { valhalla(edge(5.0, "motorway", 255)) }.resolve(point))
        assertNull(resolver { valhalla(edge(5.0, "primary", 50), edge(7.0, "primary", 70)) }.resolve(point))
        assertNull(resolver { valhalla(edge(75.0, "primary", 50)) }.resolve(point))
    }

    @Test
    fun `Overpass answers when Valhalla is down`() = runBlocking {
        val limit = resolver { request ->
            if (request.uri == RoutingRequestProtocol.valhallaLocateEndpoint) {
                response(502, "")
            } else {
                response(200, overpassWay("70"))
            }
        }.resolve(point)

        assertEquals(70, limit)
        assertEquals(2, requests.size)
    }

    @Test
    fun `a refusing Overpass mirror is rotated instead of read as a road without limit`() = runBlocking {
        val resolver = resolver { request ->
            if (request.uri == RoutingRequestProtocol.valhallaLocateEndpoint) {
                response(503, "")
            } else {
                response(502, "at capacity")
            }
        }

        assertNull(resolver.resolve(point))
        now += 16_000L
        assertNull(resolver.resolve(point))

        val mirrors = requests.map { it.uri }.filter { it != RoutingRequestProtocol.valhallaLocateEndpoint }
        assertEquals(2, mirrors.size)
        assertFalse(mirrors[0] == mirrors[1])
        assertTrue(mirrors.none { "mail.ru" in it })
    }

    @Test
    fun `a limit survives one failed lookup and is not asked again within the retry window`() = runBlocking {
        var fail = false
        val resolver = resolver { if (fail) response(502, "") else valhalla(edge(5.0, "primary", 50)) }
        assertEquals(50, resolver.resolve(point))

        fail = true
        now += 5 * 60_000L - 1
        assertEquals(50, resolver.resolve(point))
        val asked = requests.size
        now += 2
        assertNull(resolver.resolve(point))
        assertEquals(asked, requests.size)
    }

    private fun edge(distance: Double, classification: String, speedLimit: Int) =
        """{"distance":$distance,"edge_info":{"speed_limit":$speedLimit},"edge":{"classification":{"classification":"$classification"}}}"""

    private fun valhalla(vararg edges: String) =
        response(200, """[{"input_lat":41.9028,"input_lon":12.4964,"edges":[${edges.joinToString(",")}],"nodes":[]}]""")

    private fun overpassWay(maxspeed: String) =
        """{"elements":[{"type":"way","tags":{"highway":"primary","maxspeed":"$maxspeed"},
            "geometry":[{"lat":41.9027,"lon":12.4960},{"lat":41.9029,"lon":12.4970}]}]}"""

    private fun response(status: Int, body: String) =
        NativeHttpResponse(status, "", emptyMap(), body.toByteArray())
}
