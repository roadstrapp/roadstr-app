package app.roadstr.storage

import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.saved.NativeParkingPosition
import app.roadstr.feature.saved.NativeSavedPlace
import app.roadstr.feature.saved.NativeSavedPlacesProtocol
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSavedPlacesStoreTest {
    @Test
    fun `record codec is deterministic strict and immutable`() {
        val record = NativeSavedPlacesRecord(
            schemaVersion = 1,
            sequence = 4,
            favorites = listOf(place("Casa", "Via Roma", 45.0, 9.0)),
            parking = parking(44.0, 8.0, 123),
        )

        val encoded = NativeSavedPlacesRecordCodec.encode(record)
        val decoded = NativeSavedPlacesRecordCodec.decode(encoded)

        assertEquals(record, decoded)
        assertEquals(encoded.toList(), NativeSavedPlacesRecordCodec.encode(decoded).toList())
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (decoded.favorites as MutableList<NativeSavedPlace>).clear()
        }
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        assertThrows(NativeSavedPlacesException::class.java) {
            NativeSavedPlacesRecordCodec.decode(encoded)
        }
        assertThrows(NativeSavedPlacesException::class.java) {
            NativeSavedPlacesRecordCodec.encode(
                NativeSavedPlacesRecord(
                    1,
                    0,
                    listOf(place("\ud800", "", 45.0, 9.0)),
                    null,
                ),
            )
        }
    }

    @Test
    fun `transactional legacy import survives reopen without plaintext`() =
        withTempDirectory { directory ->
            val source = listOf(
                place("Casa privata", "Via Segreta", 45.0, 9.0),
                place("Lavoro", "", 44.0, 8.0),
            )
            val store = store(directory)

            store.stageLegacy(
                NativeSavedPlacesProtocol.encodeStoredFavorites(source),
                NativeSavedPlacesProtocol.encodeParking(parking(43.0, 7.0, 456)),
            )
            assertNull(runBlocking { store.load() })
            store.commitStaged()
            store.verifyLegacy(
                NativeSavedPlacesProtocol.encodeStoredFavorites(source),
                NativeSavedPlacesProtocol.encodeParking(parking(43.0, 7.0, 456)),
            )

            val reopened = runBlocking { store(directory).load() }
            assertEquals(source, reopened?.favorites)
            assertEquals(parking(43.0, 7.0, 456), reopened?.parking)
            val persisted = savedFile(directory).readBytes()
                .toString(StandardCharsets.ISO_8859_1)
            assertFalse(persisted.contains("Casa privata"))
            assertFalse(persisted.contains("Via Segreta"))
            assertFalse(persisted.contains("\"lat\""))
        }

    @Test
    fun `typed mutations are durable merge by label and share one UI revision`() =
        withTempDirectory { directory ->
            runBlocking {
                val store = store(directory)
                assertEquals(
                    NativeSavedPlacesInitializationOutcome.Initialized,
                    store.initializeEmptyForNewInstall(),
                )
                assertTrue(store.upsert(5, place("Casa", "Old", 45.0, 9.0)))
                assertTrue(store.upsert(5, place("Lavoro", "Office", 44.0, 8.0)))
                assertTrue(
                    store.merge(
                        5,
                        listOf(
                            place("Casa", "New", 46.0, 10.0),
                            place("Palestra", "Gym", 43.0, 7.0),
                        ),
                    ),
                )
                assertTrue(store.setParking(5, parking(42.0, 6.0, 789)))
                assertTrue(store.delete(5, 1))

                val reopened = store(directory).load()
                assertEquals(listOf("Casa", "Palestra"), reopened?.favorites?.map { it.label })
                assertEquals("New", reopened?.favorites?.first()?.address)
                assertEquals(parking(42.0, 6.0, 789), reopened?.parking)
                assertEquals(5L, reopened?.sequence)
            }
        }

    @Test
    fun `stale revisions are rejected locally and reset only after reopen`() =
        withTempDirectory { directory ->
            runBlocking {
                val store = store(directory)
                store.initializeEmptyForNewInstall()
                assertTrue(store.upsert(8, place("Casa", "", 45.0, 9.0)))
                assertFalse(store.upsert(7, place("Stale", "", 44.0, 8.0)))

                val reopened = store(directory)
                assertTrue(reopened.upsert(0, place("Fresh", "", 43.0, 7.0)))
                assertEquals(listOf("Casa", "Fresh"), reopened.load()?.favorites?.map { it.label })
            }
        }

    @Test
    fun `runtime mutation requires explicit initialization`() = withTempDirectory { directory ->
        runBlocking {
            val store = store(directory)
            assertThrows(NativeSavedPlacesException::class.java) {
                runBlocking { store.upsert(0, place("Casa", "", 45.0, 9.0)) }
            }
            assertEquals(
                NativeSavedPlacesInitializationOutcome.Initialized,
                store.initializeEmptyForNewInstall(),
            )
            assertEquals(
                NativeSavedPlacesInitializationOutcome.AlreadyPresent,
                store.initializeEmptyForNewInstall(),
            )
        }
    }

    @Test
    fun `corruption and a wrong key cannot erase saved places`() =
        withTempDirectory { directory ->
            runBlocking {
                val originalStore = store(directory, KEY_ONE)
                originalStore.initializeEmptyForNewInstall()
                originalStore.upsert(1, place("Private", "Secret", 45.0, 9.0))
                val active = savedFile(directory)
                val original = active.readBytes()

                assertThrows(NativeSavedPlacesException::class.java) {
                    runBlocking { store(directory, KEY_TWO).load() }
                }
                assertThrows(NativeSavedPlacesException::class.java) {
                    runBlocking {
                        store(directory, KEY_TWO).upsert(2, place("Replace", "", 44.0, 8.0))
                    }
                }
                assertTrue(original.contentEquals(active.readBytes()))

                RandomAccessFile(active, "rw").use { file ->
                    val offset = file.length() - 1
                    file.seek(offset)
                    val byte = file.readByte().toInt()
                    file.seek(offset)
                    file.writeByte(byte xor 1)
                    file.fd.sync()
                }
                val corrupt = active.readBytes()
                assertThrows(NativeSavedPlacesException::class.java) {
                    runBlocking { store(directory, KEY_ONE).load() }
                }
                assertTrue(corrupt.contentEquals(active.readBytes()))
            }
        }

    @Test
    fun `migration removes saved coordinates from public bytes and binds ciphertext`() =
        withTempDirectory { directory ->
            val favorites = NativeSavedPlacesProtocol.encodeStoredFavorites(
                listOf(place("Private place", "Hidden address", 45.0, 9.0)),
            )
            val parking = NativeSavedPlacesProtocol.encodeParking(parking(44.0, 8.0, 123))
            val snapshot = snapshot(favorites, parking)
            val publicStore = FileNativePublicSnapshotStore(directory)
            val secretStore = MemorySecretStore()
            val savedStore = store(directory)
            val marker = FileSnapshotBoundMigrationMarker(
                directory = directory,
                publicStore = publicStore,
                secretVerifier = secretStore,
                savedPlacesVerifier = savedStore,
            )
            val migration = TransactionalMigration(
                reader = LegacySnapshotReader { snapshot },
                writer = CompositeNativeSnapshotWriter(
                    publicStore = publicStore,
                    secretStore = secretStore,
                    savedPlacesStore = savedStore,
                ),
                marker = marker,
                identityVerifier = { error("anonymous snapshot must not derive identity") },
            )

            assertEquals(MigrationOutcome.Migrated, migration.run().outcome)
            assertTrue(marker.isComplete())
            val publicBytes = requireNotNull(publicStore.read())
            val publicRecord = NativeSnapshotRecordCodec.decode(publicBytes)
            assertFalse(publicRecord.ordinaryValues.containsKey("favorites"))
            assertFalse(publicRecord.ordinaryValues.containsKey("parking_position"))
            val publicText = publicBytes.toString(StandardCharsets.ISO_8859_1)
            assertFalse(publicText.contains("Private place"))
            assertFalse(publicText.contains("Hidden address"))

            runBlocking { savedStore.upsert(0, place("Changed", "", 43.0, 7.0)) }
            assertFalse(marker.isComplete())
            assertEquals(MigrationOutcome.Migrated, migration.run().outcome)
            assertTrue(marker.isComplete())
            assertEquals(
                listOf("Private place"),
                runBlocking { savedStore.load() }?.favorites?.map { it.label },
            )
        }

    private fun store(
        directory: File,
        key: ByteArray = KEY_ONE,
    ) = FileNativeSavedPlacesStore(directory, JcaTestAead(key), Dispatchers.Default)

    private fun snapshot(favorites: String, parking: String) = LegacyStorageSnapshot(
        schemaVersion = 1,
        ordinaryValues = mapOf(
            "language" to "it",
            "favorites" to favorites,
            "parking_position" to parking,
        ),
        secureValues = emptyMap(),
        identity = LegacyIdentity(null, null, null),
        assets = emptyList(),
    )

    private fun place(
        label: String,
        address: String,
        latitude: Double,
        longitude: Double,
    ) = NativeSavedPlace(label, address, NativeMapPoint(latitude, longitude))

    private fun parking(latitude: Double, longitude: Double, timestamp: Long?) =
        NativeParkingPosition(NativeMapPoint(latitude, longitude), timestamp)

    private fun savedFile(directory: File) = File(directory, NATIVE_SAVED_PLACES_FILE)

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("roadstr-native-saved-").toFile()
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
            check(committed == values)
        }

        override fun matches(expectedDigests: Map<String, String>): Boolean =
            expectedDigests == committed.mapValues { (key, value) ->
                NativeSecretDigest.sha256(key, value)
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
