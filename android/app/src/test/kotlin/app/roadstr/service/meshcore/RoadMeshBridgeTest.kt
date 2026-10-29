package app.roadstr.service.meshcore

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.protocol.interop.CompactOutcome
import app.roadstr.core.protocol.interop.CompactRoadCodec
import app.roadstr.core.protocol.interop.CompactRoadType
import app.roadstr.core.protocol.interop.RoadEventTransport
import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.service.nostr.FakeConnector
import app.roadstr.service.nostr.FakeRelayNetwork
import app.roadstr.service.nostr.ManualScheduler
import app.roadstr.service.nostr.NativePendingReportStorage
import app.roadstr.service.nostr.NativeRelayPublisher
import app.roadstr.service.nostr.NativeRoadEventService
import app.roadstr.service.nostr.TestKeys
import java.nio.ByteBuffer
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
    private val latE6 = 38_722_300
    private val lonE6 = -9_139_300
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
    private var subs = 0
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

    private fun report(
        key: String = TestKeys.PRIVATE_A,
        type: CompactRoadType = CompactRoadType.POLICE,
        createdAt: Long = clock - 60,
        lat: Int = latE6,
        lon: Int = lonE6,
        comment: String = "",
    ): Map<String, Any?> {
        val pub = NostrSchnorr.publicKey(key)
        return TestKeys.sign(CompactRoadCodec.reportDraft(pub, createdAt, lat, lon, type, comment), key)
    }

    private fun vote(report: Map<String, Any?>, still: Boolean, key: String = TestKeys.PRIVATE_B, at: Long = clock - 10): Map<String, Any?> =
        TestKeys.sign(CompactRoadCodec.confirmationDraft(NostrSchnorr.publicKey(key), at, report["id"] as String, still), key)

    private fun frame(event: Map<String, Any?>): ByteArray {
        val encoded = if ((event["kind"] as Number).toInt() == CompactRoadCodec.KIND_REPORT) {
            CompactRoadCodec.encodeReport(event)
        } else {
            CompactRoadCodec.encodeConfirmation(event)
        }
        return (encoded as CompactOutcome.Ok).value
    }

    /** A report frame built by hand, for comments the encoder refuses to write (47 and 48 bytes). */
    private fun handBuiltFrame(event: Map<String, Any?>): ByteArray {
        val comment = (event["content"] as String).toByteArray(Charsets.UTF_8)
        val tags = (event["tags"] as List<*>).map { it as List<*> }
        fun tag(name: String) = tags.first { it[0] == name }[1] as String
        fun e6(text: String) = text.replace(".", "").toInt()
        return ByteBuffer.allocate(CompactRoadCodec.REPORT_BASE_SIZE + comment.size)
            .put(0)
            .put(hex(event["pubkey"] as String))
            .putInt((event["created_at"] as Long).toInt())
            .putInt(e6(tag("lat")))
            .putInt(e6(tag("lon")))
            .put(CompactRoadType.fromWireName(tag("t"))!!.value.toByte())
            .putInt(tag("expiration").toLong().toInt())
            .putShort(0)
            .put(comment.size.toByte())
            .put(comment)
            .put(hex(event["sig"] as String))
            .array()
    }

    private fun hex(value: String) = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun relayDelivers(event: Map<String, Any?>, sub: String = "sub0") = connector.last.receive(listOf("EVENT", sub, event))

    /** Ends the report sweep so the service opens its confirmation subscription ("sub1"). */
    private fun openConfirmations() {
        connector.last.receive(listOf("EOSE", "sub0"))
        scheduler.advance(300)
    }

    private fun relayEchoes() = network.stored["wss://p1.example"].orEmpty()

    private fun shownIds() = service.events.value.map { it.id }

    private fun sentPayloads() = radio.sentDatagrams.map { it.copyOfRange(5, it.size) }

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
        config = MeshBridgeConfig(receive = false, send = true, meshToNostr = true, nostrToMesh = true)
        build()
        radio.receive(frame(report()))
        assertTrue(shownIds().isEmpty())
        assertEquals(0, bridge.stats.value.received)
    }

    @Test
    fun `a tampered or truncated frame is counted and never shown`() {
        build()
        val good = frame(report())
        val forged = good.copyOf().also { it[100] = (it[100].toInt() xor 1).toByte() } // inside the signature
        val moved = good.copyOf().also { it[44] = (it[44].toInt() xor 0x40).toByte() } // the longitude
        val short = good.copyOf(116)
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
        radio.receive(frame(report(createdAt = clock - 5 * 3_600))) // police lasts 4 h
        radio.receive(frame(report(key = TestKeys.PRIVATE_B, createdAt = clock + 600)))
        assertTrue(shownIds().isEmpty())
        assertEquals(2, bridge.stats.value.stale)
        // Five minutes of clock drift is tolerated, as on the relay path.
        radio.receive(frame(report(key = key(1), createdAt = clock + 120)))
        assertEquals(1, shownIds().size)
    }

    @Test
    fun `a speed camera heard on day 20 is still news`() {
        build()
        radio.receive(frame(report(type = CompactRoadType.SPEED_CAMERA, createdAt = clock - 20 * 86_400)))
        assertEquals(1, shownIds().size)
    }

    @Test
    fun `a flood on the radio is cut off before it costs signature checks`() {
        limits = MeshBridgeLimits(ingestPerMinute = 5)
        build()
        for (n in 1..12) radio.receive(frame(report(key = key(n), lat = latE6 + n)))
        assertEquals(5, shownIds().size)
        assertEquals(7, bridge.stats.value.rateLimited)
        clock += 61
        radio.receive(frame(report(key = key(99), lat = latE6 + 99)))
        assertEquals(6, shownIds().size)
    }

    @Test
    fun `frames of 164 and 165 bytes are read, though this app never writes them`() {
        build()
        for (length in listOf(47, 48)) {
            val event = report(key = key(length), comment = "x".repeat(length))
            val bytes = handBuiltFrame(event)
            assertEquals(117 + length, bytes.size)
            // A BLE companion link cannot carry them; a serial node or another transport can.
            bridge.onDatagram(MeshCoreDatagram(bytes, snrDb = 0.0, flooded = true))
            assertTrue("$length", event["id"] in shownIds())
        }
        assertEquals(2, bridge.stats.value.shown)
    }

    // ── votes ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a vote for a report heard on the radio applies`() {
        build()
        val event = report()
        radio.receive(frame(event))
        radio.receive(frame(vote(event, still = true)))
        assertEquals(1, service.events.value.single().confirmations)
    }

    @Test
    fun `a vote heard on the radio applies to a report that came from a relay`() {
        build()
        val event = report()
        relayDelivers(event)
        radio.receive(frame(vote(event, still = true)))
        assertEquals(1, service.events.value.single().confirmations)

        // The same rule as for every report: a denial ends it from the moment of the vote.
        radio.receive(frame(vote(event, still = false, key = TestKeys.PRIVATE_A, at = clock - 5)))
        assertTrue(service.events.value.isEmpty())
    }

    @Test
    fun `a vote that outruns its report waits for it on the radio, and is then applied`() {
        build()
        val event = report()
        radio.receive(frame(vote(event, still = true)))
        assertTrue(shownIds().isEmpty())
        assertEquals(1, bridge.stats.value.waitingForReport)

        radio.receive(frame(event))
        assertEquals(1, service.events.value.single().confirmations)
    }

    @Test
    fun `a waiting vote is applied when its report arrives from a relay, and forwarded to relays`() {
        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        build()
        val event = report()
        val confirmation = vote(event, still = true)
        radio.receive(frame(confirmation))
        assertTrue(relayEchoes().isEmpty())

        relayDelivers(event)
        assertEquals(1, service.events.value.single().confirmations)
        assertEquals(listOf(confirmation["id"]), relayEchoes().map { it["id"] })
        assertEquals("the vote is not altered", confirmation["sig"], relayEchoes().single()["sig"])
    }

    @Test
    fun `a waiting vote is forgotten after 15 minutes`() {
        build()
        val event = report()
        radio.receive(frame(vote(event, still = true)))
        clock += 16 * 60
        radio.receive(frame(event))
        assertEquals(0, service.events.value.single().confirmations)
    }

    @Test
    fun `waiting votes are bounded per report`() {
        build()
        val event = report()
        for (n in 1..12) radio.receive(frame(vote(event, still = true, key = key(n))))
        assertEquals(8, bridge.stats.value.waitingForReport)
        radio.receive(frame(event))
        assertEquals(8, service.events.value.single().confirmations)
    }

    @Test
    fun `replayed votes older than their report or than a day are dropped`() {
        build()
        val event = report(type = CompactRoadType.SPEED_CAMERA, createdAt = clock - 3 * 86_400)
        radio.receive(frame(event))
        radio.receive(frame(vote(event, still = false, at = clock - 4 * 86_400)))   // before the report
        radio.receive(frame(vote(event, still = false, key = key(1), at = clock - 2 * 86_400))) // a replay
        radio.receive(frame(vote(event, still = true, key = key(2), at = clock + 600)))  // from the future
        assertEquals(3, bridge.stats.value.stale)
        val shown = service.events.value.single()
        assertEquals(0, shown.confirmations + shown.denials)
    }

    @Test
    fun `a forged vote never counts`() {
        build()
        val event = report()
        radio.receive(frame(event))
        val good = frame(vote(event, still = false))
        radio.receive(good.copyOf().also { it[69] = 1 }) // flipped verdict
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
        assertEquals(event["sig"], published["sig"])
        assertEquals("the bridge is not the author", TestKeys.PUBLIC_B, published["pubkey"])
        assertEquals(event["tags"], published["tags"])
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
        assertEquals(1, shownIds().size)

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
        network.down.clear()
        config = MeshBridgeConfig(receive = true, meshToNostr = false)
        bridge.flushRepublish()
        assertEquals("still waiting, nothing published", 1, queue.size())
        assertTrue(relayEchoes().isEmpty())

        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        bridge.flushRepublish()
        assertEquals(1, relayEchoes().size)
    }

    @Test
    fun `an event far from the driver is shown but not republished`() {
        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        build()
        radio.receive(frame(report(lat = 48_700_000, lon = 2_200_000))) // ~1,500 km away
        assertTrue(relayEchoes().isEmpty())
        position = null
        radio.receive(frame(report(key = key(3), lat = latE6 + 10)))
        assertTrue("no position, no republishing", relayEchoes().isEmpty())
        assertEquals(2, shownIds().size)
    }

    // ── relays -> radio ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a report from a relay is put on the radio once, byte for byte`() {
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        val event = report()
        relayDelivers(event)
        relayDelivers(event) // the same event from another relay

        assertEquals(1, radio.sentDatagrams.size)
        assertArrayEquals(frame(event), sentPayloads().single())
        assertTrue(service.ledger.wasForwarded(event["id"] as String, RoadEventTransport.MESH))
        assertEquals(1, bridge.stats.value.sentToRadio)
    }

    @Test
    fun `a 46 byte comment goes on the radio in 163 bytes and a 47 byte one stays on the relays`() {
        limits = MeshBridgeLimits(forwardCooldownSeconds = 1)
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        relayDelivers(report(comment = "y".repeat(46)))
        assertEquals(163, sentPayloads().single().size)

        clock += 5
        relayDelivers(report(key = key(1), comment = "y".repeat(47)))
        assertEquals(1, radio.sentDatagrams.size)
        assertEquals(1, bridge.stats.value.notRepresentable)
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
            subs = 0
            build()
            relayDelivers(report())
            assertTrue(cfg.toString(), radio.sentDatagrams.isEmpty())
            assertEquals(cfg.toString(), 1, shownIds().size)
        }
    }

    @Test
    fun `reports far away, without a position or with the radio down are not sent`() {
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        relayDelivers(report(key = key(2), lat = 48_700_000, lon = 2_200_000))
        position = null
        relayDelivers(report(key = key(3), lat = latE6 + 7))
        assertTrue(radio.sentDatagrams.isEmpty())

        position = here
        radio.dropLink()
        relayDelivers(report(key = key(4), lat = latE6 + 9))
        assertTrue(radio.sentDatagrams.isEmpty())
    }

    @Test
    fun `other people's events respect a cooldown and an hourly total, and a refusal costs nothing`() {
        limits = MeshBridgeLimits(forwardPerHour = 3, forwardPerAuthorPerHour = 10, forwardCooldownSeconds = 20)
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        relayDelivers(report(key = key(1)))
        relayDelivers(report(key = key(2), lat = latE6 + 1)) // inside the cooldown
        assertEquals(1, radio.sentDatagrams.size)

        clock += 21
        relayDelivers(report(key = key(3), lat = latE6 + 2))
        clock += 21
        relayDelivers(report(key = key(4), lat = latE6 + 3))
        assertEquals("the refused one did not eat the hourly total", 3, radio.sentDatagrams.size)

        clock += 21
        relayDelivers(report(key = key(5), lat = latE6 + 4)) // the hourly total is spent
        assertEquals(3, radio.sentDatagrams.size)
        assertTrue(bridge.stats.value.rateLimited >= 2)

        clock += 3_600
        relayDelivers(report(key = key(6), lat = latE6 + 5, createdAt = clock - 10))
        assertEquals(4, radio.sentDatagrams.size)
    }

    @Test
    fun `one author cannot use the radio for more than its share`() {
        limits = MeshBridgeLimits(forwardPerHour = 50, forwardPerAuthorPerHour = 2, forwardCooldownSeconds = 1)
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        for (n in 1..4) {
            clock += 2
            relayDelivers(report(key = key(1), lat = latE6 + n))
        }
        assertEquals(2, radio.sentDatagrams.size)
        clock += 2
        relayDelivers(report(key = key(2), lat = latE6 + 9))
        assertEquals("another author still gets through", 3, radio.sentDatagrams.size)
    }

    // ── no loops, in any order ─────────────────────────────────────────────────────────────────

    @Test
    fun `radio to relay and back, the relay echo is not sent to the radio`() {
        config = MeshBridgeConfig(receive = true, send = true, meshToNostr = true, nostrToMesh = true)
        build()
        val event = report()
        radio.receive(frame(event))
        relayDelivers(relayEchoes().single())

        assertTrue("no storm", radio.sentDatagrams.isEmpty())
        assertEquals(1, relayEchoes().size)
        assertEquals(setOf(RoadEventTransport.MESH, RoadEventTransport.RELAY), service.events.value.single().transports)
    }

    @Test
    fun `relay to radio and back, the radio echo is not republished`() {
        config = MeshBridgeConfig(receive = true, send = true, meshToNostr = true, nostrToMesh = true)
        build()
        val event = report()
        relayDelivers(event)
        assertEquals(1, radio.sentDatagrams.size)
        radio.receive(frame(event)) // a repeater gives our own transmission back
        assertTrue(relayEchoes().isEmpty())
        assertEquals(0, queue.size())
        assertEquals(1, radio.sentDatagrams.size)
    }

    @Test
    fun `a report the relays already have is not republished when the radio brings it`() {
        config = MeshBridgeConfig(receive = true, meshToNostr = true)
        build()
        val event = report()
        relayDelivers(event)
        radio.receive(frame(event))
        assertTrue(relayEchoes().isEmpty())
        assertEquals(0, queue.size())
    }

    @Test
    fun `an own report heard back on the radio is not republished by the bridge`() {
        config = MeshBridgeConfig(receive = true, send = true, meshToNostr = true)
        build()
        val own = report()
        bridge.sendOwn(own)
        assertEquals(1, radio.sentDatagrams.size)
        radio.receive(frame(own))
        assertTrue(relayEchoes().isEmpty())
        assertEquals(0, queue.size())
    }

    @Test
    fun `an own report offered by both paths is transmitted once`() {
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        build()
        val own = report()
        relayDelivers(own)
        bridge.sendOwn(own)
        assertEquals(1, radio.sentDatagrams.size)
    }

    // ── this device's own events ───────────────────────────────────────────────────────────────

    @Test
    fun `own events go to the radio only with sending on, and are held until the radio is ready`() {
        config = MeshBridgeConfig(send = false)
        build()
        bridge.sendOwn(report())
        assertTrue(radio.sentDatagrams.isEmpty())

        config = MeshBridgeConfig(send = true)
        radio.dropLink()
        val first = report(key = key(1))
        val second = report(key = key(2), lat = latE6 + 1)
        bridge.sendOwn(first)
        bridge.sendOwn(second)
        assertTrue(radio.sentDatagrams.isEmpty())
        scheduler.advance(2_000) // the client reconnects
        bridge.onRadioReady()
        assertEquals(listOf(frame(first), frame(second)).map { it.toList() }, sentPayloads().map { it.toList() })
    }

    @Test
    fun `an own vote goes on the radio only once the radio knows its report`() {
        config = MeshBridgeConfig(receive = true, send = true)
        build()
        val theirs = report(key = TestKeys.PRIVATE_B)
        relayDelivers(theirs)
        bridge.sendOwn(vote(theirs, still = true, key = TestKeys.PRIVATE_A))
        assertTrue("the radio has not heard that report", radio.sentDatagrams.isEmpty())

        radio.receive(frame(theirs))
        bridge.sendOwn(vote(theirs, still = false, key = TestKeys.PRIVATE_A, at = clock - 5))
        assertEquals(134, sentPayloads().single().size)
    }

    @Test
    fun `own events are a loose quota, not a flood`() {
        limits = MeshBridgeLimits(ownPerMinute = 2)
        config = MeshBridgeConfig(send = true)
        build()
        for (n in 1..4) bridge.sendOwn(report(key = key(n), lat = latE6 + n))
        assertEquals(2, radio.sentDatagrams.size)
        assertEquals(2, bridge.stats.value.rateLimited)
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
    fun `a frame the radio refuses as too large is counted, not lost silently`() {
        config = MeshBridgeConfig(receive = true, send = true, nostrToMesh = true)
        radio.sendMode = FakeMeshCoreRadio.SendMode.IllegalArgument
        build()
        relayDelivers(report())
        assertEquals(1, bridge.stats.value.tooLarge)
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
