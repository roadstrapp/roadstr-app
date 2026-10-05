package app.roadstr.service.discovery

import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * A small HTTP/1.1 client for a SearXNG instance on the user's own network.
 *
 * Android's network policy can only allow plain http by host name, not by address
 * range, so OkHttp refuses `http://192.168.1.20:8888`. This sends the one GET over a
 * raw socket instead, and it only ever connects to a loopback, private, link-local or
 * VPN address, so cleartext can never leave the local network even if a name's DNS
 * answer changes. Redirects are not followed, the response is bounded (also after
 * decompression), and cancelling the call closes the socket.
 */
class NativeLanHttpTransport(
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
) : NativeSearchHttpTransport {
    override suspend fun execute(
        request: SearchProviderRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse = coroutineScope {
        val socket = Socket()
        val work = async(Dispatchers.IO) { run(socket, request, limits) }
        try {
            work.await()
        } catch (cancelled: CancellationException) {
            // Blocking reads cannot be interrupted; closing the socket is what ends them.
            closeQuietly(socket)
            throw cancelled
        } finally {
            closeQuietly(socket)
        }
    }

    private suspend fun run(
        socket: Socket,
        request: SearchProviderRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse {
        try {
            return exchange(socket, target(request), request, limits)
        } catch (failure: NativeHttpException) {
            coroutineContext.ensureActive()
            throw failure
        } catch (_: SocketTimeoutException) {
            coroutineContext.ensureActive()
            throw NativeHttpException(NativeHttpFailureKind.Timeout)
        } catch (_: IOException) {
            // A cancelled call closes its socket; that is a cancellation, not a transport failure.
            coroutineContext.ensureActive()
            throw NativeHttpException(NativeHttpFailureKind.Transport)
        }
    }

    private class Target(val host: String, val port: Int, val address: InetAddress, val path: String)

    private fun target(request: SearchProviderRequest): Target {
        val uri = try {
            URI(request.uri)
        } catch (_: Exception) {
            throw NativeHttpException(NativeHttpFailureKind.InvalidRequest)
        }
        val host = uri.host?.removeSurrounding("[", "]")
        if (uri.scheme?.lowercase(Locale.ROOT) != "http" || host.isNullOrEmpty()) {
            throw NativeHttpException(NativeHttpFailureKind.InvalidRequest)
        }
        val addresses = try {
            resolve(host)
        } catch (_: IOException) {
            throw NativeHttpException(NativeHttpFailureKind.Transport)
        }
        val address = addresses.firstOrNull()
        if (address == null || addresses.any { !isLocal(it) }) {
            throw NativeHttpException(NativeHttpFailureKind.InvalidRequest)
        }
        val port = if (uri.port == -1) HTTP_PORT else uri.port
        val path = (uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/") +
            (uri.rawQuery?.let { "?$it" } ?: "")
        return Target(host, port, address, path)
    }

    private fun exchange(
        socket: Socket,
        target: Target,
        request: SearchProviderRequest,
        limits: NativeHttpRequestLimits,
    ): NativeHttpResponse {
        val deadline = System.nanoTime() + limits.timeoutMillis * NANOS_PER_MILLI
        socket.soTimeout = remainingMillis(deadline)
        socket.connect(InetSocketAddress(target.address, target.port), remainingMillis(deadline))
        socket.getOutputStream().apply {
            write(requestBytes(target, request))
            flush()
        }
        val input = socket.getInputStream().buffered()
        val reader = ResponseReader(input, deadline, socket, limits.maxResponseBytes)
        return reader.read()
    }

    private fun requestBytes(target: Target, request: SearchProviderRequest): ByteArray {
        val hostHeader = if (target.port == HTTP_PORT) target.host else "${target.host}:${target.port}"
        val lines = ArrayList<String>()
        lines += "GET ${target.path} HTTP/1.1"
        lines += "Host: $hostHeader"
        request.headers.forEach { (name, value) ->
            if (isSafeHeader(name, value)) lines += "$name: $value"
        }
        // The instance's bot limiter wants a compression header and dislikes "Connection: close".
        lines += "Accept-Encoding: gzip, deflate"
        lines += "Connection: keep-alive"
        return (lines.joinToString("\r\n") + "\r\n\r\n").toByteArray(Charsets.US_ASCII)
    }

    private fun isSafeHeader(name: String, value: String): Boolean =
        name.matches(HEADER_NAME) && value.none { it == '\r' || it == '\n' } &&
            value.all { it.code in 0x20..0x7e } &&
            name.lowercase(Locale.ROOT) !in FORBIDDEN_HEADERS

    private class ResponseReader(
        private val input: InputStream,
        private val deadline: Long,
        private val socket: Socket,
        private val maxBytes: Long,
    ) {
        fun read(): NativeHttpResponse {
            val status = readLine() ?: throw EOFException()
            val code = STATUS_LINE.matchEntire(status)?.groupValues?.get(1)?.toIntOrNull()
                ?: throw NativeHttpException(NativeHttpFailureKind.Transport)
            val headers = readHeaders()
            val encoded = readBody(headers)
            val body = decode(encoded, headers["content-encoding"])
            return NativeHttpResponse(code, status.substringAfter(' ').substringAfter(' ', ""), headers, body)
        }

        private fun readHeaders(): Map<String, String> {
            val headers = LinkedHashMap<String, String>()
            var total = 0
            while (true) {
                val line = readLine() ?: throw EOFException()
                if (line.isEmpty()) return headers
                total += line.length
                if (total > MAX_HEADER_BYTES || headers.size > MAX_HEADERS) {
                    throw NativeHttpException(NativeHttpFailureKind.Transport)
                }
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] = line.substring(colon + 1).trim()
            }
        }

        private fun readBody(headers: Map<String, String>): ByteArray {
            if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) return readChunked()
            val length = headers["content-length"]?.toLongOrNull()
            if (length != null) {
                if (length < 0 || length > maxBytes) throw NativeHttpException(NativeHttpFailureKind.ResponseTooLarge)
                return readExactly(length.toInt())
            }
            return readUntilClose()
        }

        private fun readChunked(): ByteArray {
            val out = ByteArrayOutputStream()
            while (true) {
                val size = readLine()?.substringBefore(';')?.trim()?.toIntOrNull(HEX)
                    ?: throw NativeHttpException(NativeHttpFailureKind.Transport)
                if (size == 0) {
                    while (readLine()?.isNotEmpty() == true) Unit
                    return out.toByteArray()
                }
                if (out.size() + size.toLong() > maxBytes) {
                    throw NativeHttpException(NativeHttpFailureKind.ResponseTooLarge)
                }
                out.write(readExactly(size))
                readLine()
            }
        }

        private fun readUntilClose(): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (true) {
                checkDeadline()
                val count = input.read(buffer)
                if (count < 0) return out.toByteArray()
                if (out.size() + count.toLong() > maxBytes) {
                    throw NativeHttpException(NativeHttpFailureKind.ResponseTooLarge)
                }
                out.write(buffer, 0, count)
            }
        }

        private fun readExactly(count: Int): ByteArray {
            val bytes = ByteArray(count)
            var done = 0
            while (done < count) {
                checkDeadline()
                val read = input.read(bytes, done, count - done)
                if (read < 0) throw EOFException()
                done += read
            }
            return bytes
        }

        private fun readLine(): String? {
            val line = StringBuilder()
            while (true) {
                checkDeadline()
                val byte = input.read()
                if (byte < 0) return if (line.isEmpty()) null else line.toString()
                if (byte == '\n'.code) return line.toString().trimEnd('\r')
                if (line.length > MAX_LINE) throw NativeHttpException(NativeHttpFailureKind.Transport)
                line.append(byte.toChar())
            }
        }

        private fun decode(bytes: ByteArray, encoding: String?): ByteArray {
            val stream = when (encoding?.trim()?.lowercase(Locale.ROOT)) {
                null, "", "identity" -> return bytes
                "gzip" -> GZIPInputStream(bytes.inputStream())
                "deflate" -> InflaterInputStream(bytes.inputStream())
                else -> throw NativeHttpException(NativeHttpFailureKind.Transport)
            }
            return stream.use { readBounded(it) }
        }

        private fun readBounded(stream: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) return out.toByteArray()
                if (out.size() + count.toLong() > maxBytes) {
                    throw NativeHttpException(NativeHttpFailureKind.ResponseTooLarge)
                }
                out.write(buffer, 0, count)
            }
        }

        private fun checkDeadline() {
            val left = (deadline - System.nanoTime()) / NANOS_PER_MILLI
            if (left <= 0) throw NativeHttpException(NativeHttpFailureKind.Timeout)
            socket.soTimeout = left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }

    private fun remainingMillis(deadline: Long): Int {
        val left = (deadline - System.nanoTime()) / NANOS_PER_MILLI
        if (left <= 0) throw NativeHttpException(NativeHttpFailureKind.Timeout)
        return left.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: IOException) {
            // Already closed.
        }
    }

    companion object {
        private const val HTTP_PORT = 80
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val HEX = 16
        private const val BUFFER = 8 * 1024
        private const val MAX_LINE = 8 * 1024
        private const val MAX_HEADER_BYTES = 32 * 1024
        private const val MAX_HEADERS = 100
        private val STATUS_LINE = Regex("^HTTP/1\\.[01] (\\d{3})(?: .*)?$")
        private val HEADER_NAME = Regex("^[A-Za-z0-9-]{1,40}$")
        private val FORBIDDEN_HEADERS = setOf("host", "connection", "accept-encoding", "content-length", "transfer-encoding")

        /** Loopback, private, link-local, carrier-grade NAT (VPNs) and unique-local IPv6. */
        fun isLocal(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isMulticastAddress) return false
            if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) return true
            val bytes = address.address
            if (address is Inet4Address) {
                return (bytes[0].toInt() and 0xff) == 100 && (bytes[1].toInt() and 0xc0) == 0x40
            }
            return (bytes[0].toInt() and 0xfe) == 0xfc
        }
    }
}
