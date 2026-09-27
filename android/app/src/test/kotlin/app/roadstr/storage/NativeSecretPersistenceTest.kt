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
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeSecretPersistenceTest {
    @Test
    fun `encrypted store survives reopen without persisting plaintext`() =
        withTempDirectory { directory ->
            val store = encryptedStore(directory, KEY_ONE)
            store.stage(snapshot().secureValues)
            store.commit()
            val firstCiphertext = secretFile(directory).readBytes()

            val reopened = encryptedStore(directory, KEY_ONE)
            reopened.verify(snapshot().secureValues)
            assertTrue(reopened.matches(secretDigests(snapshot().secureValues)))

            val persisted = firstCiphertext.toString(StandardCharsets.ISO_8859_1)
            assertFalse(persisted.contains(SECRET))
            assertFalse(persisted.contains(PRIVATE_KEY))

            reopened.stage(snapshot().secureValues)
            reopened.commit()
            val secondCiphertext = secretFile(directory).readBytes()
            assertFalse(firstCiphertext.contentEquals(secondCiphertext))
        }

    @Test
    fun `corrupt activated ciphertext rolls back to authenticated backup`() =
        withTempDirectory { directory ->
            val original = snapshot().secureValues
            val replacement = original + ("nwc_uri" to SECOND_SECRET)
            val store = encryptedStore(directory, KEY_ONE)
            store.stage(original)
            store.commit()
            store.stage(replacement)

            val active = secretFile(directory)
            val stage = File(directory, NATIVE_SECRET_SNAPSHOT_FILE + NATIVE_STAGE_SUFFIX)
            val backup = File(directory, NATIVE_SECRET_SNAPSHOT_FILE + NATIVE_BACKUP_SUFFIX)
            assertTrue(active.renameTo(backup))
            assertTrue(stage.renameTo(active))
            flipLastByte(active)

            val reopened = encryptedStore(directory, KEY_ONE)
            assertTrue(reopened.matches(secretDigests(original)))
            reopened.verify(original)
            assertFalse(backup.exists())

            reopened.stage(replacement)
            reopened.commit()
            reopened.verify(replacement)
        }

    @Test
    fun `marker rejects a lost keystore key and migration can reencrypt on retry`() =
        withTempDirectory { directory ->
            val publicStore = FileNativePublicSnapshotStore(directory)
            val firstSecretStore = encryptedStore(directory, KEY_ONE)
            val first = migration(directory, publicStore, firstSecretStore)
            assertEquals(MigrationOutcome.Migrated, first.run().outcome)

            val reopenedPublicStore = FileNativePublicSnapshotStore(directory)
            val replacementSecretStore = encryptedStore(directory, KEY_TWO)
            val marker = FileSnapshotBoundMigrationMarker(
                directory,
                reopenedPublicStore,
                replacementSecretStore,
            )
            assertFalse(marker.isComplete())

            val retried = migration(directory, reopenedPublicStore, replacementSecretStore).run()
            assertEquals(MigrationOutcome.Migrated, retried.outcome)
            assertTrue(marker.isComplete())
            replacementSecretStore.verify(snapshot().secureValues)
        }

    @Test
    fun `marker rejects a missing protected file`() = withTempDirectory { directory ->
        val publicStore = FileNativePublicSnapshotStore(directory)
        val secretStore = encryptedStore(directory, KEY_ONE)
        val migration = migration(directory, publicStore, secretStore)
        assertEquals(MigrationOutcome.Migrated, migration.run().outcome)
        val marker = FileSnapshotBoundMigrationMarker(directory, publicStore, secretStore)
        assertTrue(marker.isComplete())

        assertTrue(secretFile(directory).delete())

        assertFalse(marker.isComplete())
    }

    @Test
    fun `authenticated data and ciphertext mutation fail closed`() =
        withTempDirectory { directory ->
            val store = encryptedStore(directory, KEY_ONE)
            store.stage(snapshot().secureValues)
            store.commit()
            flipLastByte(secretFile(directory))

            val failure = assertThrows(NativeSecretPersistenceException::class.java) {
                store.verify(snapshot().secureValues)
            }

            assertFalse(failure.message.orEmpty().contains(SECRET))
            assertFalse(store.matches(secretDigests(snapshot().secureValues)))
        }

    @Test
    fun `protected codec is canonical and rejects unsupported keys`() {
        val values = snapshot().secureValues
        val reversed = values.entries.reversed().associate { it.toPair() }

        assertArrayEquals(
            NativeSecretPayloadCodec.encode(values),
            NativeSecretPayloadCodec.encode(reversed),
        )
        assertEquals(values.toSortedMap(), NativeSecretPayloadCodec.decode(
            NativeSecretPayloadCodec.encode(values),
        ))
        val failure = assertThrows(NativeSecretPersistenceException::class.java) {
            NativeSecretPayloadCodec.encode(values + ("unsupported" to SECRET))
        }
        assertFalse(failure.message.orEmpty().contains(SECRET))
    }

    private fun migration(
        directory: File,
        publicStore: FileNativePublicSnapshotStore,
        secretStore: EncryptedFileNativeSecretStore,
    ) = TransactionalMigration(
        reader = LegacySnapshotReader { snapshot() },
        writer = CompositeNativeSnapshotWriter(publicStore, secretStore),
        marker = FileSnapshotBoundMigrationMarker(directory, publicStore, secretStore),
        identityVerifier = { PUBLIC_KEY },
    )

    private fun encryptedStore(directory: File, key: ByteArray) =
        EncryptedFileNativeSecretStore(directory, JcaTestAead(key))

    private fun secretDigests(values: Map<String, String>): Map<String, String> =
        values.toSortedMap().mapValues { (key, value) -> NativeSecretDigest.sha256(key, value) }

    private fun secretFile(directory: File) = File(directory, NATIVE_SECRET_SNAPSHOT_FILE)

    private fun flipLastByte(file: File) {
        RandomAccessFile(file, "rw").use { target ->
            val offset = target.length() - 1
            target.seek(offset)
            val original = target.readByte().toInt()
            target.seek(offset)
            target.writeByte(original xor 0x01)
            target.fd.sync()
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

    private fun withTempDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("roadstr-native-secrets-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val SECRET = "fixture-nwc-secret-not-real"
        const val SECOND_SECRET = "fixture-second-nwc-secret-not-real"
        val PRIVATE_KEY = "11".repeat(32)
        val PUBLIC_KEY = "22".repeat(32)
        val KEY_ONE = ByteArray(32) { 0x31 }
        val KEY_TWO = ByteArray(32) { 0x52 }
    }
}
