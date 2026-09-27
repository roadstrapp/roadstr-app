package app.roadstr.migration

import app.roadstr.storage.NativeSecretCommitmentVerifier
import app.roadstr.storage.NativeSecretDigest
import app.roadstr.storage.NativeSecretStore
import app.roadstr.storage.NativeStoragePaths
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMigrationRuntimeTest {
    @Test
    fun `native path uses a stable update-persistent root without creating it`() =
        withTempDirectory { temporary ->
            val noBackupFilesDir = File(temporary, "no-backup")
            val paths = NativeStoragePaths.under(noBackupFilesDir)

            assertEquals(
                File(noBackupFilesDir, NativeStoragePaths.ROOT_DIRECTORY_NAME),
                paths.rootDirectory,
            )
            assertFalse(paths.rootDirectory.exists())
        }

    @Test
    fun `runtime composes stores and is idempotent after recreation`() =
        withTempDirectory { temporary ->
            val paths = NativeStoragePaths.under(File(temporary, "no-backup"))
            val secretStore = MemorySecretStore()
            var legacyReads = 0
            val reader = LegacySnapshotReader {
                legacyReads += 1
                snapshot()
            }

            val first = NativeMigrationRuntime.create(
                paths = paths,
                reader = reader,
                identityVerifier = { PUBLIC_KEY },
                secretStore = secretStore,
                secretVerifier = secretStore,
            )

            assertFalse(paths.rootDirectory.exists())
            assertEquals(MigrationOutcome.Migrated, first.run().outcome)
            assertTrue(paths.rootDirectory.isDirectory)
            assertEquals(1, legacyReads)

            val second = NativeMigrationRuntime.create(
                paths = NativeStoragePaths.under(File(temporary, "no-backup")),
                reader = reader,
                identityVerifier = { PUBLIC_KEY },
                secretStore = secretStore,
                secretVerifier = secretStore,
            )

            assertEquals(MigrationOutcome.AlreadyComplete, second.run().outcome)
            assertEquals(1, legacyReads)
        }

    @Test
    fun `runtime remains failed closed when protected commitments do not reopen`() =
        withTempDirectory { temporary ->
            val paths = NativeStoragePaths.under(File(temporary, "no-backup"))
            val secretStore = MemorySecretStore()
            val first = NativeMigrationRuntime.create(
                paths = paths,
                reader = LegacySnapshotReader { snapshot() },
                identityVerifier = { PUBLIC_KEY },
                secretStore = secretStore,
                secretVerifier = secretStore,
            )
            assertEquals(MigrationOutcome.Migrated, first.run().outcome)

            val failedVerifier = NativeSecretCommitmentVerifier { false }
            val retry = NativeMigrationRuntime.create(
                paths = paths,
                reader = LegacySnapshotReader { snapshot() },
                identityVerifier = { PUBLIC_KEY },
                secretStore = secretStore,
                secretVerifier = failedVerifier,
            )

            assertEquals(MigrationOutcome.Failed, retry.run().outcome)
        }

    private class MemorySecretStore : NativeSecretStore, NativeSecretCommitmentVerifier {
        private var staged: Map<String, String>? = null
        private var committed: Map<String, String>? = null

        override fun stage(values: Map<String, String>) {
            staged = values.toSortedMap()
        }

        override fun commit() {
            committed = requireNotNull(staged)
        }

        override fun verify(values: Map<String, String>) {
            check(committed == values)
        }

        override fun matches(expectedDigests: Map<String, String>): Boolean {
            val values = committed ?: return false
            return values.keys == expectedDigests.keys && values.all { (key, value) ->
                NativeSecretDigest.sha256(key, value) == expectedDigests[key]
            }
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

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("roadstr-native-runtime-").toFile()
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
