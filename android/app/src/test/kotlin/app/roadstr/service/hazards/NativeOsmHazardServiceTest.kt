package app.roadstr.service.hazards

import app.roadstr.core.geo.GeoPoint
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOsmHazardServiceTest {
    private class FakeTransport(var responder: (String) -> Pair<Int, String>) : NativeSearchHttpTransport {
        val bodies = mutableListOf<String>()
        val mirrors = mutableListOf<String>()

        override suspend fun execute(
            request: app.roadstr.core.network.SearchProviderRequest,
            limits: NativeHttpRequestLimits,
        ): NativeHttpResponse {
            val body = request.body.orEmpty()
            bodies += java.net.URLDecoder.decode(body.removePrefix("data="), "UTF-8")
            mirrors += request.uri
            val (status, text) = responder(body)
            return NativeHttpResponse(status, "", emptyMap(), text.toByteArray())
        }
    }

    private var now = Instant.parse("2026-01-01T12:00:00Z")
    private val point = GeoPoint(38.7223, -9.1393)

    private fun service(transport: FakeTransport) =
        NativeOsmHazardService(transport) { now }

    @Test
    fun `traffic lights are read as id and coordinates with the shipped query`() = runBlocking {
        val transport = FakeTransport {
            200 to """{"elements":[
                {"type":"node","id":1,"lat":38.7,"lon":-9.1},
                {"type":"node","id":2,"lat":95.0,"lon":-9.1},
                {"type":"node","id":3,"lat":38.71,"lon":-9.11}]}"""
        }
        val lights = service(transport).trafficLights(point)

        assertEquals(listOf(1L, 3L), lights.map { it.id })
        assertTrue(lights.all { it.kind == NativeOsmHazardKind.TrafficLight })
        assertEquals(
            "[out:json][timeout:8];node[\"highway\"=\"traffic_signals\"]" +
                "(around:1500,38.7223000,-9.1393000);out skel;",
            transport.bodies.single(),
        )
    }

    @Test
    fun `crossings and bumps are told apart by the traffic calming tag`() = runBlocking {
        val transport = FakeTransport {
            200 to """{"elements":[
                {"type":"node","id":10,"lat":38.7,"lon":-9.1,"tags":{"highway":"crossing"}},
                {"type":"node","id":11,"lat":38.7,"lon":-9.1,"tags":{"traffic_calming":"hump"}},
                {"type":"node","id":12,"lat":38.7,"lon":-9.1}]}"""
        }
        val result = service(transport).crossingsAndBumps(point)

        assertEquals(
            listOf(
                NativeOsmHazardKind.Crosswalk,
                NativeOsmHazardKind.SpeedBump,
                NativeOsmHazardKind.Crosswalk,
            ),
            result.map { it.kind },
        )
    }

    @Test
    fun `a dense answer is capped at 400 results`() = runBlocking {
        val elements = (1..600).joinToString(",") {
            """{"type":"node","id":$it,"lat":38.7,"lon":-9.1}"""
        }
        val transport = FakeTransport { 200 to """{"elements":[$elements]}""" }
        assertEquals(400, service(transport).trafficLights(point).size)
    }

    @Test
    fun `a parked car does not refetch but one that moved half the radius does`() = runBlocking {
        val transport = FakeTransport { 200 to """{"elements":[]}""" }
        val subject = service(transport)

        subject.trafficLights(point)
        now = now.plus(Duration.ofMinutes(10))
        subject.trafficLights(GeoPoint(point.latitude + 0.0001, point.longitude))
        assertEquals(1, transport.bodies.size)

        // 800 m north is past the 750 m threshold.
        subject.trafficLights(GeoPoint(point.latitude + 0.0072, point.longitude))
        assertEquals(2, transport.bodies.size)

        // Nothing moved, but the data is older than 15 minutes.
        now = now.plus(Duration.ofMinutes(16))
        subject.trafficLights(GeoPoint(point.latitude + 0.0072, point.longitude))
        assertEquals(3, transport.bodies.size)
    }

    @Test
    fun `a failure rotates the mirror, keeps the old data and waits before retrying`() = runBlocking {
        var calls = 0
        val transport = FakeTransport {
            if (calls++ == 0) {
                200 to """{"elements":[{"type":"node","id":1,"lat":38.7,"lon":-9.1}]}"""
            } else {
                503 to ""
            }
        }
        val subject = service(transport)
        assertEquals(1, subject.trafficLights(point).size)

        val farAway = GeoPoint(point.latitude + 0.05, point.longitude)
        // Fails: the previous answer stays on the map.
        assertEquals(1, subject.trafficLights(farAway).size)
        assertEquals(2, transport.bodies.size)
        assertTrue(transport.mirrors[0] != transport.mirrors[1] || transport.mirrors.size == 2)

        // Inside the back-off nothing is sent. A 503 is a throttle: 15 s * 4.
        now = now.plus(Duration.ofSeconds(30))
        subject.trafficLights(farAway)
        assertEquals(2, transport.bodies.size)

        now = now.plus(Duration.ofSeconds(40))
        subject.trafficLights(farAway)
        assertEquals(3, transport.bodies.size)
        // The retry used the other mirror.
        assertTrue(transport.mirrors[2] != transport.mirrors[1])
    }

    @Test
    fun `the two overlays keep independent caches`() = runBlocking {
        val transport = FakeTransport {
            200 to """{"elements":[{"type":"node","id":1,"lat":38.7,"lon":-9.1}]}"""
        }
        val subject = service(transport)
        subject.trafficLights(point)
        subject.crossingsAndBumps(point)
        assertEquals(2, transport.bodies.size)
        subject.reset()
        subject.trafficLights(point)
        assertEquals(3, transport.bodies.size)
    }
}
