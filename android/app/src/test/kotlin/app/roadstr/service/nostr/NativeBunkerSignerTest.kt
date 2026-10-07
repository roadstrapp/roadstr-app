package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.Nip44V2
import app.roadstr.core.protocol.nostr.Nip46
import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.NostrRelayWire
import app.roadstr.core.protocol.nostr.NostrSchnorr
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The client and the signer against a relay that plays the remote signer, with real keys and real encryption. */
class NativeBunkerSignerTest {
    private val bunkerKey = "11".repeat(32)
    private val userKey = "22".repeat(32)
    private val clientKey = "33".repeat(32)
    private val bunkerPub = NostrSchnorr.publicKey(bunkerKey)
    private val userPub = NostrSchnorr.publicKey(userKey)

    /** One request the fake signer received, decrypted. */
    private class Request(val id: String, val method: String, val params: List<String>)

    /** Answers a request with zero or more replies; each is a Triple(signing key, id to answer, body). */
    private inner class FakeRelay(
        val answer: (Request) -> List<Triple<String, String, Map<String, Any?>>>,
    ) : NativeRelayConnector {
        val received = mutableListOf<Request>()

        override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
            events.onOpen()
            return object : NativeRelaySocket {
                var subscription: String? = null

                override fun send(text: String): Boolean {
                    val frame = BoundedJsonParser(text).parse() as List<*>
                    when (frame[0]) {
                        "REQ" -> subscription = frame[1] as String
                        "EVENT" -> {
                            @Suppress("UNCHECKED_CAST")
                            val event = frame[1] as Map<String, Any?>
                            val clientPub = event["pubkey"] as String
                            val plain = Nip44V2.decrypt(bunkerKey, clientPub, event["content"] as String)
                            val body = BoundedJsonParser(plain).parse() as Map<*, *>
                            val request = Request(
                                body["id"] as String,
                                body["method"] as String,
                                (body["params"] as List<*>).map { it as String },
                            )
                            received += request
                            for ((signingKey, id, reply) in answer(request)) {
                                val json = NostrJson.encode(linkedMapOf<String, Any?>("id" to id) + reply)
                                val signerPub = NostrSchnorr.publicKey(signingKey)
                                val draft = NostrEventDraft(
                                    pubkey = signerPub,
                                    createdAt = System.currentTimeMillis() / 1000,
                                    kind = Nip46.KIND,
                                    tags = listOf(listOf("p", clientPub)),
                                    content = Nip44V2.encrypt(signingKey, clientPub, json),
                                )
                                val signed = NostrSchnorr.signEvent(draft, signingKey)
                                events.onMessage(NostrRelayWire.encode(listOf("EVENT", subscription, signed)))
                            }
                        }
                    }
                    return true
                }

                override fun close() = Unit
            }
        }
    }

    private fun ok(request: Request, result: String) =
        listOf(Triple(bunkerKey, request.id, mapOf<String, Any?>("result" to result)))

    private fun well(request: Request): List<Triple<String, String, Map<String, Any?>>> = when (request.method) {
        "connect" -> ok(request, "ack")
        "get_public_key" -> ok(request, userPub)
        "sign_event" -> {
            val unsigned = BoundedJsonParser(request.params.single()).parse() as Map<*, *>
            val draft = NostrEventDraft(
                pubkey = unsigned["pubkey"] as String,
                createdAt = (unsigned["created_at"] as Number).toLong(),
                kind = (unsigned["kind"] as Number).toInt(),
                tags = (unsigned["tags"] as List<*>).map { tag -> (tag as List<*>).map { it as String } },
                content = unsigned["content"] as String,
            )
            ok(request, NostrJson.encode(NostrSchnorr.signEvent(draft, userKey)))
        }
        "nip44_encrypt" -> ok(request, Nip44V2.encrypt(userKey, request.params[0], request.params[1]))
        "nip44_decrypt" -> ok(request, Nip44V2.decrypt(userKey, request.params[0], request.params[1]))
        else -> listOf(Triple(bunkerKey, request.id, mapOf<String, Any?>("result" to null, "error" to "unknown")))
    }

    private fun client(relay: NativeRelayConnector, onAuth: (String) -> Unit = {}) = NativeBunkerClient(
        connector = relay,
        clientKeyHex = clientKey,
        remotePubkeyHex = bunkerPub,
        relays = listOf("wss://relay.example"),
        onAuthUrl = onAuth,
    )

    private fun signer(client: NativeBunkerClient) = NativeBunkerSigner(client) { userPub }

    private fun note() = NostrEventDraft(userPub, 1_700_000_000L, 1, listOf(listOf("t", "x")), "hello")

    @Test
    fun `the signer answers get_public_key and connect`() = runBlocking {
        val relay = FakeRelay(::well)
        val c = client(relay)

        val connect = c.call("connect", listOf(bunkerPub, "secret"))
        val account = c.call("get_public_key", emptyList())

        assertTrue(connect.ok)
        assertEquals("ack", connect.result)
        assertEquals(userPub, account.result)
        assertEquals(listOf("connect", "get_public_key"), relay.received.map { it.method })
        assertEquals(listOf(bunkerPub, "secret"), relay.received.first().params)
    }

    @Test
    fun `a signed event comes back valid and is the one that was asked for`() = runBlocking {
        val event = signer(client(FakeRelay(::well))).sign(note())

        assertNotNull(event)
        assertEquals(userPub, event!!["pubkey"])
        assertEquals("hello", event["content"])
        assertTrue(NativeNostrWire.verify(event))
    }

    @Test
    fun `an event the signer altered is refused`() = runBlocking {
        val tampered = FakeRelay { request ->
            val honest = well(request)
            if (request.method != "sign_event") return@FakeRelay honest
            val forged = NostrSchnorr.signEvent(
                NostrEventDraft(userPub, 1_700_000_000L, 1, listOf(listOf("t", "x")), "something else"),
                userKey,
            )
            ok(request, NostrJson.encode(forged))
        }

        assertNull(signer(client(tampered)).sign(note()))
    }

    @Test
    fun `a draft for another account is not even sent`() = runBlocking {
        val relay = FakeRelay(::well)
        val other = NostrEventDraft("b".repeat(64), 1L, 1, emptyList(), "x")

        assertNull(signer(client(relay)).sign(other))
        assertTrue(relay.received.isEmpty())
    }

    @Test
    fun `NIP-44 goes through the signer and round-trips`() = runBlocking {
        val s = signer(client(FakeRelay(::well)))
        val peerPub = NostrSchnorr.publicKey("44".repeat(32))

        val sealed = s.nip44Encrypt(peerPub, "secret text")
        assertNotNull(sealed)
        assertEquals("secret text", Nip44V2.decrypt("44".repeat(32), userPub, sealed!!))
        assertEquals("secret text", s.nip44Decrypt(peerPub, Nip44V2.encrypt("44".repeat(32), userPub, "secret text")))
    }

    @Test
    fun `an answer to another request, or from someone else, is ignored`() = runBlocking {
        val impostorKey = "55".repeat(32)
        val relay = FakeRelay { request ->
            listOf(
                Triple(bunkerKey, "not-this-one", mapOf<String, Any?>("result" to "wrong id")),
                Triple(impostorKey, request.id, mapOf<String, Any?>("result" to "from an impostor")),
            )
        }

        val reply = client(relay).call("ping", emptyList(), timeoutMillis = 400)

        assertNull(reply.result)
        assertFalse(reply.ok)
    }

    @Test
    fun `an approval request opens the address and the real answer still counts`() = runBlocking {
        val opened = mutableListOf<String>()
        val relay = FakeRelay { request ->
            listOf(
                Triple(bunkerKey, request.id, mapOf<String, Any?>("result" to "auth_url", "error" to "https://signer.example/ok")),
                Triple(bunkerKey, request.id, mapOf<String, Any?>("result" to "ack")),
            )
        }

        val reply = client(relay, onAuth = { opened += it }).call("connect", listOf(bunkerPub))

        assertEquals(listOf("https://signer.example/ok"), opened)
        assertEquals("ack", reply.result)
    }

    @Test
    fun `a refusal is reported as an error and a silent signer as no answer`() = runBlocking {
        val refusing = FakeRelay { request ->
            listOf(Triple(bunkerKey, request.id, mapOf<String, Any?>("result" to null, "error" to "denied")))
        }
        val refused = client(refusing).call("sign_event", listOf("{}"))
        assertEquals("denied", refused.error)
        assertFalse(refused.ok)

        val silent = FakeRelay { emptyList() }
        val none = client(silent).call("ping", emptyList(), timeoutMillis = 300)
        assertNull(none.result)
        assertNull(none.error)
    }
}
