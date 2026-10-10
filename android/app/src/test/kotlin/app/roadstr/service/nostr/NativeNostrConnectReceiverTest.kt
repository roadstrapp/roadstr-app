package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.protocol.nostr.Nip44V2
import app.roadstr.core.protocol.nostr.Nip46
import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrRelayWire
import app.roadstr.core.protocol.nostr.NostrSchnorr
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNostrConnectReceiverTest {
    @Test
    fun `client initiated connection accepts signed response carrying its one time secret`() = runBlocking {
        val clientKey = "1".padStart(64, '0')
        val remoteKey = "2".padStart(64, '0')
        val clientPubkey = NostrSchnorr.publicKey(clientKey)
        val remotePubkey = NostrSchnorr.publicKey(remoteKey)
        val relay = FakeRelay()
        val receiver = NativeNostrConnectReceiver(
            connector = relay,
            clientKeyHex = clientKey,
            relays = listOf("wss://relay.example"),
            nowSeconds = { 1_000L },
        )

        val signer = receiver.awaitSigner("secret-123", timeoutMillis = 1_000L) {
            val request = BoundedJsonParser(relay.sent.first()).parse() as List<*>
            val subscription = request[1] as String
            val encrypted = Nip44V2.encrypt(
                remoteKey,
                clientPubkey,
                """{"id":"connect","result":"secret-123"}""",
            )
            val event = NostrSchnorr.signEvent(
                NostrEventDraft(
                    pubkey = remotePubkey,
                    createdAt = 1_000L,
                    kind = Nip46.KIND,
                    tags = listOf(listOf("p", clientPubkey)),
                    content = encrypted,
                ),
                remoteKey,
            )
            relay.events.onMessage(NostrRelayWire.encode(listOf("EVENT", subscription, event)))
        }

        assertEquals(remotePubkey, signer)
        assertTrue(relay.sent.any { it.startsWith("[\"CLOSE\"") })
    }

    private class FakeRelay : NativeRelayConnector {
        lateinit var events: NativeRelayEvents
        val sent = mutableListOf<String>()

        override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
            this.events = events
            events.onOpen()
            return object : NativeRelaySocket {
                override fun send(text: String): Boolean = sent.add(text)
                override fun close() = Unit
            }
        }
    }
}
