package app.roadstr.storage

import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.feature.activity.NativeActivityCursorKind
import app.roadstr.feature.activity.NativeActivityInboxProtocol
import app.roadstr.feature.activity.NativeActivityNotification
import app.roadstr.feature.activity.NativeActivityNotificationType
import app.roadstr.migration.LegacyIdentity
import app.roadstr.migration.LegacySnapshotReader
import app.roadstr.migration.LegacyStorageContract
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

class NativeActivityStoreTest {
    @Test
    fun `record codec is deterministic strict and immutable`() {
        val state = NativeActivityIdentityState(
            PUBKEY_ONE,
            listOf(reaction(2), zap(1)),
            100,
            200,
        )
        val record = NativeActivityRecord(1, 4, mapOf(PUBKEY_ONE to state))

        val encoded = NativeActivityRecordCodec.encode(record)
        val decoded = NativeActivityRecordCodec.decode(encoded)

        assertEquals(record, decoded)
        assertEquals(encoded.toList(), NativeActivityRecordCodec.encode(decoded).toList())
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (decoded.identities as MutableMap<String, NativeActivityIdentityState>).clear()
        }
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (decoded.identities.getValue(PUBKEY_ONE).items as MutableList<*>).clear()
        }
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        assertThrows(NativeActivityStoreException::class.java) {
            NativeActivityRecordCodec.decode(encoded)
        }
    }

    @Test
    fun `legacy multi identity import is atomic tolerant and encrypted`() =
        withTempDirectory { directory ->
            val store = store(directory)
            val values = mapOf(
                "activity_inbox_$PUBKEY_ONE" to NativeActivityInboxProtocol.encodeNormalized(
                    listOf(zap(1), reaction(2)),
                ),
                "activity_zap_cursor_$PUBKEY_ONE" to "100",
                "activity_inbox_$PUBKEY_TWO" to "malformed-json",
                "activity_confirmation_cursor_$PUBKEY_TWO" to "200",
            )

            store.stageLegacy(values)
            assertNull(runBlocking { store.read() })
            store.commitStaged()
            store.verifyLegacy(values)

            val reopened = runBlocking { store(directory).read() }!!
            assertEquals(setOf(PUBKEY_ONE, PUBKEY_TWO), reopened.identities.keys)
            assertEquals(2, reopened.identities.getValue(PUBKEY_ONE).items.size)
            assertEquals(100L, reopened.identities.getValue(PUBKEY_ONE).zapCursorSeconds)
            assertTrue(reopened.identities.getValue(PUBKEY_TWO).items.isEmpty())
            assertEquals(200L, reopened.identities.getValue(PUBKEY_TWO).confirmationCursorSeconds)
            val persisted = activityFile(directory).readBytes()
                .toString(StandardCharsets.ISO_8859_1)
            assertFalse(persisted.contains(PUBKEY_ONE))
            assertFalse(persisted.contains(id(1)))
            assertFalse(persisted.contains("road_closure"))
        }

    @Test
    fun `typed mutations are durable idempotent monotonic and share a revision`() =
        withTempDirectory { directory ->
            runBlocking {
                val store = store(directory)
                assertEquals(
                    NativeActivityInitializationOutcome.Initialized,
                    store.initializeEmptyForNewInstall(),
                )
                assertTrue(store.seedCursor(5, NativeActivityCursorKind.Zap, PUBKEY_ONE, 100))
                assertTrue(
                    store.seedCursor(5, NativeActivityCursorKind.Confirmation, PUBKEY_ONE, 200),
                )
                assertFalse(store.seedCursor(5, NativeActivityCursorKind.Zap, PUBKEY_ONE, 999))
                assertTrue(store.record(5, PUBKEY_ONE, zap(1)))
                assertFalse(store.record(5, PUBKEY_ONE, zap(1)))
                assertFalse(store.advanceCursor(5, NativeActivityCursorKind.Zap, PUBKEY_ONE, 99))
                assertTrue(store.advanceCursor(5, NativeActivityCursorKind.Zap, PUBKEY_ONE, 101))
                assertTrue(store.markAllRead(5, PUBKEY_ONE))
                assertFalse(store.markAllRead(5, PUBKEY_ONE))

                val reopened = store(directory).read()!!
                val state = reopened.identities.getValue(PUBKEY_ONE)
                assertEquals(5L, reopened.sequence)
                assertEquals(101L, state.zapCursorSeconds)
                assertEquals(200L, state.confirmationCursorSeconds)
                assertTrue(state.items.single().isRead)
            }
        }

    @Test
    fun `stale revisions are local and mutation requires initialization`() =
        withTempDirectory { directory ->
            runBlocking {
                val original = store(directory)
                assertThrows(NativeActivityStoreException::class.java) {
                    runBlocking { original.record(0, PUBKEY_ONE, zap(1)) }
                }
                original.initializeEmptyForNewInstall()
                assertEquals(
                    NativeActivityInitializationOutcome.AlreadyPresent,
                    original.initializeEmptyForNewInstall(),
                )
                assertTrue(original.record(8, PUBKEY_ONE, zap(1)))
                assertFalse(original.record(7, PUBKEY_ONE, zap(2)))

                val reopened = store(directory)
                assertTrue(reopened.record(0, PUBKEY_ONE, zap(2)))
                assertEquals(2, reopened.loadIdentity(PUBKEY_ONE)?.items?.size)
            }
        }

    @Test
    fun `wrong key and corruption cannot overwrite activity`() =
        withTempDirectory { directory ->
            runBlocking {
                val originalStore = store(directory, KEY_ONE)
                originalStore.initializeEmptyForNewInstall()
                originalStore.record(1, PUBKEY_ONE, zap(1))
                val active = activityFile(directory)
                val original = active.readBytes()

                assertThrows(NativeActivityStoreException::class.java) {
                    runBlocking { store(directory, KEY_TWO).read() }
                }
                assertThrows(NativeActivityStoreException::class.java) {
                    runBlocking { store(directory, KEY_TWO).record(2, PUBKEY_ONE, zap(2)) }
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
                assertThrows(NativeActivityStoreException::class.java) {
                    runBlocking { store(directory, KEY_ONE).read() }
                }
                assertTrue(corrupt.contentEquals(active.readBytes()))
            }
        }

    @Test
    fun `migration removes activity from public bytes and binds exact ciphertext`() =
        withTempDirectory { directory ->
            val values = mapOf(
                "activity_inbox_$PUBKEY_ONE" to NativeActivityInboxProtocol.encodeNormalized(
                    listOf(reaction(2)),
                ),
                "activity_zap_cursor_$PUBKEY_ONE" to "100",
                "activity_confirmation_cursor_$PUBKEY_ONE" to "200",
            )
            val snapshot = LegacyStorageSnapshot(
                schemaVersion = 1,
                ordinaryValues = mapOf("language" to "it") + values,
                secureValues = emptyMap(),
                identity = LegacyIdentity(null, null, null),
                assets = emptyList(),
            )
            val publicStore = FileNativePublicSnapshotStore(directory)
            val secretStore = MemorySecretStore()
            val activityStore = store(directory)
            val marker = FileSnapshotBoundMigrationMarker(
                directory = directory,
                publicStore = publicStore,
                secretVerifier = secretStore,
                activityVerifier = activityStore,
            )
            val migration = TransactionalMigration(
                reader = LegacySnapshotReader { snapshot },
                writer = CompositeNativeSnapshotWriter(
                    publicStore = publicStore,
                    secretStore = secretStore,
                    activityStore = activityStore,
                ),
                marker = marker,
                identityVerifier = { error("anonymous snapshot must not derive identity") },
            )

            assertEquals(MigrationOutcome.Migrated, migration.run().outcome)
            assertTrue(marker.isComplete())
            val publicBytes = requireNotNull(publicStore.read())
            val publicRecord = NativeSnapshotRecordCodec.decode(publicBytes)
            assertTrue(publicRecord.ordinaryValues.keys.none(LegacyStorageContract::isDynamicKey))
            assertTrue(File(directory, NATIVE_MIGRATION_MARKER_FILE).readBytes().startsWithMagic("RSTRMIG4"))
            val publicText = publicBytes.toString(StandardCharsets.ISO_8859_1)
            assertFalse(publicText.contains(PUBKEY_ONE))
            assertFalse(publicText.contains(id(2)))

            runBlocking { activityStore.record(0, PUBKEY_ONE, zap(3)) }
            assertFalse(marker.isComplete())
            assertEquals(MigrationOutcome.Migrated, migration.run().outcome)
            assertTrue(marker.isComplete())
            assertEquals(
                listOf(id(2)),
                runBlocking { activityStore.loadIdentity(PUBKEY_ONE) }?.items?.map { it.id },
            )
        }

    @Test
    fun `legacy key normalization rejects case collisions and invalid cursors`() =
        withTempDirectory { directory ->
            val store = store(directory)
            assertThrows(NativeActivityStoreException::class.java) {
                store.stageLegacy(
                    mapOf(
                        "activity_inbox_$PUBKEY_ONE" to "[]",
                        "activity_inbox_${PUBKEY_ONE.uppercase()}" to "[]",
                    ),
                )
            }
            assertThrows(NativeActivityStoreException::class.java) {
                store.stageLegacy(mapOf("activity_zap_cursor_$PUBKEY_ONE" to "-1"))
            }
        }

    private fun store(
        directory: File,
        key: ByteArray = KEY_ONE,
    ) = FileNativeActivityStore(directory, JcaTestAead(key), Dispatchers.Default)

    private fun zap(index: Int) = NativeActivityNotification(
        id = id(index),
        type = NativeActivityNotificationType.Zap,
        createdAtSeconds = index.toLong(),
        amountSat = index.toLong(),
    )

    private fun reaction(index: Int) = NativeActivityNotification(
        id = id(index),
        type = NativeActivityNotificationType.Confirmed,
        createdAtSeconds = index.toLong(),
        category = RoadCategoryWire.ROAD_CLOSURE,
    )

    private fun id(index: Int) = index.toString(16).padStart(64, '0')

    private fun activityFile(directory: File) = File(directory, NATIVE_ACTIVITY_FILE)

    private fun ByteArray.startsWithMagic(value: String): Boolean =
        take(value.length).toByteArray().contentEquals(value.toByteArray(Charsets.US_ASCII))

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("roadstr-native-activity-").toFile()
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
        val PUBKEY_ONE = "a".repeat(64)
        val PUBKEY_TWO = "b".repeat(64)
        val KEY_ONE = ByteArray(32) { 0x31 }
        val KEY_TWO = ByteArray(32) { 0x52 }
    }
}
