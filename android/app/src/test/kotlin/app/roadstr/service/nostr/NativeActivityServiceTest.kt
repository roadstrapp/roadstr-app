package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import app.roadstr.feature.activity.NativeActivityNotificationType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeActivityServiceTest {
    private val network = FakeRelayNetwork()
    private var now = 1_800_000_000L
    private val relays = listOf("wss://one.example")
    private val store = MemoryStore()
    private val reports = NativeUserReportsService(network, relays, nowSeconds = { now })
    private val zaps = NativeZapService(network, relays = relays, nowSeconds = { now })
    private val service = NativeActivityService(zaps, reports, network, store, relays, nowSeconds = { now })

    private class MemoryStore : NativeActivityStore {
        val values = mutableMapOf<String, String>()
        override fun readInbox(pubkey: String) = values["inbox_$pubkey"]
        override fun writeInbox(pubkey: String, normalized: String) { values["inbox_$pubkey"] = normalized }
        override fun readCursor(key: String) = values[key]
        override fun writeCursor(key: String, value: String) { values[key] = value }
    }

    private fun publish(event: Map<String, Any?>) {
        network.stored.getOrPut(relays.single()) { mutableListOf() }.add(event)
    }

    private fun myReport(): Map<String, Any?> = TestKeys.sign(
        RoadstrNostrEvents.report(
            pubkey = TestKeys.PUBLIC_A, createdAt = now - 3_600, latitude = 38.7, longitude = -9.1,
            category = RoadCategoryWire.POLICE.wireKey, expiresAt = now + 3_600, content = "police",
        ),
        TestKeys.PRIVATE_A,
    )

    private fun vote(eventId: String, stillThere: Boolean, key: String, at: Long): Map<String, Any?> {
        val pub = if (key == TestKeys.PRIVATE_A) TestKeys.PUBLIC_A else TestKeys.PUBLIC_B
        return TestKeys.sign(RoadstrNostrEvents.vote(pub, at, eventId, stillThere), key)
    }

    @Test
    fun `a vote on my report after the first check becomes one notification, once`() = runBlocking {
        val mine = myReport()
        publish(mine)
        // First activation seeds the cursor at "now": nothing old arrives.
        publish(vote(mine["id"] as String, true, TestKeys.PRIVATE_B, now - 5_000))
        assertTrue(service.poll(TestKeys.PUBLIC_A).isEmpty())

        now += 100
        publish(vote(mine["id"] as String, false, TestKeys.PRIVATE_B, now - 10))
        val fresh = service.poll(TestKeys.PUBLIC_A)

        val notification = fresh.single()
        assertEquals(NativeActivityNotificationType.Denied, notification.type)
        assertEquals(RoadCategoryWire.POLICE, notification.category)
        assertNotNull(store.readCursor("activity_confirmation_cursor_${TestKeys.PUBLIC_A}"))
        // The cursor moved on to this vote's own second, so at most that vote
        // comes back; the inbox ignores a notification it already holds.
        assertTrue(service.poll(TestKeys.PUBLIC_A).all { it.id == notification.id })
    }

    @Test
    fun `confirming your own report is not an event for yourself`() = runBlocking {
        val mine = myReport()
        publish(mine)
        service.poll(TestKeys.PUBLIC_A)
        now += 100
        publish(vote(mine["id"] as String, true, TestKeys.PRIVATE_A, now - 10))

        assertTrue(service.poll(TestKeys.PUBLIC_A).isEmpty())
    }

    @Test
    fun `an account with no reports has nothing to hear about`() = runBlocking {
        assertTrue(service.poll(TestKeys.PUBLIC_B).isEmpty())
    }

    @Test
    fun `a malformed key never throws`() = runBlocking {
        assertTrue(service.poll("not-a-key").isEmpty())
    }
}
