package app.roadstr.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionalMigrationTest {
    private val expectedPrivateKey = "11".repeat(32)
    private val expectedPublicKey = "22".repeat(32)
    private val verifier = IdentityVerifier { privateKey ->
        if (privateKey == expectedPrivateKey) expectedPublicKey else error("unknown fixture key")
    }

    @Test
    fun `successful migration verifies before marking complete`() {
        val reader = FakeReader(snapshot())
        val writer = FakeWriter()
        val marker = FakeMarker()

        val result = TransactionalMigration(reader, writer, marker, verifier).run()

        assertEquals(MigrationOutcome.Migrated, result.outcome)
        assertEquals(listOf("stage", "commit", "verify"), writer.calls)
        assertTrue(marker.complete)
        assertFalse(writer.legacyDeleted)
    }

    @Test
    fun `completed migration is idempotent and does not reread or rewrite`() {
        val reader = FakeReader(snapshot())
        val writer = FakeWriter()
        val marker = FakeMarker(complete = true)
        val migration = TransactionalMigration(reader, writer, marker, verifier)

        val result = migration.run()

        assertEquals(MigrationOutcome.AlreadyComplete, result.outcome)
        assertEquals(0, reader.reads)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun `commit failure leaves marker incomplete and legacy available`() {
        val writer = FakeWriter(failAt = "commit")
        val marker = FakeMarker()

        val result = TransactionalMigration(
            FakeReader(snapshot()), writer, marker, verifier,
        ).run()

        assertEquals(MigrationOutcome.Failed, result.outcome)
        assertFalse(marker.complete)
        assertFalse(writer.legacyDeleted)
        assertNotNull(result.reason)
    }

    @Test
    fun `verification failure is still a failed migration`() {
        val writer = FakeWriter(failAt = "verify")
        val marker = FakeMarker()

        val result = TransactionalMigration(
            FakeReader(snapshot()), writer, marker, verifier,
        ).run()

        assertEquals(MigrationOutcome.Failed, result.outcome)
        assertFalse(marker.complete)
        assertFalse(writer.legacyDeleted)
    }

    @Test
    fun `public key mismatch fails before native writes`() {
        val writer = FakeWriter()
        val marker = FakeMarker()
        val invalid = snapshot().copy(
            identity = LegacyIdentity(
                publicKeyHex = "33".repeat(32),
                flavor = "nsec",
                privateKeyHex = expectedPrivateKey,
            ),
        )

        val result = TransactionalMigration(
            FakeReader(invalid), writer, marker, verifier,
        ).run()

        assertEquals(MigrationOutcome.Failed, result.outcome)
        assertTrue(writer.calls.isEmpty())
        assertFalse(marker.complete)
    }

    @Test
    fun `unsafe asset path fails before native writes`() {
        val writer = FakeWriter()
        val invalid = snapshot().copy(
            assets = listOf(
                LegacyAsset("../kokoro/model.onnx", 10, "aa".repeat(32)),
            ),
        )

        val result = TransactionalMigration(
            FakeReader(invalid), writer, FakeMarker(), verifier,
        ).run()

        assertEquals(MigrationOutcome.Failed, result.outcome)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun `unknown Hive key fails closed before native writes`() {
        val writer = FakeWriter()
        val invalid = snapshot().copy(
            ordinaryValues = snapshot().ordinaryValues + ("future_unmapped_key" to "value"),
        )

        val result = TransactionalMigration(
            FakeReader(invalid), writer, FakeMarker(), verifier,
        ).run()

        assertEquals(MigrationOutcome.Failed, result.outcome)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun `Amber identity cannot acquire a private key`() {
        val writer = FakeWriter()
        val invalid = snapshot().copy(
            identity = LegacyIdentity(expectedPublicKey, "amber", expectedPrivateKey),
        )

        val result = TransactionalMigration(
            FakeReader(invalid), writer, FakeMarker(), verifier,
        ).run()

        assertEquals(MigrationOutcome.Failed, result.outcome)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun `nsec identity must be complete on both protected maps`() {
        val writer = FakeWriter()
        val invalid = snapshot().copy(
            secureValues = mapOf(
                "nostr_flavor" to "nsec",
                "nostr_priv_hex" to expectedPrivateKey,
            ),
        )

        val result = TransactionalMigration(
            FakeReader(invalid), writer, FakeMarker(), verifier,
        ).run()

        assertEquals(MigrationOutcome.Failed, result.outcome)
        assertTrue(writer.calls.isEmpty())
        assertFalse(result.reason.orEmpty().contains(expectedPrivateKey))
    }

    @Test
    fun `identity fields must be mirrored by secure storage`() {
        val writer = FakeWriter()
        val invalid = snapshot().copy(
            secureValues = mapOf("nwc_uri" to "fixture-only"),
        )

        val result = TransactionalMigration(
            FakeReader(invalid), writer, FakeMarker(), verifier,
        ).run()

        assertEquals(MigrationOutcome.Failed, result.outcome)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun `noncanonical Hive key fails before native writes`() {
        val writer = FakeWriter()
        val invalid = snapshot().copy(
            secureValues = snapshot().secureValues +
                ("hive_settings_key" to "not-a-canonical-key"),
        )

        val result = TransactionalMigration(
            FakeReader(invalid), writer, FakeMarker(), verifier,
        ).run()

        assertEquals(MigrationOutcome.Failed, result.outcome)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun `snapshot fingerprint is stable across map and asset order`() {
        val first = snapshot().copy(
            assets = listOf(
                LegacyAsset("piper/model.onnx", 200, "bb".repeat(32)),
                LegacyAsset("kokoro/model.onnx", 100, "aa".repeat(32)),
            ),
        )
        val reordered = first.copy(
            ordinaryValues = first.ordinaryValues.entries.reversed().associate { it.toPair() },
            secureValues = first.secureValues.entries.reversed().associate { it.toPair() },
            assets = first.assets.reversed(),
        )

        assertEquals(
            LegacySnapshotFingerprint.sha256(first),
            LegacySnapshotFingerprint.sha256(reordered),
        )
        assertFalse(
            LegacySnapshotFingerprint.sha256(first) ==
                LegacySnapshotFingerprint.sha256(first.copy(ordinaryValues = first.ordinaryValues + ("language" to "de"))),
        )
    }

    private fun snapshot() = LegacyStorageSnapshot(
        schemaVersion = 1,
        ordinaryValues = mapOf("language" to "it", "themeId" to "2"),
        secureValues = mapOf(
            "nwc_uri" to "fixture-only",
            "nostr_pub_hex" to expectedPublicKey,
            "nostr_priv_hex" to expectedPrivateKey,
            "nostr_flavor" to "nsec",
        ),
        identity = LegacyIdentity(
            publicKeyHex = expectedPublicKey,
            flavor = "nsec",
            privateKeyHex = expectedPrivateKey,
        ),
        assets = listOf(
            LegacyAsset("kokoro/model.onnx", 100, "aa".repeat(32)),
        ),
    )

    private class FakeReader(
        private val value: LegacyStorageSnapshot?,
    ) : LegacySnapshotReader {
        var reads = 0
        override fun read(): LegacyStorageSnapshot? {
            reads++
            return value
        }
    }

    private class FakeMarker(
        var complete: Boolean = false,
    ) : MigrationMarker {
        override fun isComplete() = complete
        override fun markComplete() {
            complete = true
        }
    }

    private class FakeWriter(
        private val failAt: String? = null,
    ) : NativeSnapshotWriter {
        val calls = mutableListOf<String>()
        var legacyDeleted = false

        override fun stage(snapshot: LegacyStorageSnapshot) {
            calls += "stage"
            if (failAt == "stage") error("fixture stage failure")
        }

        override fun commit() {
            calls += "commit"
            if (failAt == "commit") error("fixture commit failure")
        }

        override fun verify(snapshot: LegacyStorageSnapshot) {
            calls += "verify"
            if (failAt == "verify") error("fixture verification failure")
        }
    }
}
