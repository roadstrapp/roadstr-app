package app.roadstr.service.hazards

import app.roadstr.core.geo.GeoPoint
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOsmSpeedCameraServiceTest {
    private class FakeTransport(var responder: (String) -> Pair<Int, String>) : NativeSearchHttpTransport {
        val queries = mutableListOf<String>()

        override suspend fun execute(
            request: app.roadstr.core.network.SearchProviderRequest,
            limits: NativeHttpRequestLimits,
        ): NativeHttpResponse {
            val query = URLDecoder.decode(request.body.orEmpty().removePrefix("data="), "UTF-8")
            queries += query
            val (status, body) = responder(query)
            return NativeHttpResponse(status, "", emptyMap(), body.toByteArray())
        }
    }

    private var now = Instant.parse("2026-01-01T12:00:00Z")
    private val point = GeoPoint(38.7223, -9.1393)

    private fun service(transport: FakeTransport) =
        NativeOsmSpeedCameraService(transport) { now }

    @Test
    fun `camera nodes are parsed with optional speed limits and invalid points are dropped`() = runBlocking {
        val transport = FakeTransport {
            200 to """{"elements":[
                {"type":"node","id":1,"lat":38.7,"lon":-9.1,"tags":{"highway":"speed_camera","maxspeed":"50"}},
                {"type":"node","id":2,"lat":38.71,"lon":-9.11,"tags":{"enforcement":"maxspeed","maxspeed":"50 mph"}},
                {"type":"node","id":3,"lat":95.0,"lon":-9.1,"tags":{"highway":"speed_camera"}},
                {"type":"node","id":1,"lat":38.7,"lon":-9.1,"tags":{"highway":"speed_camera"}}]}"""
        }
        val cameras = service(transport).update(point)

        assertEquals(listOf(1L, 2L), cameras.map { it.id })
        assertEquals(50, cameras[0].speedLimitKmh)
        assertEquals(80, cameras[1].speedLimitKmh)
        assertTrue(transport.queries.single().contains("speed_camera"))
        assertTrue(transport.queries.single().contains("enforcement"))
    }

    @Test
    fun `cache is reused until movement or age makes a refresh due`() = runBlocking {
        val transport = FakeTransport { 200 to """{"elements":[]}""" }
        val subject = service(transport)

        subject.update(point)
        now = now.plus(Duration.ofMinutes(10))
        subject.update(GeoPoint(point.latitude + 0.0001, point.longitude))
        assertEquals(1, transport.queries.size)

        // 1.6 km north is past the 1.5 km half-radius threshold.
        subject.update(GeoPoint(point.latitude + 0.0144, point.longitude))
        assertEquals(2, transport.queries.size)
        now = now.plus(Duration.ofMinutes(16))
        subject.update(GeoPoint(point.latitude + 0.0144, point.longitude))
        assertEquals(3, transport.queries.size)
    }

    @Test
    fun `failed mirror round keeps the previous cache and backs off`() = runBlocking {
        var calls = 0
        val transport = FakeTransport {
            if (calls++ == 0) {
                200 to """{"elements":[{"type":"node","id":7,"lat":38.7,"lon":-9.1}]}"""
            } else {
                503 to ""
            }
        }
        val subject = service(transport)
        assertEquals(listOf(7L), subject.update(point).map { it.id })

        val farAway = GeoPoint(point.latitude + 0.05, point.longitude)
        assertEquals(listOf(7L), subject.update(farAway).map { it.id })
        assertEquals(4, transport.queries.size)

        now = now.plus(Duration.ofSeconds(30))
        subject.update(farAway)
        assertEquals(4, transport.queries.size)
        assertNull(subject.cameras().firstOrNull()?.speedLimitKmh)
    }
}
