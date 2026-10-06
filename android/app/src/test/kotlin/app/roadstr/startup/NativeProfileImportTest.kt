package app.roadstr.startup

import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
import app.roadstr.feature.activity.NativeActivityInboxProtocol
import app.roadstr.feature.activity.NativeActivityNotification
import app.roadstr.feature.activity.NativeActivityNotificationType
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.onboarding.NativeMigrationReadiness
import app.roadstr.feature.profile.NativeProfileIdentityFlavor
import app.roadstr.feature.saved.NativeParkingPosition
import app.roadstr.feature.saved.NativeSavedPlace
import app.roadstr.feature.saved.NativeSavedPlacesProtocol
import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.migration.LegacyAsset
import app.roadstr.migration.LegacyIdentity
import app.roadstr.migration.LegacySnapshotReader
import app.roadstr.migration.LegacyStorageSnapshot
import app.roadstr.migration.TransactionalMigration
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProfileImportTest {
    @Test
    fun `a complete legacy profile is mapped part by part`() {
        val profile = NativeProfileMapper.map(snapshot())

        assertEquals("it", profile.settings.languageCode)
        assertTrue(profile.settings.themeId.dark)
        assertTrue(profile.onboardingCompleted)
        assertTrue(profile.reportPrivacyAcknowledged)
        assertEquals(FAVORITES, profile.favorites)
        assertEquals(PARKING, profile.parking)
        assertEquals(HISTORY, profile.searchHistory)
        assertEquals(listOf(PENDING_ROW), profile.pendingReports)
        assertEquals(
            ImportedSync("a passphrase", "wss://relay.example.org", 1_700_000_000L, true, 1_700_000_001_000L),
            profile.sync,
        )
        val identity = profile.identity!!
        assertEquals(PUBLIC_KEY, identity.publicKeyHex)
        assertEquals(NativeProfileIdentityFlavor.Nsec, identity.flavor)
        assertEquals(PRIVATE_KEY, identity.privateKeyHex)
        assertEquals("Verona Driver", identity.name)
        assertEquals(NWC, profile.nwcUri)
        assertEquals("routing-key", profile.routingApiKey)
    }

    @Test
    fun `the privacy disclosure alone decides whether onboarding is done`() {
        val others = snapshot(ordinary = ORDINARY - "privacy_disclosure_v2")

        assertFalse(NativeProfileMapper.map(others).onboardingCompleted)
        assertFalse(NativeProfileMapper.map(snapshot(ordinary = emptyMap())).onboardingCompleted)
        assertFalse(NativeProfileMapper.map(snapshot(ordinary = ORDINARY + ("privacy_disclosure_v2" to "false"))).onboardingCompleted)
    }

    @Test
    fun `an amber identity carries no private key and no identity means none is written`() {
        val amber = snapshot(
            secure = SECURE - "nostr_priv_hex" + ("nostr_flavor" to "amber"),
            identity = LegacyIdentity(PUBLIC_KEY, "amber", null),
        )

        val identity = NativeProfileMapper.map(amber).identity!!
        assertEquals(NativeProfileIdentityFlavor.Amber, identity.flavor)
        assertNull(identity.privateKeyHex)
        assertNull(NativeProfileMapper.map(snapshot(secure = emptyMap(), identity = LegacyIdentity(null, null, null))).identity)
    }

    @Test
    fun `an identity flavor nobody wrote is refused`() {
        val broken = snapshot(identity = LegacyIdentity(PUBLIC_KEY, "oauth", null))

        assertEquals("identity", assertThrows(ProfileImportException::class.java) { NativeProfileMapper.map(broken) }.part)
    }

    @Test
    fun `secure storage wins over the older plain copies of the same secrets`() {
        val both = snapshot(
            ordinary = ORDINARY + mapOf("nwcUri" to "plain-nwc", "graphhopperApiKey" to "plain-key", "fav_sync_pass" to "plain-pass"),
        )
        val plainOnly = snapshot(
            ordinary = ORDINARY + mapOf("nwcUri" to "plain-nwc", "graphhopperApiKey" to "plain-key", "fav_sync_pass" to "plain-pass"),
            secure = SECURE - "nwc_uri" - "routing_api_key" - "favorites_sync_passphrase",
        )

        assertEquals(NWC, NativeProfileMapper.map(both).nwcUri)
        val fallback = NativeProfileMapper.map(plainOnly)
        assertEquals("plain-nwc", fallback.nwcUri)
        assertEquals("plain-key", fallback.routingApiKey)
        assertEquals("plain-pass", fallback.sync.passphrase)
    }

    @Test
    fun `a bad setting falls back to the defaults and does not block the update`() {
        val profile = NativeProfileMapper.map(snapshot(ordinary = ORDINARY + ("autoDark" to "maybe")))

        assertEquals(NativeSettingsInput(), profile.settings)
        assertEquals(FAVORITES, profile.favorites)
    }

    @Test
    fun `an unreadable history starts empty`() {
        val profile = NativeProfileMapper.map(snapshot(ordinary = ORDINARY + ("searchHistory" to "not json")))

        assertTrue(profile.searchHistory.isEmpty())
    }

    @Test
    fun `activity keeps its cursors and a damaged inbox becomes an empty one`() {
        val profile = NativeProfileMapper.map(
            snapshot(
                ordinary = ORDINARY + mapOf(
                    "activity_inbox_$PUBLIC_KEY" to "malformed-json",
                    "activity_zap_cursor_$PUBLIC_KEY" to "100",
                    "activity_confirmation_cursor_$PUBLIC_KEY" to "200",
                ),
            ),
        )

        assertEquals("[]", profile.activityInboxes.getValue(PUBLIC_KEY))
        assertEquals(
            mapOf("activity_zap_cursor_$PUBLIC_KEY" to "100", "activity_confirmation_cursor_$PUBLIC_KEY" to "200"),
            profile.activityCursors,
        )
        val bad = snapshot(ordinary = ORDINARY + ("activity_zap_cursor_$PUBLIC_KEY" to "soon"))
        assertEquals("activity", assertThrows(ProfileImportException::class.java) { NativeProfileMapper.map(bad) }.part)
    }

    @Test
    fun `an inbox keeps which notifications were already read`() {
        val inbox = NativeActivityInboxProtocol.encodeNormalized(
            listOf(
                NativeActivityNotification(id = "a".repeat(64), type = NativeActivityNotificationType.Zap, createdAtSeconds = 200, amountSat = 22, isRead = true),
                NativeActivityNotification(id = "b".repeat(64), type = NativeActivityNotificationType.Zap, createdAtSeconds = 100, amountSat = 9, isRead = false),
            ),
        )

        val imported = NativeProfileMapper.map(snapshot(ordinary = ORDINARY + ("activity_inbox_$PUBLIC_KEY" to inbox)))
        val items = NativeActivityInboxProtocol.decodeNormalized(imported.activityInboxes.getValue(PUBLIC_KEY))

        assertEquals(listOf(true, false), items.map { it.isRead })
        assertEquals(1, NativeActivityInboxProtocol.unreadCount(items))
    }

    @Test
    fun `nothing printable shows a secret`() {
        val profile = NativeProfileMapper.map(snapshot())

        val text = listOf(profile.toString(), profile.identity.toString(), profile.sync.toString()).joinToString()
        assertFalse(text.contains(PRIVATE_KEY))
        assertFalse(text.contains("a passphrase"))
        assertFalse(text.contains(NWC))
    }

    @Test
    fun `the writer stages commits and verifies, and a mismatch names only the part`() {
        val targets = FakeTargets()
        val writer = LiveProfileSnapshotWriter(targets)

        assertThrows(IllegalStateException::class.java) { writer.commit() }
        writer.stage(snapshot())
        writer.commit()
        writer.verify(snapshot())
        assertEquals(1, targets.writes.size)

        targets.different = listOf("favorites", "identity")
        val failure = assertThrows(ProfileImportException::class.java) { writer.verify(snapshot()) }
        assertEquals("favorites, identity", failure.part)
        assertFalse(failure.message.orEmpty().contains(PRIVATE_KEY))
    }

    @Test
    fun `a phone with nothing from the old app skips the reader and is recorded as done`() {
        val flag = MemoryFlag()
        var reads = 0
        val startup = startup(flag, legacyPresent = false, reader = { reads += 1; snapshot() })

        startup.start()

        assertEquals(NativeMigrationReadiness.Ready, startup.readiness.value)
        assertEquals(0, reads)
        assertTrue(flag.isSet())
    }

    @Test
    fun `an old profile is imported once and only then recorded`() {
        val flag = MemoryFlag()
        val targets = FakeTargets()
        val startup = startup(flag, legacyPresent = true, reader = { snapshot() }, targets = targets)
        assertEquals(NativeMigrationReadiness.Checking, startup.readiness.value)

        startup.start()

        assertEquals(NativeMigrationReadiness.Ready, startup.readiness.value)
        assertEquals(1, targets.writes.size)
        assertTrue(flag.isSet())
    }

    @Test
    fun `a failed read leaves the flag unset so the next launch tries again`() {
        val flag = MemoryFlag()
        val first = startup(flag, legacyPresent = true, reader = { error("bridge down") })

        first.start()

        assertEquals(NativeMigrationReadiness.Failed, first.readiness.value)
        assertFalse(flag.isSet())
        val targets = FakeTargets()
        val second = startup(flag, legacyPresent = true, reader = { snapshot() }, targets = targets)
        second.start()
        assertEquals(NativeMigrationReadiness.Ready, second.readiness.value)
        assertEquals(1, targets.writes.size)
    }

    @Test
    fun `a profile that does not read back is a failure and is not recorded`() {
        val flag = MemoryFlag()
        val targets = FakeTargets().apply { different = listOf("favorites") }
        val startup = startup(flag, legacyPresent = true, reader = { snapshot() }, targets = targets)

        startup.start()

        assertEquals(NativeMigrationReadiness.Failed, startup.readiness.value)
        assertFalse(flag.isSet())
    }

    @Test
    fun `an invalid snapshot is refused before anything is written`() {
        val flag = MemoryFlag()
        val targets = FakeTargets()
        val mismatched = snapshot(identity = LegacyIdentity("33".repeat(32), "nsec", PRIVATE_KEY))
        val startup = startup(flag, legacyPresent = true, reader = { mismatched }, targets = targets)

        startup.start()

        assertEquals(NativeMigrationReadiness.Failed, startup.readiness.value)
        assertTrue(targets.writes.isEmpty())
        assertFalse(flag.isSet())
    }

    @Test
    fun `a profile already imported is not read again`() {
        val flag = MemoryFlag().apply { set() }
        var reads = 0
        val startup = startup(flag, legacyPresent = true, reader = { reads += 1; snapshot() })

        startup.start()

        assertEquals(NativeMigrationReadiness.Ready, startup.readiness.value)
        assertEquals(0, reads)
    }

    @Test
    fun `a second start while one runs does nothing`() {
        val flag = MemoryFlag()
        val queued = mutableListOf<Runnable>()
        val startup = startup(flag, legacyPresent = true, reader = { snapshot() }, executor = Executor { queued += it })

        startup.start()
        startup.start()

        assertEquals(1, queued.size)
        assertEquals(NativeMigrationReadiness.Checking, startup.readiness.value)
        queued.single().run()
        assertEquals(NativeMigrationReadiness.Ready, startup.readiness.value)
    }

    @Test
    fun `keys the new app does not know are left behind and never refuse the update`() {
        val odd = snapshot(
            ordinary = ORDINARY + mapOf("last_seen_tip_v0" to "x", "nostr_priv_hex" to PRIVATE_KEY, "activity_inbox_not-a-key" to "[]"),
            secure = SECURE + ("flutter_plugin_leftover" to "y"),
        )

        val tolerant = TolerantLegacySnapshotReader { odd }.read()!!

        assertEquals(ORDINARY.keys, tolerant.ordinaryValues.keys)
        assertEquals(SECURE.keys, tolerant.secureValues.keys)
        assertNull(TolerantLegacySnapshotReader { null }.read())
        // The strict validator refuses the raw snapshot and accepts the filtered one.
        val flag = MemoryFlag()
        val targets = FakeTargets()
        val refused = startup(flag, legacyPresent = true, reader = { odd }, targets = targets)
        refused.start()
        assertEquals(NativeMigrationReadiness.Failed, refused.readiness.value)
        val accepted = startup(flag, legacyPresent = true, reader = TolerantLegacySnapshotReader { odd }, targets = targets)
        accepted.start()
        assertEquals(NativeMigrationReadiness.Ready, accepted.readiness.value)
        assertTrue(flag.isSet())
    }

    @Test
    fun `after a failure the person can try again and the second try imports`() {
        val flag = MemoryFlag()
        val targets = FakeTargets()
        var broken = true
        val startup = startup(flag, legacyPresent = true, reader = { if (broken) error("bridge down") else snapshot() }, targets = targets)
        startup.start()
        assertEquals(NativeMigrationReadiness.Failed, startup.readiness.value)

        broken = false
        startup.retry()

        assertEquals(NativeMigrationReadiness.Ready, startup.readiness.value)
        assertEquals(1, targets.writes.size)
        assertTrue(flag.isSet())
    }

    @Test
    fun `going on without the old data records it and writes nothing`() {
        val flag = MemoryFlag()
        val targets = FakeTargets()
        val startup = startup(flag, legacyPresent = true, reader = { error("bridge down") }, targets = targets)
        startup.start()

        startup.skip()

        assertEquals(NativeMigrationReadiness.Ready, startup.readiness.value)
        assertTrue(targets.writes.isEmpty())
        assertTrue(flag.isSet())
    }

    @Test
    fun `retry and skip do nothing unless the import failed`() {
        val flag = MemoryFlag()
        val queued = mutableListOf<Runnable>()
        val running = startup(flag, legacyPresent = true, reader = { snapshot() }, executor = Executor { queued += it })
        running.start()

        running.skip()
        running.retry()

        assertEquals(NativeMigrationReadiness.Checking, running.readiness.value)
        assertFalse(flag.isSet())
        assertEquals(1, queued.size)
        queued.single().run()
        assertEquals(NativeMigrationReadiness.Ready, running.readiness.value)
        running.skip()
        running.retry()
        assertEquals(NativeMigrationReadiness.Ready, running.readiness.value)
    }

    private fun startup(
        flag: MemoryFlag,
        legacyPresent: Boolean,
        reader: LegacySnapshotReader,
        targets: FakeTargets = FakeTargets(),
        executor: Executor = Executor { it.run() },
    ) = NativeStartupMigration(
        migration = TransactionalMigration(
            reader = reader,
            writer = LiveProfileSnapshotWriter(targets),
            marker = LiveMigrationMarker(flag),
            identityVerifier = { PUBLIC_KEY },
        ),
        legacyPresent = { legacyPresent },
        flag = flag,
        executor = executor,
    )

    private class FakeTargets : ProfileImportTargets {
        val writes = mutableListOf<ImportedProfile>()
        var different: List<String> = emptyList()

        override fun write(profile: ImportedProfile) {
            writes += profile
        }

        override fun mismatches(profile: ImportedProfile): List<String> = different
    }

    private class MemoryFlag : ProfileImportFlag {
        private var value = false

        override fun isSet(): Boolean = value

        override fun set(): Boolean {
            value = true
            return true
        }
    }

    private fun snapshot(
        ordinary: Map<String, String> = ORDINARY,
        secure: Map<String, String> = SECURE,
        identity: LegacyIdentity = LegacyIdentity(PUBLIC_KEY, "nsec", PRIVATE_KEY),
    ) = LegacyStorageSnapshot(
        schemaVersion = 1,
        ordinaryValues = ordinary,
        secureValues = secure,
        identity = identity,
        assets = listOf(LegacyAsset("kokoro/model.onnx", 10, "aa".repeat(32))),
    )

    private companion object {
        val PRIVATE_KEY = "11".repeat(32)
        val PUBLIC_KEY = "22".repeat(32)
        const val NWC = "nostr+walletconnect://fixture-wallet?secret=fixture"
        const val PENDING_ROW = "{\"event\":{\"id\":\"abc\"},\"expiresAt\":200}"

        val FAVORITES = listOf(
            NativeSavedPlace("Casa", "Via Roma 1, Verona", NativeMapPoint(45.4384, 10.9916)),
            NativeSavedPlace("Ufficio", "", NativeMapPoint(45.6495, 13.7768)),
        )
        val PARKING = NativeParkingPosition(NativeMapPoint(45.4401, 10.9902), 1_700_000_000_000L)
        val HISTORY = listOf(SearchHistoryEntry("Trieste", 45.6495, 13.7768))

        val ORDINARY: Map<String, String> = mapOf(
            "language" to "it",
            "themeId" to "2",
            "onboarding_v1" to "true",
            "disclaimer_accepted" to "true",
            "privacy_disclosure_v2" to "true",
            "road_report_privacy_ack" to "true",
            "favorites" to NativeSavedPlacesProtocol.encodeStoredFavorites(FAVORITES),
            "parking_position" to NativeSavedPlacesProtocol.encodeParking(PARKING),
            "searchHistory" to NostrJson.encode(SearchHistoryProtocol.encodeStored(HISTORY)),
            "pending_road_reports" to "[\"" + PENDING_ROW.replace("\"", "\\\"") + "\"]",
            "fav_sync_custom_relay" to "wss://relay.example.org",
            "fav_sync_last_ts" to "1700000000",
            "fav_sync_legacy_cleaned" to "true",
            "favoritesSyncLastAt" to "1700000001000",
        )

        val SECURE: Map<String, String> = mapOf(
            "nostr_pub_hex" to PUBLIC_KEY,
            "nostr_priv_hex" to PRIVATE_KEY,
            "nostr_flavor" to "nsec",
            "nostr_name" to "Verona Driver",
            "nwc_uri" to NWC,
            "routing_api_key" to "routing-key",
            "favorites_sync_passphrase" to "a passphrase",
        )
    }
}
