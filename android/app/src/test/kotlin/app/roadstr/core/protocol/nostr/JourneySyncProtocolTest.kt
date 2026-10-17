package app.roadstr.core.protocol.nostr

import app.roadstr.core.journey.JourneyMode
import app.roadstr.core.journey.JourneyRecord
import app.roadstr.core.journey.JourneySession
import app.roadstr.core.journey.JourneySessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneySyncProtocolTest {
    private val pubkey = "ab".repeat(32)

    private fun session(id: Int, device: String = "device-0001") = JourneySession(
        id = "session-${id.toString().padStart(4, '0')}",
        deviceId = device,
        mode = JourneyMode.CAR,
        startedAtEpochMillis = id * 10_000L,
        endedAtEpochMillis = id * 10_000L + 5_000L,
        validatedMeters = id * 100L,
        uncertainMeters = 0,
        status = JourneySessionStatus.COMPLETED,
    )

    @Test
    fun `device history is split into bounded deterministic chunks`() {
        val record = JourneyRecord((1..260).map(::session))
        val chunks = JourneySyncProtocol.chunks(record, "device-0001")

        assertEquals(listOf(128, 128, 4), chunks.map { it.sessions.size })
        assertEquals(chunks[0], JourneySyncProtocol.decodeChunk(JourneySyncProtocol.encodeChunk(chunks[0])))
        assertTrue(JourneySyncProtocol.encodeChunk(chunks[0]).contains("device-0001"))
    }

    @Test
    fun `public address hashes the raw device identifier`() {
        val first = JourneySyncProtocol.chunkDTag(pubkey, "device-0001", 0)
        val second = JourneySyncProtocol.chunkDTag(pubkey, "device-0002", 0)

        assertTrue(first.startsWith("${JourneySyncProtocol.NAMESPACE}:"))
        assertFalse(first.contains("device-0001"))
        assertNotEquals(first, second)
    }

    @Test
    fun `manifest commits exact chunk hashes and rejects malformed rows`() {
        val manifest = JourneySyncManifest("device-0001", mapOf(0 to "12".repeat(32), 2 to "34".repeat(32)))
        assertEquals(manifest, JourneySyncProtocol.decodeManifest(JourneySyncProtocol.encodeManifest(manifest)))
        assertNull(JourneySyncProtocol.decodeManifest("${JourneySyncProtocol.NAMESPACE}\nmanifest\ndevice-0001\n0:nope"))
    }

    @Test
    fun `nip78 event exposes namespace but no trip or device facts`() {
        val d = JourneySyncProtocol.chunkDTag(pubkey, "device-0001", 0)
        val event = JourneySyncProtocol.draft(pubkey, 123, d, "ciphertext").toWireMap()

        assertTrue(JourneySyncProtocol.eventIsBound(event, pubkey) { true })
        assertEquals("ciphertext", event["content"])
        assertFalse(event.toString().contains("device-0001"))
        assertFalse(event.toString().contains("session-"))
    }
}
