package app.roadstr.service.nostr

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The connector against a throw-away WebSocket server on the loopback address. */
class NativeLanRelayConnectorTest {
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val url get() = "ws://127.0.0.1:${server.localPort}"

    @After
    fun tearDown() = server.close()

    private class Recorder : NativeRelayEvents {
        val opened = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val messages = LinkedBlockingQueue<String>()
        override fun onOpen() = opened.countDown()
        override fun onMessage(text: String) {
            messages.add(text)
        }
        override fun onEnded() = ended.countDown()
    }

    private fun serverFrame(opcode: Int, payload: ByteArray, fin: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((if (fin) 0x80 else 0) or opcode)
        when {
            payload.size < 126 -> out.write(payload.size)
            payload.size < 65_536 -> { out.write(126); out.write(payload.size shr 8); out.write(payload.size and 0xff) }
            else -> { out.write(127); for (s in 56 downTo 0 step 8) out.write(((payload.size.toLong() shr s) and 0xff).toInt()) }
        }
        out.write(payload)
        return out.toByteArray()
    }

    /** Accepts one client, answers the handshake and runs [script] with its streams. */
    private fun serve(acceptKeyOverride: String? = null, status: String = "101 Switching Protocols", script: (InputStream, java.io.OutputStream) -> Unit = { _, _ -> }) {
        Thread {
            runCatching {
                server.accept().use { client ->
                    val input = client.getInputStream()
                    val head = StringBuilder()
                    while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
                    val key = Regex("Sec-WebSocket-Key: (.+)\r\n").find(head)!!.groupValues[1].trim()
                    val accept = acceptKeyOverride ?: Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
                    )
                    val out = client.getOutputStream()
                    out.write("HTTP/1.1 $status\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
                    out.flush()
                    script(input, out)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    /** Reads one masked client frame and returns its unmasked payload. */
    private fun readClientText(input: InputStream): String {
        val first = input.read()
        assertEquals(0x81, first)
        val second = input.read()
        assertTrue("client frames must be masked", second and 0x80 != 0)
        var length = second and 0x7f
        if (length == 126) length = (input.read() shl 8) or input.read()
        val mask = ByteArray(4).also { input.readNBytes(it, 0, 4) }
        val data = input.readNBytes(length)
        return String(ByteArray(length) { (data[it].toInt() xor mask[it % 4].toInt()).toByte() })
    }

    @Test
    fun `it opens, sends masked text and receives text, including a fragmented message`() {
        val received = LinkedBlockingQueue<String>()
        serve { input, out ->
            received.add(readClientText(input))
            out.write(serverFrame(1, "[\"EOSE\",\"abc\"]".toByteArray())); out.flush()
            out.write(serverFrame(1, "[\"EV".toByteArray(), fin = false))
            out.write(serverFrame(9, "hi".toByteArray()))
            out.write(serverFrame(0, "ENT\"]".toByteArray())); out.flush()
            Thread.sleep(300)
        }
        val events = Recorder()
        val socket = NativeLanRelayConnector().connect(url, events)

        assertTrue(events.opened.await(5, TimeUnit.SECONDS))
        assertTrue(socket.send("[\"REQ\",\"abc\",{}]"))
        assertEquals("[\"REQ\",\"abc\",{}]", received.poll(5, TimeUnit.SECONDS))
        assertEquals("[\"EOSE\",\"abc\"]", events.messages.poll(5, TimeUnit.SECONDS))
        assertEquals("[\"EVENT\"]", events.messages.poll(5, TimeUnit.SECONDS))
        socket.close()
        assertTrue(events.ended.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `a long message uses the extended length and survives the round trip`() {
        val received = LinkedBlockingQueue<String>()
        val payload = "x".repeat(300)
        serve { input, out ->
            received.add(readClientText(input))
            out.write(serverFrame(1, payload.toByteArray())); out.flush()
            Thread.sleep(300)
        }
        val events = Recorder()
        val socket = NativeLanRelayConnector().connect(url, events)
        assertTrue(events.opened.await(5, TimeUnit.SECONDS))
        assertTrue(socket.send(payload))
        assertEquals(payload, received.poll(5, TimeUnit.SECONDS))
        assertEquals(payload, events.messages.poll(5, TimeUnit.SECONDS))
        socket.close()
    }

    @Test
    fun `a close frame from the relay ends the connection`() {
        serve { _, out -> out.write(serverFrame(8, byteArrayOf(0x03, 0xe8.toByte()))); out.flush(); Thread.sleep(300) }
        val events = Recorder()
        NativeLanRelayConnector().connect(url, events)
        assertTrue(events.opened.await(5, TimeUnit.SECONDS))
        assertTrue(events.ended.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `a refused upgrade or a wrong accept key never opens`() {
        serve(status = "403 Forbidden")
        val refused = Recorder()
        NativeLanRelayConnector().connect(url, refused)
        assertTrue(refused.ended.await(5, TimeUnit.SECONDS))
        assertFalse(refused.opened.await(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a forged accept key is not trusted`() {
        serve(acceptKeyOverride = "AAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        val forged = Recorder()
        NativeLanRelayConnector().connect(url, forged)
        assertTrue(forged.ended.await(5, TimeUnit.SECONDS))
        assertFalse(forged.opened.await(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a masked frame from the relay is a protocol error`() {
        serve { _, out -> out.write(byteArrayOf(0x81.toByte(), 0x81.toByte(), 1, 2, 3, 4, 5)); out.flush(); Thread.sleep(300) }
        val events = Recorder()
        NativeLanRelayConnector().connect(url, events)
        assertTrue(events.ended.await(5, TimeUnit.SECONDS))
        assertTrue(events.messages.isEmpty())
    }

    @Test
    fun `a name that resolves to a public address is never connected to`() {
        val events = Recorder()
        NativeLanRelayConnector(resolve = { listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) })
            .connect("ws://relay.local:4848", events)
        assertTrue(events.ended.await(5, TimeUnit.SECONDS))
        assertFalse(events.opened.await(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a public ws address is refused before any thread starts`() {
        val failed = runCatching { NativeLanRelayConnector().connect("ws://relay.damus.io", Recorder()) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `the selector sends ws to the local connector and wss to the secure one`() {
        val seen = mutableListOf<String>()
        val fake = object : NativeRelayConnector {
            override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
                seen += "secure:$url"
                return object : NativeRelaySocket { override fun send(text: String) = true; override fun close() {} }
            }
        }
        val local = object : NativeRelayConnector {
            override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
                seen += "local:$url"
                return object : NativeRelaySocket { override fun send(text: String) = true; override fun close() {} }
            }
        }
        val selector = NativeRelayConnectorSelector(fake, local)
        selector.connect("wss://relay.damus.io", Recorder())
        selector.connect("ws://192.168.1.2:4848", Recorder())
        assertEquals(listOf("secure:wss://relay.damus.io", "local:ws://192.168.1.2:4848"), seen)
    }
}
