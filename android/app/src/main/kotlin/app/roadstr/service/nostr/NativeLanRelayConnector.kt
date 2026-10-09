package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.CustomRelayPolicy
import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends `ws://` to the relay on the owner's own network, and everything else
 * to [secure].
 *
 * Android's network security policy refuses cleartext to any address that is
 * not listed by name in the app, and a home relay is `ws://192.168.x.y`, an
 * address nobody can list in advance. Loosening the policy for the whole app
 * would let any future mistake send something in the clear; this connector
 * keeps the policy shut and speaks plain WebSocket itself, for local
 * addresses only.
 */
class NativeRelayConnectorSelector(
    private val secure: NativeRelayConnector,
    private val local: NativeRelayConnector = NativeLanRelayConnector(),
) : NativeRelayConnector {
    override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket =
        if (url.startsWith("ws://")) local.connect(url, events) else secure.connect(url, events)
}

/**
 * A minimal RFC 6455 client: handshake, masked text frames out, text frames in
 * (with fragmentation), ping answered, close honoured. It exists only for
 * relays on the local network and refuses any address that is not one, however
 * the name resolves. Messages are capped, so a rogue peer cannot make the
 * phone buffer without limit.
 */
class NativeLanRelayConnector(
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val connectTimeoutMillis: Int = CONNECT_TIMEOUT_MILLIS,
) : NativeRelayConnector {
    private val random = SecureRandom()

    override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
        val uri = URI(url)
        require(uri.scheme.equals("ws", ignoreCase = true)) { "Not a ws:// address" }
        val host = requireNotNull(uri.host) { "Relay address has no host" }
        require(CustomRelayPolicy.normalise(url) != null) { "Plain ws:// is only for the local network" }
        val port = if (uri.port >= 0) uri.port else DEFAULT_PORT
        val path = (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
        val connection = Connection(events)
        Thread({ connection.run(host, port, path) }, "roadstr-lan-relay").apply { isDaemon = true }.start()
        return connection
    }

    private inner class Connection(private val events: NativeRelayEvents) : NativeRelaySocket {
        private val ended = AtomicBoolean(false)
        private val writeLock = Any()

        @Volatile private var socket: Socket? = null

        @Volatile private var open = false

        fun run(host: String, port: Int, path: String) {
            try {
                // Every address the name resolves to must be local; connect to
                // exactly those, so the check cannot be sidestepped by a second lookup.
                val addresses = resolve(host)
                if (addresses.isEmpty() || !addresses.all(CustomRelayPolicy::isLocalNetworkAddress)) {
                    throw IOException("not a local address")
                }
                val connected = Socket()
                socket = connected
                connected.tcpNoDelay = true
                connected.connect(InetSocketAddress(addresses.first(), port), connectTimeoutMillis)
                val input = BufferedInputStream(connected.getInputStream())
                handshake(connected, input, host, port, path)
                open = true
                events.onOpen()
                readFrames(input)
            } catch (_: Exception) {
                // Whatever went wrong, the caller hears one thing: it ended.
            } finally {
                finish()
            }
        }

        private fun handshake(socket: Socket, input: BufferedInputStream, host: String, port: Int, path: String) {
            val key = ByteArray(16).also(random::nextBytes).let { Base64.getEncoder().encodeToString(it) }
            val hostHeader = if (host.contains(':')) "[$host]:$port" else "$host:$port"
            val request = "GET $path HTTP/1.1\r\n" +
                "Host: $hostHeader\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"
            socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val head = readHead(input)
            val lines = head.split("\r\n")
            if (!lines.first().split(' ').getOrNull(1).equals("101")) throw IOException("upgrade refused")
            val accept = lines.drop(1)
                .firstOrNull { it.startsWith("sec-websocket-accept:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()
            val expected = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray(Charsets.US_ASCII)),
            )
            if (accept != expected) throw IOException("bad accept key")
        }

        /** Reads up to the blank line without consuming a byte of what follows. */
        private fun readHead(input: BufferedInputStream): String {
            val head = ByteArrayOutputStream()
            var tail = 0
            while (true) {
                val next = input.read()
                if (next < 0) throw EOFException()
                head.write(next)
                if (head.size() > MAX_HEAD_BYTES) throw IOException("response head too large")
                tail = ((tail shl 8) or next) and 0xffffffff.toInt()
                if (tail == CRLF_CRLF) break
            }
            return head.toString(Charsets.ISO_8859_1.name()).trimEnd()
        }

        private fun readFrames(input: BufferedInputStream) {
            val message = ByteArrayOutputStream()
            var textStarted = false
            while (true) {
                val first = input.read()
                if (first < 0) return
                val fin = first and 0x80 != 0
                val opcode = first and 0x0f
                if (first and 0x70 != 0) throw IOException("reserved bits set")
                val second = input.read()
                if (second < 0) return
                // A server never masks its frames.
                if (second and 0x80 != 0) throw IOException("masked server frame")
                val length = when (val short = second and 0x7f) {
                    126 -> readLength(input, 2)
                    127 -> readLength(input, 8)
                    else -> short.toLong()
                }
                val control = opcode and 0x08 != 0
                if (length > MAX_MESSAGE_BYTES || (control && (length > 125 || !fin))) {
                    throw IOException("frame not acceptable")
                }
                val payload = ByteArray(length.toInt())
                readFully(input, payload)
                when (opcode) {
                    OP_TEXT, OP_CONTINUATION -> {
                        if (opcode == OP_TEXT) {
                            if (textStarted) throw IOException("text inside a message")
                            textStarted = true
                            message.reset()
                        } else if (!textStarted) {
                            throw IOException("stray continuation")
                        }
                        if (message.size() + payload.size > MAX_MESSAGE_BYTES) throw IOException("message too large")
                        message.write(payload)
                        if (fin) {
                            textStarted = false
                            events.onMessage(message.toString(Charsets.UTF_8.name()))
                        }
                    }

                    OP_PING -> writeFrame(OP_PONG, payload)
                    OP_CLOSE -> {
                        writeFrame(OP_CLOSE, payload.copyOf(minOf(payload.size, 2)))
                        return
                    }

                    OP_PONG, OP_BINARY -> Unit
                    else -> throw IOException("unknown opcode")
                }
            }
        }

        private fun readLength(input: BufferedInputStream, bytes: Int): Long {
            var value = 0L
            repeat(bytes) {
                val next = input.read()
                if (next < 0) throw EOFException()
                value = (value shl 8) or next.toLong()
            }
            if (value < 0) throw IOException("length out of range")
            return value
        }

        private fun readFully(input: BufferedInputStream, into: ByteArray) {
            var read = 0
            while (read < into.size) {
                val count = input.read(into, read, into.size - read)
                if (count < 0) throw EOFException()
                read += count
            }
        }

        private fun writeFrame(opcode: Int, payload: ByteArray): Boolean {
            val target = socket ?: return false
            return try {
                val frame = ByteArrayOutputStream(payload.size + 14)
                frame.write(0x80 or opcode)
                when {
                    payload.size < 126 -> frame.write(0x80 or payload.size)
                    payload.size < 65_536 -> {
                        frame.write(0x80 or 126)
                        frame.write(payload.size shr 8)
                        frame.write(payload.size and 0xff)
                    }
                    else -> {
                        frame.write(0x80 or 127)
                        for (shift in 56 downTo 0 step 8) frame.write(((payload.size.toLong() shr shift) and 0xff).toInt())
                    }
                }
                val mask = ByteArray(4).also(random::nextBytes)
                frame.write(mask)
                for (index in payload.indices) frame.write(payload[index].toInt() xor mask[index % 4].toInt())
                synchronized(writeLock) {
                    target.getOutputStream().apply {
                        write(frame.toByteArray())
                        flush()
                    }
                }
                true
            } catch (_: IOException) {
                false
            }
        }

        override fun send(text: String): Boolean {
            if (!open || ended.get()) return false
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_MESSAGE_BYTES) return false
            return writeFrame(OP_TEXT, bytes)
        }

        override fun close() {
            open = false
            runCatching { socket?.close() }
            finish()
        }

        private fun finish() {
            if (ended.compareAndSet(false, true)) {
                runCatching { socket?.close() }
                events.onEnded()
            }
        }
    }

    private companion object {
        const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val DEFAULT_PORT = 80
        const val CONNECT_TIMEOUT_MILLIS = 4_000
        const val MAX_HEAD_BYTES = 8 * 1024
        // UTF-8 bytes are capped at the decoder's UTF-16 ceiling. This is
        // intentionally at least as strict for non-ASCII relay payloads.
        const val MAX_MESSAGE_BYTES = NostrRelayMessageDecoder.MAX_FRAME_UTF16_CODE_UNITS
        const val CRLF_CRLF = 0x0d0a0d0a
        const val OP_CONTINUATION = 0x0
        const val OP_TEXT = 0x1
        const val OP_BINARY = 0x2
        const val OP_CLOSE = 0x8
        const val OP_PING = 0x9
        const val OP_PONG = 0xa
    }
}
