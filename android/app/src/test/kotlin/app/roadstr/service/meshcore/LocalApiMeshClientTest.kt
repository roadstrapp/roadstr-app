package app.roadstr.service.meshcore

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.NostrJson
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The local API client against a simulated MeshCore Open (protocol 1 of LOCAL_API.md). */
class LocalApiMeshClientTest {
    private class TestIdentity : LocalApiIdentity {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val signatures = AtomicInteger()

        override fun publicKey(): ByteArray = LocalApiIdentity.rawPublicKey(pair.public as ECPublicKey)

        override fun sign(message: ByteArray): ByteArray {
            signatures.incrementAndGet()
            return Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(message)
                sign()
            }
        }

        fun verifies(message: String, signature: String): Boolean = Signature.getInstance("SHA256withECDSA").run {
            initVerify(pair.public)
            update(message.toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(signature))
        }
    }

    private val server = MockWebServer()
    private val sockets = LinkedBlockingQueue<WebSocket>()
    private val opened = Collections.synchronizedList(mutableListOf<WebSocket>())
    private val inbox = LinkedBlockingQueue<Map<String, Any?>>()
    private val identity = TestIdentity()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val datagrams = Collections.synchronizedList(mutableListOf<MeshCoreDatagram>())
    private val states = Collections.synchronizedList(mutableListOf<MeshCoreState>())
    private var cursor: String? = null
    @Volatile private var now = 1_000_000L

    private fun client(retryBaseMillis: Long = 50, notApprovedRetryMillis: Long = 60_000) = LocalApiMeshClient(
        scope = scope,
        channelName = { "#roadstr" },
        identity = identity,
        readCursor = { cursor },
        writeCursor = { cursor = it },
        clientVersion = "1.2.3",
        url = server.url("/api/v1").toString().replaceFirst("http", "ws"),
        retryBaseMillis = retryBaseMillis,
        retryMaxMillis = 400,
        notApprovedRetryMillis = notApprovedRetryMillis,
        clock = { now },
        onDatagram = { datagrams += it },
    ).also { client -> scope.launch { client.state.collect { states += it } } }

    /** MeshCore Open accepts the next WebSocket. */
    private fun accept(onOpen: (WebSocket) -> Unit = {}) {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened += webSocket
                        sockets += webSocket
                        onOpen(webSocket)
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        inbox += BoundedJsonParser(text).parse() as Map<String, Any?>
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                    }
                },
            ),
        )
    }

    private fun next(): Map<String, Any?> = inbox.poll(5, TimeUnit.SECONDS) ?: fail("nothing from the client").let { emptyMap() }

    private fun socket(): WebSocket = sockets.poll(5, TimeUnit.SECONDS) ?: fail("no connection").let { error("unreachable") }

    private fun WebSocket.json(vararg fields: Pair<String, Any?>) = send(NostrJson.encode(linkedMapOf(*fields)))

    private fun eventually(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until) {
            if (condition()) return
            Thread.sleep(10)
        }
        fail(what)
    }

    /** Hello, challenge, pair and approval; returns the subscription request that follows. */
    private fun approve(ws: WebSocket, nonce: String = "n0nce", device: Map<String, Any?> = linkedMapOf("name" to "T-Deck")): Map<String, Any?> {
        val hello = next()
        assertEquals("hello", hello["type"])
        ws.json("type" to "pairing.challenge", "protocol" to 1, "nonce" to nonce, "known" to true)
        val pair = next()
        assertEquals("pair", pair["type"])
        assertTrue(identity.verifies("meshcore-open-pair-v1:$nonce", pair["signature"] as String))
        ws.json("type" to "hello", "protocol" to 1, "device" to device)
        val subscribe = next()
        assertEquals("events.subscribe", subscribe["method"])
        return subscribe
    }

    /** Hello, challenge, pair, approval and subscription; returns the subscription's params. */
    @Suppress("UNCHECKED_CAST")
    private fun handshake(ws: WebSocket, nonce: String = "n0nce"): Map<String, Any?> {
        val subscribe = approve(ws, nonce)
        ws.json("id" to subscribe["id"], "ok" to true, "result" to linkedMapOf("subscriptionId" to "s1"))
        return subscribe["params"] as Map<String, Any?>
    }

    private fun WebSocket.unknownChannel(request: Map<String, Any?>) = json(
        "id" to request["id"], "ok" to false,
        "error" to linkedMapOf("code" to "unknown_channel", "message" to "no channel named #roadstr"),
    )

    private fun WebSocket.device(connected: Boolean, vararg channels: String) = json(
        "type" to "device",
        "device" to linkedMapOf("connected" to connected, "name" to "T-Deck"),
        "channels" to channels.mapIndexed { index, name -> linkedMapOf("index" to index, "name" to name, "isPublic" to false) },
    )

    private fun problem(client: LocalApiMeshClient): MeshCoreProblem? = (client.state.value as? MeshCoreState.Failed)?.problem

    private fun quiet(what: String) = assertNull(what, inbox.poll(300, TimeUnit.MILLISECONDS))

    @After
    fun tearDown() {
        scope.cancel()
        synchronized(opened) { opened.forEach { runCatching { it.close(1001, null) } } }
        // MockWebServer can wait forever on a socket the client already dropped; the test is over.
        runCatching { server.shutdown() }
    }

    @Test
    fun `the fingerprint is the one MeshCore Open shows`() {
        assertEquals("FDEA B9AC F371 0362", LocalApiIdentity.fingerprint(ByteArray(64) { it.toByte() }))
    }

    @Test
    fun `pairing signs its own string, once, and never the server's message`() {
        accept()
        val client = client()
        client.start()
        val ws = socket()

        @Suppress("UNCHECKED_CAST")
        val hello = next()["client"] as Map<String, Any?>
        assertEquals("Roadstr", hello["name"])
        assertEquals(Base64.getEncoder().encodeToString(identity.publicKey()), hello["publicKey"])

        ws.json(
            "type" to "pairing.challenge", "protocol" to 1, "nonce" to "abc", "known" to false,
            "message" to "please sign this transfer",
        )
        val signature = next()["signature"] as String
        assertTrue(identity.verifies("meshcore-open-pair-v1:abc", signature))
        assertFalse(identity.verifies("please sign this transfer", signature))

        ws.json("type" to "pairing.challenge", "protocol" to 1, "nonce" to "second", "message" to "meshcore-open-pair-v1:second")
        assertNull("a second challenge is ignored", inbox.poll(300, TimeUnit.MILLISECONDS))
        assertEquals(1, identity.signatures.get())
        client.stop()
    }

    @Test
    fun `an approved hello subscribes to the shared channel and the radio becomes ready`() {
        accept()
        val client = client()
        client.start()
        val params = handshake(socket())
        assertEquals(listOf("channel.data"), params["events"])
        assertEquals("#roadstr", params["channel"])
        assertEquals(listOf(0xFF52L, 0x0150L), params["dataTypes"])
        assertFalse("no cursor yet", "since" in params)
        eventually("ready") { client.state.value is MeshCoreState.Ready }
        assertEquals("T-Deck", (client.state.value as MeshCoreState.Ready).device.name)
        client.stop()
    }

    @Test
    fun `frames are delivered in order, the cursor is the server's receivedAt, and a restart resubscribes from it`() {
        accept()
        val client = client()
        client.start()
        val ws = socket()
        handshake(ws)
        // Both the development data_type and the requested Road Events one are Roadstr's.
        val types = listOf(0xFF52, 0x0150, 0xFF52)
        val hops = listOf(2, null, 0)
        for (n in 1..3) {
            ws.json(
                "type" to "event", "subscriptionId" to "s1",
                "event" to linkedMapOf(
                    "type" to "channel.data", "channel" to "#roadstr", "channelIndex" to 0, "dataType" to types[n - 1],
                    "payload" to Base64.getEncoder().encodeToString(byteArrayOf(n.toByte())),
                    "receivedAt" to "2026-10-27T10:00:0${n}Z", "outgoing" to false, "hops" to hops[n - 1],
                ),
            )
        }
        ws.json(
            "type" to "event", "subscriptionId" to "s1",
            "event" to linkedMapOf("type" to "channel.data", "channel" to "#roadstr", "dataType" to 0xFF53, "payload" to "AQ=="),
        )
        ws.json(
            "type" to "event", "subscriptionId" to "s1",
            "event" to linkedMapOf("type" to "channel.data", "channel" to "#other", "dataType" to 0xFF52, "payload" to "AQ=="),
        )
        eventually("three frames") { datagrams.size == 3 }
        assertEquals(listOf(1, 2, 3), datagrams.map { it.payload.single().toInt() })
        assertEquals(hops, datagrams.map { it.hops })
        assertEquals("2026-10-27T10:00:03Z", cursor)

        client.stop()
        assertEquals(MeshCoreState.Off, client.state.value)
        accept()
        client.start()
        val params = handshake(socket())
        assertEquals("2026-10-27T10:00:03Z", params["since"])
        eventually("ready again") { client.state.value is MeshCoreState.Ready }
        client.stop()
    }

    @Test
    fun `a send goes out as channel sendData and reports the answer`() {
        accept()
        val client = client()
        client.start()
        val ws = socket()
        handshake(ws)
        eventually("ready") { client.ready }

        val results = LinkedBlockingQueue<MeshCoreSendResult>()
        client.send(ByteArray(134) { 7 }) { results += it }
        val request = next()
        assertEquals("channel.sendData", request["method"])
        @Suppress("UNCHECKED_CAST")
        val params = request["params"] as Map<String, Any?>
        assertEquals("#roadstr", params["channel"])
        assertEquals(0xFF52L, params["dataType"])
        assertEquals(134, Base64.getDecoder().decode(params["payload"] as String).size)
        ws.json("id" to request["id"], "ok" to true, "result" to linkedMapOf("queued" to true))
        assertEquals(MeshCoreSendResult.Sent, results.poll(5, TimeUnit.SECONDS))

        client.send(ByteArray(164)) { results += it }
        assertEquals(MeshCoreSendResult.TooLarge, results.poll(1, TimeUnit.SECONDS))
        client.stop()
    }

    @Test
    fun `a denied app backs off for a minute and says it is not approved`() {
        accept()
        val client = client()
        client.start()
        val ws = socket()
        next()
        ws.json("type" to "pairing.challenge", "protocol" to 1, "nonce" to "x", "known" to false)
        next()
        ws.close(4003, "pairing denied")
        eventually("not approved") {
            (client.state.value as? MeshCoreState.Failed)?.problem == MeshCoreProblem.NotApproved
        }
        assertTrue(((client.state.value as MeshCoreState.Failed).retryInMillis ?: 0) >= 60_000)
        client.stop()
    }

    @Test
    fun `a missing channel on a connected radio is asked for once, then subscribed`() {
        accept()
        val client = client()
        client.start()
        val ws = socket()
        ws.unknownChannel(approve(ws, device = linkedMapOf("name" to "T-Deck", "connected" to true)))
        val join = next()
        assertEquals("channels.join", join["method"])
        assertEquals(linkedMapOf<String, Any?>("name" to "#roadstr"), join["params"])
        eventually("waiting for the user") { client.state.value == MeshCoreState.AwaitingChannel }
        ws.json("id" to join["id"], "ok" to true, "result" to linkedMapOf("index" to 19, "name" to "#roadstr", "created" to true))
        val subscribe = next()
        assertEquals("events.subscribe", subscribe["method"])
        ws.json("id" to subscribe["id"], "ok" to true, "result" to linkedMapOf("subscriptionId" to "s1"))
        eventually("ready") { client.state.value is MeshCoreState.Ready }
        // The device message that follows a join finds the subscription in place.
        ws.device(true, "#roadstr")
        quiet("nothing more to ask")
        assertEquals(1, server.requestCount)
        client.stop()
    }

    @Test
    fun `a server without channels join leaves the channel to the user and retries after a minute`() {
        accept()
        val client = client()
        client.start()
        val ws = socket()
        ws.unknownChannel(approve(ws))
        val join = next()
        assertEquals("channels.join", join["method"])
        ws.json("id" to join["id"], "ok" to false, "error" to linkedMapOf("code" to "unknown_method", "message" to "?"))
        eventually("channel missing") { problem(client) == MeshCoreProblem.LocalApiChannelMissing }
        assertTrue(((client.state.value as MeshCoreState.Failed).retryInMillis ?: 0) >= 60_000)
        quiet("no loop")
        client.stop()
    }

    @Test
    fun `a denied join is not asked again for ten minutes, or until the user flips the switch`() {
        accept()
        val client = client()
        client.start()
        var ws = socket()
        ws.unknownChannel(approve(ws))
        val join = next()
        assertEquals("channels.join", join["method"])
        ws.json("id" to join["id"], "ok" to false, "error" to linkedMapOf("code" to "denied", "message" to "the user said no"))
        eventually("channel missing") { problem(client) == MeshCoreProblem.LocalApiChannelMissing }
        // A channel list without #roadstr changes nothing.
        ws.device(true, "Public")
        quiet("no second subscription or join on this connection")

        // A new connection within the pause: subscribed, refused, and not asked.
        client.stop()
        accept()
        client.start()
        ws = socket()
        ws.unknownChannel(approve(ws))
        eventually("channel missing again") { problem(client) == MeshCoreProblem.LocalApiChannelMissing }
        quiet("no join within ten minutes")

        // Ten minutes later it is asked again.
        client.stop()
        now += 10 * 60_000L
        accept()
        client.start()
        ws = socket()
        ws.unknownChannel(approve(ws))
        val again = next()
        assertEquals("channels.join", again["method"])
        ws.json("id" to again["id"], "ok" to false, "error" to linkedMapOf("code" to "denied", "message" to "no"))
        eventually("denied again") { problem(client) == MeshCoreProblem.LocalApiChannelMissing }

        // Flipping the MeshCore Open switch is an answer too.
        client.stop()
        client.allowPromptsAgain()
        accept()
        client.start()
        ws = socket()
        ws.unknownChannel(approve(ws))
        assertEquals("channels.join", next()["method"])
        client.stop()
    }

    @Test
    fun `a radio that goes away is reported on the open socket, and its return reuses the subscription`() {
        accept()
        val client = client()
        client.start()
        val ws = socket()
        handshake(ws)
        eventually("ready") { client.ready }
        ws.device(false)
        eventually("no radio") { problem(client) == MeshCoreProblem.LocalApiNoRadio }
        assertFalse(client.ready)
        quiet("nothing to ask while the radio is gone")
        assertEquals(1, server.requestCount)
        assertTrue(synchronized(states) { states.count { it == MeshCoreState.Connecting } } <= 1)
        // The server keeps the subscription across the radio's absence: subscribing again would double it.
        ws.device(true, "#roadstr")
        eventually("ready again") { client.ready }
        quiet("no second subscription")
        assertEquals(1, server.requestCount)
        client.stop()
    }

    @Test
    fun `a radio that comes back is subscribed to at once`() {
        accept()
        val client = client()
        client.start()
        val ws = socket()
        ws.unknownChannel(approve(ws, device = linkedMapOf("name" to "", "connected" to false)))
        eventually("no radio") { problem(client) == MeshCoreProblem.LocalApiNoRadio }
        quiet("no join without a radio")
        ws.device(true, "Public", "#roadstr")
        val subscribe = next()
        assertEquals("events.subscribe", subscribe["method"])
        ws.json("id" to subscribe["id"], "ok" to true, "result" to linkedMapOf("subscriptionId" to "s2"))
        eventually("ready") { client.state.value is MeshCoreState.Ready }
        assertEquals("T-Deck", (client.state.value as MeshCoreState.Ready).device.name)
        assertEquals(1, server.requestCount)
        client.stop()
    }

    @Test
    fun `without a subscription the slow retry still subscribes again, on the same socket`() {
        accept()
        val client = client(notApprovedRetryMillis = 200)
        client.start()
        val ws = socket()
        ws.unknownChannel(approve(ws, device = linkedMapOf("name" to "T-Deck", "connected" to false)))
        eventually("no radio") { problem(client) == MeshCoreProblem.LocalApiNoRadio }
        assertEquals("events.subscribe", next()["method"])
        assertEquals(1, server.requestCount)
        client.stop()
    }

    @Test
    fun `without a radio MeshCore Open knows no channel, and the user is told so`() {
        accept()
        val client = client()
        client.start()
        val ws = socket()
        next()
        ws.json("type" to "pairing.challenge", "protocol" to 1, "nonce" to "x", "known" to true)
        next()
        ws.json("type" to "hello", "protocol" to 1, "device" to linkedMapOf("name" to "", "connected" to false))
        val subscribe = next()
        ws.unknownChannel(subscribe)
        eventually("no radio") {
            (client.state.value as? MeshCoreState.Failed)?.problem == MeshCoreProblem.LocalApiNoRadio
        }
        assertTrue(((client.state.value as MeshCoreState.Failed).retryInMillis ?: 0) >= 60_000)
        client.stop()
    }

    @Test
    fun `opening a socket alone does not reset the backoff`() {
        repeat(3) { accept { ws -> ws.close(1000, "not today") } }
        val client = client(retryBaseMillis = 50)
        client.start()
        eventually("three failures") {
            synchronized(states) { states.count { it is MeshCoreState.Failed } } >= 3
        }
        client.stop()
        val waits = synchronized(states) { states.filterIsInstance<MeshCoreState.Failed>().map { it.retryInMillis } }
        assertEquals(listOf(50L, 100L, 200L), waits.take(3))
    }

    @Test
    fun `stopping is quiet, final and never a failure`() {
        accept()
        val client = client()
        client.start()
        socket()
        next()
        client.stop()
        Thread.sleep(300)
        assertEquals(MeshCoreState.Off, client.state.value)
        assertTrue(synchronized(states) { states.none { it is MeshCoreState.Failed } })
        assertFalse(client.ready)
    }
}
