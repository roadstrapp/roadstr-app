package app.roadstr.roadtest

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small Keystore-backed value store for privacy-sensitive road-test state.
 *
 * Values live in the existing SharedPreferences files only as bounded AES-GCM
 * envelopes. A legacy plaintext value is returned only after it has been
 * replaced durably by ciphertext, which makes the upgrade fail closed.
 */
internal class NativeRoadTestProtectedPreferences(
    context: Context,
    private val preferencesName: String,
    private val keyAlias: String,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )

    fun read(key: String): String? {
        requireSafeKey(key)
        val stored = preferences.getString(key, null) ?: return null
        if (!stored.startsWith(ENVELOPE_PREFIX)) {
            check(validPlaintextSize(stored)) { "Legacy protected value is too large" }
            check(write(key, stored)) { "Unable to migrate protected value" }
            return stored
        }
        return decrypt(key, stored.removePrefix(ENVELOPE_PREFIX))
    }

    fun write(key: String, value: String): Boolean {
        requireSafeKey(key)
        if (!validPlaintextSize(value)) return false
        val encoded = runCatching { ENVELOPE_PREFIX + encrypt(key, value) }.getOrNull()
            ?: return false
        return preferences.edit().putString(key, encoded).commit()
    }

    fun remove(key: String): Boolean {
        requireSafeKey(key)
        return preferences.edit().remove(key).commit()
    }

    private fun encrypt(key: String, value: String): String {
        val plaintext = value.toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(associatedData(key))
            val nonce = cipher.iv
            check(nonce.size == NONCE_BYTES)
            val ciphertext = cipher.doFinal(plaintext)
            val envelope = ByteBuffer.allocate(1 + NONCE_BYTES + ciphertext.size)
                .put(FORMAT_VERSION)
                .put(nonce)
                .put(ciphertext)
                .array()
            check(envelope.size <= MAX_ENVELOPE_BYTES)
            return Base64.encodeToString(envelope, Base64.NO_WRAP)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun decrypt(key: String, encoded: String): String {
        if (encoded.length > MAX_BASE64_CHARS) error("Protected value is too large")
        val envelope = Base64.decode(encoded, Base64.NO_WRAP)
        if (envelope.size !in MIN_ENVELOPE_BYTES..MAX_ENVELOPE_BYTES) {
            error("Protected value has invalid framing")
        }
        val buffer = ByteBuffer.wrap(envelope)
        if (buffer.get() != FORMAT_VERSION) error("Protected value has an unsupported version")
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            existingKey(),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(associatedData(key))
        val plaintext = cipher.doFinal(ciphertext)
        return try {
            if (plaintext.size > MAX_PLAINTEXT_BYTES) error("Protected value is too large")
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plaintext))
                .toString()
        } finally {
            plaintext.fill(0)
        }
    }

    private fun associatedData(key: String): ByteArray =
        "roadstr-roadtest-protected-v1\u0000$preferencesName\u0000$key".toByteArray(Charsets.UTF_8)

    private fun getOrCreateKey(): SecretKey = synchronized(KEYSTORE_LOCK) {
        val store = keyStore()
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return@synchronized it }
        val generator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                keyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generator.generateKey()
    }

    private fun existingKey(): SecretKey = synchronized(KEYSTORE_LOCK) {
        keyStore().getKey(keyAlias, null) as? SecretKey
            ?: error("Protected key is unavailable")
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun requireSafeKey(key: String) {
        require(KEY_PATTERN.matches(key)) { "Protected preference key is invalid" }
    }

    private fun validPlaintextSize(value: String): Boolean =
        value.toByteArray(Charsets.UTF_8).size <= MAX_PLAINTEXT_BYTES

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENVELOPE_PREFIX = "gcm1:"
        const val FORMAT_VERSION: Byte = 1
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val MAX_PLAINTEXT_BYTES = 128 * 1024
        const val MAX_ENVELOPE_BYTES = MAX_PLAINTEXT_BYTES + NONCE_BYTES + 32
        const val MIN_ENVELOPE_BYTES = 1 + NONCE_BYTES + TAG_BITS / 8
        const val MAX_BASE64_CHARS = (MAX_ENVELOPE_BYTES * 4 / 3) + 8
        val KEY_PATTERN = Regex("^[A-Za-z0-9._-]{1,128}$")
        val KEYSTORE_LOCK = Any()
    }
}
