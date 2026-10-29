package app.roadstr.feature.home

import app.roadstr.core.protocol.interop.CompactOutcome
import app.roadstr.core.protocol.interop.CompactRoadCodec
import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.report.NativeRoadEventSession
import app.roadstr.service.nostr.FakeConnector
import app.roadstr.service.nostr.FakeRelayNetwork
import app.roadstr.service.nostr.ManualScheduler
import app.roadstr.service.nostr.NativeFavoritesSyncService
import app.roadstr.service.nostr.NativeFavoritesSyncStore
import app.roadstr.service.nostr.NativeLocalKeySigner
import app.roadstr.service.nostr.NativeNostrSigner
import app.roadstr.service.nostr.NativePendingReportStorage
import app.roadstr.service.nostr.NativeProfileVisibilityService
import app.roadstr.service.nostr.NativeRelayFetcher
import app.roadstr.service.nostr.NativeRelayPublisher
import app.roadstr.service.nostr.NativeRoadEventService
import app.roadstr.service.nostr.TestKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** This device's own reports and votes: one event each, in the shared layout, offered to the radio bridge. */
class NativeShellOwnRoadEventsTest {
    private val network = FakeRelayNetwork()
    private val now = 1_800_003_700L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val messages = mutableListOf<NativeShellMessage>()
    private val queue = mutableListOf<String>()
    private val session = NativeRoadEventSession()
    private val heard = mutableListOf<Map<String, Any?>>()
    private var signCalls = 0

    private class MemoryStore : NativeFavoritesSyncStore {
        override var lastCreatedAt: Long? = null
        override var legacyCleaned: Boolean = false
    }

    /** Counts how many times the account's signer was asked: Amber/bunker users feel each one. */
    private fun countingSigner(): NativeNostrSigner {
        val inner = NativeLocalKeySigner { TestKeys.PRIVATE_A }
        return object : NativeNostrSigner by inner {
            override suspend fun sign(draft: NostrEventDraft): Map<String, Any?>? {
                signCalls++
                return inner.sign(draft)
            }
        }
    }

    private fun nostr(signer: NativeNostrSigner = countingSigner()): NativeShellNostr {
        val connector = FakeConnector()
        return NativeShellNostr(
            signer = signer,
            roadEvents = NativeRoadEventService(
                connector = connector,
                publisher = NativeRelayPublisher(network, listOf("wss://p1.example"), 300),
                scheduler = ManualScheduler(),
                relays = listOf("wss://a.example"),
                pendingStorage = object : NativePendingReportStorage {
                    override fun read() = queue.toList()
                    override fun write(rows: List<String>) {
                        queue.clear(); queue += rows
                    }
                },
                nowSeconds = { now },
                scope = scope,
            ),
            favoritesSync = NativeFavoritesSyncService(signer, network, MemoryStore(), { null }, { null }, { now }),
            visibility = NativeProfileVisibilityService(
                signer, NativeRelayPublisher(network, listOf("wss://p1.example")),
                NativeRelayFetcher(network), listOf("wss://p1.example"), { now },
            ),
            syncSecrets = object : NativeShellSyncSecrets {
                override fun passphraseConfigured() = false
                override fun setPassphrase(value: String) = true
                override fun customRelay(): String? = null
                override fun setCustomRelay(value: String) = true
                override fun lastSyncMillis(): Long? = null
                override fun markSynced(epochMillis: Long) = Unit
            },
            files = object : NativeShellFavoriteFiles {
                override fun export(fileName: String, content: String) = Unit
                override fun requestImport() = Unit
                override val imports = MutableSharedFlow<String>()
            },
            profileLookup = { null },
            reportPrivacyAcknowledged = { true },
            acknowledgeReportPrivacy = {},
        )
    }

    private fun controller(n: NativeShellNostr) = NativeShellReportController(
        nostr = n,
        scope = scope,
        session = session,
        nowSeconds = { now },
        message = { m, _ -> messages += m },
    ).also { it.onCompatibleEvent = { event -> heard += event } }

    private fun report(c: NativeShellReportController, category: RoadCategoryWire, comment: String = "") {
        c.openComposer(NativeMapPoint(38.7223, -9.1393))
        val revision = session.state.value.revision
        session.selectCategory(revision, category)
        if (comment.isNotEmpty()) session.updateComment(revision, comment)
        c.submit(revision)
    }

    private fun sent(): List<Map<String, Any?>> = network.stored["wss://p1.example"].orEmpty()

    @Test
    fun `a report is one event in the shared layout, and the bridge hears it`() = runBlocking {
        val n = nostr()
        report(controller(n), RoadCategoryWire.POLICE)

        val event = sent().single()
        assertEquals(1, signCalls)
        val tagNames = (event["tags"] as List<*>).map { (it as List<*>)[0] }
        assertEquals(listOf("lat", "lon", "g", "g", "g", "t", "expiration"), tagNames)
        assertEquals(listOf(event["id"]), heard.map { it["id"] })
        assertEquals(listOf(event["id"]), n.roadEvents.events.value.map { it.id })
        // It rebuilds into a frame and back with the same ID and signature.
        val frame = (CompactRoadCodec.encodeReport(event) as CompactOutcome.Ok).value
        assertEquals(117, frame.size)
    }

    @Test
    fun `relays keep a report at least 14 days, and a 30-day category for its 30 days`() = runBlocking {
        report(controller(nostr()), RoadCategoryWire.POLICE)
        report(controller(nostr()), RoadCategoryWire.SPEED_CAMERA)
        val expirations = sent().map { event ->
            val tags = (event["tags"] as List<*>).map { it as List<*> }
            (tags.first { it[0] == "expiration" }[1] as String).toLong() - (event["created_at"] as Number).toLong()
        }
        assertEquals(listOf(CompactRoadCodec.RELAY_TTL_SECONDS, 30 * 86_400L), expirations)
    }

    @Test
    fun `a report with a long comment still goes to the relays and is offered to the bridge`() = runBlocking {
        report(controller(nostr()), RoadCategoryWire.ACCIDENT, comment = "corsia di destra chiusa dopo il ponte, coda lunga")
        val event = sent().single()
        assertEquals(1, heard.size)
        // Over 46 bytes: valid on relays, never framed. The bridge counts it as not representable.
        assertEquals(CompactOutcome.Failed(app.roadstr.core.protocol.interop.CompactError.COMMENT_TOO_LONG), CompactRoadCodec.encodeReport(event))
    }

    @Test
    fun `an unreachable relay queues the one event`() = runBlocking {
        network.down += "wss://p1.example"
        report(controller(nostr()), RoadCategoryWire.ICE)
        assertEquals(1, queue.size)
        assertEquals(setOf(NativeShellMessage.ReportQueued), messages.toSet())
        assertEquals(1, heard.size)
    }

    @Test
    fun `a vote is two tags whatever report it is on, and the bridge hears it`() = runBlocking {
        val n = nostr()
        val c = controller(n)
        // A report from someone else, accepted off the radio.
        val theirs = TestKeys.sign(
            CompactRoadCodec.reportDraft(TestKeys.PUBLIC_B, now - 100, 38_722_300, -9_139_300, app.roadstr.core.protocol.interop.CompactRoadType.POLICE),
            TestKeys.PRIVATE_B,
        )
        assertTrue(n.roadEvents.acceptInteropEvent(theirs, app.roadstr.core.protocol.interop.RoadEventTransport.MESH))

        c.vote(theirs["id"] as String, true)
        c.vote("c".repeat(64), false)
        val votes = sent()
        assertEquals(2, votes.size)
        for (vote in votes) {
            assertEquals(1316, (vote["kind"] as Number).toInt())
            assertEquals(listOf("e", "status"), (vote["tags"] as List<*>).map { (it as List<*>)[0] })
        }
        assertEquals(votes.map { it["id"] }, heard.map { it["id"] })
        assertEquals(1, n.roadEvents.events.value.single().confirmations)
    }
}
