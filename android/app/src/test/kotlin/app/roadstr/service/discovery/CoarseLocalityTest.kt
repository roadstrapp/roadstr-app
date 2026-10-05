package app.roadstr.service.discovery

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoarseLocalityTest {
    private class Transport(val handler: (SearchProviderRequest) -> NativeHttpResponse) : NativeSearchHttpTransport {
        val requests = mutableListOf<SearchProviderRequest>()
        override suspend fun execute(request: SearchProviderRequest, limits: NativeHttpRequestLimits): NativeHttpResponse {
            requests += request
            return handler(request)
        }
    }

    private fun ok(body: String) = NativeHttpResponse(200, "OK", emptyMap(), body.toByteArray())
    private val pacer = HostPacer(0, now = { 0L }, pause = { })
    private val here = GeoPoint(45.123456, 9.654321)

    @Test
    fun `the town is found from a rounded point`() = runBlocking {
        val transport = Transport { ok("""{"address":{"town":"Testville"}}""") }
        assertEquals("Testville", CoarseLocality(transport, pacer).nameOf(here, "en"))
        assertTrue(transport.requests.single().uri.contains("lat=45.12&lon=9.65"))
    }

    @Test
    fun `the same grid cell is answered from memory`() = runBlocking {
        val transport = Transport { ok("""{"address":{"town":"Testville"}}""") }
        val locality = CoarseLocality(transport, pacer)
        locality.nameOf(here, "en")
        locality.nameOf(GeoPoint(45.1201, 9.6499), "en")
        assertEquals(1, transport.requests.size)
        locality.nameOf(GeoPoint(45.2, 9.65), "en")
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `failures give no name`() = runBlocking {
        assertNull(CoarseLocality(Transport { throw IOException("down") }, pacer).nameOf(here, "en"))
        assertNull(CoarseLocality(Transport { NativeHttpResponse(503, "x", emptyMap(), ByteArray(0)) }, pacer).nameOf(here, "en"))
        assertNull(CoarseLocality(Transport { ok("""{"address":{}}""") }, pacer).nameOf(here, "en"))
    }
}
