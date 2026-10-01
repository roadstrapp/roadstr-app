package app.roadstr.storage

import app.roadstr.migration.LegacyIdentity
import app.roadstr.migration.LegacySnapshotReader
import app.roadstr.migration.LegacyStorageSnapshot
import app.roadstr.migration.MigrationOutcome
import app.roadstr.migration.TransactionalMigration
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePendingReportStoreTest {
    @Test
    fun `record codec is deterministic strict and immutable`() {
        val record = NativePendingReportRecord(1, 3, listOf(row("one", 200)))

        val encoded = NativePendingReportRecordCodec.encode(record)
        val decoded = NativePendingReportRecordCodec.decode(encoded)

        assertEquals(record, decoded)
        assertEquals(encoded.toList(), NativePendingReportRecordCodec.encode(decoded).toList())
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (decoded.storageRows as MutableList<String>).clear()
        }
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        assertThrows(NativePendingReportStoreException::class.java) {
            NativePendingReportRecordCodec.decode(encoded)
        }
    }

    @Test
    fun `legacy import is atomic tolerant encrypted and survives reopen`() =
        withTempDirectory { directory ->
            val store = store(directory)
            val raw = outer(row("private-location", 200), "broken", includeNonString = true)

            store.stageLegacy(raw)
            assertThrows(NativePendingReportStoreException::class.java) {
                runBlocking { store.load() }
            }
            store.commitStaged()
            store.verifyLegacy(raw)

            val reopened = runBlocking { store(directory).load() }
            assertEquals(listOf("private-location"), reopened.map { it.id })
            val persisted = pendingFile(directory).readBytes()
                .toString(StandardCharsets.ISO_8859_1)
            assertFalse(persisted.contains("private-location"))
            assertFalse(persisted.contains("expiresAt"))
        }

    @Test
    fun `enqueue and flush commit one FIFO replacement`() = withTempDirectory { directory ->
        runBlocking {
            val store = store(directory)
            assertEquals(
                NativePendingReportInitializationOutcome.Initialized,
                store.initializeEmptyForNewInstall(),
            )
            store.enqueue(5, event("expired"), 100)
            store.enqueue(5, event("sent"), 200)
            store.enqueue(5, event("retry"), 200)

            val result = store.flush(
                revision = 5,
                now = 100,
                verify = { true },
                publish = { it["id"] == "sent" },
            )

            assertEquals(listOf("sent", "retry"), result.attemptedIds)
            assertEquals(listOf("retry"), result.remaining.map { it.id })
            assertEquals(listOf("retry"), store(directory).load().map { it.id })
            val persisted = NativePendingReportPersistenceCodec(JcaTestAead(KEY_ONE))
                .decode(pendingFile(directory).readBytes())
            assertEquals(4L, persisted.sequence)
        }
    }

    @Test
    fun `concurrent enqueues serialize without loss`() = withTempDirectory { directory ->
        runBlocking {
            val store = store(directory)
            store.initializeEmptyForNewInstall()

            (0 until 50).map { index ->
                async(Dispatchers.Default) {
                    store.enqueue(1, event("event-$index"), 200)
                }
            }.awaitAll()

            assertEquals(50, store.load().size)
            assertEquals(50, store(directory).load().size)
        }
    }

    @Test
    fun `stale revisions fail closed and initialization is explicit`() =
        withTempDirectory { directory ->
            runBlocking {
                val store = store(directory)
                assertThrows(NativePendingReportStoreException::class.java) {
                    runBlocking { store.enqueue(0, event("missing"), 200) }
                }
                store.initializeEmptyForNewInstall()
                assertEquals(
                    NativePendingReportInitializationOutcome.AlreadyPresent,
                    store.initializeEmptyForNewInstall(),
                )
                assertTrue(store.enqueue(8, event("fresh"), 200))
                assertFalse(store.enqueue(7, event("stale"), 200))
                assertEquals(listOf("fresh"), store.load().map { it.id })
            }
        }

    @Test
    fun `wrong key and corruption cannot overwrite queue`() =
        withTempDirectory { directory ->
            runBlocking {
                val original = store(directory, KEY_ONE)
                original.initializeEmptyForNewInstall()
                original.enqueue(1, event("private"), 200)
                val active = pendingFile(directory)
                val bytes = active.readBytes()

                assertThrows(NativePendingReportStoreException::class.java) {
                    runBlocking { store(directory, KEY_TWO).load() }
                }
                assertThrows(NativePendingReportStoreException::class.java) {
                    runBlocking { store(directory, KEY_TWO).enqueue(2, event("replace"), 200) }
                }
                assertTrue(bytes.contentEquals(active.readBytes()))

                RandomAccessFile(active, "rw").use { file ->
                    val offset = file.length() - 1
                    file.seek(offset)
                    val value = file.readByte().toInt()
                    file.seek(offset)
                    file.writeByte(value xor 1)
                    file.fd.sync()
                }
                val corrupt = active.readBytes()
                assertThrows(NativePendingReportStoreException::class.java) {
                    runBlocking { store(directory, KEY_ONE).load() }
                }
                assertTrue(corrupt.contentEquals(active.readBytes()))
            }
        }

    @Test
    fun `migration removes reports from public bytes and marker binds ciphertext`() =
        withTempDirectory { directory ->
            val raw = outer(row("signed-private-report", 200))
            val snapshot = LegacyStorageSnapshot(
                1,
                mapOf("language" to "it", "pending_road_reports" to raw),
                emptyMap(),
                LegacyIdentity(null, null, null),
                emptyList(),
            )
            val publicStore = FileNativePublicSnapshotStore(directory)
            val secrets = MemorySecretStore()
            val pendingStore = store(directory)
            val marker = FileSnapshotBoundMigrationMarker(
                directory,
                publicStore,
                secrets,
                pendingReportVerifier = pendingStore,
            )
            val migration = TransactionalMigration(
                LegacySnapshotReader { snapshot },
                CompositeNativeSnapshotWriter(
                    publicStore,
                    secrets,
                    pendingReportStore = pendingStore,
                ),
                marker,
                { error("anonymous snapshot must not derive identity") },
            )

            assertEquals(MigrationOutcome.Migrated, migration.run().outcome)
            assertTrue(marker.isComplete())
            val publicBytes = requireNotNull(publicStore.read())
            assertFalse(
                NativeSnapshotRecordCodec.decode(publicBytes).ordinaryValues
                    .containsKey("pending_road_reports"),
            )
            assertFalse(publicBytes.toString(StandardCharsets.ISO_8859_1).contains("signed-private"))
            assertTrue(
                File(directory, NATIVE_MIGRATION_MARKER_FILE).readBytes()
                    .take(8).toByteArray()
                    .contentEquals("RSTRMIG5".toByteArray(Charsets.US_ASCII)),
            )

            runBlocking { pendingStore.enqueue(0, event("changed"), 300) }
            assertFalse(marker.isComplete())
            assertEquals(MigrationOutcome.Migrated, migration.run().outcome)
            assertTrue(marker.isComplete())
            assertEquals(
                listOf("signed-private-report"),
                runBlocking { pendingStore.load() }.map { it.id },
            )
        }

    private fun store(directory: File, key: ByteArray = KEY_ONE) =
        FileNativePendingReportStore(directory, JcaTestAead(key), Dispatchers.Default)

    private fun event(id: String): Map<String, Any?> = linkedMapOf("id" to id)

    private fun row(id: String, expiresAt: Long): String =
        "{\"event\":{\"id\":\"$id\"},\"expiresAt\":$expiresAt}"

    private fun outer(
        vararg rows: String,
        includeNonString: Boolean = false,
    ): String = buildString {
        append('[')
        rows.forEachIndexed { index, row ->
            if (index > 0) append(',')
            append('"')
            for (character in row) {
                if (character == '"' || character == '\\') append('\\')
                append(character)
            }
            append('"')
        }
        if (includeNonString) {
            if (rows.isNotEmpty()) append(',')
            append("42")
        }
        append(']')
    }

    private fun pendingFile(directory: File) = File(directory, NATIVE_PENDING_REPORT_FILE)

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("roadstr-native-pending-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class MemorySecretStore : NativeSecretStore, NativeSecretCommitmentVerifier {
        private var staged = emptyMap<String, String>()
        private var committed = emptyMap<String, String>()

        override fun stage(values: Map<String, String>) {
            staged = values.toMap()
        }

        override fun commit() {
            committed = staged
        }

        override fun verify(values: Map<String, String>) {
            check(values == committed)
        }

        override fun matches(expectedDigests: Map<String, String>): Boolean =
            expectedDigests == committed.mapValues { (key, value) ->
                NativeSecretDigest.sha256(key, value)
            }
    }

    private class JcaTestAead(key: ByteArray) : NativeSecretAead {
        private val secretKey = SecretKeySpec(key.copyOf(), "AES")
        private val random = SecureRandom()

        override fun seal(plaintext: ByteArray, associatedData: ByteArray): NativeSecretSealedData {
            val nonce = ByteArray(NATIVE_SECRET_GCM_NONCE_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey,
                GCMParameterSpec(NATIVE_SECRET_GCM_TAG_BYTES * 8, nonce),
            )
            cipher.updateAAD(associatedData)
            return NativeSecretSealedData(nonce, cipher.doFinal(plaintext))
        }

        override fun open(sealed: NativeSecretSealedData, associatedData: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(NATIVE_SECRET_GCM_TAG_BYTES * 8, sealed.nonce),
            )
            cipher.updateAAD(associatedData)
            return cipher.doFinal(sealed.ciphertext)
        }
    }

    private companion object {
        val KEY_ONE = ByteArray(32) { 0x31 }
        val KEY_TWO = ByteArray(32) { 0x52 }
    }
}
