package app.roadstr.service.nostr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRelayPublisherTest {
    private val event: Map<String, Any?> = mapOf("id" to "a".repeat(64), "kind" to 1)

    private fun connector(
        openBeforeReturn: Boolean,
        reply: (String) -> String?,
    ) = object : NativeRelayConnector {
        val sent = mutableListOf<Pair<String, String>>()

        override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
            val socket = object : NativeRelaySocket {
                override fun send(text: String): Boolean {
                    sent += url to text
                    reply(url)?.let(events::onMessage)
                    return true
                }

                override fun close() {}
            }
            if (openBeforeReturn) {
                events.onOpen()
            } else {
                // Opens only after connect() has handed the socket back.
                Thread { Thread.sleep(20); events.onOpen() }.start()
            }
            return socket
        }
    }

    private val ok = "[\"OK\",\"${"a".repeat(64)}\",true,\"\"]"

    @Test
    fun `the frame is sent exactly once whichever of open and return comes first`() = runBlocking {
        for (early in listOf(true, false)) {
            val relay = connector(early) { ok }
            val published = NativeRelayPublisher(relay, listOf("wss://r1"), 2_000).publish(event)
            assertTrue("early=$early", published)
            assertEquals("early=$early", 1, relay.sent.size)
        }
    }

    @Test
    fun `one accepting relay is enough and all relays are tried`() = runBlocking {
        val relay = connector(true) { url -> if (url == "wss://good") ok else "[\"OK\",\"${"a".repeat(64)}\",false,\"blocked\"]" }
        val published = NativeRelayPublisher(relay, listOf("wss://bad", "wss://good"), 2_000).publish(event)
        assertTrue(published)
        assertEquals(2, relay.sent.size)
    }

    @Test
    fun `an OK for another event does not count`() = runBlocking {
        val other = "[\"OK\",\"${"b".repeat(64)}\",true,\"\"]"
        val relay = connector(true) { other }
        assertFalse(NativeRelayPublisher(relay, listOf("wss://r1"), 300).publish(event))
    }

    @Test
    fun `silence times out and a relay that refuses the connection counts as no`() = runBlocking {
        val silent = connector(true) { null }
        assertFalse(NativeRelayPublisher(silent, listOf("wss://r1"), 200).publish(event))
        val broken = object : NativeRelayConnector {
            override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket =
                throw IllegalStateException("refused")
        }
        assertFalse(NativeRelayPublisher(broken, listOf("wss://r1"), 200).publish(event))
    }

    @Test
    fun `an event without an id or no relays is never sent`() = runBlocking {
        val relay = connector(true) { ok }
        assertFalse(NativeRelayPublisher(relay, listOf("wss://r1")).publish(mapOf("kind" to 1)))
        assertFalse(NativeRelayPublisher(relay, emptyList()).publish(event))
        assertTrue(relay.sent.isEmpty())
    }
}
