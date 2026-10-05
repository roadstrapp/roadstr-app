package app.roadstr.service.discovery

import app.roadstr.core.network.SearchProviderHttpMethod
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpRequestLimits
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeLanHttpTransportTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val servers = mutableListOf<ServerSocket>()
    private val transport = NativeLanHttpTransport(resolve = { listOf(loopback) })
    private val limits = NativeHttpRequestLimits(timeoutMillis = 2_000, maxResponseBytes = 4_096)

    @After
    fun stop() = servers.forEach { runCatching { it.close() } }

    /** Serves one connection with [respond] and remembers the raw request. */
    private fun serve(respond: (java.net.Socket) -> Unit): Pair<Int, AtomicReference<String>> {
        val server = ServerSocket(0, 1, loopback).also { servers += it }
        val seen = AtomicReference("")
        Thread {
            runCatching {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val lines = ArrayList<String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        lines += line
                    }
                    seen.set(lines.joinToString("\n"))
                    respond(socket)
                }
            }
        }.start()
        return server.localPort to seen
    }

    private fun request(port: Int, path: String = "/search?q=x", headers: Map<String, String> = emptyMap()) =
        SearchProviderRequest(SearchProviderHttpMethod.Get, "http://127.0.0.1:$port$path", headers)

    private fun send(socket: java.net.Socket, head: String, body: ByteArray) {
        socket.getOutputStream().write(head.toByteArray(Charsets.US_ASCII) + body)
        socket.getOutputStream().flush()
    }

    private fun failureKind(block: suspend () -> Unit): NativeHttpFailureKind {
        try {
            runBlocking { block() }
        } catch (failure: NativeHttpException) {
            return failure.kind
        }
        fail("expected a failure")
        error("unreachable")
    }

    @Test
    fun `a plain answer with a length is read`() = runBlocking {
        val (port, seen) = serve { send(it, "HTTP/1.1 200 OK\r\nContent-Length: 11\r\nContent-Type: application/json\r\n\r\n", "{\"ok\":true}".toByteArray()) }
        val response = transport.execute(request(port, headers = mapOf("User-Agent" to "Roadstr/1.0")), limits)
        assertEquals(200, response.statusCode)
        assertEquals("{\"ok\":true}", response.bodyUtf8)
        val raw = seen.get()
        assertTrue(raw.startsWith("GET /search?q=x HTTP/1.1"))
        assertTrue(raw.contains("Host: 127.0.0.1:$port"))
        assertTrue(raw.contains("User-Agent: Roadstr/1.0"))
        assertTrue(raw.contains("Connection: keep-alive"))
        assertTrue(raw.contains("Accept-Encoding: gzip, deflate"))
        assertFalse(raw.contains("Cookie"))
    }

    @Test
    fun `a chunked answer is reassembled`() = runBlocking {
        val (port, _) = serve { send(it, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", "5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n".toByteArray()) }
        assertEquals("hello world", transport.execute(request(port), limits).bodyUtf8)
    }

    @Test
    fun `a gzip answer is decoded`() = runBlocking {
        val packed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write("zipped body".toByteArray()) } }.toByteArray()
        val (port, _) = serve { send(it, "HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: ${packed.size}\r\n\r\n", packed) }
        assertEquals("zipped body", transport.execute(request(port), limits).bodyUtf8)
    }

    @Test
    fun `a redirect is returned, not followed`() = runBlocking {
        val (port, _) = serve { send(it, "HTTP/1.1 302 Found\r\nLocation: http://elsewhere.example/\r\nContent-Length: 0\r\n\r\n", ByteArray(0)) }
        val response = transport.execute(request(port), limits)
        assertEquals(302, response.statusCode)
        assertEquals("http://elsewhere.example/", response.headers["location"])
    }

    @Test
    fun `an oversized answer is refused before and while it is read`() {
        val (declared, _) = serve { send(it, "HTTP/1.1 200 OK\r\nContent-Length: 999999\r\n\r\n", ByteArray(0)) }
        assertEquals(NativeHttpFailureKind.ResponseTooLarge, failureKind { transport.execute(request(declared), limits) })
        val big = "5000\r\n".toByteArray() + ByteArray(0x5000) { 'a'.code.toByte() } + "\r\n0\r\n\r\n".toByteArray()
        val (chunked, _) = serve { send(it, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", big) }
        assertEquals(NativeHttpFailureKind.ResponseTooLarge, failureKind { transport.execute(request(chunked), limits) })
    }

    @Test
    fun `a decompression bomb is cut off`() {
        val packed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(ByteArray(200_000)) } }.toByteArray()
        val (port, _) = serve { send(it, "HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: ${packed.size}\r\n\r\n", packed) }
        assertEquals(NativeHttpFailureKind.ResponseTooLarge, failureKind { transport.execute(request(port), limits) })
    }

    @Test
    fun `a silent server times out`() {
        val (port, _) = serve { Thread.sleep(3_000) }
        val quick = NativeHttpRequestLimits(timeoutMillis = 300, maxResponseBytes = 4_096)
        assertEquals(NativeHttpFailureKind.Timeout, failureKind { transport.execute(request(port), quick) })
    }

    @Test
    fun `a public address is never contacted`() {
        val public = NativeLanHttpTransport(resolve = { listOf(InetAddress.getByName("8.8.8.8")) })
        val mixed = NativeLanHttpTransport(resolve = { listOf(loopback, InetAddress.getByName("8.8.8.8")) })
        for (candidate in listOf(public, mixed)) {
            assertEquals(
                NativeHttpFailureKind.InvalidRequest,
                failureKind { candidate.execute(request(1), limits) },
            )
        }
    }

    @Test
    fun `only plain http urls are accepted`() {
        val https = SearchProviderRequest(SearchProviderHttpMethod.Get, "https://127.0.0.1/x", emptyMap())
        assertEquals(NativeHttpFailureKind.InvalidRequest, failureKind { transport.execute(https, limits) })
        val junk = SearchProviderRequest(SearchProviderHttpMethod.Get, "not a url", emptyMap())
        assertEquals(NativeHttpFailureKind.InvalidRequest, failureKind { transport.execute(junk, limits) })
    }

    @Test
    fun `a header cannot smuggle a second request`() = runBlocking {
        val (port, seen) = serve { send(it, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n", ByteArray(0)) }
        transport.execute(request(port, headers = mapOf("X-A" to "1\r\nGET /evil HTTP/1.1", "X-B" to "ok", "Bad Name" to "x")), limits)
        val raw = seen.get()
        assertTrue(raw.contains("X-B: ok"))
        assertFalse(raw.contains("evil"))
        assertFalse(raw.contains("Bad Name"))
    }

    @Test
    fun `cancelling closes the socket instead of waiting`() = runBlocking {
        val (port, _) = serve { Thread.sleep(3_000) }
        val call = async { transport.execute(request(port), NativeHttpRequestLimits(10_000, 4_096)) }
        delay(200)
        val started = System.nanoTime()
        call.cancel()
        try {
            call.await()
            fail("expected cancellation")
        } catch (_: CancellationException) {
            assertTrue((System.nanoTime() - started) / 1_000_000 < 1_500)
        }
    }

    @Test
    fun `only local addresses count as local`() {
        val local = listOf("192.168.1.1", "10.0.0.1", "172.20.0.1", "169.254.1.1", "127.0.0.1", "100.64.0.1", "100.127.255.1", "::1", "fd00::1", "fe80::1")
        val other = listOf("8.8.8.8", "100.128.0.1", "100.63.0.1", "172.32.0.1", "0.0.0.0", "224.0.0.1", "2001:4860:4860::8888")
        local.forEach { assertTrue(it, NativeLanHttpTransport.isLocal(InetAddress.getByName(it))) }
        other.forEach { assertFalse(it, NativeLanHttpTransport.isLocal(InetAddress.getByName(it))) }
    }
}
