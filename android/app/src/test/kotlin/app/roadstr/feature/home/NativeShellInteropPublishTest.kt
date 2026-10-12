package app.roadstr.feature.home

import app.roadstr.core.protocol.interop.CompactError
import app.roadstr.core.protocol.interop.CompactOutcome
import app.roadstr.core.protocol.interop.CompactRoadCodec
import app.roadstr.core.protocol.interop.InteropPublishPolicy
import app.roadstr.core.protocol.interop.RoadEventLayout
import app.roadstr.core.protocol.interop.ExternalRoadReportAdapter
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
import app.roadstr.service.nostr.NativeRoadEventProtocol
import app.roadstr.service.nostr.NativeRoadEventService
import app.roadstr.service.nostr.TestKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sending a report also in the jooray/roadstr layout, and voting in the layout of the report voted on. */
class NativeShellInteropPublishTest {
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

    private fun controller(n: NativeShellNostr, policy: InteropPublishPolicy) = NativeShellReportController(
        nostr = n,
        scope = scope,
        session = session,
        nowSeconds = { now },
        message = { m, _ -> messages += m },
        interopPolicy = { policy },
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
    fun `by default only the native event is sent`() = runBlocking {
        report(controller(nostr(), InteropPublishPolicy.OFF), RoadCategoryWire.POLICE)
        assertEquals(1, sent().size)
        assertEquals(1, signCalls)
        assertTrue(heard.isEmpty())
    }

    @Test
    fun `with the switch on a second, separately signed event in the compatible layout follows`() = runBlocking {
        val n = nostr()
        report(controller(n, InteropPublishPolicy(enabled = true)), RoadCategoryWire.POLICE)

        assertEquals(2, sent().size)
        assertEquals(2, signCalls)
        val native = sent()[0]
        val compat = sent()[1]
        assertEquals(RoadEventLayout.ROADSTR_NATIVE, ExternalRoadReportAdapter.layoutOf(native))
        assertEquals(RoadEventLayout.COMPACT_CANONICAL, ExternalRoadReportAdapter.layoutOf(compat))
        assertNotEquals("a different layout is a different event", native["id"], compat["id"])
        assertEquals(TestKeys.PUBLIC_A, compat["pubkey"])
        // It rebuilds into a frame and back with the same ID and signature.
        val frame = (CompactRoadCodec.encodeReport(compat) as CompactOutcome.Ok).value
        assertEquals(110, frame.size)
        assertEquals(listOf(compat["id"]), heard.map { it["id"] })
        // One marker for the two.
        assertEquals(listOf(native["id"]), n.roadEvents.events.value.map { it.id })
        assertEquals(NativeRoadEventProtocol.Roadstr, n.roadEvents.events.value.single().protocol)
    }

    @Test
    fun `a one time key signs the copy without bothering the account signer`() = runBlocking {
        val n = nostr()
        report(controller(n, InteropPublishPolicy(enabled = true, ephemeralKey = true)), RoadCategoryWire.HAZARD)

        assertEquals(2, sent().size)
        assertEquals("only the native event needed the account", 1, signCalls)
        val compat = sent()[1]
        assertNotEquals(TestKeys.PUBLIC_A, compat["pubkey"])
        assertEquals(RoadEventLayout.COMPACT_CANONICAL, ExternalRoadReportAdapter.layoutOf(compat))
        // Made by this device, so it is still one marker here even though the key differs.
        assertEquals(1, n.roadEvents.events.value.size)
    }

    @Test
    fun `a comment travels to relays in the copy but the copy cannot become a radio frame`() = runBlocking {
        report(controller(nostr(), InteropPublishPolicy(enabled = true)), RoadCategoryWire.ACCIDENT, comment = "corsia di destra")
        val compat = sent()[1]
        assertEquals("corsia di destra", compat["content"])
        assertEquals(CompactOutcome.Failed(CompactError.NON_EMPTY_CONTENT), CompactRoadCodec.encode(compat))
    }

    @Test
    fun `a category the format does not have is only sent natively`() = runBlocking {
        report(controller(nostr(), InteropPublishPolicy(enabled = true)), RoadCategoryWire.POLICE_STATION)
        assertEquals(1, sent().size)
        assertTrue(messages.none { it == NativeShellMessage.InteropPublishFailed })
    }

    @Test
    fun `a refused second signature leaves the native report published and says so`() = runBlocking {
        val inner = NativeLocalKeySigner { TestKeys.PRIVATE_A }
        var calls = 0
        val second = object : NativeNostrSigner by inner {
            override suspend fun sign(draft: NostrEventDraft): Map<String, Any?>? =
                if (++calls == 1) inner.sign(draft) else null
        }
        report(controller(nostr(second), InteropPublishPolicy(enabled = true)), RoadCategoryWire.FOG)
        assertEquals(1, sent().size)
        assertTrue(messages.contains(NativeShellMessage.ReportPublished))
        assertTrue(messages.contains(NativeShellMessage.InteropPublishFailed))
    }

    @Test
    fun `an unreachable relay queues both events`() = runBlocking {
        network.down += "wss://p1.example"
        report(controller(nostr(), InteropPublishPolicy(enabled = true)), RoadCategoryWire.ICE)
        assertEquals(2, queue.size)
        assertEquals(setOf(NativeShellMessage.ReportQueued), messages.toSet() - setOf())
    }

    @Test
    fun `a vote on a compatible report uses the compatible confirmation, a vote on a native one does not`() = runBlocking {
        val n = nostr()
        val c = controller(n, InteropPublishPolicy.OFF)
        // A compatible report from someone else, accepted off the radio.
        val theirs = TestKeys.sign(
            CompactRoadCodec.reportDraft(TestKeys.PUBLIC_B, now - 100, 387_223_000, -91_393_000, app.roadstr.core.protocol.interop.CompactRoadType.POLICE),
            TestKeys.PRIVATE_B,
        )
        assertTrue(n.roadEvents.acceptInteropEvent(theirs, app.roadstr.core.protocol.interop.RoadEventTransport.MESH))

        c.vote(theirs["id"] as String, true)
        val confirmation = sent().single()
        assertEquals(1316, (confirmation["kind"] as Number).toInt())
        val tagNames = (confirmation["tags"] as List<*>).map { (it as List<*>)[0] }
        assertEquals(listOf("e", "g", "g", "g", "status", "lat", "lon", "expiration", "alt"), tagNames)
        assertEquals(listOf(confirmation["id"]), heard.map { it["id"] })

        heard.clear()
        c.vote("c".repeat(64), true) // unknown to the map: Roadstr's own two-tag vote
        val plain = sent().last()
        assertEquals(listOf("e", "status"), (plain["tags"] as List<*>).map { (it as List<*>)[0] })
        assertTrue(heard.isEmpty())
        assertFalse(sent().size < 2)
    }
}
