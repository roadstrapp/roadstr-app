package app.roadstr.core.protocol.nostr

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV

class Nip04DecryptException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Legacy NIP-04 encryption retained for NWC peers that do not advertise NIP-44.
 *
 * NIP-04 has no authentication tag. This implementation can reject malformed
 * envelopes and invalid padding, but it cannot reliably detect every wrong-key
 * or modified ciphertext that happens to decrypt to valid PKCS#7 and UTF-8.
 */
object Nip04Cipher {
    const val MAX_PLAINTEXT_BYTES: Int = 65_535
    // Canonical maximum (87,412) plus four `%3D` padding escapes (+8 chars).
    const val MAX_PAYLOAD_CHARACTERS: Int = 87_420

    private const val blockSize = 16
    private const val keySize = 32
    private const val ivSize = 16
    private const val maxCiphertextBytes = 65_536
    private const val delimiter = "?iv="
    private val curveParameters = requireNotNull(SECNamedCurves.getByName("secp256k1"))
    private val secureRandom = SecureRandom()
    private val base64Encoder = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    fun encrypt(
        privateKeyHex: String,
        publicKeyHex: String,
        plaintext: String,
    ): String =
        encryptWithIv(
            privateKeyHex = privateKeyHex,
            publicKeyHex = publicKeyHex,
            plaintext = plaintext,
            iv = ByteArray(ivSize).also(secureRandom::nextBytes),
        )

    fun encryptWithIv(
        privateKeyHex: String,
        publicKeyHex: String,
        plaintext: String,
        iv: ByteArray,
    ): String =
        encryptWithSharedSecret(
            secret = sharedSecret(privateKeyHex, publicKeyHex),
            plaintext = plaintext,
            iv = iv,
        )

    fun encryptWithSharedSecret(
        secret: ByteArray,
        plaintext: String,
        iv: ByteArray,
    ): String {
        requireLength("shared secret", secret, keySize)
        requireLength("IV", iv, ivSize)
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        require(plaintextBytes.size <= MAX_PLAINTEXT_BYTES) {
            "NIP-04 plaintext must be 0..$MAX_PLAINTEXT_BYTES bytes, got ${plaintextBytes.size}"
        }
        val ciphertext = aesCbcEncrypt(secret, iv, plaintextBytes)
        return base64Encoder.encodeToString(ciphertext) + delimiter +
            base64Encoder.encodeToString(iv)
    }

    @Throws(Nip04DecryptException::class)
    fun decrypt(
        privateKeyHex: String,
        publicKeyHex: String,
        payload: String,
    ): String {
        // Parse hostile relay input before doing any elliptic-curve work.
        val envelope = decodeEnvelope(payload)
        return decryptEnvelope(sharedSecret(privateKeyHex, publicKeyHex), envelope)
    }

    @Throws(Nip04DecryptException::class)
    fun decryptWithSharedSecret(
        secret: ByteArray,
        payload: String,
    ): String {
        requireLength("shared secret", secret, keySize)
        return decryptEnvelope(secret, decodeEnvelope(payload))
    }

    /** Returns the unhashed 32-byte X coordinate required by NIP-04. */
    fun sharedSecret(
        privateKeyHex: String,
        publicKeyHex: String,
    ): ByteArray {
        val privateScalar = BigInteger(1, decodeHex(privateKeyHex, "private key", keySize))
        require(privateScalar.signum() > 0 && privateScalar < curveParameters.n) {
            "private key must be a valid secp256k1 scalar"
        }
        val publicX = decodeHex(publicKeyHex, "public key", keySize)
        val publicPoint = try {
            curveParameters.curve.decodePoint(byteArrayOf(0x02) + publicX).normalize()
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("public key is not a valid secp256k1 point", error)
        }
        require(!publicPoint.isInfinity && publicPoint.isValid) {
            "public key is not a valid secp256k1 point"
        }
        val sharedPoint = publicPoint.multiply(privateScalar).normalize()
        require(!sharedPoint.isInfinity && sharedPoint.isValid) {
            "ECDH produced an invalid secp256k1 point"
        }
        return sharedPoint.affineXCoord.getEncoded().also { sharedX ->
            require(sharedX.size == keySize) { "ECDH x-coordinate must be 32 bytes" }
        }
    }

    private data class Envelope(
        val ciphertext: ByteArray,
        val iv: ByteArray,
    )

    @Throws(Nip04DecryptException::class)
    private fun decryptEnvelope(
        secret: ByteArray,
        envelope: Envelope,
    ): String {
        val plaintext = try {
            aesCbcDecrypt(secret, envelope.iv, envelope.ciphertext)
        } catch (error: Nip04DecryptException) {
            throw error
        } catch (error: RuntimeException) {
            throw Nip04DecryptException("invalid padding or key", error)
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(plaintext)).toString()
        } catch (error: Exception) {
            throw Nip04DecryptException("invalid UTF-8 plaintext", error)
        }
    }

    @Throws(Nip04DecryptException::class)
    private fun decodeEnvelope(payload: String): Envelope {
        if (payload.length > MAX_PAYLOAD_CHARACTERS) {
            throw Nip04DecryptException("payload too long")
        }
        val separator = payload.indexOf(delimiter)
        if (separator <= 0 || separator != payload.lastIndexOf(delimiter)) {
            throw Nip04DecryptException("invalid payload format")
        }
        val ciphertext = decodeCompatibleBase64(
            payload.substring(0, separator),
            "ciphertext",
        )
        val iv = decodeCompatibleBase64(
            payload.substring(separator + delimiter.length),
            "IV",
        )
        if (iv.size != ivSize) throw Nip04DecryptException("IV must be 16 bytes")
        if (
            ciphertext.isEmpty() ||
            ciphertext.size > maxCiphertextBytes ||
            ciphertext.size % blockSize != 0
        ) {
            throw Nip04DecryptException("invalid ciphertext length")
        }
        return Envelope(ciphertext, iv)
    }

    @Throws(Nip04DecryptException::class)
    private fun decodeCompatibleBase64(
        value: String,
        name: String,
    ): ByteArray {
        if (value.isEmpty()) throw Nip04DecryptException("empty $name base64")
        val normalized = StringBuilder(value.length + 2)
        var sourceIndex = 0
        while (sourceIndex < value.length) {
            var character = value[sourceIndex]
            if (character == '%') {
                if (
                    sourceIndex + 2 >= value.length ||
                    value[sourceIndex + 1] != '3' ||
                    value[sourceIndex + 2].lowercaseChar() != 'd'
                ) {
                    throw Nip04DecryptException("invalid $name base64")
                }
                character = '='
                sourceIndex += 3
            } else {
                sourceIndex++
            }
            normalized.append(
                when (character) {
                    '-' -> '+'
                    '_' -> '/'
                    in 'A'..'Z', in 'a'..'z', in '0'..'9', '+', '/', '=' -> character
                    else -> throw Nip04DecryptException("invalid $name base64")
                },
            )
        }
        val firstPadding = normalized.indexOf("=")
        if (
            firstPadding >= 0 &&
            (
                normalized.length % 4 != 0 ||
                    normalized.length - firstPadding > 2 ||
                    normalized.substring(firstPadding).any { it != '=' }
            )
        ) {
            throw Nip04DecryptException("invalid $name base64 padding")
        }
        if (firstPadding < 0) {
            if (normalized.length % 4 != 0) {
                throw Nip04DecryptException("invalid $name base64 length")
            }
        }
        val decoded = try {
            base64Decoder.decode(normalized.toString())
        } catch (error: IllegalArgumentException) {
            throw Nip04DecryptException("invalid $name base64", error)
        }
        if (base64Encoder.encodeToString(decoded) != normalized.toString()) {
            throw Nip04DecryptException("invalid $name base64 trailing bits")
        }
        return decoded
    }

    private fun aesCbcEncrypt(
        key: ByteArray,
        iv: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val padding = blockSize - (plaintext.size % blockSize)
        val padded = plaintext.copyOf(plaintext.size + padding)
        padded.fill(padding.toByte(), plaintext.size, padded.size)
        return aesCbc(key, iv, padded, encrypting = true)
    }

    @Throws(Nip04DecryptException::class)
    private fun aesCbcDecrypt(
        key: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val padded = aesCbc(key, iv, ciphertext, encrypting = false)
        val padding = padded.last().toInt() and 0xff
        if (padding == 0 || padding > blockSize || padding > padded.size) {
            throw Nip04DecryptException("invalid PKCS#7 padding")
        }
        var mismatch = 0
        for (index in padded.size - padding until padded.size) {
            mismatch = mismatch or ((padded[index].toInt() and 0xff) xor padding)
        }
        if (mismatch != 0) throw Nip04DecryptException("invalid PKCS#7 padding")
        return padded.copyOf(padded.size - padding)
    }

    private fun aesCbc(
        key: ByteArray,
        iv: ByteArray,
        input: ByteArray,
        encrypting: Boolean,
    ): ByteArray {
        val cipher = CBCBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(encrypting, ParametersWithIV(KeyParameter(key), iv))
        return ByteArray(input.size).also { output ->
            for (offset in input.indices step blockSize) {
                cipher.processBlock(input, offset, output, offset)
            }
        }
    }

    private fun decodeHex(
        value: String,
        name: String,
        expectedBytes: Int,
    ): ByteArray {
        require(value.length == expectedBytes * 2) {
            "$name must contain ${expectedBytes * 2} hexadecimal characters"
        }
        return ByteArray(expectedBytes) { index ->
            val high = Character.digit(value[index * 2], 16)
            val low = Character.digit(value[index * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "$name contains non-hexadecimal characters" }
            ((high shl 4) or low).toByte()
        }
    }

    private fun requireLength(
        name: String,
        value: ByteArray,
        expected: Int,
    ) {
        require(value.size == expected) { "$name must be $expected bytes, got ${value.size}" }
    }
}
