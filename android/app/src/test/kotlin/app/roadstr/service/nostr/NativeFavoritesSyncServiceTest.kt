package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.FavoritesSyncProtocol
import app.roadstr.core.protocol.nostr.NostrEventDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeFavoritesSyncServiceTest {
    private val network = FakeRelayNetwork()
    private val now = 1_800_003_700L
    private val signer = NativeLocalKeySigner { TestKeys.PRIVATE_A }
    private val places = listOf(
        mapOf<String, Any?>("label" to "Casa", "address" to "Rua Augusta 1", "lat" to 38.7107, "lon" to -9.1368),
        mapOf<String, Any?>("label" to "Lavoro", "address" to "", "lat" to 38.7, "lon" to -9.1),
    )

    private class MemoryStore : NativeFavoritesSyncStore {
        override var lastCreatedAt: Long? = null
        override var legacyCleaned: Boolean = false
    }

    private fun service(
        store: NativeFavoritesSyncStore = MemoryStore(),
        passphrase: String? = null,
        custom: String? = null,
        s: NativeNostrSigner = signer,
    ) = NativeFavoritesSyncService(
        signer = s,
        connector = network,
        store = store,
        passphrase = { passphrase },
        customRelay = { custom },
        nowSeconds = { now },
    )

    private fun snapshots(url: String = FavoritesSyncProtocol.defaultRelays.first()) =
        network.stored[url].orEmpty().filter { (it["kind"] as Number).toInt() == 30078 }

    @Test
    fun `a push is published to every default relay under the hashed tag at the top of the hour`() = runBlocking {
        val store = MemoryStore()
        assertTrue(service(store).push(places))

        for (url in FavoritesSyncProtocol.defaultRelays) {
            val event = snapshots(url).single { NativeNostrWire.tagValue(it, "d") == FavoritesSyncProtocol.hashedDTag(TestKeys.PUBLIC_A) }
            assertEquals(0L, (event["created_at"] as Number).toLong() % 3600)
            assertTrue(NativeNostrWire.verify(event))
            assertFalse("the plaintext must not be on the relay", (event["content"] as String).contains("Casa"))
        }
        assertNotNull(store.lastCreatedAt)
    }

    @Test
    fun `padding hides how many places there are`() = runBlocking {
        service().push(places.take(1))
        val small = (snapshots().first()["content"] as String).length
        val other = FakeRelayNetwork().also { }
        val bigger = NativeFavoritesSyncService(
            signer, other, MemoryStore(), { null }, { null }, { now },
        )
        bigger.push(places)
        val larger = (other.stored.values.first().first()["content"] as String).length
        assertEquals(small, larger)
    }

    @Test
    fun `a fresh device pulls what another pushed`() = runBlocking {
        service().push(places)
        val pulled = service(store = MemoryStore()).pull()

        assertTrue(pulled is NativeFavoritesPull.Ok)
        assertEquals(
            listOf("Casa", "Lavoro"),
            (pulled as NativeFavoritesPull.Ok).favorites.map { it["label"] },
        )
    }

    @Test
    fun `a snapshot older than the local high-water mark is ignored`() = runBlocking {
        service().push(places)
        val ahead = MemoryStore().also { it.lastCreatedAt = now + 10 * 3600 }
        assertEquals(NativeFavoritesPull.None, service(store = ahead).pull())
    }

    @Test
    fun `a passphrase adds an independent layer and a wrong one is reported as locked`() = runBlocking {
        assertTrue(service(passphrase = "hunter2").push(places))

        assertEquals(NativeFavoritesPull.Locked, service(store = MemoryStore(), passphrase = null).pull())
        assertEquals(NativeFavoritesPull.Locked, service(store = MemoryStore(), passphrase = "nope").pull())
        val opened = service(store = MemoryStore(), passphrase = "hunter2").pull()
        assertEquals(2, (opened as NativeFavoritesPull.Ok).favorites.size)
    }

    @Test
    fun `another account's snapshot under our tag and forged ones are never used`() = runBlocking {
        val tag = FavoritesSyncProtocol.hashedDTag(TestKeys.PUBLIC_A)
        val stranger = TestKeys.sign(
            FavoritesSyncProtocol.snapshotDraft(TestKeys.PUBLIC_B, now, "x").let {
                NostrEventDraft(it.pubkey, it.createdAt, it.kind, listOf(listOf("d", tag)), it.content)
            },
            TestKeys.PRIVATE_B,
        )
        network.stored.getOrPut(FavoritesSyncProtocol.defaultRelays.first()) { mutableListOf() } += stranger
        val forged = TestKeys.sign(
            FavoritesSyncProtocol.snapshotDraft(TestKeys.PUBLIC_A, now, "x"),
        ).toMutableMap().also { it["sig"] = "0".repeat(128) }
        network.stored.getOrPut(FavoritesSyncProtocol.defaultRelays[1]) { mutableListOf() } += forged

        assertEquals(NativeFavoritesPull.NotFound, service().pull())
    }

    @Test
    fun `a snapshot from the old fixed tag is still found`() = runBlocking {
        val inner = signer.nip44Encrypt(
            TestKeys.PUBLIC_A,
            FavoritesSyncProtocol.padToBucket(FavoritesSyncProtocol.encodeFavorites(places)),
        )!!
        val legacy = TestKeys.sign(
            NostrEventDraft(
                TestKeys.PUBLIC_A, now - 7200, 30078,
                listOf(listOf("d", FavoritesSyncProtocol.LEGACY_D_TAG)), inner,
            ),
        )
        network.stored.getOrPut(FavoritesSyncProtocol.defaultRelays.last()) { mutableListOf() } += legacy

        val pulled = service().pull()
        assertEquals(2, (pulled as NativeFavoritesPull.Ok).favorites.size)
    }

    @Test
    fun `a relay that is down does not stop the others`() = runBlocking {
        network.down += FavoritesSyncProtocol.defaultRelays[0]
        network.down += FavoritesSyncProtocol.defaultRelays[1]
        assertTrue(service().push(places))
        val live = snapshots(FavoritesSyncProtocol.defaultRelays[2]).filter {
            NativeNostrWire.tagValue(it, "d") == FavoritesSyncProtocol.hashedDTag(TestKeys.PUBLIC_A)
        }
        assertEquals(1, live.size)
        assertTrue(service(store = MemoryStore()).pull() is NativeFavoritesPull.Ok)
    }

    @Test
    fun `a custom relay is added only if it is a valid wss address`() {
        assertEquals(
            FavoritesSyncProtocol.defaultRelays + "wss://my.relay.example",
            service(custom = "wss://my.relay.example/").relays(),
        )
        assertEquals(FavoritesSyncProtocol.defaultRelays, service(custom = "ws://my.relay.example").relays())
        assertEquals(FavoritesSyncProtocol.defaultRelays, service(custom = "wss://localhost").relays())
        assertEquals(FavoritesSyncProtocol.defaultRelays, service(custom = null).relays())
    }

    @Test
    fun `the obsolete fixed-tag snapshot is wiped once, and only when signing is silent`() = runBlocking {
        val store = MemoryStore()
        service(store).push(places)
        val relay = FavoritesSyncProtocol.defaultRelays.first()
        assertTrue(store.legacyCleaned)
        assertTrue(network.stored[relay].orEmpty().any { (it["kind"] as Number).toInt() == 5 })
        val wiped = network.stored[relay].orEmpty().single {
            NativeNostrWire.tagValue(it, "d") == FavoritesSyncProtocol.LEGACY_D_TAG
        }
        assertEquals("", wiped["content"])

        val interactive = object : NativeNostrSigner by signer {
            override val signsSilently = false
        }
        val other = FakeRelayNetwork()
        val interactiveStore = MemoryStore()
        NativeFavoritesSyncService(interactive, other, interactiveStore, { null }, { null }, { now }).push(places)
        assertFalse("no extra popups for an external signer", interactiveStore.legacyCleaned)
    }

    @Test
    fun `logged out nothing is sent`() = runBlocking {
        val loggedOut = NativeLocalKeySigner { null }
        assertFalse(service(s = loggedOut).push(places))
        assertEquals(NativeFavoritesPull.None, service(s = loggedOut).pull())
        assertTrue(network.connections.isEmpty())
    }

    @Test
    fun `a snapshot too large for NIP-44 is refused`() = runBlocking {
        val huge = (1..400).map {
            mapOf<String, Any?>("label" to "L$it", "address" to "a".repeat(450), "lat" to 1.0, "lon" to 2.0)
        }
        assertFalse(service().push(huge))
        assertTrue(network.stored.isEmpty())
    }

    @Test
    fun `an account that never published reads as not found, not as a failure`() = runBlocking {
        assertEquals(NativeFavoritesPull.NotFound, service().pull())
    }
}
