package app.roadstr.service.nostr

import app.roadstr.core.protocol.interop.CompactRoadCodec
import app.roadstr.core.protocol.interop.CompactReportLocation
import app.roadstr.core.protocol.interop.CompactRoadType
import app.roadstr.core.protocol.interop.RoadEventTransport
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Road reports in the jooray/roadstr layout, arriving over a relay or by radio. */
class NativeRoadEventServiceInteropTest {
    private val now = 1_800_000_000L
    private val latitude = 38.7223
    private val longitude = -9.1393
    private val latE7 = 387_223_000
    private val lonE7 = -91_393_000
    private val connector = FakeConnector()
    private val scheduler = ManualScheduler()
    private var subCounter = 0
    private var interopEnabled = true

    private fun service(): NativeRoadEventService = NativeRoadEventService(
        connector = connector,
        publisher = NativeRelayPublisher(connector, emptyList(), timeoutMillis = 200),
        scheduler = scheduler,
        relays = listOf("wss://a.example"),
        nowSeconds = { now },
        random = Random(1),
        newSubscriptionId = { "sub${subCounter++}" },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        interopEnabled = { interopEnabled },
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
        lat: Int = latE7,
        lon: Int = lonE7,
    ) = TestKeys.sign(CompactRoadCodec.reportDraft(key(true, author), createdAt, lat, lon, type), key(false, author))

    private fun compactVote(
        report: Map<String, Any?>,
        stillThere: Boolean,
        at: Long,
        author: Boolean = false,
        lat: Int = latE7,
        lon: Int = lonE7,
    ) = TestKeys.sign(
        CompactRoadCodec.confirmationDraft(
            key(true, author), at, CompactReportLocation(report["id"] as String, lat, lon), stillThere,
        ),
        key(false, author),
    )

    private fun nativeReport(createdAt: Long = now - 60, author: Boolean = true, lat: Double = latitude, lon: Double = longitude) =
        TestKeys.sign(
            RoadstrNostrEvents.report(
                pubkey = key(true, author), createdAt = createdAt, latitude = lat, longitude = lon,
                category = "police", expiresAt = createdAt + RoadCategoryWire.POLICE.ttlSeconds, content = "",
            ),
            key(false, author),
        )

    @Test
    fun `a compatible report shows with its provenance and the type's own lifetime`() {
        val subject = connected()
        val event = compactReport(CompactRoadType.ROAD_CLOSURE, createdAt = now - 2 * 86_400)
        subject.relay("sub0", event)

        val shown = subject.events.value.single()
        assertEquals(event["id"], shown.id)
        assertEquals(NativeRoadEventProtocol.Interop, shown.protocol)
        assertEquals(setOf(RoadEventTransport.RELAY), shown.transports)
        assertEquals(RoadCategoryWire.ROAD_CLOSURE, shown.category)
        assertEquals(latitude, shown.latitude, 0.0)
        assertEquals(longitude, shown.longitude, 0.0)
        // Roadstr lets its own road closures live 24 h; this format says 7 days. The 14 day
        // `expiration` tag is only what relays keep, and decides nothing here.
        assertEquals(event["created_at"] as Long + 7 * 86_400, shown.lifetimeEndsAt)
    }

    @Test
    fun `a compatible report older than its type's lifetime is not shown even though relays still serve it`() {
        val subject = connected()
        subject.relay("sub0", compactReport(CompactRoadType.POLICE, createdAt = now - 3 * 3_600))
        assertTrue(subject.events.value.isEmpty())
    }

    @Test
    fun `turning interoperability off hides compatible reports and leaves native ones alone`() {
        interopEnabled = false
        val subject = connected()
        subject.relay("sub0", compactReport())
        val native = nativeReport(author = false)
        subject.relay("sub0", native)
        assertEquals(listOf(native["id"]), subject.events.value.map { it.id })
    }

    @Test
    fun `a forged compatible report never gets past the signature check`() {
        val subject = connected()
        val forged = compactReport().toMutableMap().also { it["sig"] = "00".repeat(64) }
        subject.relay("sub0", forged)
        assertTrue(subject.events.value.isEmpty())
    }

    @Test
    fun `a confirmation extends the lifetime from its own time and a denial ends it`() {
        val subject = connected()
        val event = compactReport(createdAt = now - 3_000)
        subject.relay("sub0", event)
        val base = subject.events.value.single().lifetimeEndsAt!!
        assertEquals(now - 3_000 + 7_200, base)
        openConfirmations()

        subject.relay("sub1", compactVote(event, true, now - 600, author = false))
        val extended = subject.events.value.single()
        assertEquals(now - 600 + 7_200, extended.lifetimeEndsAt)
        assertEquals(1, extended.confirmations)

        subject.relay("sub1", compactVote(event, false, now - 100, author = true))
        assertTrue("a denial cuts the report at the time of the denial", subject.events.value.isEmpty())
    }

    @Test
    fun `a later confirmation revives a denied report and one key counts once`() {
        val subject = connected()
        val event = compactReport(createdAt = now - 3_000)
        subject.relay("sub0", event)
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
    fun `a confirmation that repeats the wrong place or predates its report is ignored`() {
        val subject = connected()
        val event = compactReport(createdAt = now - 3_000)
        subject.relay("sub0", event)
        openConfirmations()
        subject.relay("sub1", compactVote(event, false, now - 100, lat = latE7 + 1))
        subject.relay("sub1", compactVote(event, false, now - 4_000))
        val shown = subject.events.value.single()
        assertEquals(0, shown.denials)
        assertEquals(now - 3_000 + 7_200, shown.lifetimeEndsAt)
    }

    @Test
    fun `a plain Roadstr vote also counts on a compatible report`() {
        val subject = connected()
        val event = compactReport(createdAt = now - 3_000)
        subject.relay("sub0", event)
        openConfirmations()
        val vote = TestKeys.sign(
            RoadstrNostrEvents.vote(TestKeys.PUBLIC_B, now - 30, event["id"] as String, true),
            TestKeys.PRIVATE_B,
        )
        subject.relay("sub1", vote)
        assertEquals(1, subject.events.value.single().confirmations)
    }

    @Test
    fun `the same author's native report and compatible twin are one marker in either order`() {
        for (nativeFirst in listOf(true, false)) {
            subCounter = 0
            val subject = connected()
            val native = nativeReport(createdAt = now - 120)
            val twin = compactReport(createdAt = now - 118)
            if (nativeFirst) {
                subject.relay("sub0", native)
                subject.relay("sub0", twin)
            } else {
                subject.relay("sub0", twin)
                subject.relay("sub0", native)
            }
            val shown = subject.events.value.single()
            assertEquals("nativeFirst=$nativeFirst", native["id"], shown.id)
            assertEquals(NativeRoadEventProtocol.Roadstr, shown.protocol)
        }
    }

    @Test
    fun `votes cast on the twin land on the marker that is shown`() {
        val subject = connected()
        val native = nativeReport(createdAt = now - 120)
        val twin = compactReport(createdAt = now - 118)
        subject.relay("sub0", native)
        subject.relay("sub0", twin)
        openConfirmations()
        subject.relay("sub1", compactVote(twin, true, now - 10, author = false))
        assertEquals(1, subject.events.value.single().confirmations)
    }

    @Test
    fun `the confirmation request also names the twin so its votes are fetched`() {
        val subject = connected()
        val native = nativeReport(createdAt = now - 120)
        val twin = compactReport(createdAt = now - 118)
        subject.relay("sub0", native)
        subject.relay("sub0", twin)
        connector.last.receive(listOf("EOSE", "sub0"))
        scheduler.advance(300)
        val request = connector.last.sent.last()
        assertTrue(request, request.contains(native["id"] as String) && request.contains(twin["id"] as String))
    }

    @Test
    fun `different authors are never merged however close they are`() {
        val subject = connected()
        subject.relay("sub0", nativeReport(createdAt = now - 120, author = true))
        subject.relay("sub0", compactReport(createdAt = now - 119, author = false))
        assertEquals(2, subject.events.value.size)
    }

    @Test
    fun `an event rebuilt from the radio is accepted, tagged with its transport, and merged with the relay copy`() {
        val subject = connected()
        val event = compactReport()
        assertTrue(subject.acceptInteropEvent(event, RoadEventTransport.MESH))
        assertEquals(setOf(RoadEventTransport.MESH), subject.events.value.single().transports)

        subject.relay("sub0", event)
        val shown = subject.events.value.single()
        assertEquals(setOf(RoadEventTransport.MESH, RoadEventTransport.RELAY), shown.transports)
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
        subject.acceptInteropEvent(event, RoadEventTransport.MESH)
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
    fun `the radio path verifies the signature unless the codec already did, and honours the switch`() {
        val subject = connected()
        val forged = compactReport().toMutableMap().also { it["sig"] = "00".repeat(64) }
        assertFalse(subject.acceptInteropEvent(forged, RoadEventTransport.MESH))
        assertTrue(subject.events.value.isEmpty())

        interopEnabled = false
        assertFalse(subject.acceptInteropEvent(compactReport(), RoadEventTransport.MESH))
        interopEnabled = true
        assertTrue(subject.acceptInteropEvent(compactReport(), RoadEventTransport.MESH))
        assertEquals(1, subject.events.value.size)
    }
}
