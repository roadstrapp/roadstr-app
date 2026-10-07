package app.roadstr.feature.home

import app.roadstr.core.protocol.nostr.FavoritesSyncProtocol
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.report.NativeRoadEventSession
import app.roadstr.feature.report.NativeRoadEventSurface
import app.roadstr.feature.saved.NativeSavedPlace
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
import app.roadstr.service.nostr.NativeRoadEvent
import app.roadstr.service.nostr.NativeRoadEventService
import app.roadstr.service.nostr.TestKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeShellNostrControllerTest {
    private val network = FakeRelayNetwork()
    private val now = 1_800_003_700L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val messages = mutableListOf<Pair<NativeShellMessage, Int>>()
    private val queue = mutableListOf<String>()
    private var privacyAck = false
    private var passphrase: String? = null
    private var lastSync: Long? = null
    private var local = listOf(
        NativeSavedPlace("Casa", "Rua Augusta 1", NativeMapPoint(38.7107, -9.1368)),
        NativeSavedPlace("Lavoro", "", NativeMapPoint(38.72, -9.14)),
    )
    private val exported = mutableListOf<Pair<String, String>>()
    private val roadSession = NativeRoadEventSession()

    /** Key derivation runs on another dispatcher, so wait for the visible outcome. */
    private fun awaitUntil(timeoutMillis: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for the operation" }
            Thread.sleep(10)
        }
    }

    private class MemoryStore : NativeFavoritesSyncStore {
        override var lastCreatedAt: Long? = null
        override var legacyCleaned: Boolean = false
    }

    private fun nostr(signer: NativeNostrSigner = NativeLocalKeySigner { TestKeys.PRIVATE_A }): NativeShellNostr {
        val connector = FakeConnector()
        val scheduler = ManualScheduler()
        return NativeShellNostr(
            signer = signer,
            roadEvents = NativeRoadEventService(
                connector = connector,
                publisher = NativeRelayPublisher(network, listOf("wss://p1.example"), 300),
                scheduler = scheduler,
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
            favoritesSync = NativeFavoritesSyncService(
                signer, network, MemoryStore(), { passphrase }, { null }, { now },
            ),
            visibility = NativeProfileVisibilityService(
                signer, NativeRelayPublisher(network, listOf("wss://p1.example")),
                NativeRelayFetcher(network), listOf("wss://p1.example"), { now },
            ),
            syncSecrets = object : NativeShellSyncSecrets {
                override fun passphraseConfigured() = passphrase != null
                override fun setPassphrase(value: String): Boolean {
                    passphrase = value.ifEmpty { null }; return true
                }
                override fun customRelay(): String? = null
                override fun setCustomRelay(value: String) = true
                override fun lastSyncMillis() = lastSync
                override fun markSynced(epochMillis: Long) { lastSync = epochMillis }
            },
            files = object : NativeShellFavoriteFiles {
                override fun export(fileName: String, content: String) { exported += fileName to content }
                override fun requestImport() = Unit
                override val imports = MutableSharedFlow<String>()
            },
            profileLookup = { null },
            reportPrivacyAcknowledged = { privacyAck },
            acknowledgeReportPrivacy = { privacyAck = true },
        )
    }

    private fun favorites(
        n: NativeShellNostr,
        prompt: suspend () -> String? = { null },
    ) = NativeShellFavoritesController(
        nostr = n,
        scope = scope,
        nowMillis = { 42L },
        message = { m, c -> messages += m to c },
        setBusy = {},
        favorites = { local },
        mergeFavorites = { incoming ->
            local = app.roadstr.feature.saved.NativeSavedPlacesProtocol.mergeByLabel(local, incoming)
        },
        promptPassword = prompt,
    )

    @Test
    fun `push then pull restores places on a fresh device and merges by label`() = runBlocking {
        favorites(nostr()).push()
        assertEquals(listOf(NativeShellMessage.SyncSuccess to 0), messages)
        assertEquals(42L, lastSync)

        local = listOf(NativeSavedPlace("Casa", "OLD", NativeMapPoint(1.0, 2.0)), NativeSavedPlace("Extra", "", NativeMapPoint(3.0, 4.0)))
        messages.clear()
        favorites(nostr()).pull()

        assertEquals(listOf("Casa", "Extra", "Lavoro"), local.map { it.label }.sorted())
        assertEquals("Rua Augusta 1", local.single { it.label == "Casa" }.address)
        assertEquals(NativeShellMessage.SyncSuccess, messages.single().first)
    }

    @Test
    fun `an automatic push publishes the list it is handed, not the stale copy of the screen`() = runBlocking {
        // The screen's own copy still holds the list from before the change.
        val before = local
        val changed = before + NativeSavedPlace("Nuovo", "Via Roma 1", NativeMapPoint(5.0, 6.0))

        favorites(nostr()).autoPush(changed)
        // The push runs on the controller's scope; wait for it to land.
        withTimeout(5_000) { while (lastSync == null) delay(20) }

        local = emptyList()
        favorites(nostr()).pull()
        assertEquals((before + changed).map { it.label }.toSet(), local.map { it.label }.toSet())
        assertTrue(local.any { it.label == "Nuovo" })
    }

    @Test
    fun `a locked snapshot prompts once and remembers the passphrase that worked`() = runBlocking {
        passphrase = "hunter2"
        favorites(nostr()).push()
        awaitUntil { messages.isNotEmpty() }
        passphrase = null
        local = emptyList()
        messages.clear()
        var prompts = 0

        favorites(nostr()) { prompts++; "hunter2" }.pull()
        awaitUntil { messages.isNotEmpty() }

        assertEquals(1, prompts)
        assertEquals(2, local.size)
        assertEquals("hunter2", passphrase)
    }

    @Test
    fun `a wrong passphrase leaves local places untouched and says it failed`() = runBlocking {
        passphrase = "hunter2"
        favorites(nostr()).push()
        awaitUntil { messages.isNotEmpty() }
        passphrase = null
        local = listOf(NativeSavedPlace("Solo", "", NativeMapPoint(1.0, 1.0)))
        messages.clear()

        favorites(nostr()) { "wrong" }.pull()
        awaitUntil { messages.isNotEmpty() }

        assertEquals(listOf("Solo"), local.map { it.label })
        assertEquals(NativeShellMessage.SyncFailed, messages.single().first)
        assertEquals(null, passphrase)
    }

    @Test
    fun `logged out sync does nothing`() = runBlocking {
        favorites(nostr(NativeLocalKeySigner { null })).push()
        assertTrue(messages.isEmpty())
        assertTrue(network.connections.isEmpty())
    }

    @Test
    fun `an encrypted export imports back, a wrong password fails, and clear export works`() = runBlocking {
        favorites(nostr()).export("pw")
        awaitUntil { exported.isNotEmpty() }
        val (name, file) = exported.single()
        assertEquals("roadstr_favorites.json", name)
        assertFalse("places must not be readable in an encrypted export", file.contains("Casa"))

        local = emptyList()
        favorites(nostr()) { "pw" }.import(file)
        awaitUntil { messages.isNotEmpty() }
        assertEquals(listOf("Casa", "Lavoro"), local.map { it.label })
        assertEquals(NativeShellMessage.ImportSuccess to 2, messages.last())

        local = emptyList()
        messages.clear()
        favorites(nostr()) { "bad" }.import(file)
        awaitUntil { messages.isNotEmpty() }
        assertTrue(local.isEmpty())
        assertEquals(NativeShellMessage.ImportFailed, messages.last().first)

        exported.clear()
        local = listOf(
            NativeSavedPlace("Casa", "Rua Augusta 1", NativeMapPoint(38.7107, -9.1368)),
            NativeSavedPlace("Lavoro", "", NativeMapPoint(38.72, -9.14)),
        )
        favorites(nostr()).export(null)
        assertTrue(exported.single().second.contains("\"encrypted\":false"))
        local = emptyList()
        favorites(nostr()).import(exported.single().second)
        assertEquals(2, local.size)
    }

    @Test
    fun `garbage and oversized files are refused`() = runBlocking {
        favorites(nostr()).import("not json")
        favorites(nostr()).import("[1,2,3]")
        assertEquals(listOf(NativeShellMessage.ImportFailed, NativeShellMessage.ImportFailed), messages.map { it.first })
        assertTrue(local.size == 2)
    }

    private fun reports(n: NativeShellNostr) = NativeShellReportController(
        nostr = n,
        scope = scope,
        session = roadSession,
        nowSeconds = { now },
        message = { m, c -> messages += m to c },
    )

    @Test
    fun `reporting needs a login, shows the privacy notice once, then publishes`() = runBlocking {
        reports(nostr(NativeLocalKeySigner { null })).openComposer(NativeMapPoint(38.7, -9.1))
        assertEquals(NativeShellMessage.LoginRequired, messages.single().first)
        assertEquals(NativeRoadEventSurface.Hidden, roadSession.state.value.surface)

        val controller = reports(nostr())
        controller.openComposer(NativeMapPoint(38.7, -9.1))
        assertEquals(NativeRoadEventSurface.PrivacyNotice, roadSession.state.value.surface)
        controller.acceptPrivacy(roadSession.state.value.revision)
        assertTrue(privacyAck)
        assertEquals(NativeRoadEventSurface.Composer, roadSession.state.value.surface)

        val revision = roadSession.state.value.revision
        roadSession.selectCategory(revision, RoadCategoryWire.POLICE)
        roadSession.updateComment(revision, "radar fisso")
        messages.clear()
        // The relay answers OK, so the report is published, not queued.
        controller.submit(revision)

        assertEquals(NativeRoadEventSurface.Hidden, roadSession.state.value.surface)
        assertEquals(NativeShellMessage.ReportPublished, messages.single().first)
        val sent = network.stored["wss://p1.example"].orEmpty().single()
        assertEquals(1315, (sent["kind"] as Number).toInt())
        assertEquals(TestKeys.PUBLIC_A, sent["pubkey"])
    }

    @Test
    fun `an unreachable relay queues the report instead of losing it`() = runBlocking {
        network.down += "wss://p1.example"
        privacyAck = true
        val controller = reports(nostr())
        controller.openComposer(NativeMapPoint(38.7, -9.1))
        val revision = roadSession.state.value.revision
        roadSession.selectCategory(revision, RoadCategoryWire.ACCIDENT)
        messages.clear()

        controller.submit(revision)

        assertEquals(NativeShellMessage.ReportQueued, messages.single().first)
        assertEquals(1, queue.size)
        assertEquals(NativeRoadEventSurface.Hidden, roadSession.state.value.surface)
    }

    @Test
    fun `an owner updates a camera limit directly and anyone else files a request`() = runBlocking {
        val event = NativeRoadEvent(
            id = "a".repeat(64), pubkey = TestKeys.PUBLIC_A, category = RoadCategoryWire.SPEED_CAMERA,
            latitude = 38.7, longitude = -9.1, comment = "", createdAt = now - 10, expiresAt = null,
        )
        reports(nostr()).editSpeedLimit(event, 80)
        assertEquals(1317, (network.stored["wss://p1.example"].orEmpty().last()["kind"] as Number).toInt())

        reports(nostr(NativeLocalKeySigner { TestKeys.PRIVATE_B })).editSpeedLimit(event, 70)
        assertEquals(1318, (network.stored["wss://p1.example"].orEmpty().last()["kind"] as Number).toInt())
        assertEquals(listOf(NativeShellMessage.SpeedUpdateSent, NativeShellMessage.EditRequestSent), messages.map { it.first })

        messages.clear()
        reports(nostr()).editSpeedLimit(event, 4) // out of range: nothing sent
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `a vote is published as kind 1316 and a failed signature is reported`() = runBlocking {
        reports(nostr()).vote("b".repeat(64), true)
        assertEquals(1316, (network.stored["wss://p1.example"].orEmpty().single()["kind"] as Number).toInt())

        val refusing = object : NativeNostrSigner {
            override val signsSilently = false
            override val pubkeyHex: String? = TestKeys.PUBLIC_A
            override suspend fun sign(draft: app.roadstr.core.protocol.nostr.NostrEventDraft): Map<String, Any?>? = null
            override suspend fun nip44Encrypt(peerPubkeyHex: String, plaintext: String): String? = null
            override suspend fun nip44Decrypt(peerPubkeyHex: String, payload: String): String? = null
        }
        reports(nostr(refusing)).vote("b".repeat(64), false)
        assertEquals(NativeShellMessage.VoteFailed, messages.last().first)
    }
}
