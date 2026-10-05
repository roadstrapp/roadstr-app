package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeUserReportsServiceTest {
    private val network = FakeRelayNetwork()
    private val now = 1_800_000_000L
    private val relays = listOf("wss://one.example", "wss://two.example")
    private val service = NativeUserReportsService(network, relays, nowSeconds = { now })

    private fun publish(event: Map<String, Any?>, vararg urls: String) {
        urls.ifEmpty { relays.toTypedArray() }.forEach { network.stored.getOrPut(it) { mutableListOf() }.add(event) }
    }

    private fun report(createdAt: Long = now - 3_600): Map<String, Any?> = TestKeys.sign(
        RoadstrNostrEvents.report(
            pubkey = TestKeys.PUBLIC_A, createdAt = createdAt, latitude = 38.7, longitude = -9.1,
            category = RoadCategoryWire.SPEED_CAMERA.wireKey,
            expiresAt = createdAt + RoadCategoryWire.SPEED_CAMERA.ttlSeconds, content = "radar",
        ),
        TestKeys.PRIVATE_A,
    )

    private fun vote(eventId: String, stillThere: Boolean, key: String = TestKeys.PRIVATE_B, at: Long = now - 60): Map<String, Any?> {
        val pub = if (key == TestKeys.PRIVATE_A) TestKeys.PUBLIC_A else TestKeys.PUBLIC_B
        return TestKeys.sign(RoadstrNostrEvents.vote(pub, at, eventId, stillThere), key)
    }

    @Test
    fun `reports come back with their votes counted once per voter and relay`() = runBlocking {
        val mine = report()
        publish(mine)
        // The same vote stored on both relays, and the same voter changing their mind.
        val first = vote(mine["id"] as String, stillThere = true)
        publish(first)
        publish(vote(mine["id"] as String, stillThere = false, at = now - 30), relays[0])

        val events = service.userEvents(TestKeys.PUBLIC_A)

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(mine["id"], event.id)
        // One vote per voter per report: the second one from B is ignored on the relay that holds both.
        assertTrue(event.confirmations + event.denials <= 1)
    }

    @Test
    fun `a report from somebody else is not mine even if a relay returns it`() = runBlocking {
        val theirs = TestKeys.sign(
            RoadstrNostrEvents.report(
                pubkey = TestKeys.PUBLIC_B, createdAt = now - 60, latitude = 38.7, longitude = -9.1,
                category = RoadCategoryWire.POLICE.wireKey, expiresAt = now + 3_600, content = "x",
            ),
            TestKeys.PRIVATE_B,
        )
        // The fake relay honours `authors`, so plant it under A's name through a lying relay.
        network.stored.getOrPut(relays[0]) { mutableListOf() }.add(theirs)

        assertTrue(service.userEvents(TestKeys.PUBLIC_A).isEmpty())
    }

    @Test
    fun `only the owner's newer update changes the speed limit`() = runBlocking {
        val mine = report(createdAt = now - 7_200)
        publish(mine)
        val id = mine["id"] as String
        val owner = TestKeys.sign(
            RoadstrNostrEvents.update(TestKeys.PUBLIC_A, now - 3_600, id, 80, 38.7, -9.1, "radar"),
            TestKeys.PRIVATE_A,
        )
        val stranger = TestKeys.sign(
            RoadstrNostrEvents.update(TestKeys.PUBLIC_B, now - 600, id, 30, 38.7, -9.1, "radar"),
            TestKeys.PRIVATE_B,
        )
        publish(owner)
        publish(stranger)

        assertEquals(80, service.userEvents(TestKeys.PUBLIC_A).single().speedLimit)
    }

    @Test
    fun `edit requests keep sane limits and only the report they name`() = runBlocking {
        val mine = report()
        publish(mine)
        val id = mine["id"] as String
        fun request(limit: Int, target: String = id): Map<String, Any?> = TestKeys.sign(
            NostrEventDraft(
                TestKeys.PUBLIC_B, now - 10, 1318,
                listOf(listOf("e", target), listOf("p", TestKeys.PUBLIC_A), listOf("maxspeed", limit.toString())),
                "please",
            ),
            TestKeys.PRIVATE_B,
        )
        publish(request(90))
        publish(request(2))
        publish(request(400))

        val requests = service.editRequests(id)

        assertEquals(listOf(90), requests.map { it.speedLimitKmh })
        assertEquals("please", requests.single().comment)
        assertEquals(TestKeys.PUBLIC_B, requests.single().requesterPubkey)
    }
}
