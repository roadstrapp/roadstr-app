package app.roadstr.service.search

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.SearchProviderHttpMethod
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSearchServiceTest {
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun shutDownServers() {
        servers.forEach(MockWebServer::shutdown)
    }

    @Test
    fun `empty query reaches no provider`() = runBlocking {
        var calls = 0
        val service = NativeSearchService(
            NativeSearchHttpTransport { _, _ ->
                calls++
                response("unexpected")
            },
        )

        assertTrue(service.search(NativeSearchQuery(" \t\n ")).isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `type ahead without location executes only Photon with exact bounds`() = runBlocking {
        val requests = mutableListOf<SearchProviderRequest>()
        val limits = mutableListOf<NativeHttpRequestLimits>()
        val service = NativeSearchService(
            NativeSearchHttpTransport { request, requestLimits ->
                requests += request
                limits += requestLimits
                response(photonBody("Photon", 45.0, 9.0))
            },
        )

        val results = service.search(
            NativeSearchQuery(
                query = "muse",
                languageCode = "it",
                phase = NativeSearchPhase.TYPE_AHEAD,
            ),
        )

        assertEquals(listOf("Photon"), results.map { it.shortName })
        assertEquals(1, requests.size)
        assertTrue(requests.single().uri.startsWith("https://photon.komoot.io/api/"))
        assertFalse(requests.single().uri.contains("lang="))
        assertEquals(NativeSearchService.PHOTON_TIMEOUT_MILLIS, limits.single().timeoutMillis)
        assertEquals(NetworkResponseLimit.Route.bytes, limits.single().maxResponseBytes)
    }

    @Test
    fun `providers run concurrently first non-empty is partial and final waits for all`() =
        runBlocking {
            val started = Channel<Provider>(Channel.UNLIMITED)
            val responses = Provider.entries.associateWith { CompletableDeferred<NativeHttpResponse>() }
            val recordedLimits = mutableMapOf<Provider, NativeHttpRequestLimits>()
            val service = NativeSearchService(
                NativeSearchHttpTransport { request, limits ->
                    val provider = providerOf(request)
                    synchronized(recordedLimits) { recordedLimits[provider] = limits }
                    started.send(provider)
                    responses.getValue(provider).await()
                },
            )
            val partial = CompletableDeferred<List<String>>()
            val result = async(Dispatchers.Default) {
                service.search(
                    NativeSearchQuery(
                        query = "pharmacy",
                        near = SearchResponsePoint(45.0, 9.0),
                    ),
                    onPartial = { values ->
                        partial.complete(values.map { it.shortName })
                    },
                )
            }

            val initialProviders = buildSet {
                repeat(3) { add(withTimeout(2_000) { started.receive() }) }
            }
            assertEquals(Provider.entries.toSet(), initialProviders)

            responses.getValue(Provider.PHOTON).complete(photonBodyResponse("Photon", 45.001, 9.001))
            assertEquals(listOf("Photon"), withTimeout(2_000) { partial.await() })
            assertFalse(result.isCompleted)

            responses.getValue(Provider.POI).complete(overpassBodyResponse("Local pharmacy", 45.002, 9.002))
            assertFalse(result.isCompleted)
            responses.getValue(Provider.NOMINATIM).complete(
                nominatimBodyResponse("Nominatim", 45.02, 9.02),
            )

            assertEquals(
                listOf("Local pharmacy", "Photon", "Nominatim"),
                withTimeout(2_000) { result.await() }.map { it.shortName },
            )
            assertEquals(
                NativeSearchService.NOMINATIM_LIMITS,
                recordedLimits[Provider.NOMINATIM],
            )
            assertEquals(
                NativeSearchService.PHOTON_LIMITS,
                recordedLimits[Provider.PHOTON],
            )
            assertEquals(
                NativeSearchService.OVERPASS_LIMITS,
                recordedLimits[Provider.POI],
            )
        }

    @Test
    fun `provider failure degrades to empty without hiding its peer`() = runBlocking {
        val service = NativeSearchService(
            NativeSearchHttpTransport { request, _ ->
                when (providerOf(request)) {
                    Provider.NOMINATIM -> nominatimBodyResponse("Recovered peer", 45.0, 9.0)
                    Provider.PHOTON -> throw IOException("provider failed")
                    Provider.POI -> error("POI is disabled without a location")
                }
            },
        )

        val results = service.search(NativeSearchQuery("museum"))

        assertEquals(listOf("Recovered peer"), results.map { it.shortName })
    }

    @Test
    fun `all-empty settled search launches exactly one relaxed geocoder batch`() = runBlocking {
        val requests = mutableListOf<SearchProviderRequest>()
        val service = NativeSearchService(
            NativeSearchHttpTransport { request, _ ->
                synchronized(requests) { requests += request }
                if (
                    request.uri.contains("nominatim") &&
                    request.uri.contains("q=via%20ricci&")
                ) {
                    nominatimBodyResponse("Recovered", 45.0, 9.0)
                } else if (request.uri.contains("nominatim")) {
                    response("[]")
                } else {
                    response("{\"features\":[]}")
                }
            },
        )

        val results = service.search(NativeSearchQuery("via roberto ricci"))

        assertEquals(listOf("Recovered"), results.map { it.shortName })
        assertEquals(4, requests.size)
        assertEquals(2, requests.count { it.uri.contains("nominatim") })
        assertEquals(2, requests.count { it.uri.contains("photon") })
        assertEquals(1, requests.count { it.uri.contains("q=via%20ricci&") })
        assertEquals(1, requests.count { it.uri.contains("q=via+ricci&") })
    }

    @Test
    fun `throwing partial callback cannot fail final search`() = runBlocking {
        val service = NativeSearchService(
            NativeSearchHttpTransport { _, _ ->
                photonBodyResponse("Photon", 45.0, 9.0)
            },
        )

        val results = service.search(
            NativeSearchQuery("museum", phase = NativeSearchPhase.TYPE_AHEAD),
            onPartial = { throw IllegalStateException("stale UI") },
        )

        assertEquals(listOf("Photon"), results.map { it.shortName })
    }

    @Test
    fun `Overpass rotates after failure and keeps nearest parsed results`() = runBlocking {
        val requests = mutableListOf<SearchProviderRequest>()
        val service = NativeSearchService(
            transport = NativeSearchHttpTransport { request, limits ->
                requests += request
                assertEquals(NativeSearchService.OVERPASS_LIMITS, limits)
                if (requests.size == 1) {
                    response("busy", statusCode = 503)
                } else {
                    response(
                        overpassBody(
                            "Far" to SearchResponsePoint(45.02, 9.02),
                            "Near" to SearchResponsePoint(45.001, 9.001),
                        ),
                    )
                }
            },
            overpassMirrors = listOf("https://one.invalid/api", "https://two.invalid/api"),
        )

        val first = service.searchPoiResults("farmacie", SearchResponsePoint(45.0, 9.0))
        val second = service.searchPoiResults("farmacia", SearchResponsePoint(45.0, 9.0))

        assertEquals(listOf("Near", "Far"), first.map { it.shortName })
        assertEquals("https://one.invalid/api", requests[0].uri)
        assertEquals("https://two.invalid/api", requests[1].uri)
        assertEquals("https://two.invalid/api", requests[2].uri)
        assertTrue(requests.all { it.method == SearchProviderHttpMethod.Post })
        assertTrue(requests.first().body!!.contains("around%3A4000%2C45.0000000%2C9.0000000"))
    }

    @Test
    fun `Overpass search crosses the real bounded adapter`() = runBlocking {
        val server = newServer()
        server.enqueue(MockResponse().setBody(overpassBody("Farmacia Test" to SearchResponsePoint(45.001, 9.001))))
        val service = NativeSearchService(
            transport = NativeBoundedHttpClient(),
            overpassMirrors = listOf(server.url("/interpreter").toString()),
        )

        val results = service.searchPoiResults("pharmacy", SearchResponsePoint(45.0, 9.0))

        assertEquals(listOf("Farmacia Test"), results.map { it.shortName })
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals("POST", recorded!!.method)
        assertEquals("application/x-www-form-urlencoded", recorded.getHeader("Content-Type"))
        assertEquals("Roadstr/1.0 (navigation app)", recorded.getHeader("User-Agent"))
        assertTrue(recorded.body.readUtf8().contains("out+center+12%3B"))
    }

    @Test
    fun `caller cancellation stops every provider without relaxed retry`() = runBlocking {
        val calls = AtomicInteger()
        val cancelled = AtomicInteger()
        val allStarted = CompletableDeferred<Unit>()
        val service = NativeSearchService(
            NativeSearchHttpTransport { _, _ ->
                if (calls.incrementAndGet() == 3) allStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.incrementAndGet()
                }
            },
        )
        val job = launch(Dispatchers.Default) {
            service.search(
                NativeSearchQuery(
                    query = "pharmacy",
                    near = SearchResponsePoint(45.0, 9.0),
                ),
            )
        }
        withTimeout(2_000) { allStarted.await() }

        job.cancelAndJoin()

        assertEquals(3, calls.get())
        assertEquals(3, cancelled.get())
    }

    @Test
    fun `category admission stays narrow and query preserves compound tags`() {
        assertEquals(
            listOf("amenity=pharmacy"),
            NativePoiSearchProtocol.categoryFilters("farmacie"),
        )
        assertNull(NativePoiSearchProtocol.categoryFilters("barcellona"))
        val query = NativePoiSearchProtocol.overpassQuery(
            filters = requireNotNull(NativePoiSearchProtocol.categoryFilters("pizzeria")),
            center = SearchResponsePoint(44.12345678, 12.98765432),
        )
        assertEquals(
            "[out:json][timeout:5];(" +
                "node[\"amenity\"=\"restaurant\"][\"cuisine\"=\"pizza\"]" +
                "(around:4000,44.1234568,12.9876543);" +
                "way[\"amenity\"=\"restaurant\"][\"cuisine\"=\"pizza\"]" +
                "(around:4000,44.1234568,12.9876543);" +
                ");out center 12;",
            query,
        )
    }

    private fun newServer(): MockWebServer = MockWebServer().also { server ->
        server.start()
        servers += server
    }

    private fun providerOf(request: SearchProviderRequest): Provider = when {
        request.method == SearchProviderHttpMethod.Post -> Provider.POI
        request.uri.contains("nominatim") -> Provider.NOMINATIM
        else -> Provider.PHOTON
    }

    private fun response(body: String, statusCode: Int = 200): NativeHttpResponse =
        NativeHttpResponse(
            statusCode = statusCode,
            reasonPhrase = if (statusCode == 200) "OK" else "Failure",
            headers = emptyMap(),
            bodyBytes = body.toByteArray(Charsets.UTF_8),
        )

    private fun photonBodyResponse(
        name: String,
        latitude: Double,
        longitude: Double,
    ): NativeHttpResponse = response(photonBody(name, latitude, longitude))

    private fun photonBody(name: String, latitude: Double, longitude: Double): String =
        """{"features":[{"geometry":{"coordinates":[$longitude,$latitude]},"properties":{"name":"$name"}}]}"""

    private fun nominatimBodyResponse(
        name: String,
        latitude: Double,
        longitude: Double,
    ): NativeHttpResponse = response(
        """[{"lat":"$latitude","lon":"$longitude","display_name":"$name","class":"place","type":"city","address":{}}]""",
    )

    private fun overpassBodyResponse(
        name: String,
        latitude: Double,
        longitude: Double,
    ): NativeHttpResponse = response(
        overpassBody(name to SearchResponsePoint(latitude, longitude)),
    )

    private fun overpassBody(
        vararg entries: Pair<String, SearchResponsePoint>,
    ): String = entries.mapIndexed { index, (name, point) ->
        """{"type":"node","id":${index + 1},"lat":${point.latitude},"lon":${point.longitude},"tags":{"name":"$name","amenity":"pharmacy"}}"""
    }.joinToString(prefix = "{\"elements\":[", postfix = "]}")

    private enum class Provider {
        NOMINATIM,
        PHOTON,
        POI,
    }
}
