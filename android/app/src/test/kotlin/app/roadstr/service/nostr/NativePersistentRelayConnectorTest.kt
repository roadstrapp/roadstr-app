package app.roadstr.service.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePersistentRelayConnectorTest {
    @Test
    fun `logical clients reuse one physical relay and stop receiving after close`() {
        val physical = FakeConnector()
        val persistent = NativePersistentRelayConnector(physical)
        val first = RecordingEvents()
        val firstSocket = persistent.connect("wss://relay.example", first)
        physical.open()
        assertEquals(1, first.opens)
        assertTrue(firstSocket.send("one"))

        firstSocket.close()
        val second = RecordingEvents()
        val secondSocket = persistent.connect("wss://relay.example", second)
        assertEquals(1, physical.connects)
        assertEquals(1, second.opens)
        physical.message("frame")
        assertTrue(first.messages.isEmpty())
        assertEquals(listOf("frame"), second.messages)
        assertTrue(secondSocket.send("two"))
        assertEquals(listOf("one", "two"), physical.sent)

        persistent.close()
        assertEquals(1, physical.closes)
        assertFalse(secondSocket.send("three"))
    }

    @Test
    fun `ended physical relay is replaced on the next logical connection`() {
        val physical = FakeConnector()
        val persistent = NativePersistentRelayConnector(physical)
        val first = RecordingEvents()
        persistent.connect("wss://relay.example", first)
        physical.open()
        physical.end()
        assertEquals(1, first.ends)

        persistent.connect("wss://relay.example", RecordingEvents())
        assertEquals(2, physical.connects)
    }

    private class RecordingEvents : NativeRelayEvents {
        var opens = 0
        var ends = 0
        val messages = mutableListOf<String>()
        override fun onOpen() { opens++ }
        override fun onMessage(text: String) { messages += text }
        override fun onEnded() { ends++ }
    }

    private class FakeConnector : NativeRelayConnector {
        var connects = 0
        var closes = 0
        val sent = mutableListOf<String>()
        private var events: NativeRelayEvents? = null

        override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
            connects++
            this.events = events
            return object : NativeRelaySocket {
                override fun send(text: String): Boolean = sent.add(text)
                override fun close() { closes++ }
            }
        }

        fun open() = events!!.onOpen()
        fun message(text: String) = events!!.onMessage(text)
        fun end() = events!!.onEnded()
    }
}
