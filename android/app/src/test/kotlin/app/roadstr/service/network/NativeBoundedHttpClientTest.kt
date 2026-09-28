package app.roadstr.service.network

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.RoutingRequestProtocol
import app.roadstr.core.network.SearchProviderProtocol
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeBoundedHttpClientTest {
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun shutDownServers() {
        servers.forEach(MockWebServer::shutdown)
    }

    @Test
    fun `routing GET preserves request and returns bounded response`() = runBlocking {
        val server = newServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setStatus("HTTP/1.1 201 Created")
                .addHeader("X-Trace", "one")
                .addHeader("X-Trace", "two")
                .setBody("route-ok"),
        )
        val request = RoutingRequestProtocol.graphHopperProbe(
            server = server.url("/route?stale=1").toString(),
            apiKey = "test-key",
        )

        val response = NativeBoundedHttpClient().execute(
            request = request,
            timeout = NetworkTimeoutBudget.Routing,
            responseLimit = NetworkResponseLimit.Route,
        )

        assertEquals(201, response.statusCode)
        assertEquals("Created", response.reasonPhrase)
        assertEquals("route-ok", response.bodyUtf8)
        assertEquals("one,two", response.headers["x-trace"])
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.startsWith("/route?point=0.0,0.0"))
        assertFalse(recorded.path!!.contains("stale=1"))
        assertFalse(recorded.path!!.contains("key=test-key"))
        assertEquals("Roadstr/1.0", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `search POST preserves form body and content type`() = runBlocking {
        val server = newServer()
        server.enqueue(MockResponse().setBody("{}"))
        val request = SearchProviderProtocol.overpass(
            mirror = server.url("/interpreter").toString(),
            query = "[out:json];node(1);out;",
        )

        val response = NativeBoundedHttpClient().execute(
            request = request,
            timeout = NetworkTimeoutBudget.Standard,
            responseLimit = NetworkResponseLimit.AreaQuery,
        )

        assertEquals(200, response.statusCode)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("application/x-www-form-urlencoded", recorded.getHeader("Content-Type"))
        assertEquals("Roadstr/1.0 (navigation app)", recorded.getHeader("User-Agent"))
        assertEquals(
            "data=%5Bout%3Ajson%5D%3Bnode%281%29%3Bout%3B",
            recorded.body.readUtf8(),
        )
    }

    @Test
    fun `redirect is returned without forwarding the request`() = runBlocking {
        val server = newServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", server.url("/leak?key=do-not-forward")),
        )
        server.enqueue(MockResponse().setBody("must-not-be-requested"))

        val response = NativeBoundedHttpClient().executeWithBounds(
            request = NativeHttpRequest(
                method = NativeHttpMethod.Get,
                uri = server.url("/start?lat=45.0&lon=9.0").toString(),
                headers = mapOf("Authorization" to "private-key"),
            ),
            timeoutMillis = 2_000,
            maxBytes = 64,
        )

        assertEquals(302, response.statusCode)
        assertEquals(1, server.requestCount)
        assertEquals("/start?lat=45.0&lon=9.0", server.takeRequest().path)
    }

    @Test
    fun `declared oversized response is rejected before body read`() = runBlocking {
        val server = newServer()
        server.enqueue(
            MockResponse()
                .setBody("x")
                .setHeader("Content-Length", "9"),
        )

        expectFailure(NativeHttpFailureKind.ResponseTooLarge) {
            NativeBoundedHttpClient().executeWithBounds(
                request = get(server, "/declared"),
                timeoutMillis = 2_000,
                maxBytes = 8,
            )
        }
        Unit
    }

    @Test
    fun `chunked oversized response is rejected while streaming`() = runBlocking {
        val server = newServer()
        server.enqueue(MockResponse().setChunkedBody("12345", 2))

        expectFailure(NativeHttpFailureKind.ResponseTooLarge) {
            NativeBoundedHttpClient().executeWithBounds(
                request = get(server, "/chunked"),
                timeoutMillis = 2_000,
                maxBytes = 4,
            )
        }
        Unit
    }

    @Test
    fun `exact response budget remains accepted`() = runBlocking {
        val server = newServer()
        server.enqueue(MockResponse().setChunkedBody("12345678", 3))

        val response = NativeBoundedHttpClient().executeWithBounds(
            request = get(server, "/exact"),
            timeoutMillis = 2_000,
            maxBytes = 8,
        )

        assertEquals("12345678", response.bodyUtf8)
    }

    @Test
    fun `total call deadline includes slow response body`() = runBlocking {
        val server = newServer()
        server.enqueue(
            MockResponse()
                .setBody("slow")
                .throttleBody(1, 250, TimeUnit.MILLISECONDS),
        )

        expectFailure(NativeHttpFailureKind.Timeout) {
            NativeBoundedHttpClient().executeWithBounds(
                request = get(server, "/slow"),
                timeoutMillis = 100,
                maxBytes = 16,
            )
        }
        Unit
    }

    @Test
    fun `coroutine cancellation cancels the physical OkHttp call`() = runBlocking {
        val server = newServer()
        server.enqueue(
            MockResponse()
                .setBody("blocked")
                .setBodyDelay(5, TimeUnit.SECONDS),
        )
        val calls = RecordingCallFactory(OkHttpClient())
        val client = NativeBoundedHttpClient.forTesting(calls)
        val job = launch(Dispatchers.Default) {
            client.executeWithBounds(
                request = get(server, "/cancel"),
                timeoutMillis = 10_000,
                maxBytes = 32,
            )
        }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))

        job.cancelAndJoin()

        assertTrue(calls.lastCall!!.isCanceled())
    }

    @Test
    fun `invalid request failure never exposes URI secrets`() = runBlocking {
        val secret = "do-not-log-this-key"
        val failure = expectFailure(NativeHttpFailureKind.InvalidRequest) {
            NativeBoundedHttpClient().executeWithBounds(
                request = NativeHttpRequest(
                    method = NativeHttpMethod.Get,
                    uri = "ftp://example.invalid/path?key=$secret",
                ),
                timeoutMillis = 2_000,
                maxBytes = 16,
            )
        }

        assertFalse(failure.toString().contains(secret))
        assertFalse(failure.stackTraceToString().contains(secret))
    }

    @Test
    fun `transport failure is not retried behind the orchestrator`() = runBlocking {
        val server = newServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setBody("hidden retry"))

        expectFailure(NativeHttpFailureKind.Transport) {
            NativeBoundedHttpClient().executeWithBounds(
                request = get(server, "/single-attempt"),
                timeoutMillis = 2_000,
                maxBytes = 32,
            )
        }

        assertEquals(1, server.requestCount)
    }

    private fun newServer(): MockWebServer = MockWebServer().also { server ->
        server.start()
        servers += server
    }

    private fun get(server: MockWebServer, path: String): NativeHttpRequest =
        NativeHttpRequest(
            method = NativeHttpMethod.Get,
            uri = server.url(path).toString(),
        )

    private suspend fun expectFailure(
        expected: NativeHttpFailureKind,
        block: suspend () -> Unit,
    ): NativeHttpException {
        try {
            block()
            fail("Expected native HTTP failure $expected")
        } catch (failure: NativeHttpException) {
            assertEquals(expected, failure.kind)
            return failure
        }
        error("unreachable")
    }

    private class RecordingCallFactory(
        private val delegate: Call.Factory,
    ) : Call.Factory {
        @Volatile
        var lastCall: Call? = null
            private set

        override fun newCall(request: Request): Call =
            delegate.newCall(request).also { call -> lastCall = call }
    }
}
