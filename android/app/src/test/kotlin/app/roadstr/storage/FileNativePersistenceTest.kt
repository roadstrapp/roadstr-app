package app.roadstr.storage

import app.roadstr.migration.LegacyAsset
import app.roadstr.migration.LegacyIdentity
import app.roadstr.migration.LegacySnapshotReader
import app.roadstr.migration.LegacyStorageSnapshot
import app.roadstr.migration.MigrationOutcome
import app.roadstr.migration.TransactionalMigration
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FileNativePersistenceTest {
    @Test
    fun `public snapshot survives store recreation`() = withTempDirectory { directory ->
        val encoded = NativeSnapshotRecordCodec.encode(snapshot())
        FileNativePublicSnapshotStore(directory).apply {
            stage(encoded)
            commit()
        }

        val reopened = FileNativePublicSnapshotStore(directory).read()

        assertArrayEquals(encoded, reopened)
        assertFalse(File(directory, NATIVE_PUBLIC_SNAPSHOT_FILE + NATIVE_STAGE_SUFFIX).exists())
        assertFalse(File(directory, NATIVE_PUBLIC_SNAPSHOT_FILE + NATIVE_BACKUP_SUFFIX).exists())
    }

    @Test
    fun `restart restores backup after interrupted replacement and keeps stage retryable`() =
        withTempDirectory { directory ->
            val original = NativeSnapshotRecordCodec.encode(snapshot(language = "it"))
            val replacement = NativeSnapshotRecordCodec.encode(snapshot(language = "en"))
            val store = FileNativePublicSnapshotStore(directory)
            store.stage(original)
            store.commit()
            store.stage(replacement)

            val active = File(directory, NATIVE_PUBLIC_SNAPSHOT_FILE)
            val backup = File(directory, NATIVE_PUBLIC_SNAPSHOT_FILE + NATIVE_BACKUP_SUFFIX)
            assertTrue(active.renameTo(backup))

            val reopened = FileNativePublicSnapshotStore(directory)
            assertArrayEquals(original, reopened.read())
            assertTrue(active.isFile)

            reopened.commit()
            assertArrayEquals(replacement, FileNativePublicSnapshotStore(directory).read())
            assertFalse(backup.exists())
        }

    @Test
    fun `restart rolls back a corrupt activated replacement when backup is valid`() =
        withTempDirectory { directory ->
            val original = NativeSnapshotRecordCodec.encode(snapshot(language = "it"))
            val replacement = NativeSnapshotRecordCodec.encode(snapshot(language = "en"))
            val store = FileNativePublicSnapshotStore(directory)
            store.stage(original)
            store.commit()
            store.stage(replacement)

            val active = File(directory, NATIVE_PUBLIC_SNAPSHOT_FILE)
            val stage = File(directory, NATIVE_PUBLIC_SNAPSHOT_FILE + NATIVE_STAGE_SUFFIX)
            val backup = File(directory, NATIVE_PUBLIC_SNAPSHOT_FILE + NATIVE_BACKUP_SUFFIX)
            assertTrue(active.renameTo(backup))
            assertTrue(stage.renameTo(active))
            RandomAccessFile(active, "rw").use { file ->
                val offset = file.length() - 1
                file.seek(offset)
                val originalByte = file.readByte().toInt()
                file.seek(offset)
                file.writeByte(originalByte xor 0x01)
                file.fd.sync()
            }

            val reopened = FileNativePublicSnapshotStore(directory)
            assertArrayEquals(original, reopened.read())
            assertFalse(backup.exists())
            assertArrayEquals(original, active.readBytes())
        }

    @Test
    fun `completion marker survives reopen and prevents a second legacy read`() =
        withTempDirectory { directory ->
            val publicStore = FileNativePublicSnapshotStore(directory)
            val secretStore = MemorySecretStore()
            val first = migration(directory, publicStore, secretStore)

            assertEquals(MigrationOutcome.Migrated, first.run().outcome)

            val reopenedPublicStore = FileNativePublicSnapshotStore(directory)
            val reopenedMarker = FileSnapshotBoundMigrationMarker(directory, reopenedPublicStore)
            var legacyReads = 0
            val second = TransactionalMigration(
                reader = LegacySnapshotReader {
                    legacyReads += 1
                    snapshot()
                },
                writer = CompositeNativeSnapshotWriter(reopenedPublicStore, secretStore),
                marker = reopenedMarker,
                identityVerifier = { PUBLIC_KEY },
            )

            assertTrue(reopenedMarker.isComplete())
            assertEquals(MigrationOutcome.AlreadyComplete, second.run().outcome)
            assertEquals(0, legacyReads)
        }

    @Test
    fun `public files and marker never contain raw protected values`() =
        withTempDirectory { directory ->
            val publicStore = FileNativePublicSnapshotStore(directory)
            val result = migration(directory, publicStore, MemorySecretStore()).run()

            assertEquals(MigrationOutcome.Migrated, result.outcome)
            val persisted = requireNotNull(directory.listFiles())
                .filter(File::isFile)
                .flatMap { it.readBytes().asIterable() }
                .toByteArray()
                .toString(StandardCharsets.ISO_8859_1)
            assertFalse(persisted.contains(SECRET))
            assertFalse(persisted.contains(PRIVATE_KEY))
        }

    @Test
    fun `mutated public record invalidates marker and migration repairs it`() =
        withTempDirectory { directory ->
            val publicStore = FileNativePublicSnapshotStore(directory)
            val secretStore = MemorySecretStore()
            val first = migration(directory, publicStore, secretStore)
            assertEquals(MigrationOutcome.Migrated, first.run().outcome)

            val active = File(directory, NATIVE_PUBLIC_SNAPSHOT_FILE)
            RandomAccessFile(active, "rw").use { file ->
                val offset = file.length() - 1
                file.seek(offset)
                val original = file.readByte().toInt()
                file.seek(offset)
                file.writeByte(original xor 0x01)
                file.fd.sync()
            }

            val reopenedPublicStore = FileNativePublicSnapshotStore(directory)
            val marker = FileSnapshotBoundMigrationMarker(directory, reopenedPublicStore)
            assertFalse(marker.isComplete())

            val repaired = migration(directory, reopenedPublicStore, secretStore).run()
            assertEquals(MigrationOutcome.Migrated, repaired.outcome)
            assertTrue(marker.isComplete())
            assertEquals(
                NativeSnapshotRecord.fromLegacy(snapshot()),
                NativeSnapshotRecordCodec.decode(requireNotNull(reopenedPublicStore.read())),
            )
        }

    @Test
    fun `commit without a stage fails with a value-free error`() =
        withTempDirectory { directory ->
            val failure = assertThrows(NativePersistenceException::class.java) {
                FileNativePublicSnapshotStore(directory).commit()
            }

            assertFalse(failure.message.orEmpty().contains(directory.path))
            assertFalse(failure.message.orEmpty().contains(SECRET))
        }

    private fun migration(
        directory: File,
        publicStore: FileNativePublicSnapshotStore,
        secretStore: MemorySecretStore,
    ) = TransactionalMigration(
        reader = LegacySnapshotReader { snapshot() },
        writer = CompositeNativeSnapshotWriter(publicStore, secretStore),
        marker = FileSnapshotBoundMigrationMarker(directory, publicStore),
        identityVerifier = { PUBLIC_KEY },
    )

    private fun snapshot(language: String = "it") = LegacyStorageSnapshot(
        schemaVersion = 1,
        ordinaryValues = mapOf("language" to language, "themeId" to "2"),
        secureValues = mapOf(
            "nostr_pub_hex" to PUBLIC_KEY,
            "nostr_priv_hex" to PRIVATE_KEY,
            "nostr_flavor" to "nsec",
            "nwc_uri" to SECRET,
        ),
        identity = LegacyIdentity(PUBLIC_KEY, "nsec", PRIVATE_KEY),
        assets = listOf(LegacyAsset("kokoro/model.onnx", 10, "aa".repeat(32))),
    )

    private class MemorySecretStore : NativeSecretStore {
        private var staged: Map<String, String>? = null
        private var committed: Map<String, String>? = null

        override fun stage(values: Map<String, String>) {
            staged = values.toMap()
        }

        override fun commit() {
            committed = requireNotNull(staged).toMap()
        }

        override fun verify(values: Map<String, String>) {
            check(committed == values)
        }
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("roadstr-native-store-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val SECRET = "fixture-nwc-secret-not-real"
        val PRIVATE_KEY = "11".repeat(32)
        val PUBLIC_KEY = "22".repeat(32)
    }
}
