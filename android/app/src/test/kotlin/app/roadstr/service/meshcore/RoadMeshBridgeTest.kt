package app.roadstr.service.meshcore

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.protocol.interop.CompactOutcome
import app.roadstr.core.protocol.interop.CompactReportLocation
import app.roadstr.core.protocol.interop.CompactRoadCodec
import app.roadstr.core.protocol.interop.CompactRoadType
import app.roadstr.core.protocol.interop.InteropVectors
import app.roadstr.core.protocol.interop.RoadEventTransport
import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.service.nostr.FakeConnector
import app.roadstr.service.nostr.FakeRelayNetwork
import app.roadstr.service.nostr.ManualScheduler
import app.roadstr.service.nostr.NativePendingReportStorage
import app.roadstr.service.nostr.NativeRelayPublisher
import app.roadstr.service.nostr.NativeRoadEventService
import app.roadstr.service.nostr.TestKeys
import java.util.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bridge between a (simulated) MeshCore radio, the road-event service and (simulated) relays. */
class RoadMeshBridgeTest {
    private var clock = 1_800_000_000L
    private val here = GeoPoint(38.7223, -9.1393)
    private val latE7 = 387_223_000
    private val lonE7 = -91_393_000
    private val network = FakeRelayNetwork()
    private val connector = FakeConnector()
    private val scheduler = ManualScheduler()
    private val radio = FakeMeshCoreRadio().also { it.configureRoadstrChannel(1) }
    private val rows = mutableListOf<String>()
    private val storage = object : NativePendingReportStorage {
        override fun read() = rows.toList()
        override fun write(rows: List<String>) {
            this@RoadMeshBridgeTest.rows.clear()
            this@RoadMeshBridgeTest.rows += rows
        }
    }
    private val queue = MeshRepublishQueue(storage, { clock })
    private var config = MeshBridgeConfig(receive = true)
    private var position: GeoPoint? = here
    private var limits = MeshBridgeLimits()
    private lateinit var bridge: RoadMeshBridge
    private lateinit var client: MeshCoreClient
    private lateinit var service: NativeRoadEventService

    private fun build(): RoadMeshBridge {
        service = NativeRoadEventService(
            connector = connector,
            publisher = NativeRelayPublisher(network, listOf("wss://p1.example"), 300),
            scheduler = scheduler,
            relays = listOf("wss://a.example"),
            nowSeconds = { clock },
            random = Random(1),
            newSubscriptionId = { "sub${subs++}" },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        client = MeshCoreClient(radio, scheduler, { "#roadstr" }, random = Random(1), onDatagram = { bridge.onDatagram(it) })
        bridge = RoadMeshBridge(
            radio = client,
            roads = service,
            republish = queue,
            config = { config },
            position = { position },
            nowSeconds = { clock },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            limits = limits,
        )
        service.setInteropListener(bridge)
        service.connect()
        connector.last.open()
        service.subscribeArea(here.latitude, here.longitude)
        client.start()
        return bridge
    }

    private var subs = 0

    private fun report(
        key: String = TestKeys.PRIVATE_A,
        type: CompactRoadType = CompactRoadType.POLICE,
        createdAt: Long = clock - 60,
        lat: Int = latE7,
        lon: Int = lonE7,
        comment: String = "",
    ): Map<String, Any?> {
        val pub = NostrSchnorr.publicKey(key)
        return TestKeys.sign(CompactRoadCodec.reportDraft(pub, createdAt, lat, lon, type, comment), key)
    }

    private fun vote(report: Map<String, Any?>, still: Boolean, key: String = TestKeys.PRIVATE_B, at: Long = clock - 10): Map<String, Any?> {
        val place = CompactReportLocation(report["id"] as String, latE7, lonE7)
        return TestKeys.sign(CompactRoadCodec.confirmationDraft(NostrSchnorr.publicKey(key), at, place, still), key)
    }

    private fun frame(event: Map<String, Any?>): ByteArray = (CompactRoadCodec.encode(event) as CompactOutcome.Ok).value

    private fun relayDelivers(event: Map<String, Any?>) = connector.last.receive(listOf("EVENT", "sub0", event))

    private fun relayEchoes() = network.stored["wss://p1.example"].orEmpty()

    private fun shownIds() = service.events.value.map { it.id }

    private fun key(n: Int) = "%064x".format(n + 100)

    // ── radio -> app ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a report heard on the radio is verified and shown, and by default goes nowhere else`() {
        build()
        val event = report()
        radio.receive(frame(event))

        assertEquals(listOf(event["id"]), shownIds())
        assertEquals(setOf(RoadEventTransport.MESH), service.events.value.single().transports)
        assertTrue(relayEchoes().isEmpty())
        assertEquals(0, queue.size())
        assertTrue(radio.sentDatagrams.isEmpty())
        assertEquals(1, bridge.stats.value.shown)
    }

    @Test
    fun `with receiving off the radio is not read at all`() {
        config = MeshBridgeConfig(receive = false)
        build()
        radio.receive(frame(report()))
        assertTrue(shownIds().isEmpty())
        assertEquals(0, bridge.stats.value.received)
    }

    @Test
    fun `a damaged or forged frame is counted and never shown`() {
        build()
        val good = frame(report())
        val forged = good.copyOf().also { it[100] = (it[100].toInt() xor 1).toByte() }   // inside the signature
        val moved = good.copyOf().also { it[44] = (it[44].toInt() xor 0x40).toByte() }   // far from the signed spot
        val short = good.copyOf(109)
        for (bad in listOf(forged, moved, short)) radio.receive(bad)

        assertTrue(shownIds().isEmpty())
        assertEquals(2, bridge.stats.value.invalidSignature)
        assertEquals(1, bridge.stats.value.malformed)
    }

    @Test
    fun `the same frame heard twice is handled once`() {
        build()
        val bytes = frame(report())
        radio.receive(bytes)
        radio.receive(bytes)
        assertEquals(1, bridge.stats.value.shown)
        assertEquals(2, bridge.stats.value.received)
    }

    @Test
    fun `old frames replayed later and clocks from the future are dropped`() {
        build()
        radio.receive(frame(report(createdAt = clock - 5 * 3_600)))             // police lasts 2 h
        radio.receive(frame(report(key = TestKeys.PRIVATE_B, createdAt = clock + 600)))
        assertTrue(shownIds().isEmpty())
        assertEquals(2, bridge.stats.value.stale)
        // Five minutes of clock drift is tolerated, as on the relay path.
        radio.receive(frame(report(key = key(1), createdAt = clock + 120)))
        assertEquals(1, shownIds().size)
    }

    @Test
    fun `a flood on the radio is cut off before it costs signature checks`() {
        limits = MeshBridgeLimits(ingestPerMinute = 5)
        build()
        for (n in 1..12) radio.receive(frame(report(key = key(n), lat = latE7 + n)))
        assertEquals(5, shownIds().size)
        assertEquals(7, bridge.stats.value.rateLimited)
        clock += 61
        radio.receive(frame(report(key = key(99), lat = latE7 + 99)))
        assertEquals(6, shownIds().size)
    }

    @Test
    fun `a report from an author never seen before is treated like any other`() {
        build()
        radio.receive(frame(report(key = key(12345))))
        assertEquals(1, shownIds().size)
    }

    @Test
    fun `a frame the reference encoder got off by one unit is recovered, counted, and still verified`() {
        clock = 1_700_000_100L
        build()
        val trap = InteropVectors.referenceTruncation.first { !it.matchesCorrectEncoding }
        radio.receive(trap.binary)
        assertEquals(listOf(trap.expectedEventId), shownIds())
        assertEquals(1, bridge.stats.value.recoveredFromTruncation)
    }

    // ── confirmations ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a confirmation that outruns its report waits for it and is then applied`() {
        build()
        val event = report()
        val confirmation = vote(event, still = true)
        radio.receive(frame(confirmation))
        assertTrue(shownIds().isEmpty())
        assertEquals(1, bridge.stats.value.waitingForReport)

        radio.receive(frame(event))
        assertEquals(1, service.events.value.single().confirmations)
    }

    @Test
    fun `a confirmation for a report that never appears is forgotten after a while`() {
        build()
        val event = report()
        radio.receive(frame(vote(event, still = true)))
        clock += 16 * 60
        radio.receive(frame(report(key = key(7), lat = latE7 + 5)))   // anything that triggers housekeeping
        radio.receive(frame(report(key = TestKeys.PRIVATE_A, createdAt = clock - 30)))
        radio.receive(frame(event.toMutableMap().also { }))            // the report finally shows up, late
        // The report is old by now but alive (police lasts 2 h): it shows, without the forgotten vote.
        assertEquals(0, service.events.value.firstOrNull { it.id == event["id"] }?.confirmations)
    }

    @Test
    fun `a forged confirmation never counts`() {
        build()
        val event = report()
        radio.receive(frame(event))
        val good = frame(vote(event, still = false))
        radio.receive(good.copyOf().also { it[69] = 1 })     // flipped verdict
        assertEquals(0, service.events.value.single().denials + service.events.value.single().confirmations)
        assertEquals(1, bridge.stats.value.invalidSignature)
    }

    // ── radio -> relays ────────────────────────────────────────────────────────────────────────

    @Test
    fun `with the bridge on a radio report is published with its original author and signature`() {
        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        build()
        val event = report(key = TestKeys.PRIVATE_B)
        radio.receive(frame(event))

        val published = relayEchoes().single()
        assertEquals(event["id"], published["id"])
        assertEquals(event["pubkey"], published["pubkey"])
        assertEquals(event["sig"], published["sig"])
        assertEquals("the bridge is not the author", TestKeys.PUBLIC_B, published["pubkey"])
        assertEquals(0, queue.size())
        assertTrue(service.ledger.wasForwarded(event["id"] as String, RoadEventTransport.RELAY))
    }

    @Test
    fun `without internet the report waits in the queue and goes out when the connection is back`() = runBlocking {
        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        network.down += "wss://p1.example"
        build()
        val event = report()
        radio.receive(frame(event))
        assertEquals(1, queue.size())
        assertTrue(relayEchoes().isEmpty())
        assertEquals(1, shownIds().size)   // shown locally regardless

        network.down.clear()
        bridge.flushRepublish()
        assertEquals(0, queue.size())
        assertEquals(event["id"], relayEchoes().single()["id"])
    }

    @Test
    fun `switching the bridge off stops the queue from being published`() = runBlocking {
        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        network.down += "wss://p1.example"
        build()
        radio.receive(frame(report()))
        assertEquals(1, queue.size())

        network.down.clear()
        config = MeshBridgeConfig(receive = true, meshToNostr = false)
        bridge.flushRepublish()
        assertEquals("still waiting, nothing published", 1, queue.size())
        assertTrue(relayEchoes().isEmpty())

        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        bridge.flushRepublish()
        assertEquals(0, queue.size())
        assertEquals(1, relayEchoes().size)
    }

    @Test
    fun `a queued report that has become stale is never published`() = runBlocking {
        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        network.down += "wss://p1.example"
        build()
        radio.receive(frame(report()))
        network.down.clear()
        clock += 3 * 3_600
        bridge.flushRepublish()
        assertEquals(0, queue.size())
        assertTrue(relayEchoes().isEmpty())
    }

    @Test
    fun `the queue is bounded, drops the oldest, survives a restart and can be cleared`() {
        val small = MeshRepublishQueue(storage, { clock }, capacity = 3)
        for (n in 1..5) assertTrue(small.enqueue(report(key = key(n)), clock + 3_600))
        assertEquals(3, small.size())
        assertTrue(small.enqueue(report(key = key(5)), clock + 3_600) )     // already queued: not doubled
        assertEquals(3, MeshRepublishQueue(storage, { clock }, capacity = 3).size())   // a new instance sees the same rows
        small.clear()
        assertEquals(0, small.size())
        assertFalse(small.enqueue(report(), clock - 1))                       // already stale
    }

    @Test
    fun `an event the relays already have is not published again`() {
        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        build()
        val event = report()
        relayDelivers(event)
        radio.receive(frame(event))
        assertTrue(relayEchoes().isEmpty())
        assertEquals(0, queue.size())
        assertEquals(setOf(RoadEventTransport.RELAY, RoadEventTransport.MESH), service.events.value.single().transports)
    }

    @Test
    fun `an event far from the driver is shown but not republished`() {
        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        build()
        radio.receive(frame(report(lat = 487_000_000, lon = 22_000_000)))   // ~1,500 km away
        assertTrue(relayEchoes().isEmpty())
        position = null
        radio.receive(frame(report(key = key(3), lat = latE7 + 10)))
        assertTrue("no position, no republishing", relayEchoes().isEmpty())
    }

    // ── relays -> radio ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a compatible report from a relay is put on the radio once, when allowed`() {
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        val event = report()
        relayDelivers(event)
        relayDelivers(event)   // the same event from another relay

        assertEquals(1, radio.sentDatagrams.size)
        assertArrayEquals(frame(event), radio.sentDatagrams.single().copyOfRange(5, 5 + 110))
        assertTrue(service.ledger.wasForwarded(event["id"] as String, RoadEventTransport.MESH))
        assertEquals(1, bridge.stats.value.sentToRadio)
    }

    @Test
    fun `nothing is put on the radio unless both the bridge and sending are on`() {
        for (cfg in listOf(
            MeshBridgeConfig(receive = true, send = true, nostrToMesh = false),
            MeshBridgeConfig(receive = true, send = false, nostrToMesh = true),
            MeshBridgeConfig(),
        )) {
            config = cfg
            radio.sentDatagrams.clear()
            build()
            relayDelivers(report(key = key(subs + 1)))
            assertTrue(cfg.toString(), radio.sentDatagrams.isEmpty())
        }
    }

    @Test
    fun `reports with a comment, far away, or with the radio down are not sent`() {
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        relayDelivers(report(comment = "radar"))
        assertEquals(1, bridge.stats.value.notRepresentable)
        relayDelivers(report(key = key(2), lat = 487_000_000, lon = 22_000_000))
        position = null
        relayDelivers(report(key = key(3), lat = latE7 + 7))
        assertTrue(radio.sentDatagrams.isEmpty())

        position = here
        radio.dropLink()
        relayDelivers(report(key = key(4), lat = latE7 + 9))
        assertTrue(radio.sentDatagrams.isEmpty())
    }

    @Test
    fun `other people's events respect a cooldown and an hourly total, and a refusal costs nothing`() {
        limits = MeshBridgeLimits(forwardPerHour = 3, forwardPerAuthorPerHour = 10, forwardCooldownSeconds = 20)
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        relayDelivers(report(key = key(1)))
        relayDelivers(report(key = key(2), lat = latE7 + 1))           // inside the cooldown
        assertEquals(1, radio.sentDatagrams.size)

        clock += 21
        relayDelivers(report(key = key(3), lat = latE7 + 2))
        clock += 21
        relayDelivers(report(key = key(4), lat = latE7 + 3))
        assertEquals("the refused one did not eat the hourly total", 3, radio.sentDatagrams.size)

        clock += 21
        relayDelivers(report(key = key(5), lat = latE7 + 4))           // the hourly total is spent
        assertEquals(3, radio.sentDatagrams.size)
        assertTrue(bridge.stats.value.rateLimited >= 2)

        clock += 3_600
        relayDelivers(report(key = key(6), lat = latE7 + 5, createdAt = clock - 10))
        assertEquals(4, radio.sentDatagrams.size)
    }

    @Test
    fun `one author cannot use the radio for more than its share`() {
        limits = MeshBridgeLimits(forwardPerHour = 50, forwardPerAuthorPerHour = 2, forwardCooldownSeconds = 1)
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        for (n in 1..4) {
            clock += 2
            relayDelivers(report(key = key(1), lat = latE7 + n))
        }
        assertEquals(2, radio.sentDatagrams.size)
        clock += 2
        relayDelivers(report(key = key(2), lat = latE7 + 9))
        assertEquals("another author still gets through", 3, radio.sentDatagrams.size)
    }

    @Test
    fun `an event that went radio to relay is not sent back to the radio when the relay echoes it`() = runBlocking {
        config = MeshBridgeConfig(receive = true, send = true, meshToNostr = true, nostrToMesh = true)
        build()
        val event = report()
        radio.receive(frame(event))
        val published = relayEchoes().single()
        relayDelivers(published)   // the relay hands it back through our subscription

        assertTrue("no storm", radio.sentDatagrams.isEmpty())
        assertEquals(1, relayEchoes().size)
    }

    @Test
    fun `an event that went relay to radio is not republished when the radio echoes it`() {
        config = MeshBridgeConfig(receive = true, send = true, meshToNostr = true, nostrToMesh = true)
        build()
        val event = report()
        relayDelivers(event)
        assertEquals(1, radio.sentDatagrams.size)
        radio.receive(frame(event))   // a repeater gives our own transmission back
        assertTrue(relayEchoes().isEmpty())
        assertEquals(0, queue.size())
    }

    // ── this device's own events ───────────────────────────────────────────────────────────────

    @Test
    fun `an own report goes to the radio only with sending on and is held while the radio is down`() {
        config = MeshBridgeConfig(send = false)
        build()
        val event = report()
        bridge.sendOwn(event)
        assertTrue(radio.sentDatagrams.isEmpty())

        config = MeshBridgeConfig(send = true)
        radio.dropLink()
        bridge.sendOwn(event)
        assertTrue(radio.sentDatagrams.isEmpty())
        scheduler.advance(2_000)   // the client reconnects
        bridge.onRadioReady()
        assertEquals(1, radio.sentDatagrams.size)
    }

    @Test
    fun `an own event offered by both paths is transmitted once`() = runBlocking {
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        val event = report()
        service.publishInteropReport(event)   // the relay path reports it to the bridge as well
        bridge.sendOwn(event)
        assertEquals(1, radio.sentDatagrams.size)
    }

    @Test
    fun `a vote only goes on the radio if the radio knows its report`() {
        config = MeshBridgeConfig(receive = true, send = true)
        build()
        val theirs = report(key = TestKeys.PRIVATE_B)
        relayDelivers(theirs)                       // the bridge learns where the report is
        val myVote = vote(theirs, still = true, key = TestKeys.PRIVATE_A)
        bridge.sendOwn(myVote)
        assertTrue("the radio has not seen that report", radio.sentDatagrams.isEmpty())

        radio.receive(frame(theirs))                // it is now on the radio
        bridge.sendOwn(vote(theirs, still = false, key = TestKeys.PRIVATE_A, at = clock - 5))
        assertEquals(1, radio.sentDatagrams.size)
        assertEquals(134, radio.sentDatagrams.single().size - 5)
    }

    // ── failures of the radio ──────────────────────────────────────────────────────────────────

    @Test
    fun `firmware without channel datagrams fails the radio and leaves the event free to be offered later`() {
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        radio.sendMode = FakeMeshCoreRadio.SendMode.UnsupportedCommand
        build()
        val event = report()
        relayDelivers(event)
        assertTrue(client.state.value is MeshCoreState.Failed)
        assertFalse(service.ledger.wasForwarded(event["id"] as String, RoadEventTransport.MESH))
        assertEquals(0, bridge.stats.value.sentToRadio)
    }

    @Test
    fun `losing bluetooth while sending does not mark the event as sent`() {
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        radio.sendMode = FakeMeshCoreRadio.SendMode.Silent
        build()
        val event = report()
        relayDelivers(event)
        radio.dropLink()
        assertFalse(service.ledger.wasForwarded(event["id"] as String, RoadEventTransport.MESH))
        assertEquals(0, bridge.stats.value.sentToRadio)
    }
}
