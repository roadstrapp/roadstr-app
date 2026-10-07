package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.Nip44V2
import app.roadstr.core.protocol.nostr.Nip46
import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.NostrRelayEventMessage
import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import app.roadstr.core.protocol.nostr.NostrRelayWire
import app.roadstr.core.protocol.nostr.NostrSchnorr
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Manual probe, off unless ROADSTR_LIVE_PROBE=1: the bunker client against real public relays, with a
 * throw-away signer played from this same test, so the only unknown left is the relays themselves.
 */
class ZzLiveBunkerProbeTest {
    private fun newKey(): String = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }

    @Test
    fun live() = runBlocking {
        if (System.getenv("ROADSTR_LIVE_PROBE") != "1") return@runBlocking
        val relays = listOf("wss://relay.damus.io", "wss://nos.lol")
        val bunkerKey = newKey()
        val userKey = newKey()
        val clientKey = newKey()
        val bunkerPub = NostrSchnorr.publicKey(bunkerKey)
        val userPub = NostrSchnorr.publicKey(userKey)
        val inner = OkHttpRelayConnector()
        // Everything a relay says about control frames, to see why a request goes missing.
        val connector = NativeRelayConnector { url, events ->
            inner.connect(
                url,
                object : NativeRelayEvents {
                    override fun onOpen() = events.onOpen()
                    override fun onMessage(text: String) {
                        if (!text.startsWith("[\"EVENT\"")) println("FRAME ${url.substringAfter("://")} ${text.take(140)}")
                        events.onMessage(text)
                    }
                    override fun onEnded() {
                        println("ENDED ${url.substringAfter("://")}")
                        events.onEnded()
                    }
                },
            )
        }

        // The signer: listens on the relays and answers what it understands.
        val stop = AtomicBoolean(false)
        val handled = HashSet<String>()
        val responders = relays.map { url ->
            lateinit var socket: NativeRelaySocket
            socket = connector.connect(
                url,
                object : NativeRelayEvents {
                    override fun onOpen() {
                        val filter = linkedMapOf<String, Any?>(
                            "kinds" to listOf(Nip46.KIND),
                            "#p" to listOf(bunkerPub),
                            "since" to System.currentTimeMillis() / 1000 - 10,
                        )
                        socket.send(NostrRelayWire.encode(listOf("REQ", "bunker", filter)))
                    }

                    override fun onMessage(text: String) {
                        if (stop.get()) return
                        val message = NostrRelayMessageDecoder.decode(text).message as? NostrRelayEventMessage ?: return
                        val event = message.event
                        val id = event["id"] as? String ?: return
                        synchronized(handled) { if (!handled.add(id)) return }
                        if (!NativeNostrWire.verify(event)) return
                        val clientPub = event["pubkey"] as String
                        val plain = Nip44V2.decrypt(bunkerKey, clientPub, event["content"] as String)
                        val body = BoundedJsonParser(plain).parse() as Map<*, *>
                        val params = (body["params"] as List<*>).map { it as String }
                        println("BUNKER ${relays.indexOf(url)} got ${body["method"]}")
                        val result: String = when (body["method"]) {
                            "connect" -> "ack"
                            "get_public_key" -> userPub
                            "sign_event" -> {
                                val unsigned = BoundedJsonParser(params.single()).parse() as Map<*, *>
                                val draft = NostrEventDraft(
                                    unsigned["pubkey"] as String,
                                    (unsigned["created_at"] as Number).toLong(),
                                    (unsigned["kind"] as Number).toInt(),
                                    (unsigned["tags"] as List<*>).map { tag -> (tag as List<*>).map { it as String } },
                                    unsigned["content"] as String,
                                )
                                NostrJson.encode(NostrSchnorr.signEvent(draft, userKey))
                            }
                            else -> return
                        }
                        val reply = NostrJson.encode(linkedMapOf("id" to body["id"], "result" to result))
                        val answer = NostrSchnorr.signEvent(
                            Nip46.requestDraft(
                                bunkerPub, clientPub, Nip44V2.encrypt(bunkerKey, clientPub, reply),
                                System.currentTimeMillis() / 1000,
                            ),
                            bunkerKey,
                        )
                        // Any relay will do for the answer: publish it on all of them, once each socket is open.
                        relays.forEach { target ->
                            val opened = java.util.concurrent.CountDownLatch(1)
                            val out = connector.connect(
                                target,
                                object : NativeRelayEvents {
                                    override fun onOpen() = opened.countDown()
                                    override fun onMessage(text: String) =
                                        println("RELAYSAYS ${target.substringAfter("://")} ${text.take(120)}")
                                    override fun onEnded() = opened.countDown()
                                },
                            )
                            Thread {
                                if (opened.await(8, java.util.concurrent.TimeUnit.SECONDS)) {
                                    out.send(NostrRelayWire.encode(NostrRelayWire.publish(answer)))
                                }
                                Thread.sleep(2_000)
                                out.close()
                            }.start()
                        }
                    }

                    override fun onEnded() = Unit
                },
            )
            socket
        }
        Thread.sleep(2_000)

        val client = NativeBunkerClient(connector, clientKey, bunkerPub, relays) { println("DIAG $it") }
        val started = System.currentTimeMillis()
        val connect = client.call("connect", listOf(bunkerPub, "s3cret"), 30_000)
        println("CONNECT ${connect.result} err=${connect.error} in ${System.currentTimeMillis() - started} ms")
        val t2 = System.currentTimeMillis()
        val account = client.call("get_public_key", emptyList(), 30_000)
        println("ACCOUNT ok=${account.ok} matches=${account.result == userPub} err=${account.error} in ${System.currentTimeMillis() - t2} ms")
        val note = NostrEventDraft(userPub, System.currentTimeMillis() / 1000, 1, emptyList(), "live bunker probe")
        val signed = NativeBunkerSigner(client) { userPub }.sign(note)
        println("SIGNED ${signed != null && NativeNostrWire.verify(signed)}")
        stop.set(true)
        responders.forEach(NativeRelaySocket::close)
    }
}
