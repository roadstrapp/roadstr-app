package app.roadstr.storage

import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
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
import java.util.concurrent.atomic.AtomicInteger
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

class NativeSearchHistoryStoreTest {
    @Test
    fun `empty store loads safely and codec round-trips immutable values`() =
        withTempDirectory { directory ->
            runBlocking {
                val store = store(directory)
                assertTrue(store.load().isEmpty())

                val encoded = NativeSearchHistoryCodec.encode(
                    listOf(entry("Casa", 45.0, 9.0)),
                )
                val decoded = NativeSearchHistoryCodec.decode(encoded)
                assertEquals(listOf(entry("Casa", 45.0, 9.0)), decoded)
                @Suppress("UNCHECKED_CAST")
                assertThrows(UnsupportedOperationException::class.java) {
                    (decoded as MutableList<SearchHistoryEntry>).clear()
                }
            }
        }

    @Test
    fun `legacy import skips corrupt rows survives reopen and persists no plaintext`() =
        withTempDirectory { directory ->
            runBlocking {
                val imported = store(directory).importLegacy(
                    listOf(
                        "{broken",
                        "{\"label\":\"Casa\",\"lat\":45.0,\"lon\":9.0}",
                        42,
                        "{\"label\":\"Lavoro\",\"lat\":44.0,\"lon\":12.0}",
                    ),
                )

                assertEquals(listOf("Casa", "Lavoro"), imported.map { it.label })
                assertEquals(imported, store(directory).load())
                val persisted = historyFile(directory).readBytes()
                    .toString(StandardCharsets.ISO_8859_1)
                assertFalse(persisted.contains("Casa"))
                assertFalse(persisted.contains("Lavoro"))
                assertFalse(persisted.contains("\"lat\""))
            }
        }

    @Test
    fun `legacy import preserves the full one-hundred-row read window`() =
        withTempDirectory { directory ->
            runBlocking {
                val raw = List(120) { index ->
                    SearchHistoryProtocol.encodeEntry(
                        entry("Place $index", latitude = index / 10.0, longitude = 9.0),
                    )
                }

                val imported = store(directory).importLegacy(raw)

                assertEquals(SearchHistoryProtocol.MAX_LOADED_ITEMS, imported.size)
                assertEquals("Place 0", imported.first().label)
                assertEquals("Place 99", imported.last().label)
                assertEquals(imported, store(directory).load())
            }
        }

    @Test
    fun `prepend applies coordinate dedupe and five-row operational cap`() =
        withTempDirectory { directory ->
            runBlocking {
                val store = store(directory)
                repeat(6) { index ->
                    store.prepend(entry("Place $index", 40.0 + index, 9.0))
                }
                val updated = store.prepend(entry("Renamed", 45.00005, 9.00005))

                assertEquals(5, updated.size)
                assertEquals(listOf("Renamed", "Place 4", "Place 3", "Place 2", "Place 1"), updated.map { it.label })
                assertEquals(updated, store(directory).load())
            }
        }

    @Test
    fun `clear is durable across reopen`() = withTempDirectory { directory ->
        runBlocking {
            val store = store(directory)
            store.prepend(entry("Casa", 45.0, 9.0))

            store.clear()

            assertTrue(store(directory).load().isEmpty())
        }
    }

    @Test
    fun `corrupt active history loads empty but mutation fails closed without replacement`() =
        withTempDirectory { directory ->
            runBlocking {
                store(directory).prepend(entry("Private label", 45.0, 9.0))
                val active = historyFile(directory)
                RandomAccessFile(active, "rw").use { file ->
                    val offset = file.length() - 1
                    file.seek(offset)
                    val original = file.readByte().toInt()
                    file.seek(offset)
                    file.writeByte(original xor 0x01)
                    file.fd.sync()
                }
                val damaged = active.readBytes()

                val reopened = store(directory)
                assertTrue(reopened.load().isEmpty())
                assertThrows(NativeSearchHistoryException::class.java) {
                    runBlocking {
                        reopened.prepend(entry("Must not replace", 44.0, 12.0))
                    }
                }

                assertTrue(damaged.contentEquals(active.readBytes()))
            }
        }

    @Test
    fun `lost or changed key cannot overwrite existing history`() =
        withTempDirectory { directory ->
            runBlocking {
                store(directory, KEY_ONE).prepend(entry("Casa", 45.0, 9.0))
                val original = historyFile(directory).readBytes()
                val wrongKeyStore = store(directory, KEY_TWO)

                assertTrue(wrongKeyStore.load().isEmpty())
                assertNull(wrongKeyStore.committedCiphertextDigest())
                assertThrows(NativeSearchHistoryException::class.java) {
                    runBlocking {
                        wrongKeyStore.prepend(entry("Replacement", 44.0, 12.0))
                    }
                }

                assertTrue(original.contentEquals(historyFile(directory).readBytes()))
                assertEquals("Casa", store(directory, KEY_ONE).load().single().label)
            }
        }

    @Test
    fun `migration extracts encrypted history and marker binds exact ciphertext`() =
        withTempDirectory { directory ->
            runBlocking {
                val normalizedHistory =
                    """["{\"label\":\"Private Place\",\"lat\":45.0,\"lon\":9.0}"]"""
                val snapshot = LegacyStorageSnapshot(
                    schemaVersion = 1,
                    ordinaryValues = mapOf(
                        "language" to "it",
                        SearchHistoryProtocol.STORAGE_KEY to normalizedHistory,
                    ),
                    secureValues = emptyMap(),
                    identity = LegacyIdentity(null, null, null),
                    assets = emptyList(),
                )
                val publicStore = FileNativePublicSnapshotStore(directory)
                val secretStore = EncryptedFileNativeSecretStore(
                    directory,
                    JcaTestAead(KEY_TWO),
                )
                val historyStore = store(directory)
                val marker = FileSnapshotBoundMigrationMarker(
                    directory = directory,
                    publicStore = publicStore,
                    secretVerifier = secretStore,
                    searchHistoryVerifier = historyStore,
                )
                val migration = TransactionalMigration(
                    reader = LegacySnapshotReader { snapshot },
                    writer = CompositeNativeSnapshotWriter(
                        publicStore,
                        secretStore,
                        historyStore,
                    ),
                    marker = marker,
                    identityVerifier = { error("anonymous snapshot must not derive an identity") },
                )

                assertEquals(MigrationOutcome.Migrated, migration.run().outcome)
                assertTrue(marker.isComplete())
                assertEquals("Private Place", historyStore.load().single().label)
                val publicRecord = NativeSnapshotRecordCodec.decode(
                    requireNotNull(publicStore.read()),
                )
                assertFalse(
                    publicRecord.ordinaryValues.containsKey(SearchHistoryProtocol.STORAGE_KEY),
                )
                val persisted = requireNotNull(directory.listFiles())
                    .filter(File::isFile)
                    .flatMap { it.readBytes().asIterable() }
                    .toByteArray()
                    .toString(StandardCharsets.ISO_8859_1)
                assertFalse(persisted.contains("Private Place"))
                assertFalse(persisted.contains("\"lat\":45.0"))

                historyStore.prepend(entry("Replacement", 44.0, 12.0))
                assertFalse(marker.isComplete())

                assertEquals(MigrationOutcome.Migrated, migration.run().outcome)
                assertTrue(marker.isComplete())
                assertEquals("Private Place", historyStore.load().single().label)
            }
        }

    @Test
    fun `staged replacement is invisible until commit and remains retryable`() =
        withTempDirectory { directory ->
            runBlocking {
                val original = listOf(entry("Original", 45.0, 9.0))
                store(directory).importLegacy(original.map(SearchHistoryProtocol::encodeEntry))
                val replacement = NativeSearchHistoryPersistenceCodec(JcaTestAead(KEY_ONE)).encode(
                    listOf(entry("Replacement", 44.0, 12.0)),
                )
                val atomic = RecoverableAtomicFile(
                    directory = directory,
                    fileName = NATIVE_SEARCH_HISTORY_FILE,
                    maxBytes = NATIVE_SEARCH_HISTORY_FILE_MAX_BYTES,
                    validator = NativeSearchHistoryPersistenceCodec(
                        JcaTestAead(KEY_ONE),
                    )::decode,
                )
                atomic.stage(replacement)

                assertEquals(original, store(directory).load())

                atomic.commit()
                assertEquals("Replacement", store(directory).load().single().label)
            }
        }

    @Test
    fun `concurrent prepends are serialized without malformed or duplicate rows`() =
        withTempDirectory { directory ->
            runBlocking {
                val sequence = AtomicInteger()
                val store = store(directory)
                (0 until 30).map { index ->
                    async(Dispatchers.Default) {
                        val order = sequence.incrementAndGet()
                        store.prepend(entry("Place $index/$order", 40.0 + index / 100.0, 9.0))
                    }
                }.awaitAll()

                val reopened = store(directory).load()
                assertEquals(SearchHistoryProtocol.MAX_STORED_ITEMS, reopened.size)
                assertEquals(reopened.size, reopened.map { it.label }.toSet().size)
                assertTrue(reopened.all { it.latitude in 40.0..40.29 })
            }
        }

    @Test
    fun `invalid entry and damaged codec failures expose no values or paths`() =
        withTempDirectory { directory ->
            runBlocking {
                val secretLabel = "label-must-not-escape"
                val failure = assertThrows(NativeSearchHistoryException::class.java) {
                    runBlocking {
                        store(directory).prepend(entry(secretLabel, Double.NaN, 9.0))
                    }
                }
                assertFalse(failure.toString().contains(secretLabel))
                assertFalse(failure.toString().contains(directory.path))

                val damaged = NativeSearchHistoryCodec.encode(emptyList()).also { bytes ->
                    bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
                }
                val codecFailure = assertThrows(NativeSearchHistoryException::class.java) {
                    NativeSearchHistoryCodec.decode(damaged)
                }
                assertFalse(codecFailure.toString().contains(directory.path))
            }
        }

    private fun store(
        directory: File,
        key: ByteArray = KEY_ONE,
    ): FileNativeSearchHistoryStore = FileNativeSearchHistoryStore(
        directory,
        JcaTestAead(key),
        Dispatchers.Default,
    )

    private fun historyFile(directory: File): File =
        File(directory, NATIVE_SEARCH_HISTORY_FILE)

    private fun entry(
        label: String,
        latitude: Double,
        longitude: Double,
    ): SearchHistoryEntry = SearchHistoryEntry(label, latitude, longitude)

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("roadstr-native-history-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class JcaTestAead(key: ByteArray) : NativeSecretAead {
        private val secretKey = SecretKeySpec(key.copyOf(), "AES")
        private val secureRandom = SecureRandom()

        override fun seal(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ): NativeSecretSealedData {
            val nonce = ByteArray(NATIVE_SECRET_GCM_NONCE_BYTES).also(secureRandom::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey,
                GCMParameterSpec(NATIVE_SECRET_GCM_TAG_BYTES * 8, nonce),
            )
            cipher.updateAAD(associatedData)
            return NativeSecretSealedData(nonce, cipher.doFinal(plaintext))
        }

        override fun open(
            sealed: NativeSecretSealedData,
            associatedData: ByteArray,
        ): ByteArray {
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
