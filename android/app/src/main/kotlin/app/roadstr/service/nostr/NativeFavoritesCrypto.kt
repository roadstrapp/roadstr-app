package app.roadstr.service.nostr

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter

class NativeFavoritesDecryptException : Exception("wrong password or corrupted file")

/**
 * Password protection for exported and synced favourites.
 *
 * Same envelope as the Flutter FavoritesCrypto, so a file or snapshot made by
 * either app opens in the other: PBKDF2-HMAC-SHA256 (600k iterations, the
 * OWASP minimum) derives an AES-256 key, AES-256-GCM encrypts and authenticates
 * with a 128-bit tag, and the fields travel as Base64. BouncyCastle derives the
 * key because the platform's PBKDF2WithHmacSHA256 only exists from API 26.
 *
 * Derivation is deliberately expensive: call from a background dispatcher.
 */
object NativeFavoritesCrypto {
    const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 1_000_000
    private val random = SecureRandom()

    fun encrypt(plaintext: String, password: String): Map<String, Any?> {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(deriveKey(password, salt, ITERATIONS), "AES"),
            GCMParameterSpec(TAG_BITS, iv),
        )
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return linkedMapOf(
            "v" to 1,
            "iterations" to ITERATIONS,
            "salt" to encoder.encodeToString(salt),
            "iv" to encoder.encodeToString(iv),
            "ciphertext" to encoder.encodeToString(ciphertext),
        )
    }

    /**
     * Throws [NativeFavoritesDecryptException] for a wrong password and for any
     * corruption alike: telling them apart would help a brute-forcer. The
     * iteration count comes from an untrusted file; an unbounded value would
     * freeze the app in PBKDF2 for hours, so anything outside a sane window is
     * rejected as corrupt.
     */
    fun decrypt(envelope: Map<*, *>, password: String): String {
        try {
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(envelope["salt"] as String)
            val iv = decoder.decode(envelope["iv"] as String)
            val ciphertext = decoder.decode(envelope["ciphertext"] as String)
            val iterations = (envelope["iterations"] as? Number)?.toInt() ?: ITERATIONS
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw NativeFavoritesDecryptException()
            if (iv.size != IV_BYTES || salt.isEmpty() || salt.size > 256) throw NativeFavoritesDecryptException()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(deriveKey(password, salt, iterations), "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (error: NativeFavoritesDecryptException) {
            throw error
        } catch (_: Exception) {
            throw NativeFavoritesDecryptException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init(password.toByteArray(Charsets.UTF_8), salt, iterations)
        return (generator.generateDerivedParameters(KEY_BITS) as KeyParameter).key
    }
}
