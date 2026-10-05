package app.roadstr.service.discovery

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativePlaceLookupTest {
    private class Transport(val handler: (SearchProviderRequest) -> NativeHttpResponse) : NativeSearchHttpTransport {
        val requests = mutableListOf<SearchProviderRequest>()
        override suspend fun execute(request: SearchProviderRequest, limits: NativeHttpRequestLimits): NativeHttpResponse {
            requests += request
            return handler(request)
        }
    }

    private fun ok(body: String) = NativeHttpResponse(200, "OK", emptyMap(), body.toByteArray())
    private val near = GeoPoint(45.44, 10.99)
    private val row = """[{"osm_type":"node","osm_id":7,"lat":"45.44","lon":"10.99","name":"Osteria Blu","category":"amenity","type":"restaurant"}]"""

    @Test
    fun `a name is looked up through the pacer`() = runBlocking {
        val pauses = mutableListOf<Long>()
        var clock = 0L
        val pacer = HostPacer(1_100, now = { clock }, pause = { pauses += it; clock += it })
        val transport = Transport { ok(row) }
        val lookup = NativePlaceLookup(transport, pacer)
        lookup.find("Osteria Blu", "Verona", near, "it")
        lookup.find("Altro Posto", "Verona", near, "it")
        assertEquals(2, transport.requests.size)
        assertTrue("the second request waited for the first", pauses.isNotEmpty())
    }

    @Test
    fun `the same name is answered from memory`() = runBlocking {
        val transport = Transport { ok(row) }
        val lookup = NativePlaceLookup(transport, HostPacer(0, now = { 0L }, pause = { }))
        assertEquals("Osteria Blu", lookup.find("Osteria Blu", "Verona", near, "it").single().name)
        lookup.find("Osteria Blu", "Verona", near, "it")
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `failures and odd answers give nothing`() = runBlocking {
        val pacer = HostPacer(0, now = { 0L }, pause = { })
        assertTrue(NativePlaceLookup(Transport { throw IOException("down") }, pacer).find("A", null, near, "it").isEmpty())
        assertTrue(NativePlaceLookup(Transport { NativeHttpResponse(429, "x", emptyMap(), ByteArray(0)) }, pacer).find("A", null, near, "it").isEmpty())
        assertTrue(NativePlaceLookup(Transport { ok("garbage") }, pacer).find("A", null, near, "it").isEmpty())
        assertTrue(NativePlaceLookup(Transport { ok(row) }, pacer).find("  ", null, near, "it").isEmpty())
    }

    @Test
    fun `cancellation is not swallowed`() {
        val lookup = NativePlaceLookup(Transport { throw CancellationException("stop") }, HostPacer(0, now = { 0L }, pause = { }))
        try {
            runBlocking { lookup.find("A", null, near, "it") }
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }
}
