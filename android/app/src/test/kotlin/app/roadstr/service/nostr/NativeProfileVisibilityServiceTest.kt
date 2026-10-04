package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProfileVisibilityServiceTest {
    private val network = FakeRelayNetwork()
    private val relays = listOf("wss://r1.example", "wss://r2.example")
    private var now = 1_800_000_000L

    private fun service(key: String? = TestKeys.PRIVATE_A) = NativeProfileVisibilityService(
        signer = NativeLocalKeySigner { key },
        publisher = NativeRelayPublisher(network, relays),
        fetcher = NativeRelayFetcher(network),
        relays = relays,
        nowSeconds = { now },
    )

    @Test
    fun `no declaration means pseudonymous, never clear`() = runBlocking {
        assertNull(service().fetch(TestKeys.PUBLIC_A))
    }

    @Test
    fun `the newest declaration wins and publishing replaces the previous one`() = runBlocking {
        assertTrue(service().publish(true))
        assertEquals(true, service().fetch(TestKeys.PUBLIC_A))
        now += 100
        assertTrue(service().publish(false))
        assertEquals(false, service().fetch(TestKeys.PUBLIC_A))
        // One replaceable event per relay, not a growing history.
        assertEquals(1, network.stored.getValue(relays[0]).size)
    }

    @Test
    fun `declarations of other people or forged ones do not count`() = runBlocking {
        // B declares itself clear; asking about A must not find it.
        val b = TestKeys.sign(
            RoadstrNostrEvents.profileVisibility(TestKeys.PUBLIC_B, now, true),
            TestKeys.PRIVATE_B,
        )
        relays.forEach { network.stored.getOrPut(it) { mutableListOf() } += b }
        assertNull(service().fetch(TestKeys.PUBLIC_A))
        assertEquals(true, service().fetch(TestKeys.PUBLIC_B))

        val forged = TestKeys.sign(
            RoadstrNostrEvents.profileVisibility(TestKeys.PUBLIC_A, now + 50, true),
        ).toMutableMap().also { it["sig"] = "1".repeat(128) }
        relays.forEach { network.stored.getOrPut(it) { mutableListOf() } += forged }
        assertNull(service().fetch(TestKeys.PUBLIC_A))
    }

    @Test
    fun `logged out publishing does nothing and a bad pubkey is not queried`() = runBlocking {
        assertFalse(service(key = null).publish(true))
        assertNull(service().fetch("not-a-key"))
        assertTrue(network.connections.isEmpty())
    }

    @Test
    fun `one relay being down still gives an answer`() = runBlocking {
        service().publish(true)
        network.down += relays[0]
        assertEquals(true, service().fetch(TestKeys.PUBLIC_A))
    }
}
