package app.roadstr.service.transit

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.TransitRequestPoint
import app.roadstr.core.network.TransitProtocol
import app.roadstr.core.network.TransitProvider
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeTransitHttpTransport
import java.io.File
import java.time.Instant
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
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTransitServiceTest {
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun shutDownServers() {
        servers.forEach(MockWebServer::shutdown)
    }

    @Test
    fun `plan executes the exact bounded request and parses the Berlin fixture`() = runBlocking {
        var requestUri: String? = null
        var userAgent: String? = null
        var limits: NativeHttpRequestLimits? = null
        val service = service(
            transport = NativeTransitHttpTransport { request, requestLimits ->
                requestUri = request.uri
                userAgent = request.headers["User-Agent"]
                limits = requestLimits
                response(fixture())
            },
        )

        val result = service.plan(origin, destination, departure) as NativeTransitPlan

        assertEquals(2, result.itineraries.size)
        assertEquals(origin, result.plan.origin)
        assertEquals(destination, result.plan.destination)
        assertEquals(departure, result.plan.requestedDeparture)
        assertTrue(requestUri!!.contains("numItineraries=3"))
        assertTrue(requestUri!!.contains("maxPreTransitTime=1800"))
        assertEquals(TransitProtocol.USER_AGENT, userAgent)
        assertEquals(NetworkTimeoutBudget.Transit.milliseconds, limits!!.timeoutMillis)
        assertEquals(NetworkResponseLimit.TransitPlan.bytes, limits!!.maxResponseBytes)
    }

    @Test
    fun `loopback endpoint crosses the real no-redirect bounded adapter`() = runBlocking {
        val server = newServer()
        server.enqueue(MockResponse().setBody(fixture()))
        val service = service(
            transport = NativeBoundedHttpClient(),
            endpoint = server.url("/api/v1/plan?stale=1").toString(),
        )

        val result = service.plan(origin, destination, departure)

        assertTrue(result is NativeTransitPlan)
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertEquals("GET", recorded!!.method)
        assertFalse(recorded.path!!.contains("stale=1"))
        assertTrue(recorded.path!!.contains("fromPlace=52.5186%2C13.4081"))
        assertEquals(TransitProtocol.USER_AGENT, recorded.getHeader("User-Agent"))
    }

    @Test
    fun `successful no-coverage response is distinct from failure and is not retried`() = runBlocking {
        var calls = 0
        val result = service(
            transport = NativeTransitHttpTransport { _, _ ->
                calls++
                response("{}")
            },
        ).plan(origin, destination, departure)

        assertEquals(NativeTransitUnavailable, result)
        assertEquals(1, calls)
    }

    @Test
    fun `transient statuses retry with exponential and server requested delay`() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        val service = service(
            transport = NativeTransitHttpTransport { _, _ ->
                calls++
                when (calls) {
                    1 -> response("busy", statusCode = 503)
                    2 -> response("slow down", statusCode = 429, headers = mapOf("retry-after" to "2"))
                    else -> response(fixture())
                }
            },
            sleep = waits::add,
        )

        val result = service.plan(origin, destination, departure)

        assertTrue(result is NativeTransitPlan)
        assertEquals(3, calls)
        assertEquals(listOf(400L, 2_000L), waits)
    }

    @Test
    fun `permanent HTTP status does not retry or retain provider body`() = runBlocking {
        var calls = 0
        val secret = "precise-provider-body"
        val result = service(
            transport = NativeTransitHttpTransport { _, _ ->
                calls++
                response(secret, statusCode = 400)
            },
        ).plan(origin, destination, departure) as NativeTransitFailure

        assertEquals(1, calls)
        assertFalse(result.transient)
        assertEquals("HTTP 400", result.message)
        assertFalse(result.toString().contains(secret))
    }

    @Test
    fun `malformed response is permanent and value free`() = runBlocking {
        val secret = "malformed-secret-provider-payload"
        val result = service(
            transport = NativeTransitHttpTransport { _, _ -> response(secret) },
        ).plan(origin, destination, departure) as NativeTransitFailure

        assertFalse(result.transient)
        assertEquals("malformed transit response", result.message)
        assertFalse(result.toString().contains(secret))
    }

    @Test
    fun `transport classifications preserve retryability without leaking requests`() = runBlocking {
        val oversized = service(
            transport = NativeTransitHttpTransport { _, _ ->
                throw NativeHttpException(NativeHttpFailureKind.ResponseTooLarge)
            },
        ).plan(origin, destination, departure) as NativeTransitFailure
        assertFalse(oversized.transient)
        assertEquals("transit response is too large", oversized.message)

        var calls = 0
        val timeout = service(
            transport = NativeTransitHttpTransport { _, _ ->
                calls++
                throw NativeHttpException(NativeHttpFailureKind.Timeout)
            },
        ).plan(origin, destination, departure) as NativeTransitFailure
        assertTrue(timeout.transient)
        assertEquals("request timed out", timeout.message)
        assertEquals(3, calls)
        assertFalse(timeout.toString().contains("52.5186"))
    }

    @Test
    fun `caller cancellation stops the active request without retry`() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val service = service(
            transport = NativeTransitHttpTransport { _, _ ->
                calls.incrementAndGet()
                started.complete(Unit)
                awaitCancellation()
            },
        )
        val job = launch(Dispatchers.Default) {
            service.plan(origin, destination, departure)
        }
        started.await()

        job.cancelAndJoin()

        assertEquals(1, calls.get())
    }

    @Test
    fun `exhausted transient retry reports failure after shipped schedule`() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        val result = service(
            transport = NativeTransitHttpTransport { _, _ ->
                calls++
                throw NativeHttpException(NativeHttpFailureKind.Transport)
            },
            sleep = waits::add,
        ).plan(origin, destination, departure) as NativeTransitFailure

        assertTrue(result.transient)
        assertEquals("connection failed", result.message)
        assertEquals(3, calls)
        assertEquals(listOf(400L, 800L), waits)
    }

    @Test
    fun `provider router honors coverage priority and transient-only fallback`() = runBlocking {
        val plan = NativeTransitPlan(
            TransitProtocol.parsePlan(
                fixture(),
                provider = TransitProtocol.TRANSITOUS_PROVIDER,
            ) as app.roadstr.core.network.TransitParsedPlan,
        )
        val regional = FakeProvider(
            descriptor = descriptor(
                id = "regional",
                priority = 200,
                coverage = NativeTransitCoverage.BoundingBox(40.0, 5.0, 55.0, 16.0),
            ),
            result = NativeTransitFailure("temporary", transient = true),
        )
        val global = FakeProvider(
            descriptor = descriptor("global", 100, NativeTransitCoverage.Worldwide),
            result = plan,
        )
        val query = NativeTransitQuery(
            from = TransitRequestPoint(45.4642, 9.1900),
            to = TransitRequestPoint(45.4855, 9.2043),
            departure = departure,
            languageCode = "it",
        )

        assertEquals(plan, NativeTransitProviderRouter(listOf(global, regional)).plan(query))
        assertEquals(1, regional.calls)
        assertEquals(1, global.calls)

        regional.result = NativeTransitFailure("configuration", transient = false)
        regional.calls = 0
        global.calls = 0
        assertEquals(regional.result, NativeTransitProviderRouter(listOf(global, regional)).plan(query))
        assertEquals(1, regional.calls)
        assertEquals(0, global.calls)
    }

    /** Opt-in smoke probe: one identified, bounded request; never part of the default test run. */
    @Test
    fun `live Transitous v6 probe parses a real Milan journey`() = runBlocking {
        if (System.getenv("ROADSTR_TRANSIT_LIVE_PROBE") != "1") return@runBlocking
        val result = NativeTransitService().plan(
            from = TransitRequestPoint(45.4642, 9.1900),
            to = TransitRequestPoint(45.4855, 9.2043),
            departure = Instant.now().plusSeconds(300),
            languageCode = "it",
        ) as NativeTransitPlan

        assertEquals(TransitProtocol.TRANSITOUS_PROVIDER, result.plan.provider)
        assertTrue(result.itineraries.isNotEmpty())
        assertTrue(result.itineraries.first().transitLegs.isNotEmpty())
        assertTrue(result.itineraries.first().legs.any { it.geometry.isNotEmpty() })
    }

    private fun service(
        transport: NativeTransitHttpTransport,
        endpoint: String = "https://example.test/api/v6/plan",
        sleep: suspend (Long) -> Unit = {},
    ) = NativeTransitService(transport, endpoint, sleep)

    private fun response(
        body: String,
        statusCode: Int = 200,
        headers: Map<String, String> = emptyMap(),
    ) = NativeHttpResponse(statusCode, "test", headers, body.toByteArray())

    private fun newServer(): MockWebServer = MockWebServer().also {
        it.start()
        servers += it
    }

    private fun fixture(): String {
        var current: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (current != null) {
            val candidate = File(current, "test/fixtures/transit_plan_berlin.json")
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile
        }
        error("Unable to locate transit_plan_berlin.json")
    }

    private fun descriptor(
        id: String,
        priority: Int,
        coverage: NativeTransitCoverage,
    ) = NativeTransitProviderDescriptor(
        provider = TransitProvider(id, id, null),
        capabilities = setOf(NativeTransitCapability.DoorToDoorRouting),
        coverage = coverage,
        priority = priority,
    )

    private class FakeProvider(
        override val descriptor: NativeTransitProviderDescriptor,
        var result: NativeTransitResult,
    ) : NativeTransitProvider {
        var calls = 0

        override suspend fun plan(query: NativeTransitQuery): NativeTransitResult {
            calls += 1
            return result
        }
    }

    companion object {
        private val origin = TransitRequestPoint(52.5186, 13.4081)
        private val destination = TransitRequestPoint(48.1372, 11.5756)
        private val departure = Instant.parse("2026-08-19T06:00:00Z")
    }
}
