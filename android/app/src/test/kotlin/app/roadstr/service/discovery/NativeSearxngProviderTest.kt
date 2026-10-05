package app.roadstr.service.discovery

import app.roadstr.core.discovery.web.ConnectionTest
import app.roadstr.core.discovery.web.EndpointRejection
import app.roadstr.core.discovery.web.SearxngCapability
import app.roadstr.core.discovery.web.WebDiscoveryMode
import app.roadstr.core.discovery.web.WebDiscoveryOutcome
import app.roadstr.core.discovery.web.WebDiscoveryRequest
import app.roadstr.core.discovery.web.WebDiscoverySettings
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingTransport(
    val handler: (SearchProviderRequest) -> NativeHttpResponse,
) : NativeSearchHttpTransport {
    val requests = mutableListOf<SearchProviderRequest>()
    val searches get() = requests.filter { it.uri.contains("/search?q=") && !it.uri.contains("q=roadstr&") }

    override suspend fun execute(request: SearchProviderRequest, limits: NativeHttpRequestLimits): NativeHttpResponse {
        requests += request
        return handler(request)
    }
}

private fun json(body: String, status: Int = 200, headers: Map<String, String> = emptyMap()) =
    NativeHttpResponse(status, "x", headers, body.toByteArray())

private const val CONFIG = """{"version":"1","engines":[
  {"name":"duckduckgo","categories":["general"],"enabled":true},
  {"name":"google","categories":["general"],"enabled":true},
  {"name":"brave","categories":["general"],"enabled":true}]}"""

private const val RESULTS = """{"results":[
  {"url":"https://a.example/menu","title":"A","content":"steak","engines":["brave"]},
  {"url":"https://g.example","title":"G","content":"x","engines":["google"]}]}"""

class NativeSearxngProviderTest {
    private var clock = 1_000_000L
    private var current = WebDiscoverySettings(mode = WebDiscoveryMode.ON, endpointText = "https://s.example")

    private fun ok(call: SearchProviderRequest): NativeHttpResponse =
        if (call.uri.contains("/config")) json(CONFIG) else json(RESULTS)

    private fun provider(
        secure: RecordingTransport = RecordingTransport(::ok),
        local: RecordingTransport = RecordingTransport(::ok),
    ) = NativeSearxngProvider({ current }, secure, local, now = { clock })

    private fun ask(text: String = "steak house") = WebDiscoveryRequest(text, "en")

    @Test
    fun `off and unconfigured never touch the network`() = runBlocking {
        val secure = RecordingTransport(::ok)
        current = current.copy(mode = WebDiscoveryMode.OFF)
        assertEquals(WebDiscoveryOutcome.Disabled, provider(secure).discover(ask()))
        current = current.copy(mode = WebDiscoveryMode.ON, endpointText = "  ")
        assertEquals(WebDiscoveryOutcome.Disabled, provider(secure).discover(ask()))
        assertTrue(secure.requests.isEmpty())
    }

    @Test
    fun `a bad address is rejected without a request`() = runBlocking {
        val secure = RecordingTransport(::ok)
        current = current.copy(endpointText = "http://search.example")
        assertEquals(WebDiscoveryOutcome.Rejected(EndpointRejection.NOT_HTTPS), provider(secure).discover(ask()))
        assertTrue(secure.requests.isEmpty())
    }

    @Test
    fun `a search probes once, keeps google out and returns clean results`() = runBlocking {
        val secure = RecordingTransport(::ok)
        val outcome = provider(secure).discover(ask()) as WebDiscoveryOutcome.Results
        assertEquals(listOf("A"), outcome.results.map { it.title })
        assertEquals("s.example", outcome.host)
        assertEquals(3, secure.requests.size)
        val search = secure.searches.single()
        assertTrue(search.uri.contains("&engines=duckduckgo,brave&"))
        assertTrue(secure.requests.all { it.headers.getValue("User-Agent").startsWith("Roadstr/") })
        assertTrue(secure.requests.none { it.uri.contains("lat=") || it.uri.contains("lon=") })
    }

    @Test
    fun `a repeated search is answered from memory and a new one skips the probe`() = runBlocking {
        val secure = RecordingTransport(::ok)
        val provider = provider(secure)
        provider.discover(ask("steak house"))
        provider.discover(ask("steak house"))
        assertEquals(3, secure.requests.size)
        provider.discover(ask("vegan pizza"))
        assertEquals(4, secure.requests.size)
        clock += NativeSearxngProvider.CACHE_TTL_MILLIS + 1
        provider.discover(ask("steak house"))
        assertEquals(5, secure.requests.size)
    }

    @Test
    fun `two identical searches at once make one request`() = runBlocking {
        val secure = RecordingTransport(::ok)
        val provider = provider(secure)
        val first = async { provider.discover(ask()) }
        val second = async { provider.discover(ask()) }
        first.await()
        second.await()
        assertEquals(1, secure.searches.size)
    }

    @Test
    fun `json switched off is reported and not retried blindly`() = runBlocking {
        val secure = RecordingTransport { call -> if (call.uri.contains("/config")) json(CONFIG) else json("", 403) }
        val outcome = provider(secure).discover(ask())
        assertEquals(WebDiscoveryOutcome.Incompatible(SearxngCapability.JsonDisabled), outcome)
        assertTrue(secure.searches.isEmpty())
    }

    @Test
    fun `json switched off after a good probe clears what was learned`() = runBlocking {
        var blocked = false
        val secure = RecordingTransport { call ->
            if (call.uri.contains("/config")) json(CONFIG) else if (blocked && !call.uri.contains("q=roadstr&")) json("", 403) else json(RESULTS)
        }
        val provider = provider(secure)
        provider.discover(ask("one"))
        blocked = true
        assertEquals(
            WebDiscoveryOutcome.Incompatible(SearxngCapability.JsonDisabled),
            provider.discover(ask("two")),
        )
    }

    @Test
    fun `a rate limit pauses further requests until it is over`() = runBlocking {
        val secure = RecordingTransport { call ->
            if (call.uri.contains("/config")) json(CONFIG) else json("", 429, mapOf("retry-after" to "30"))
        }
        val provider = provider(secure)
        assertEquals(WebDiscoveryOutcome.RateLimited(30), provider.discover(ask()))
        val before = secure.requests.size
        val again = provider.discover(ask("other")) as WebDiscoveryOutcome.RateLimited
        assertEquals(before, secure.requests.size)
        assertEquals(30L, again.retryAfterSeconds)
        clock += 31_000
        assertTrue(provider.discover(ask("other")) !is WebDiscoveryOutcome.RateLimited || secure.requests.size > before)
    }

    @Test
    fun `three failures in a row pause the provider for five minutes`() = runBlocking {
        val secure = RecordingTransport { throw IOException("down") }
        val provider = provider(secure)
        repeat(3) { assertEquals(WebDiscoveryOutcome.Failed, provider.discover(ask("q$it"))) }
        val sent = secure.requests.size
        assertTrue(provider.discover(ask("again")) is WebDiscoveryOutcome.RateLimited)
        assertEquals(sent, secure.requests.size)
        clock += NativeSearxngProvider.FAILURE_PAUSE_MILLIS + 1
        assertEquals(WebDiscoveryOutcome.Failed, provider.discover(ask("later")))
    }

    @Test
    fun `an answer that is not searxng is reported`() = runBlocking {
        val secure = RecordingTransport { call -> if (call.uri.contains("/config")) json("<html>") else json("<html>nope</html>") }
        assertEquals(
            WebDiscoveryOutcome.Incompatible(SearxngCapability.NotSearxng),
            provider(secure).discover(ask()),
        )
    }

    @Test
    fun `a strict policy refuses an instance with no engine list`() = runBlocking {
        current = current.copy(strictSources = true)
        val secure = RecordingTransport { call -> if (call.uri.contains("/config")) json("", 404) else json(RESULTS) }
        assertEquals(
            WebDiscoveryOutcome.Incompatible(SearxngCapability.NoEngineList),
            provider(secure).discover(ask()),
        )
    }

    @Test
    fun `https and loopback use okhttp, a local network uses the raw socket`() = runBlocking {
        val secure = RecordingTransport(::ok)
        val local = RecordingTransport(::ok)
        current = current.copy(endpointText = "http://127.0.0.1:8888")
        provider(secure, local).discover(ask())
        assertEquals(3, secure.requests.size)
        assertTrue(local.requests.isEmpty())

        val secure2 = RecordingTransport(::ok)
        val local2 = RecordingTransport(::ok)
        current = current.copy(endpointText = "http://192.168.1.20:8888", ownInstanceConfirmed = true)
        provider(secure2, local2).discover(ask())
        assertTrue(secure2.requests.isEmpty())
        assertEquals(3, local2.requests.size)

        val unconfirmed = RecordingTransport(::ok)
        current = current.copy(ownInstanceConfirmed = false)
        val outcome = provider(unconfirmed, unconfirmed).discover(ask())
        assertEquals(WebDiscoveryOutcome.Rejected(EndpointRejection.LOCAL_HTTP_NOT_CONFIRMED), outcome)
        assertTrue(unconfirmed.requests.isEmpty())
    }

    @Test
    fun `the connection test reports what it found and always asks again`() = runBlocking {
        val secure = RecordingTransport(::ok)
        val provider = provider(secure)
        val first = provider.testConnection() as ConnectionTest.Tested
        assertTrue(first.capability is SearxngCapability.Compatible)
        assertEquals("s.example", first.host)
        provider.testConnection()
        assertEquals(4, secure.requests.size)

        current = current.copy(endpointText = "")
        assertEquals(ConnectionTest.NotConfigured, provider.testConnection())
        current = current.copy(endpointText = "javascript:x")
        assertEquals(ConnectionTest.Rejected(EndpointRejection.INVALID), provider.testConnection())
    }

    @Test
    fun `an unreachable instance is told apart in the test`() = runBlocking {
        val secure = RecordingTransport { throw IOException("down") }
        val tested = provider(secure).testConnection() as ConnectionTest.Tested
        assertEquals(SearxngCapability.Unreachable, tested.capability)
    }

    @Test
    fun `settings never print the instance address`() {
        assertEquals("WebDiscoverySettings(mode=ON)", current.toString())
        assertEquals("WebDiscoveryRequest", ask().toString())
    }
}
