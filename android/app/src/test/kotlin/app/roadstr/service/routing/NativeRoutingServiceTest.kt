package app.roadstr.service.routing

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.RoutingAvoidanceMode
import app.roadstr.core.network.RoutingProvider
import app.roadstr.core.network.RoutingProviderConfiguration
import app.roadstr.core.network.RoutingProviderConfigurationIssue
import app.roadstr.core.network.RoutingRequestHttpMethod
import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.core.network.RoutingRouteAvoidance
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeRoutingHttpTransport
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
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

    @Test
    fun `hard avoidance uses exact Valhalla policy limits and classification`() = runBlocking {
        var recordedMethod: RoutingRequestHttpMethod? = null
        var recordedUri: String? = null
        var recordedUserAgent: String? = null
        var recordedLimits: NativeHttpRequestLimits? = null
        val service = NativeRoutingService(
            transport = NativeRoutingHttpTransport { request, limits ->
                recordedMethod = request.method
                recordedUri = request.uri
                recordedUserAgent = request.headers["User-Agent"]
                recordedLimits = limits
                response(valhallaBody())
            },
            valhallaEndpointOverride = VALHALLA_TEST_ENDPOINT,
            osrmRetimeEndpointOverride = null,
        )

        val route = service.getAvoidanceRoute(
            avoidanceQuery(
                mode = RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS,
                languageCode = "it",
            ),
        )

        assertEquals(RoutingRequestHttpMethod.Get, recordedMethod)
        assertEquals(
            """{"locations":[{"lat":45.0,"lon":9.0},{"lat":45.01,"lon":9.01}],"costing":"auto","costing_options":{"auto":{"exclude_highways":true,"exclude_tolls":true}},"units":"kilometers","language":"it-IT"}""",
            decodeValhallaJson(recordedUri!!),
        )
        assertEquals("Roadstr/1.0", recordedUserAgent)
        assertEquals(
            NativeRoutingService.VALHALLA_TIMEOUT_MILLIS,
            recordedLimits!!.timeoutMillis,
        )
        assertEquals(
            NetworkResponseLimit.JourneyRoute.bytes,
            recordedLimits!!.maxResponseBytes,
        )
        assertEquals(RoutingRouteAvoidance.HighwayAndTollFree, route.avoidance)
        assertEquals(120.0, route.totalDurationS, 0.0)
    }

    @Test
    fun `rejected hard route retries soft once and applies OSRM retiming`() = runBlocking {
        val uris = mutableListOf<String>()
        val timeouts = mutableListOf<Long>()
        val service = NativeRoutingService(
            transport = NativeRoutingHttpTransport { request, limits ->
                uris += request.uri
                timeouts += limits.timeoutMillis
                when (uris.size) {
                    1, 2 -> response(valhallaBody(hasHighway = true))
                    3 -> response(osrmRetimeBody(durationSeconds = 66.0))
                    else -> error("Unexpected routing request")
                }
            },
            valhallaEndpointOverride = VALHALLA_TEST_ENDPOINT,
            osrmRetimeEndpointOverride = OSRM_RETIME_TEST_ENDPOINT,
        )

        val route = service.getAvoidanceRoute(
            avoidanceQuery(RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS),
        )

        assertEquals(3, uris.size)
        assertTrue(decodeValhallaJson(uris[0]).contains("\"exclude_highways\":true"))
        assertTrue(decodeValhallaJson(uris[1]).contains("\"use_highways\":0"))
        assertTrue(decodeValhallaJson(uris[1]).contains("\"toll_booth_penalty\":900"))
        assertTrue(uris[2].startsWith("$OSRM_RETIME_TEST_ENDPOINT/9.00000,45.00000;"))
        assertTrue(uris[2].endsWith("?overview=false&steps=false"))
        assertEquals(
            listOf(
                NativeRoutingService.VALHALLA_TIMEOUT_MILLIS,
                NativeRoutingService.VALHALLA_TIMEOUT_MILLIS,
                NativeRoutingService.OSRM_RETIME_TIMEOUT_MILLIS,
            ),
            timeouts,
        )
        assertEquals(RoutingRouteAvoidance.MinimizedHighwaysAndTolls, route.avoidance)
        assertEquals(66.0, route.totalDurationS, 0.0)
        assertTrue(route.fromAvoidanceRouter)
    }

    @Test
    fun `hard transport failure retries soft and returns its route`() = runBlocking {
        val payloads = mutableListOf<String>()
        val service = NativeRoutingService(
            transport = NativeRoutingHttpTransport { request, _ ->
                payloads += decodeValhallaJson(request.uri)
                if (payloads.size == 1) {
                    throw NativeHttpException(NativeHttpFailureKind.Transport)
                }
                response(valhallaBody())
            },
            valhallaEndpointOverride = VALHALLA_TEST_ENDPOINT,
            osrmRetimeEndpointOverride = null,
        )

        val route = service.getAvoidanceRoute(
            avoidanceQuery(RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS),
        )

        assertEquals(2, payloads.size)
        assertTrue(payloads.first().contains("\"exclude_tolls\":true"))
        assertTrue(payloads.last().contains("\"use_tolls\":0"))
        assertEquals(RoutingRouteAvoidance.HighwayAndTollFree, route.avoidance)
    }

    @Test
    fun `soft avoidance failure is terminal and preserves final status`() = runBlocking {
        var calls = 0
        val service = NativeRoutingService(
            transport = NativeRoutingHttpTransport { _, _ ->
                calls++
                response(
                    body = "provider-body-must-not-escape",
                    statusCode = if (calls == 1) 502 else 503,
                )
            },
            valhallaEndpointOverride = VALHALLA_TEST_ENDPOINT,
            osrmRetimeEndpointOverride = null,
        )

        val failure = expectFailure(NativeRoutingFailureKind.HttpStatus) {
            service.getAvoidanceRoute(
                avoidanceQuery(RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS),
            )
        }

        assertEquals(2, calls)
        assertEquals(503, failure.statusCode)
        assertFalse(failure.toString().contains("provider-body-must-not-escape"))
    }

    @Test
    fun `off-road avoidance uses tracks policy without hidden fallback`() = runBlocking {
        val payloads = mutableListOf<String>()
        val service = NativeRoutingService(
            transport = NativeRoutingHttpTransport { request, _ ->
                payloads += decodeValhallaJson(request.uri)
                response(valhallaBody(hasHighway = true, hasToll = true))
            },
            valhallaEndpointOverride = VALHALLA_TEST_ENDPOINT,
            osrmRetimeEndpointOverride = null,
        )

        val route = service.getAvoidanceRoute(
            avoidanceQuery(RoutingAvoidanceMode.OFF_ROAD),
        )

        assertEquals(1, payloads.size)
        assertTrue(payloads.single().contains("\"use_tracks\":0"))
        assertFalse(payloads.single().contains("exclude_highways"))
        assertEquals(RoutingRouteAvoidance.OffRoadAvoided, route.avoidance)
    }

    @Test
    fun `off-road failure is terminal after one request`() = runBlocking {
        var calls = 0
        val service = NativeRoutingService(
            transport = NativeRoutingHttpTransport { _, _ ->
                calls++
                throw NativeHttpException(NativeHttpFailureKind.Timeout)
            },
            valhallaEndpointOverride = VALHALLA_TEST_ENDPOINT,
            osrmRetimeEndpointOverride = null,
        )

        expectFailure(NativeRoutingFailureKind.Timeout) {
            service.getAvoidanceRoute(avoidanceQuery(RoutingAvoidanceMode.OFF_ROAD))
        }

        assertEquals(1, calls)
    }

    @Test
    fun `retiming failure keeps accepted Valhalla route`() = runBlocking {
        var calls = 0
        val service = NativeRoutingService(
            transport = NativeRoutingHttpTransport { _, _ ->
                calls++
                if (calls == 1) response(valhallaBody()) else response("malformed-retime")
            },
            valhallaEndpointOverride = VALHALLA_TEST_ENDPOINT,
            osrmRetimeEndpointOverride = OSRM_RETIME_TEST_ENDPOINT,
        )

        val route = service.getAvoidanceRoute(
            avoidanceQuery(RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS),
        )

        assertEquals(2, calls)
        assertEquals(120.0, route.totalDurationS, 0.0)
        assertEquals(RoutingRouteAvoidance.HighwayAndTollFree, route.avoidance)
    }

    @Test
    fun `avoidance cancellation stops hard attempt without soft fallback`() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val service = NativeRoutingService(
            transport = NativeRoutingHttpTransport { _, _ ->
                calls.incrementAndGet()
                started.complete(Unit)
                awaitCancellation()
            },
            valhallaEndpointOverride = VALHALLA_TEST_ENDPOINT,
            osrmRetimeEndpointOverride = null,
        )
        val job = launch(Dispatchers.Default) {
            service.getAvoidanceRoute(
                avoidanceQuery(RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS),
            )
        }
        started.await()

        job.cancelAndJoin()

        assertEquals(1, calls.get())
    }

    @Test
    fun `retiming cancellation propagates instead of returning stale route`() = runBlocking {
        var calls = 0
        val service = NativeRoutingService(
            transport = NativeRoutingHttpTransport { _, _ ->
                calls++
                if (calls == 1) {
                    response(valhallaBody())
                } else {
                    throw CancellationException("cancelled retiming")
                }
            },
            valhallaEndpointOverride = VALHALLA_TEST_ENDPOINT,
            osrmRetimeEndpointOverride = OSRM_RETIME_TEST_ENDPOINT,
        )

        try {
            service.getAvoidanceRoute(
                avoidanceQuery(RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS),
            )
            fail("Expected retiming cancellation")
        } catch (_: CancellationException) {
            // Expected: cancellation must not be converted to a stale success.
        }

        assertEquals(2, calls)
    }

    @Test
    fun `avoidance requests cross the real bounded adapter`() = runBlocking {
        val server = newServer()
        server.enqueue(MockResponse().setBody(valhallaBody()))
        server.enqueue(MockResponse().setBody(osrmRetimeBody(durationSeconds = 72.0)))
        val valhallaEndpoint = server.url("/route").toString()
        val retimeEndpoint = server.url("/route/v1/driving").toString().removeSuffix("/")
        val service = NativeRoutingService(
            transport = NativeBoundedHttpClient(),
            valhallaEndpointOverride = valhallaEndpoint,
            osrmRetimeEndpointOverride = retimeEndpoint,
        )

        val route = service.getAvoidanceRoute(
            avoidanceQuery(RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS),
        )

        assertEquals(72.0, route.totalDurationS, 0.0)
        val valhallaRequest = server.takeRequest(2, TimeUnit.SECONDS)
        val retimeRequest = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(valhallaRequest)
        assertNotNull(retimeRequest)
        assertTrue(valhallaRequest!!.path!!.startsWith("/route?json="))
        assertTrue(
            valhallaRequest.requestUrl!!.queryParameter("json")!!
                .contains("\"exclude_highways\":true"),
        )
        assertTrue(retimeRequest!!.path!!.startsWith("/route/v1/driving/9.00000,45.00000;"))
        assertEquals("Roadstr/1.0", valhallaRequest.getHeader("User-Agent"))
        assertEquals("Roadstr/1.0", retimeRequest.getHeader("User-Agent"))
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

    private fun avoidanceQuery(
        mode: RoutingAvoidanceMode,
        languageCode: String = "en",
    ): NativeAvoidanceRoutingQuery = NativeAvoidanceRoutingQuery(
        origin = RoutingRequestPoint(45.0, 9.0),
        destination = RoutingRequestPoint(45.01, 9.01),
        mode = mode,
        languageCode = languageCode,
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

    private fun valhallaBody(
        hasHighway: Boolean = false,
        hasToll: Boolean = false,
    ): String {
        val shape = encodeValhallaShape(
            RoutingRequestPoint(45.0, 9.0),
            RoutingRequestPoint(45.01, 9.01),
        ).replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"trip":{"status":0,"summary":{"length":1.362,"time":120.0,"has_highway":$hasHighway,"has_toll":$hasToll},"legs":[{"shape":"$shape","maneuvers":[{"type":1,"instruction":"Start","length":1.362,"begin_shape_index":0}]}]}}"""
    }

    private fun osrmRetimeBody(durationSeconds: Double): String =
        """{"code":"Ok","routes":[{"legs":[{}, {"distance":1362.0,"duration":$durationSeconds}, {}]}]}"""

    private fun decodeValhallaJson(uri: String): String {
        val rawQuery = checkNotNull(URI(uri).rawQuery)
        val encodedJson = rawQuery.substringAfter("json=", missingDelimiterValue = "")
        check(encodedJson.isNotEmpty())
        return URLDecoder.decode(encodedJson, Charsets.UTF_8.name())
    }

    private fun encodeValhallaShape(vararg points: RoutingRequestPoint): String {
        val output = StringBuilder()
        var previousLatitude = 0
        var previousLongitude = 0

        fun appendDelta(delta: Int) {
            var value = if (delta < 0) (delta shl 1).inv() else delta shl 1
            while (value >= 0x20) {
                output.append(((value and 0x1f) or 0x20).plus(63).toChar())
                value = value shr 5
            }
            output.append(value.plus(63).toChar())
        }

        points.forEach { point ->
            val latitude = (point.latitude * 1_000_000).roundToInt()
            val longitude = (point.longitude * 1_000_000).roundToInt()
            appendDelta(latitude - previousLatitude)
            appendDelta(longitude - previousLongitude)
            previousLatitude = latitude
            previousLongitude = longitude
        }
        return output.toString()
    }

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

    private companion object {
        const val VALHALLA_TEST_ENDPOINT = "https://valhalla.test/route"
        const val OSRM_RETIME_TEST_ENDPOINT = "https://osrm.test/route/v1/driving"
    }
}
