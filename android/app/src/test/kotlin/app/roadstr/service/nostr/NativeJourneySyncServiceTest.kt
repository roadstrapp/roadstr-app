package app.roadstr.service.nostr

import app.roadstr.core.journey.JourneyMode
import app.roadstr.core.journey.JourneyRecord
import app.roadstr.core.journey.JourneySession
import app.roadstr.core.journey.JourneySessionStatus
import app.roadstr.core.journey.JourneySyncResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeJourneySyncServiceTest {
    private val network = FakeRelayNetwork()

    private class Store : NativeJourneySyncStore {
        override var lastCreatedAt: Long? = null
        override var lastSuccessfulSyncMillis: Long? = null
        private val fingerprints = HashMap<String, String>()
        override fun publishedFingerprint(address: String) = fingerprints[address]
        override fun setPublishedFingerprint(address: String, fingerprint: String) { fingerprints[address] = fingerprint }
    }

    private fun session(id: String, device: String, start: Long) = JourneySession(
        id = id,
        deviceId = device,
        mode = JourneyMode.CAR,
        startedAtEpochMillis = start,
        endedAtEpochMillis = start + 10_000,
        validatedMeters = 2_000,
        uncertainMeters = 0,
        status = JourneySessionStatus.COMPLETED,
    )

    private fun service(device: String, store: Store = Store()) = NativeJourneySyncService(
        signer = NativeLocalKeySigner { TestKeys.PRIVATE_A },
        connector = network,
        store = store,
        deviceId = device,
        customRelay = { null },
        nowSeconds = { 1_900_000_000L },
    )

    @Test
    fun `two devices converge without overwriting each other`() = runBlocking {
        val a = session("session-a001", "device-0001", 1_000)
        val b = session("session-b001", "device-0002", 20_000)
        assertTrue(service("device-0001").synchronize(JourneyRecord(listOf(a))) is JourneySyncResult.Ok)
        assertTrue(service("device-0002").synchronize(JourneyRecord(listOf(b))) is JourneySyncResult.Ok)

        val result = service("device-0001", Store()).synchronize(JourneyRecord(listOf(a))) as JourneySyncResult.Ok
        assertEquals(listOf("session-b001"), result.record.sessions.map { it.id })
        assertEquals(setOf("device-0002"), result.completeDeviceIds)
    }

    @Test
    fun `duplicate relay copies and repeated sync do not create sessions`() = runBlocking {
        val store = Store()
        val service = service("device-0001", store)
        val record = JourneyRecord(listOf(session("session-a001", "device-0001", 1_000)))
        service.synchronize(record)
        val eventsBefore = network.stored.values.sumOf { it.size }
        val result = service.synchronize(record) as JourneySyncResult.Ok

        assertTrue(result.record.sessions.isEmpty())
        assertEquals(eventsBefore, network.stored.values.sumOf { it.size })
    }

    @Test
    fun `signer without nip44 never falls back to plaintext`() = runBlocking {
        val delegate = NativeLocalKeySigner { TestKeys.PRIVATE_A }
        val noCrypto = object : NativeNostrSigner by delegate {
            override suspend fun nip44Encrypt(peerPubkeyHex: String, plaintext: String): String? = null
        }
        val service = NativeJourneySyncService(noCrypto, network, Store(), "device-0001", { null })
        val result = service.synchronize(JourneyRecord(listOf(session("session-a001", "device-0001", 1_000))))

        assertEquals(JourneySyncResult.CryptoUnavailable, result)
        assertEquals(0, network.stored.values.sumOf { it.size })
        assertTrue(service.state.value.localChangesPending)
    }

    @Test
    fun `overlapping sessions remain distinct and are reported`() = runBlocking {
        val a = session("session-a001", "device-0001", 1_000)
        val b = session("session-b001", "device-0002", 5_000)
        service("device-0002").synchronize(JourneyRecord(listOf(b)))
        val result = service("device-0001").synchronize(JourneyRecord(listOf(a))) as JourneySyncResult.Ok

        assertEquals(1, result.potentialOverlaps)
        assertFalse(result.record.sessions.isEmpty())
    }
}
