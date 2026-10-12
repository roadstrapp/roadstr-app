package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.RoadCategoryWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadEventLedgerTest {
    private fun id(n: Int) = n.toString(16).padStart(64, '0')

    @Test
    fun `the same event seen on two transports is one event`() {
        val ledger = RoadEventLedger()
        val first = ledger.observe(id(1), RoadEventTransport.RELAY)
        assertTrue(first.firstSighting)
        assertTrue(first.newOnThisTransport)

        val radio = ledger.observe(id(1), RoadEventTransport.MESH)
        assertFalse(radio.firstSighting)
        assertTrue(radio.newOnThisTransport)
        assertEquals(setOf(RoadEventTransport.RELAY), radio.alsoSeenOn)

        val again = ledger.observe(id(1), RoadEventTransport.MESH)
        assertFalse(again.newOnThisTransport)
        assertEquals(1, ledger.size())
    }

    @Test
    fun `a bridge only offers an event to a transport that has not seen it`() {
        val ledger = RoadEventLedger()
        ledger.observe(id(1), RoadEventTransport.RELAY)
        assertTrue(ledger.shouldForward(id(1), RoadEventTransport.MESH))
        assertFalse(ledger.shouldForward(id(1), RoadEventTransport.RELAY))

        ledger.markForwarded(id(1), RoadEventTransport.MESH)
        assertFalse(ledger.shouldForward(id(1), RoadEventTransport.MESH))
    }

    @Test
    fun `relay to radio to relay does not loop`() {
        val ledger = RoadEventLedger()
        // The radio event arrives, is queued for the relays and marked forwarded...
        ledger.observe(id(2), RoadEventTransport.MESH)
        assertTrue(ledger.shouldForward(id(2), RoadEventTransport.RELAY))
        ledger.markForwarded(id(2), RoadEventTransport.RELAY)
        // ...the relay then echoes it back to us: it must not go back out to the radio.
        ledger.observe(id(2), RoadEventTransport.RELAY)
        assertFalse(ledger.shouldForward(id(2), RoadEventTransport.MESH))
        assertFalse(ledger.shouldForward(id(2), RoadEventTransport.RELAY))
    }

    @Test
    fun `the ledger is bounded and forgets the least recently used event`() {
        val ledger = RoadEventLedger(capacity = 4)
        for (n in 1..4) ledger.observe(id(n), RoadEventTransport.RELAY)
        ledger.observe(id(1), RoadEventTransport.RELAY) // touch: 2 is now the oldest
        ledger.observe(id(5), RoadEventTransport.RELAY)
        assertEquals(4, ledger.size())
        assertFalse(ledger.seenOn(id(2), RoadEventTransport.RELAY))
        assertTrue(ledger.seenOn(id(1), RoadEventTransport.RELAY))
    }

    @Test
    fun `a snapshot restores what was seen and forwarded, and ignores garbage`() {
        val ledger = RoadEventLedger()
        ledger.observe(id(1), RoadEventTransport.MESH)
        ledger.markForwarded(id(1), RoadEventTransport.RELAY)
        val lines = ledger.snapshot() + listOf("nonsense", "${id(9)}:99:0", "${id(8)}:1")

        val restored = RoadEventLedger()
        restored.restore(lines)
        assertEquals(1, restored.size())
        assertTrue(restored.seenOn(id(1), RoadEventTransport.MESH))
        assertTrue(restored.wasForwarded(id(1), RoadEventTransport.RELAY))
    }

    @Test
    fun `only what touched the radio is worth keeping across a restart`() {
        val ledger = RoadEventLedger()
        ledger.observe(id(1), RoadEventTransport.RELAY)                  // relay only: relearned from the relay
        ledger.observe(id(2), RoadEventTransport.MESH)                   // heard on the radio
        ledger.observe(id(3), RoadEventTransport.RELAY)
        ledger.markForwarded(id(3), RoadEventTransport.MESH)             // put on the radio by us
        ledger.observe(id(4), RoadEventTransport.MESH)
        ledger.markForwarded(id(4), RoadEventTransport.RELAY)            // bridged to the relays

        val kept = ledger.snapshotRadioInvolved(10)
        assertEquals(listOf(id(2), id(3), id(4)), kept.map { it.substringBefore(':') })

        val restarted = RoadEventLedger()
        restarted.restore(kept)
        // After the restart the relays resend id 3: it must not go back on the air.
        assertTrue(restarted.wasForwarded(id(3), RoadEventTransport.MESH))
        assertFalse(restarted.shouldForward(id(3), RoadEventTransport.MESH))
        assertFalse("heard on the radio already", restarted.shouldForward(id(2), RoadEventTransport.MESH))
        assertEquals(listOf(id(4)), ledger.snapshotRadioInvolved(1).map { it.substringBefore(':') })
    }

    @Test
    fun `the rate limiter never lets more than its quota through a window`() {
        val limiter = BridgeRateLimiter(maxEvents = 3, windowSeconds = 60)
        assertTrue(limiter.tryAcquire(0))
        assertTrue(limiter.tryAcquire(10))
        assertTrue(limiter.tryAcquire(20))
        assertFalse(limiter.tryAcquire(30))
        assertFalse(limiter.tryAcquire(59))
        assertTrue(limiter.tryAcquire(60))
        assertFalse(limiter.tryAcquire(61))
    }

    @Test
    fun `a refusal by one limiter does not spend the quota of the others`() {
        val total = BridgeRateLimiter(maxEvents = 2, windowSeconds = 60)
        val cooldown = BridgeRateLimiter(maxEvents = 1, windowSeconds = 20)
        assertTrue(tryAcquireAll(0, total, cooldown))
        repeat(5) { assertFalse(tryAcquireAll(5, total, cooldown)) }   // blocked by the cooldown each time
        assertTrue("the total still has its second unit", tryAcquireAll(21, total, cooldown))
        assertFalse(tryAcquireAll(42, total, cooldown))
    }

    @Test
    fun `a zero quota blocks everything`() {
        assertFalse(BridgeRateLimiter(0, 60).tryAcquire(0))
    }

    @Test
    fun `equivalence needs the same key, category, time and place`() {
        val base = IncidentKey("a".repeat(64), RoadCategoryWire.POLICE, 1_700_000_000, 48.856614, 2.3522219)
        assertTrue(RoadEventEquivalence.sameIncident(base, base.copy(createdAt = base.createdAt + 300, latitude = 48.8566145)))
        assertFalse(RoadEventEquivalence.sameIncident(base, base.copy(pubkey = "b".repeat(64))))
        assertFalse(RoadEventEquivalence.sameIncident(base, base.copy(category = RoadCategoryWire.HAZARD)))
        assertFalse(RoadEventEquivalence.sameIncident(base, base.copy(createdAt = base.createdAt + 301)))
        assertFalse(RoadEventEquivalence.sameIncident(base, base.copy(longitude = base.longitude + 0.00001)))
    }
}
