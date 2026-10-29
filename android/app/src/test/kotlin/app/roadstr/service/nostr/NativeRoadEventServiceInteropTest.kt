package app.roadstr.service.nostr

import app.roadstr.core.protocol.interop.CompactRoadCodec
import app.roadstr.core.protocol.interop.CompactRoadType
import app.roadstr.core.protocol.interop.RoadEventTransport
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Road reports of the shared layout, arriving over a relay or rebuilt from a radio frame. */
class NativeRoadEventServiceInteropTest {
    private val now = 1_800_000_000L
    private val latitude = 38.7223
    private val longitude = -9.1393
    private val latE6 = 38_722_300
    private val lonE6 = -9_139_300
    private val connector = FakeConnector()
    private val scheduler = ManualScheduler()
    private var subCounter = 0

    private fun service(): NativeRoadEventService = NativeRoadEventService(
        connector = connector,
        publisher = NativeRelayPublisher(connector, emptyList(), timeoutMillis = 200),
        scheduler = scheduler,
        relays = listOf("wss://a.example"),
        nowSeconds = { now },
        random = Random(1),
        newSubscriptionId = { "sub${subCounter++}" },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private fun connected(): NativeRoadEventService = service().also {
        it.connect()
        connector.last.open()
        it.subscribeArea(latitude, longitude)
    }

    private fun NativeRoadEventService.relay(sub: String, event: Map<String, Any?>) =
        connector.last.receive(listOf("EVENT", sub, event))

    /** Ends the report sweep so the service opens its confirmation subscription ("sub1"). */
    private fun openConfirmations() {
        connector.last.receive(listOf("EOSE", "sub0"))
        scheduler.advance(300)
    }

    private fun key(pub: Boolean, a: Boolean) = if (a) (if (pub) TestKeys.PUBLIC_A else TestKeys.PRIVATE_A) else (if (pub) TestKeys.PUBLIC_B else TestKeys.PRIVATE_B)

    private fun compactReport(
        type: CompactRoadType = CompactRoadType.POLICE,
        createdAt: Long = now - 60,
        author: Boolean = true,
        lat: Int = latE6,
        lon: Int = lonE6,
    ) = TestKeys.sign(CompactRoadCodec.reportDraft(key(true, author), createdAt, lat, lon, type), key(false, author))

    private fun compactVote(
        report: Map<String, Any?>,
        stillThere: Boolean,
        at: Long,
        author: Boolean = false,
    ) = TestKeys.sign(
        CompactRoadCodec.confirmationDraft(key(true, author), at, report["id"] as String, stillThere),
        key(false, author),
    )

    /** A report heard on the radio: rebuilt by the codec, so already verified. */
    private fun NativeRoadEventService.heard(event: Map<String, Any?>) =
        acceptInteropEvent(event, RoadEventTransport.MESH, alreadyVerified = true)

    @Test
    fun `a report from a relay is Roadstr's own and the bridge hears about it`() {
        val subject = connected()
        val heard = mutableListOf<RoadEventTransport>()
        subject.setInteropListener { _, via, _ -> heard += via }
        val event = compactReport()
        subject.relay("sub0", event)

        val shown = subject.events.value.single()
        assertEquals(event["id"], shown.id)
        assertEquals(NativeRoadEventProtocol.Roadstr, shown.protocol)
        assertEquals(listOf(RoadEventTransport.RELAY), heard)
        assertTrue(subject.ledger.seenOn(event["id"] as String, RoadEventTransport.RELAY))
    }

    @Test
    fun `a report heard on the radio shows with its provenance and the type's own lifetime`() {
        val subject = connected()
        val event = compactReport(CompactRoadType.ROAD_CLOSURE, createdAt = now - 2 * 3_600)
        assertTrue(subject.heard(event))

        val shown = subject.events.value.single()
        assertEquals(NativeRoadEventProtocol.Interop, shown.protocol)
        assertEquals(setOf(RoadEventTransport.MESH), shown.transports)
        assertEquals(RoadCategoryWire.ROAD_CLOSURE, shown.category)
        assertEquals(latitude, shown.latitude, 0.0)
        assertEquals(longitude, shown.longitude, 0.0)
        assertEquals(now - 2 * 3_600 + 86_400, shown.lifetimeEndsAt)
    }

    @Test
    fun `a speed camera heard on the radio is shown for 30 days, not cut at day 14`() {
        val subject = connected()
        val event = compactReport(CompactRoadType.SPEED_CAMERA, createdAt = now - 20 * 86_400)
        assertTrue(subject.heard(event))
        assertEquals(now - 20 * 86_400 + 30 * 86_400, subject.events.value.single().lifetimeEndsAt)
    }

    @Test
    fun `a report older than its type's lifetime is not shown even though relays still serve it`() {
        val subject = connected()
        assertFalse(subject.heard(compactReport(CompactRoadType.POLICE, createdAt = now - 5 * 3_600)))
        assertTrue(subject.events.value.isEmpty())
    }

    @Test
    fun `a report in the deprecated layout is not shown`() {
        val subject = connected()
        val event = compactReport()
        @Suppress("UNCHECKED_CAST")
        val tags = event["tags"] as List<List<String>>
        val draft = app.roadstr.core.protocol.nostr.NostrEventDraft(
            TestKeys.PUBLIC_A, now - 60, 1315, tags + listOf(listOf("alt", "Roadstr: police report")), "",
        )
        subject.relay("sub0", TestKeys.sign(draft))
        assertTrue(subject.events.value.isEmpty())
    }

    @Test
    fun `a two-tag vote extends the lifetime from its own time and a denial ends it`() {
        val subject = connected()
        val event = compactReport(createdAt = now - 3_000)
        subject.heard(event)
        assertEquals(now - 3_000 + 14_400, subject.events.value.single().lifetimeEndsAt)
        openConfirmations()

        subject.relay("sub1", compactVote(event, true, now - 600, author = false))
        val extended = subject.events.value.single()
        assertEquals(now - 600 + 14_400, extended.lifetimeEndsAt)
        assertEquals(1, extended.confirmations)

        assertTrue(subject.heard(compactVote(event, false, now - 100, author = true)))
        assertTrue("a denial cuts the report at the time of the denial", subject.events.value.isEmpty())
    }

    @Test
    fun `a later confirmation revives a denied report and one key counts once`() {
        val subject = connected()
        val event = compactReport(createdAt = now - 3_000)
        subject.heard(event)
        openConfirmations()
        subject.relay("sub1", compactVote(event, false, now - 900, author = false))
        assertTrue(subject.events.value.isEmpty())
        subject.relay("sub1", compactVote(event, true, now - 800, author = true))
        // 'B' denied at -900 and 'A' confirmed at -800: A's later confirmation revives it.
        val revived = subject.events.value.single()
        assertEquals(1, revived.confirmations)
        assertEquals(1, revived.denials)
        // B changes their mind: only their latest vote counts.
        subject.relay("sub1", compactVote(event, true, now - 700, author = false))
        val settled = subject.events.value.single()
        assertEquals(2, settled.confirmations)
        assertEquals(0, settled.denials)
    }

    @Test
    fun `a vote that predates its report is ignored`() {
        val subject = connected()
        val event = compactReport(createdAt = now - 3_000)
        subject.heard(event)
        openConfirmations()
        subject.relay("sub1", compactVote(event, false, now - 4_000))
        val shown = subject.events.value.single()
        assertEquals(0, shown.denials)
        assertEquals(now - 3_000 + 14_400, shown.lifetimeEndsAt)
    }

    @Test
    fun `a vote heard on the radio counts on a report that came from a relay`() {
        val subject = connected()
        val event = compactReport(createdAt = now - 3_000)
        subject.relay("sub0", event)
        assertTrue(subject.heard(compactVote(event, true, now - 30)))
        assertEquals(1, subject.events.value.single().confirmations)
    }

    @Test
    fun `a report heard on the radio and then on a relay is one marker with both transports`() {
        val subject = connected()
        val event = compactReport()
        assertTrue(subject.heard(event))
        subject.relay("sub0", event)
        val shown = subject.events.value.single()
        assertEquals(setOf(RoadEventTransport.MESH, RoadEventTransport.RELAY), shown.transports)
        assertEquals(NativeRoadEventProtocol.Interop, shown.protocol)
        assertTrue(subject.ledger.seenOn(event["id"] as String, RoadEventTransport.MESH))
        assertTrue(subject.ledger.seenOn(event["id"] as String, RoadEventTransport.RELAY))
    }

    @Test
    fun `the bridge listener hears each event once per transport`() {
        val subject = connected()
        val heard = mutableListOf<Pair<RoadEventTransport, Boolean>>()
        subject.setInteropListener { _, via, isNew -> heard += via to isNew }
        val event = compactReport()
        subject.relay("sub0", event)
        subject.relay("sub0", event)
        subject.heard(event)
        assertEquals(
            listOf(
                RoadEventTransport.RELAY to true,
                RoadEventTransport.RELAY to false,
                RoadEventTransport.MESH to true,
            ),
            heard,
        )
    }

    @Test
    fun `the radio path verifies the signature unless the codec already did`() {
        val subject = connected()
        val forged = compactReport().toMutableMap().also { it["sig"] = "00".repeat(64) }
        assertFalse(subject.acceptInteropEvent(forged, RoadEventTransport.MESH))
        assertTrue(subject.events.value.isEmpty())
        assertTrue(subject.acceptInteropEvent(compactReport(), RoadEventTransport.MESH))
        assertEquals(1, subject.events.value.size)
    }
}
