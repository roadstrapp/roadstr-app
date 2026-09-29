package app.roadstr.service.transit

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.TransitRequestPoint
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
        assertTrue(requestUri!!.contains("numItineraries=3"))
        assertTrue(requestUri!!.contains("maxPreTransitTime=1800"))
        assertEquals("Roadstr/1.0 (navigation app)", userAgent)
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
        assertTrue(recorded.path!!.contains("fromPlace=44.4184%2C12.1966"))
        assertEquals("Roadstr/1.0 (navigation app)", recorded.getHeader("User-Agent"))
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
        assertFalse(timeout.toString().contains("44.4184"))
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

    private fun service(
        transport: NativeTransitHttpTransport,
        endpoint: String = "https://example.test/api/v1/plan",
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

    companion object {
        private val origin = TransitRequestPoint(44.4184, 12.1966)
        private val destination = TransitRequestPoint(44.5058, 11.3428)
        private val departure = Instant.parse("2026-08-19T06:00:00Z")
    }
}
