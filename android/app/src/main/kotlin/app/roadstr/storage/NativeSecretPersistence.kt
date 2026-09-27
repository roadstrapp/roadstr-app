package app.roadstr.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.roadstr.migration.LegacySnapshotLimits
import app.roadstr.migration.LegacyStorageContract
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal const val NATIVE_SECRET_SNAPSHOT_FILE = "native_secrets_v1.bin"
internal const val NATIVE_SECRET_FILE_MAX_BYTES = 1024 * 1024
internal const val NATIVE_SECRET_GCM_NONCE_BYTES = 12
internal const val NATIVE_SECRET_GCM_TAG_BYTES = 16

class NativeSecretPersistenceException(message: String) : IllegalStateException(message)

internal data class NativeSecretSealedData(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

/** Small cryptographic boundary; production uses a non-exportable Keystore key. */
internal interface NativeSecretAead {
    fun seal(plaintext: ByteArray, associatedData: ByteArray): NativeSecretSealedData
    fun open(sealed: NativeSecretSealedData, associatedData: ByteArray): ByteArray
}

/** Canonical plaintext map used only in memory immediately around AES-GCM. */
internal object NativeSecretPayloadCodec {
    private val magic = "RSTRSEC1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1

    fun encode(values: Map<String, String>): ByteArray {
        validate(values)
        val outputBuffer = ByteArrayOutputStream()
        DataOutputStream(outputBuffer).use { output ->
            output.write(magic)
            output.writeInt(FORMAT_VERSION)
            output.writeInt(values.size)
            for ((key, value) in values.toSortedMap()) {
                output.writeBoundedString(key, LegacySnapshotLimits.MAX_KEY_BYTES)
                output.writeBoundedString(value, LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES)
            }
        }
        return outputBuffer.toByteArray().also { encoded ->
            if (encoded.size > MAX_PLAINTEXT_BYTES) fail("Native protected payload is too large")
        }
    }

    fun decode(encoded: ByteArray): Map<String, String> {
        if (encoded.size !in MIN_PLAINTEXT_BYTES..MAX_PLAINTEXT_BYTES) {
            fail("Native protected payload size is invalid")
        }
        val cursor = Cursor(encoded)
        if (!MessageDigest.isEqual(magic, cursor.readBytes(magic.size))) {
            fail("Native protected payload header is invalid")
        }
        if (cursor.readInt() != FORMAT_VERSION) {
            fail("Native protected payload version is unsupported")
        }
        val count = cursor.readInt()
        if (count !in 0..LegacySnapshotLimits.MAX_ENTRIES_PER_STORE) {
            fail("Native protected payload count is invalid")
        }

        val values = LinkedHashMap<String, String>(count)
        var previousKey: String? = null
        repeat(count) {
            val key = cursor.readString(LegacySnapshotLimits.MAX_KEY_BYTES)
            val value = cursor.readString(LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES)
            if (key !in LegacyStorageContract.secureKeys ||
                (previousKey != null && key <= previousKey!!)
            ) {
                fail("Native protected payload key set is invalid")
            }
            values[key] = value
            previousKey = key
        }
        if (!cursor.isAtEnd()) fail("Native protected payload has trailing data")
        validate(values)
        return values
    }

    fun validate(values: Map<String, String>) {
        if (values.size > LegacySnapshotLimits.MAX_ENTRIES_PER_STORE ||
            values.keys.any { it !in LegacyStorageContract.secureKeys }
        ) {
            fail("Native protected payload key set is invalid")
        }
        for ((key, value) in values) {
            if (key.toByteArray(Charsets.UTF_8).size > LegacySnapshotLimits.MAX_KEY_BYTES ||
                value.toByteArray(Charsets.UTF_8).size > LegacySnapshotLimits.MAX_SECURE_VALUE_BYTES
            ) {
                fail("Native protected payload field is too large")
            }
        }
    }

    private fun DataOutputStream.writeBoundedString(value: String, maxBytes: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) fail("Native protected payload field is too large")
        writeInt(bytes.size)
        write(bytes)
    }

    private class Cursor(private val bytes: ByteArray) {
        private var offset = 0

        fun readInt(): Int = ByteBuffer.wrap(readBytes(Int.SIZE_BYTES)).int

        fun readString(maxBytes: Int): String {
            val length = readInt()
            if (length !in 0..maxBytes) fail("Native protected payload field is invalid")
            val encoded = readBytes(length)
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString()
            } catch (_: CharacterCodingException) {
                fail("Native protected payload text is invalid")
            }
        }

        fun readBytes(count: Int): ByteArray {
            if (count < 0 || count > bytes.size - offset) {
                fail("Native protected payload is truncated")
            }
            return bytes.copyOfRange(offset, offset + count).also { offset += count }
        }

        fun isAtEnd(): Boolean = offset == bytes.size
    }

    private const val MIN_PLAINTEXT_BYTES = 16
    private const val MAX_PLAINTEXT_BYTES = NATIVE_SECRET_FILE_MAX_BYTES - 64

    private fun fail(message: String): Nothing = throw NativeSecretPersistenceException(message)
}

/** Versioned binary framing around the nonce and authenticated ciphertext. */
internal object NativeSecretEncryptedEnvelope {
    private val magic = "RSTRGCM1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1
    val associatedData = "roadstr-native-secret-envelope-v1".toByteArray(Charsets.US_ASCII)

    fun encode(sealed: NativeSecretSealedData): ByteArray {
        validate(sealed)
        val outputBuffer = ByteArrayOutputStream(HEADER_BYTES + sealed.nonce.size + sealed.ciphertext.size)
        DataOutputStream(outputBuffer).use { output ->
            output.write(magic)
            output.writeInt(FORMAT_VERSION)
            output.writeInt(sealed.nonce.size)
            output.writeInt(sealed.ciphertext.size)
            output.write(sealed.nonce)
            output.write(sealed.ciphertext)
        }
        return outputBuffer.toByteArray()
    }

    fun decode(encoded: ByteArray): NativeSecretSealedData {
        if (encoded.size !in MIN_ENVELOPE_BYTES..NATIVE_SECRET_FILE_MAX_BYTES) {
            fail("Native protected envelope size is invalid")
        }
        val buffer = ByteBuffer.wrap(encoded)
        val actualMagic = ByteArray(magic.size).also(buffer::get)
        if (!MessageDigest.isEqual(magic, actualMagic) || buffer.int != FORMAT_VERSION) {
            fail("Native protected envelope header is invalid")
        }
        val nonceSize = buffer.int
        val ciphertextSize = buffer.int
        if (nonceSize != NATIVE_SECRET_GCM_NONCE_BYTES ||
            ciphertextSize !in NATIVE_SECRET_GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES ||
            nonceSize + ciphertextSize != buffer.remaining()
        ) {
            fail("Native protected envelope framing is invalid")
        }
        val nonce = ByteArray(nonceSize).also(buffer::get)
        val ciphertext = ByteArray(ciphertextSize).also(buffer::get)
        return NativeSecretSealedData(nonce, ciphertext).also(::validate)
    }

    private fun validate(sealed: NativeSecretSealedData) {
        if (sealed.nonce.size != NATIVE_SECRET_GCM_NONCE_BYTES ||
            sealed.ciphertext.size !in NATIVE_SECRET_GCM_TAG_BYTES..MAX_CIPHERTEXT_BYTES
        ) {
            fail("Native protected envelope is invalid")
        }
    }

    private const val HEADER_BYTES = 20
    private const val MIN_ENVELOPE_BYTES = HEADER_BYTES +
        NATIVE_SECRET_GCM_NONCE_BYTES + NATIVE_SECRET_GCM_TAG_BYTES
    private const val MAX_CIPHERTEXT_BYTES = NATIVE_SECRET_FILE_MAX_BYTES -
        HEADER_BYTES - NATIVE_SECRET_GCM_NONCE_BYTES

    private fun fail(message: String): Nothing = throw NativeSecretPersistenceException(message)
}

/**
 * Recoverable encrypted implementation shared by the Android adapter and JVM
 * tests. It never exposes a method that returns decrypted values.
 */
internal class EncryptedFileNativeSecretStore(
    directory: File,
    private val aead: NativeSecretAead,
) : NativeSecretStore, NativeSecretCommitmentVerifier {
    private val file = RecoverableAtomicFile(
        directory = directory,
        fileName = NATIVE_SECRET_SNAPSHOT_FILE,
        maxBytes = NATIVE_SECRET_FILE_MAX_BYTES,
        validator = { decodeValues(it) },
    )

    override fun stage(values: Map<String, String>) {
        val plaintext = NativeSecretPayloadCodec.encode(values)
        try {
            val sealed = aead.seal(plaintext, NativeSecretEncryptedEnvelope.associatedData)
            file.stage(NativeSecretEncryptedEnvelope.encode(sealed))
        } catch (error: NativePersistenceException) {
            throw error
        } catch (_: Exception) {
            throw NativeSecretPersistenceException("Native protected store staging failed")
        } finally {
            plaintext.fill(0)
        }
    }

    override fun commit() {
        try {
            file.commit()
        } catch (_: RuntimeException) {
            throw NativeSecretPersistenceException("Native protected store commit failed")
        }
    }

    override fun verify(values: Map<String, String>) {
        try {
            NativeSecretPayloadCodec.validate(values)
            val actual = file.read()?.let(::decodeValues)
            if (actual != values.toSortedMap()) {
                throw NativeSecretPersistenceException("Native protected store verification failed")
            }
        } catch (error: NativeSecretPersistenceException) {
            throw error
        } catch (_: RuntimeException) {
            throw NativeSecretPersistenceException("Native protected store verification failed")
        }
    }

    override fun matches(expectedDigests: Map<String, String>): Boolean {
        return try {
            if (expectedDigests.keys.any { it !in LegacyStorageContract.secureKeys } ||
                expectedDigests.values.any { !HEX_64.matches(it) }
            ) {
                return false
            }
            val actual = file.read()?.let(::decodeValues) ?: return false
            if (actual.keys != expectedDigests.keys) return false
            actual.all { (key, value) ->
                val actualDigest = NativeSecretDigest.sha256(key, value)
                    .toByteArray(Charsets.US_ASCII)
                val expectedDigest = expectedDigests.getValue(key)
                    .toByteArray(Charsets.US_ASCII)
                MessageDigest.isEqual(actualDigest, expectedDigest)
            }
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun decodeValues(encoded: ByteArray): Map<String, String> {
        val sealed = NativeSecretEncryptedEnvelope.decode(encoded)
        val plaintext = try {
            aead.open(sealed, NativeSecretEncryptedEnvelope.associatedData)
        } catch (_: Exception) {
            throw NativeSecretPersistenceException("Native protected envelope authentication failed")
        }
        return try {
            NativeSecretPayloadCodec.decode(plaintext).toSortedMap()
        } finally {
            plaintext.fill(0)
        }
    }

    private companion object {
        val HEX_64 = Regex("^[0-9a-fA-F]{64}$")
    }
}

/** Android production adapter backed by a non-exportable AES-256 Keystore key. */
class AndroidKeystoreNativeSecretStore(
    directory: File,
    keyAlias: String = DEFAULT_KEY_ALIAS,
) : NativeSecretStore, NativeSecretCommitmentVerifier {
    private val delegate = EncryptedFileNativeSecretStore(
        directory = directory,
        aead = AndroidKeystoreAead(keyAlias),
    )

    override fun stage(values: Map<String, String>) = delegate.stage(values)

    override fun commit() = delegate.commit()

    override fun verify(values: Map<String, String>) = delegate.verify(values)

    override fun matches(expectedDigests: Map<String, String>): Boolean =
        delegate.matches(expectedDigests)

    companion object {
        const val DEFAULT_KEY_ALIAS = "app.roadstr.native.secrets.v1"
    }
}

/** AES-GCM implementation whose key material never leaves AndroidKeyStore. */
internal class AndroidKeystoreAead(
    private val keyAlias: String,
) : NativeSecretAead {
    init {
        if (!KEY_ALIAS.matches(keyAlias)) {
            throw NativeSecretPersistenceException("Native protected key alias is invalid")
        }
    }

    override fun seal(plaintext: ByteArray, associatedData: ByteArray): NativeSecretSealedData =
        guarded("Native protected encryption failed") {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(associatedData)
            val nonce = cipher.iv
            if (nonce.size != NATIVE_SECRET_GCM_NONCE_BYTES) {
                throw NativeSecretPersistenceException("Native protected nonce is invalid")
            }
            NativeSecretSealedData(nonce.copyOf(), cipher.doFinal(plaintext))
        }

    override fun open(sealed: NativeSecretSealedData, associatedData: ByteArray): ByteArray =
        guarded("Native protected decryption failed") {
            if (sealed.nonce.size != NATIVE_SECRET_GCM_NONCE_BYTES) {
                throw NativeSecretPersistenceException("Native protected nonce is invalid")
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingKey(),
                GCMParameterSpec(GCM_TAG_BITS, sealed.nonce),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(sealed.ciphertext)
        }

    private fun getOrCreateKey(): SecretKey = synchronized(KEYSTORE_LOCK) {
        val keyStore = loadedKeyStore()
        if (keyStore.containsAlias(keyAlias)) return@synchronized keyStore.secretKey()

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specification = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(specification)
        generator.generateKey()
        loadedKeyStore().secretKey()
    }

    private fun getExistingKey(): SecretKey = synchronized(KEYSTORE_LOCK) {
        loadedKeyStore().secretKey()
    }

    private fun KeyStore.secretKey(): SecretKey {
        if (!containsAlias(keyAlias)) {
            throw NativeSecretPersistenceException("Native protected key is unavailable")
        }
        val key = getKey(keyAlias, null) as? SecretKey
            ?: throw NativeSecretPersistenceException("Native protected key is invalid")
        if (!key.algorithm.equals(KeyProperties.KEY_ALGORITHM_AES, ignoreCase = true)) {
            throw NativeSecretPersistenceException("Native protected key is invalid")
        }
        return key
    }

    private fun loadedKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private inline fun <T> guarded(message: String, block: () -> T): T = try {
        block()
    } catch (error: NativeSecretPersistenceException) {
        throw error
    } catch (_: Exception) {
        throw NativeSecretPersistenceException(message)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = NATIVE_SECRET_GCM_TAG_BYTES * 8
        val KEY_ALIAS = Regex("^[A-Za-z0-9._-]{1,128}$")
        val KEYSTORE_LOCK = Any()
    }
}
