package app.roadstr.service.routing

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.RoutingProvider
import app.roadstr.core.network.RoutingProviderConfiguration
import app.roadstr.core.network.RoutingProviderConfigurationIssue
import app.roadstr.core.network.RoutingRequestHttpMethod
import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeRoutingHttpTransport
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeRoutingServiceTest {
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun shutDownServers() {
        servers.forEach(MockWebServer::shutdown)
    }

    @Test
    fun `OSRM request is executed with compatibility limits and parsed`() = runBlocking {
        var recordedUri: String? = null
        var recordedLimits: NativeHttpRequestLimits? = null
        val service = NativeRoutingService(
            NativeRoutingHttpTransport { request, limits ->
                recordedUri = request.uri
                recordedLimits = limits
                response(osrmBody(1_234.0))
            },
        )

        val routes = service.getRoutes(
            query(
                provider = RoutingProvider.OSRM,
                bearing = 91.4,
                via = listOf(RoutingRequestPoint(45.005, 9.005)),
            ),
        )

        assertEquals(1, routes.size)
        assertEquals(1_234.0, routes.single().totalDistanceM, 0.0)
        assertTrue(recordedUri!!.contains("bearings=91,45;"))
        assertFalse(recordedUri!!.contains("alternatives=3"))
        assertEquals(
            NativeRoutingService.DEFAULT_ROUTING_TIMEOUT_MILLIS,
            recordedLimits!!.timeoutMillis,
        )
        assertEquals(
            NetworkResponseLimit.JourneyRoute.bytes,
            recordedLimits!!.maxResponseBytes,
        )
    }

    @Test
    fun `OpenRouteService POST carries resolved key and parses one route`() = runBlocking {
        var method: RoutingRequestHttpMethod? = null
        var authorization: String? = null
        var body: String? = null
        val service = NativeRoutingService(
            NativeRoutingHttpTransport { request, _ ->
                method = request.method
                authorization = request.headers["Authorization"]
                body = request.body
                response(openRouteBody())
            },
        )

        val routes = service.getRoutes(
            query(
                provider = RoutingProvider.OPEN_ROUTE,
                apiKey = "private-test-key",
                languageCode = "it",
            ),
        )

        assertEquals(RoutingRequestHttpMethod.Post, method)
        assertEquals("private-test-key", authorization)
        assertTrue(body!!.contains("\"language\":\"it\""))
        assertEquals(432.0, routes.single().totalDistanceM, 0.0)
    }

    @Test
    fun `self-hosted GraphHopper crosses the real bounded adapter`() = runBlocking {
        val server = newServer()
        server.enqueue(MockResponse().setBody(graphHopperBody()))
        val service = NativeRoutingService()

        val routes = service.getRoutes(
            query(
                provider = RoutingProvider.GRAPH_HOPPER,
                graphHopperServer = server.url("/route?stale=1").toString(),
            ),
        )

        assertEquals(654.0, routes.single().totalDistanceM, 0.0)
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertTrue(recorded!!.path!!.startsWith("/route?point=45.0,9.0"))
        assertFalse(recorded.path!!.contains("stale=1"))
        assertFalse(recorded.path!!.contains("key="))
        assertEquals("Roadstr/1.0", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `HTTP status failure is value-free`() = runBlocking {
        val secret = "provider-body-must-not-escape"
        val service = NativeRoutingService(
            NativeRoutingHttpTransport { _, _ -> response(secret, statusCode = 503) },
        )

        val failure = expectFailure(NativeRoutingFailureKind.HttpStatus) {
            service.getRoutes(query(RoutingProvider.OSRM))
        }

        assertEquals(503, failure.statusCode)
        assertFalse(failure.toString().contains(secret))
        assertFalse(failure.stackTraceToString().contains(secret))
    }

    @Test
    fun `malformed response is value-free`() = runBlocking {
        val secret = "malformed-provider-payload"
        val service = NativeRoutingService(
            NativeRoutingHttpTransport { _, _ -> response(secret) },
        )

        val failure = expectFailure(NativeRoutingFailureKind.InvalidResponse) {
            service.getRoutes(query(RoutingProvider.OSRM))
        }

        assertNull(failure.statusCode)
        assertFalse(failure.toString().contains(secret))
        assertFalse(failure.stackTraceToString().contains(secret))
    }

    @Test
    fun `invalid GraphHopper configuration fails before transport`() = runBlocking {
        var calls = 0
        val service = NativeRoutingService(
            NativeRoutingHttpTransport { _, _ ->
                calls++
                response(graphHopperBody())
            },
        )

        expectFailure(NativeRoutingFailureKind.InvalidConfiguration) {
            service.getRoutes(
                query(
                    provider = RoutingProvider.GRAPH_HOPPER,
                    graphHopperServer = "http://192.168.1.50:8989/route",
                ),
            )
        }

        assertEquals(0, calls)
    }

    @Test
    fun `constrained transport failure retries once without bearing`() = runBlocking {
        val uris = mutableListOf<String>()
        val service = NativeRoutingService(
            NativeRoutingHttpTransport { request, _ ->
                uris += request.uri
                if (uris.size == 1) {
                    throw NativeHttpException(NativeHttpFailureKind.Transport)
                }
                response(osrmBody(2_200.0))
            },
        )

        val routes = service.getRerouteRoutes(
            query = query(RoutingProvider.OSRM, bearing = 273.0),
            speedKilometresPerHour = 30.0,
            straightLineDistanceMeters = 1_000.0,
        )

        assertEquals(2_200.0, routes.single().totalDistanceM, 0.0)
        assertEquals(2, uris.size)
        assertTrue(uris.first().contains("bearings=273,45;"))
        assertFalse(uris.last().contains("bearings="))
    }

    @Test
    fun `implausible constrained route retries once without bearing`() = runBlocking {
        val uris = mutableListOf<String>()
        val service = NativeRoutingService(
            NativeRoutingHttpTransport { request, _ ->
                uris += request.uri
                response(osrmBody(if (uris.size == 1) 13_001.0 else 2_500.0))
            },
        )

        val routes = service.getRerouteRoutes(
            query = query(RoutingProvider.OSRM, bearing = 90.0),
            speedKilometresPerHour = 30.0,
            straightLineDistanceMeters = 1_000.0,
        )

        assertEquals(2_500.0, routes.single().totalDistanceM, 0.0)
        assertEquals(2, uris.size)
        assertFalse(uris.last().contains("bearings="))
    }

    @Test
    fun `caller cancellation stops the active attempt without fallback`() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val service = NativeRoutingService(
            NativeRoutingHttpTransport { _, _ ->
                calls.incrementAndGet()
                started.complete(Unit)
                awaitCancellation()
            },
        )
        val job = launch(Dispatchers.Default) {
            service.getRerouteRoutes(
                query = query(RoutingProvider.OSRM, bearing = 180.0),
                speedKilometresPerHour = 30.0,
                straightLineDistanceMeters = 1_000.0,
            )
        }
        started.await()

        job.cancelAndJoin()

        assertEquals(1, calls.get())
    }

    @Test
    fun `non-OSRM provider failure is terminal without hidden retry`() = runBlocking {
        var calls = 0
        val service = NativeRoutingService(
            NativeRoutingHttpTransport { _, _ ->
                calls++
                throw NativeHttpException(NativeHttpFailureKind.Timeout)
            },
        )

        expectFailure(NativeRoutingFailureKind.Timeout) {
            service.getRerouteRoutes(
                query = query(
                    provider = RoutingProvider.GRAPH_HOPPER,
                    apiKey = "public-test-key",
                    bearing = 90.0,
                ),
                speedKilometresPerHour = 30.0,
                straightLineDistanceMeters = 1_000.0,
            )
        }

        assertEquals(1, calls)
    }

    private fun newServer(): MockWebServer = MockWebServer().also { server ->
        server.start()
        servers += server
    }

    private fun query(
        provider: RoutingProvider,
        apiKey: String? = null,
        graphHopperServer: String? = null,
        languageCode: String = "en",
        bearing: Double? = null,
        via: List<RoutingRequestPoint> = emptyList(),
    ): NativeRoutingQuery = NativeRoutingQuery(
        origin = RoutingRequestPoint(45.0, 9.0),
        destination = RoutingRequestPoint(45.01, 9.01),
        configuration = RoutingProviderConfiguration(
            provider = provider,
            apiKey = apiKey,
            graphHopperServer = graphHopperServer,
            issue = RoutingProviderConfigurationIssue.NONE,
            readsCredentials = provider != RoutingProvider.OSRM,
            legacyApiKeyMigration = null,
        ),
        languageCode = languageCode,
        originBearingDegrees = bearing,
        via = via,
    )

    private fun response(body: String, statusCode: Int = 200): NativeHttpResponse =
        NativeHttpResponse(
            statusCode = statusCode,
            reasonPhrase = if (statusCode == 200) "OK" else "Failure",
            headers = emptyMap(),
            bodyBytes = body.toByteArray(Charsets.UTF_8),
        )

    private fun osrmBody(distance: Double): String =
        """{"code":"Ok","routes":[{"distance":$distance,"duration":60.0,"geometry":{"coordinates":[[9.0,45.0],[9.01,45.01]]},"legs":[{"steps":[{"distance":$distance,"name":"Road","ref":"","maneuver":{"type":"depart","modifier":"straight","location":[9.0,45.0]}}]}]}]}"""

    private fun openRouteBody(): String =
        """{"features":[{"properties":{"summary":{"distance":432.0,"duration":60.0},"segments":[{"steps":[{"instruction":"Continue","distance":432.0,"type":6,"way_points":[0,1]}]}]},"geometry":{"coordinates":[[9.0,45.0],[9.01,45.01]]}}]}"""

    private fun graphHopperBody(): String =
        """{"paths":[{"distance":654.0,"time":60000,"points":{"coordinates":[[9.0,45.0],[9.01,45.01]]},"instructions":[{"text":"Continue","distance":654.0,"sign":0,"interval":[0,1]}]}]}"""

    private suspend fun expectFailure(
        expected: NativeRoutingFailureKind,
        block: suspend () -> Unit,
    ): NativeRoutingException {
        try {
            block()
            fail("Expected native routing failure $expected")
        } catch (failure: NativeRoutingException) {
            assertEquals(expected, failure.kind)
            return failure
        }
        error("unreachable")
    }
}
