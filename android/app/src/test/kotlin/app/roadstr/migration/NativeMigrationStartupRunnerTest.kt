package app.roadstr.migration

import app.roadstr.storage.NativeSecretCommitmentVerifier
import app.roadstr.storage.NativeSecretDigest
import app.roadstr.storage.NativeSecretStore
import app.roadstr.storage.NativeStoragePaths
import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMigrationStartupRunnerTest {
    @Test
    fun `runner is single flight and becomes terminal after success`() =
        withTempDirectory { temporary ->
            val executor = QueuedExecutor()
            val runner = NativeMigrationStartupRunner(
                runtime = runtime(File(temporary, "first")),
                executor = executor,
            )
            val outcomes = mutableListOf<MigrationOutcome>()

            assertTrue(runner.start { outcomes += it.outcome })
            assertFalse(runner.start { outcomes += it.outcome })
            assertEquals(NativeMigrationRunnerState.Running, runner.state())
            assertEquals(0, outcomes.size)

            executor.runNext()

            assertEquals(listOf(MigrationOutcome.Migrated), outcomes)
            assertEquals(NativeMigrationRunnerState.Terminal, runner.state())
            assertFalse(runner.start { outcomes += it.outcome })
        }

    @Test
    fun `failed run returns to idle and can retry after the verifier is repaired`() =
        withTempDirectory { temporary ->
            val executor = QueuedExecutor()
            val secretStore = MemorySecretStore()
            val verifier = ToggleVerifier(secretStore)
            val runner = NativeMigrationStartupRunner(
                runtime = runtime(File(temporary, "retry"), secretStore, verifier),
                executor = executor,
            )
            val outcomes = mutableListOf<MigrationOutcome>()

            assertTrue(runner.start { outcomes += it.outcome })
            executor.runNext()
            assertEquals(listOf(MigrationOutcome.Failed), outcomes)
            assertEquals(NativeMigrationRunnerState.Idle, runner.state())

            verifier.allow = true
            assertTrue(runner.start { outcomes += it.outcome })
            executor.runNext()

            assertEquals(
                listOf(MigrationOutcome.Failed, MigrationOutcome.AlreadyComplete),
                outcomes,
            )
            assertEquals(NativeMigrationRunnerState.Terminal, runner.state())
        }

    @Test
    fun `executor rejection reports a neutral failure and leaves runner retryable`() =
        withTempDirectory { temporary ->
            val runner = NativeMigrationStartupRunner(
                runtime = runtime(File(temporary, "rejected")),
                executor = Executor { throw RejectedExecutionException() },
            )
            val outcomes = mutableListOf<MigrationResult>()

            assertFalse(runner.start(outcomes::add))

            assertEquals(1, outcomes.size)
            assertEquals(MigrationOutcome.Failed, outcomes.single().outcome)
            assertEquals("Native migration worker unavailable", outcomes.single().reason)
            assertEquals(NativeMigrationRunnerState.Idle, runner.state())
        }

    private fun runtime(
        root: File,
        secretStore: MemorySecretStore = MemorySecretStore(),
        secretVerifier: NativeSecretCommitmentVerifier = secretStore,
    ): NativeMigrationRuntime = NativeMigrationRuntime.create(
        paths = NativeStoragePaths.under(root),
        reader = LegacySnapshotReader { snapshot() },
        identityVerifier = { PUBLIC_KEY },
        secretStore = secretStore,
        secretVerifier = secretVerifier,
    )

    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runNext() {
            check(tasks.isNotEmpty()) { "No queued migration task" }
            tasks.removeFirst().run()
        }
    }

    private class ToggleVerifier(
        private val delegate: MemorySecretStore,
        var allow: Boolean = false,
    ) : NativeSecretCommitmentVerifier {
        override fun matches(expectedDigests: Map<String, String>): Boolean =
            allow && delegate.matches(expectedDigests)
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
        val directory = Files.createTempDirectory("roadstr-native-runner-").toFile()
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
