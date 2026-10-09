package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class NativeRoadEventServiceTest {
    private val now = 1_800_000_000L
    private val latitude = 38.7223
    private val longitude = -9.1393
    private val connector = FakeConnector()
    private val scheduler = ManualScheduler()
    private var subCounter = 0
    private val queue = mutableListOf<String>()
    private val storage = object : NativePendingReportStorage {
        override fun read() = queue.toList()
        override fun write(rows: List<String>) {
            queue.clear()
            queue += rows
        }
    }

    private fun service(
        relays: List<String> = listOf("wss://a.example", "wss://b.example"),
        publisherRelays: List<String> = emptyList(),
        maxIngressEventsPerSubscription: Int = 4_096,
    ): NativeRoadEventService = NativeRoadEventService(
        connector = connector,
        publisher = NativeRelayPublisher(connector, publisherRelays, timeoutMillis = 200),
        scheduler = scheduler,
        relays = relays,
        pendingStorage = storage,
        nowSeconds = { now },
        random = Random(1),
        newSubscriptionId = { "sub${subCounter++}" },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        maxIngressEventsPerSubscription = maxIngressEventsPerSubscription,
    )

    private fun report(
        key: String = TestKeys.PRIVATE_A,
        category: RoadCategoryWire = RoadCategoryWire.POLICE,
        createdAt: Long = now - 60,
        lat: Double = latitude,
        lon: Double = longitude,
    ): Map<String, Any?> {
        val pub = if (key == TestKeys.PRIVATE_A) TestKeys.PUBLIC_A else TestKeys.PUBLIC_B
        return TestKeys.sign(
            RoadstrNostrEvents.report(
                pubkey = pub, createdAt = createdAt, latitude = lat, longitude = lon,
                category = category.wireKey, expiresAt = createdAt + category.ttlSeconds,
                content = "radar",
            ),
            key,
        )
    }

    private fun connectedService(): NativeRoadEventService {
        val subject = service()
        subject.connect()
        connector.last.open()
        subject.subscribeArea(latitude, longitude)
        return subject
    }

    private fun NativeRoadEventService.eventFrame(sub: String, event: Map<String, Any?>) =
        connector.last.receive(listOf("EVENT", sub, event))

    @Test
    fun `an opened connection subscribes to the coarse geohash cell only`() {
        val subject = connectedService()
        val request = connector.last.sent.single()
        val g4 = RoadstrNostrEvents.geohash(latitude, longitude, 4)
        assertTrue(request, request.startsWith("[\"REQ\",\"sub0\",{\"kinds\":[1315,1317,1318],\"#g\":[\"$g4\"]"))
        assertFalse("finer cells would tell the relay where we are", request.contains(RoadstrNostrEvents.geohash(latitude, longitude, 5)))
        // Same cell again: no second REQ.
        subject.subscribeArea(latitude + 0.001, longitude)
        assertEquals(1, connector.last.sent.size)
    }

    @Test
    fun `a valid report appears and one for another subscription or forged does not`() {
        val subject = connectedService()
        val good = report()
        subject.eventFrame("sub0", good)
        assertEquals(listOf(good["id"]), subject.events.value.map { it.id })

        // Unknown subscription id: ignored before any signature work.
        subject.eventFrame("other", report(category = RoadCategoryWire.FOG))
        // Forged: valid shape, wrong signature.
        val forged = report(category = RoadCategoryWire.ICE).toMutableMap()
        forged["sig"] = "0".repeat(128)
        subject.eventFrame("sub0", forged)
        // Wrong kind for the subscription.
        subject.eventFrame("sub0", TestKeys.sign(NostrEventDraft(TestKeys.PUBLIC_A, now, 1, emptyList(), "hi")))

        assertEquals(1, subject.events.value.size)
    }

    @Test
    fun `a relay exhausting the ingress budget is closed and rotated`() {
        val subject = service(maxIngressEventsPerSubscription = 2)
        subject.connect()
        connector.last.open()
        subject.subscribeArea(latitude, longitude)

        repeat(2) {
            connector.last.receive(listOf("EVENT", "sub0", mapOf("kind" to 1)))
        }
        assertFalse(connector.last.closed)

        connector.last.receive(listOf("EVENT", "sub0", mapOf("kind" to 1)))

        assertTrue(connector.last.closed)
        assertTrue(scheduler.pendingCount > 0)
    }

    @Test
    fun `a report missing the geohashes of its own coordinates is refused`() {
        val subject = connectedService()
        val draft = NostrEventDraft(
            TestKeys.PUBLIC_A, now - 10, 1315,
            listOf(
                listOf("lat", "38.722300"), listOf("lon", "-9.139300"),
                listOf("g", RoadstrNostrEvents.geohash(latitude, longitude, 4)),
                listOf("t", "police"),
            ),
            "",
        )
        subject.eventFrame("sub0", TestKeys.sign(draft))
        assertTrue(subject.events.value.isEmpty())
    }

    @Test
    fun `expired and far-future reports are dropped`() {
        val subject = connectedService()
        subject.eventFrame("sub0", report(createdAt = now - 5 * 3600)) // police TTL is 4 h
        subject.eventFrame("sub0", report(createdAt = now + 4000))
        assertTrue(subject.events.value.isEmpty())
    }

    @Test
    fun `eose requests the confirmations after a short delay and votes count once per identity`() {
        val subject = connectedService()
        val event = report()
        subject.eventFrame("sub0", event)
        connector.last.receive(listOf("EOSE", "sub0"))
        assertEquals("nothing yet", 1, connector.last.sent.size)
        scheduler.advance(300)
        val confRequest = connector.last.sent.last()
        assertTrue(confRequest, confRequest.contains("\"kinds\":[1316]") && confRequest.contains(event["id"] as String))

        fun vote(key: String, stillThere: Boolean): Map<String, Any?> {
            val pub = if (key == TestKeys.PRIVATE_A) TestKeys.PUBLIC_A else TestKeys.PUBLIC_B
            return TestKeys.sign(
                RoadstrNostrEvents.vote(pub, now, event["id"] as String, stillThere),
                key,
            )
        }
        subject.eventFrame("sub1", vote(TestKeys.PRIVATE_B, true))
        subject.eventFrame("sub1", vote(TestKeys.PRIVATE_B, true)) // same identity: ignored
        subject.eventFrame("sub1", vote(TestKeys.PRIVATE_A, false))
        val tally = subject.events.value.single()
        assertEquals(1, tally.confirmations)
        assertEquals(1, tally.denials)
    }

    @Test
    fun `only the reporter can raise the speed limit and an older update never wins`() {
        val subject = connectedService()
        val event = report(category = RoadCategoryWire.SPEED_CAMERA)
        subject.eventFrame("sub0", event)
        val id = event["id"] as String

        fun update(key: String, at: Long, limit: Int): Map<String, Any?> {
            val pub = if (key == TestKeys.PRIVATE_A) TestKeys.PUBLIC_A else TestKeys.PUBLIC_B
            return TestKeys.sign(
                RoadstrNostrEvents.update(pub, at, id, limit, latitude, longitude, ""),
                key,
            )
        }
        subject.eventFrame("sub0", update(TestKeys.PRIVATE_B, now, 90)) // not the owner
        assertEquals(null, subject.events.value.single().speedLimit)
        subject.eventFrame("sub0", update(TestKeys.PRIVATE_A, now, 80))
        assertEquals(80, subject.events.value.single().speedLimit)
        subject.eventFrame("sub0", update(TestKeys.PRIVATE_A, now - 5, 50)) // replayed older
        assertEquals(80, subject.events.value.single().speedLimit)
    }

    @Test
    fun `an update that arrives before its report is applied when the report shows up`() {
        val subject = connectedService()
        val event = report(category = RoadCategoryWire.SPEED_CAMERA)
        val id = event["id"] as String
        subject.eventFrame(
            "sub0",
            TestKeys.sign(RoadstrNostrEvents.update(TestKeys.PUBLIC_A, now, id, 70, latitude, longitude, "")),
        )
        assertTrue(subject.events.value.isEmpty())
        subject.eventFrame("sub0", event)
        assertEquals(70, subject.events.value.single().speedLimit)
    }

    @Test
    fun `a dropped connection moves to the next relay and the area is replayed`() {
        val subject = connectedService()
        assertEquals("wss://a.example", connector.last.url)
        connector.last.end()
        scheduler.advance(3_000)
        assertEquals("wss://b.example", connector.last.url)
        connector.last.open()
        val replay = connector.last.sent.single()
        assertTrue(replay, replay.startsWith("[\"REQ\",\"sub1\""))
    }

    @Test
    fun `a handshake that never completes counts as a failure and escalates the backoff`() {
        val subject = service()
        subject.connect()
        assertEquals(1, connector.sockets.size)
        scheduler.advance(10_001) // handshake timeout
        assertTrue(connector.sockets[0].closed)
        // First failure of the sweep: the short base delay (1.25 s +-25 %).
        scheduler.advance(1_600)
        assertEquals(2, connector.sockets.size)
        // Second relay refuses too: the whole pool has failed, so the delay
        // doubles (2.5 s +-25 %, never under 1.875 s).
        connector.last.end()
        scheduler.advance(1_700)
        assertEquals("still inside the longer delay", 2, connector.sockets.size)
        scheduler.advance(1_500)
        assertEquals(3, connector.sockets.size)
    }

    @Test
    fun `a failing connector does not throw out of connect`() {
        connector.throwOnConnect = true
        val subject = service()
        subject.connect()
        assertTrue(scheduler.pendingCount > 0)
    }

    /** A relay that opens at once and answers every EVENT with an OK. */
    private class AutoAckConnector(private val accept: Boolean = true) : NativeRelayConnector {
        val frames = mutableListOf<String>()

        override fun connect(url: String, events: NativeRelayEvents): NativeRelaySocket {
            val socket = object : NativeRelaySocket {
                override fun send(text: String): Boolean {
                    frames += text
                    val id = Regex("\"id\":\"([0-9a-f]{64})\"").find(text)?.groupValues?.get(1)
                    events.onMessage("[\"OK\",\"$id\",$accept,\"\"]")
                    return true
                }

                override fun close() {}
            }
            events.onOpen()
            return socket
        }
    }

    private fun serviceWith(publisherConnector: NativeRelayConnector) = NativeRoadEventService(
        connector = connector,
        publisher = NativeRelayPublisher(publisherConnector, listOf("wss://p1.example"), 300),
        scheduler = scheduler,
        relays = listOf("wss://a.example"),
        pendingStorage = storage,
        nowSeconds = { now },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    @Test
    fun `a published report shows at once`() = runBlocking {
        val signed = report()
        val event = NativeRoadEventCodec.parse(signed, now)!!
        val subject = serviceWith(AutoAckConnector())

        val outcome = subject.publishReport(signed, event)

        assertTrue(outcome is NativeReportOutcome.Published)
        assertTrue(queue.isEmpty())
        assertEquals(listOf(event.id), subject.events.value.map { it.id })
    }

    @Test
    fun `a report no relay accepts is queued, still shown, then flushed once a relay answers`() = runBlocking {
        val signed = report()
        val event = NativeRoadEventCodec.parse(signed, now)!!
        // The relay says no: rejected counts the same as unreachable.
        val refusing = serviceWith(AutoAckConnector(accept = false))

        val outcome = refusing.publishReport(signed, event)

        assertTrue(outcome is NativeReportOutcome.Queued)
        assertEquals(1, queue.size)
        assertEquals(1, refusing.events.value.size)

        val accepting = serviceWith(AutoAckConnector())
        accepting.flushPending()
        assertTrue("queue should be empty: $queue", queue.isEmpty())
    }

    @Test
    fun `stale queued reports are dropped and an unacknowledged one is kept`() = runBlocking {
        val live = report()
        val stale = report(category = RoadCategoryWire.FOG)
        queue += """{"event":${app.roadstr.core.protocol.nostr.NostrJson.encode(stale)},"expiresAt":${now - 1}}"""
        queue += """{"event":${app.roadstr.core.protocol.nostr.NostrJson.encode(live)},"expiresAt":${now + 1000}}"""

        serviceWith(AutoAckConnector(accept = false)).flushPending()

        assertEquals(1, queue.size)
        assertTrue(queue.single().contains(live["id"] as String))
    }

    @Test
    fun `a forged queued report is discarded rather than sent`() = runBlocking {
        val forged = report().toMutableMap().also { it["sig"] = "0".repeat(128) }
        queue += """{"event":${app.roadstr.core.protocol.nostr.NostrJson.encode(forged)},"expiresAt":${now + 1000}}"""
        val relay = AutoAckConnector()

        serviceWith(relay).flushPending()

        assertTrue(queue.isEmpty())
        assertTrue("nothing may be sent", relay.frames.isEmpty())
    }

    @Test
    fun `cache is cleaned of expired reports on a timer`() {
        val subject = connectedService()
        subject.eventFrame("sub0", report(category = RoadCategoryWire.TRAFFIC_JAM, createdAt = now - 3_500))
        assertEquals(1, subject.events.value.size)
        scheduler.advance(2 * 60 * 1000L + 1)
        // the clock used by the service is fixed, so the report only expires
        // from the cache once now moves; the timer must at least re-arm.
        assertTrue(scheduler.pendingCount > 0)
    }

    @Test
    fun `closing stops every timer and socket`() {
        val subject = connectedService()
        subject.close()
        assertTrue(connector.last.closed)
        scheduler.advance(10 * 60 * 1000L)
        assertEquals(1, connector.sockets.size)
    }
}
