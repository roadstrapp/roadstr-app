package app.roadstr.storage

import app.roadstr.core.search.SearchHistoryProtocol
import app.roadstr.migration.LegacyAsset
import app.roadstr.migration.LegacyIdentity
import app.roadstr.migration.LegacySnapshotEnvelope
import app.roadstr.migration.LegacySnapshotReader
import app.roadstr.migration.LegacyStorageSnapshot
import app.roadstr.migration.MigrationMarker
import app.roadstr.migration.MigrationOutcome
import app.roadstr.migration.TransactionalMigration
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeSnapshotStoreTest {
    @Test
    fun `public record contains commitments but no protected values`() {
        val encoded = NativeSnapshotRecordCodec.encode(snapshot())

        val publicBytes = String(encoded, StandardCharsets.ISO_8859_1)
        assertFalse(publicBytes.contains(SECRET))
        assertFalse(publicBytes.contains(PRIVATE_KEY))

        val decoded = NativeSnapshotRecordCodec.decode(encoded)
        assertNull(decoded.identity.privateKeyHex)
        assertEquals(
            NativeSecretDigest.sha256("nwc_uri", SECRET),
            decoded.secureValueDigests["nwc_uri"],
        )
    }

    @Test
    fun `composite writer stages and verifies both stores`() {
        val calls = mutableListOf<String>()
        val publicStore = FakePublicStore(calls)
        val secretStore = FakeSecretStore(calls)
        val writer = CompositeNativeSnapshotWriter(publicStore, secretStore)

        writer.stage(snapshot())
        writer.commit()
        writer.verify(snapshot())

        assertEquals(
            listOf("public-stage", "secret-stage", "public-commit", "secret-commit", "secret-verify"),
            calls,
        )
        assertEquals(snapshot().secureValues, secretStore.committed)
    }

    @Test
    fun `search history cannot fall through to the public snapshot`() {
        val calls = mutableListOf<String>()
        val writer = CompositeNativeSnapshotWriter(
            FakePublicStore(calls),
            FakeSecretStore(calls),
        )
        val normalizedHistory =
            """["{\"label\":\"Private Place\",\"lat\":45.0,\"lon\":9.0}"]"""
        val sensitive = snapshot().copy(
            ordinaryValues = snapshot().ordinaryValues +
                (SearchHistoryProtocol.STORAGE_KEY to normalizedHistory),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            writer.stage(sensitive)
        }

        assertTrue(calls.isEmpty())
        assertFalse(failure.message.orEmpty().contains("Private Place"))
        val encoded = NativeSnapshotRecordCodec.encode(sensitive)
        assertFalse(String(encoded, StandardCharsets.ISO_8859_1).contains("Private Place"))
        assertFalse(
            NativeSnapshotRecordCodec.decode(encoded).ordinaryValues
                .containsKey(SearchHistoryProtocol.STORAGE_KEY),
        )
    }

    @Test
    fun `protected commit failure keeps migration marker incomplete and can retry`() {
        val calls = mutableListOf<String>()
        val publicStore = FakePublicStore(calls)
        val secretStore = FakeSecretStore(calls, failCommit = true)
        val writer = CompositeNativeSnapshotWriter(publicStore, secretStore)
        val marker = FakeMarker()
        val migration = TransactionalMigration(
            reader = LegacySnapshotReader { snapshot() },
            writer = writer,
            marker = marker,
            identityVerifier = { PRIVATE_KEY -> PUBLIC_KEY },
        )

        val failed = migration.run()

        assertEquals(MigrationOutcome.Failed, failed.outcome)
        assertFalse(marker.complete)
        assertFalse(failed.reason.orEmpty().contains(SECRET))

        secretStore.failCommit = false
        val retried = migration.run()

        assertEquals(MigrationOutcome.Migrated, retried.outcome)
        assertTrue(marker.complete)
    }

    @Test
    fun `corrupt public record fails verification without exposing secrets`() {
        val publicStore = FakePublicStore(mutableListOf())
        val secretStore = FakeSecretStore(mutableListOf())
        val writer = CompositeNativeSnapshotWriter(publicStore, secretStore)
        writer.stage(snapshot())
        writer.commit()
        publicStore.corrupt()

        val failure = assertThrows(IllegalStateException::class.java) {
            writer.verify(snapshot())
        }

        assertFalse(failure.message.orEmpty().contains(SECRET))
    }

    @Test
    fun `native codec rejects a record carrying private identity material`() {
        val legacyBytes = LegacySnapshotEnvelope.encode(
            snapshot().copy(
                identity = LegacyIdentity(PUBLIC_KEY, "nsec", PRIVATE_KEY),
            ),
        )

        assertThrows(NativeSnapshotCodecException::class.java) {
            NativeSnapshotRecordCodec.decode(legacyBytes)
        }
    }

    private fun snapshot() = LegacyStorageSnapshot(
        schemaVersion = 1,
        ordinaryValues = mapOf("language" to "it", "themeId" to "2"),
        secureValues = mapOf(
            "nostr_pub_hex" to PUBLIC_KEY,
            "nostr_priv_hex" to PRIVATE_KEY,
            "nostr_flavor" to "nsec",
            "nwc_uri" to SECRET,
        ),
        identity = LegacyIdentity(PUBLIC_KEY, "nsec", PRIVATE_KEY),
        assets = listOf(LegacyAsset("kokoro/model.onnx", 10, "aa".repeat(32))),
    )

    private class FakePublicStore(
        private val calls: MutableList<String>,
    ) : NativePublicSnapshotStore {
        private var staged: ByteArray? = null
        private var committed: ByteArray? = null

        override fun stage(encodedRecord: ByteArray) {
            calls += "public-stage"
            staged = encodedRecord.copyOf()
        }

        override fun commit() {
            calls += "public-commit"
            committed = requireNotNull(staged).copyOf()
        }

        override fun read(): ByteArray? = committed?.copyOf()

        fun corrupt() {
            val value = requireNotNull(committed)
            value[value.lastIndex - 1] = (value[value.lastIndex - 1].toInt() xor 0x01).toByte()
        }
    }

    private class FakeSecretStore(
        private val calls: MutableList<String>,
        var failCommit: Boolean = false,
    ) : NativeSecretStore {
        private var staged: Map<String, String>? = null
        var committed: Map<String, String>? = null
            private set

        override fun stage(values: Map<String, String>) {
            calls += "secret-stage"
            staged = values.toMap()
        }

        override fun commit() {
            calls += "secret-commit"
            if (failCommit) error("secret=$SECRET")
            committed = requireNotNull(staged)
        }

        override fun verify(values: Map<String, String>) {
            calls += "secret-verify"
            check(committed == values)
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

    private companion object {
        const val SECRET = "fixture-nwc-secret-not-real"
        val PRIVATE_KEY = "11".repeat(32)
        val PUBLIC_KEY = "22".repeat(32)
    }
}
