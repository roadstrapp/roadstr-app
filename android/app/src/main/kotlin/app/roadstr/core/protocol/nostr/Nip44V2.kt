package app.roadstr.core.protocol.nostr

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.util.Arrays as BouncyArrays
import org.bouncycastle.util.encoders.Base64

class Nip44DecryptException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class Nip44MessageKeys(
    val chachaKey: ByteArray,
    val chachaNonce: ByteArray,
    val hmacKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is Nip44MessageKeys &&
            chachaKey.contentEquals(other.chachaKey) &&
            chachaNonce.contentEquals(other.chachaNonce) &&
            hmacKey.contentEquals(other.hmacKey)

    override fun hashCode(): Int {
        var result = chachaKey.contentHashCode()
        result = 31 * result + chachaNonce.contentHashCode()
        return 31 * result + hmacKey.contentHashCode()
    }
}

/** Roadstr's shipped NIP-44 v2 profile, with the original u16 plaintext limit. */
object Nip44V2 {
    private val salt = "nip44-v2".toByteArray(Charsets.UTF_8)
    private val curveParameters = requireNotNull(SECNamedCurves.getByName("secp256k1"))
    private val secureRandom = SecureRandom()
    private const val version: Byte = 0x02
    private const val keyLength = 32
    private const val maxPlaintextLength = 65_535
    private const val minimumEnvelopeLength = 1 + 32 + 32 + 32
    private const val maximumEnvelopeLength = 1 + 32 + (2 + 65_536) + 32
    private const val maximumBase64Length = ((maximumEnvelopeLength + 2) / 3) * 4

    fun encrypt(
        privateKeyHex: String,
        publicKeyHex: String,
        plaintext: String,
    ): String {
        val conversationKey = conversationKey(privateKeyHex, publicKeyHex)
        val nonce = ByteArray(32).also(secureRandom::nextBytes)
        return encryptWithConversationKey(conversationKey, nonce, plaintext)
    }

    fun encryptWithNonce(
        privateKeyHex: String,
        publicKeyHex: String,
        plaintext: String,
        nonce: ByteArray,
    ): String =
        encryptWithConversationKey(
            conversationKey(privateKeyHex, publicKeyHex),
            nonce,
            plaintext,
        )

    fun encryptWithConversationKey(
        conversationKey: ByteArray,
        nonce: ByteArray,
        plaintext: String,
    ): String {
        requireLength("conversation key", conversationKey, keyLength)
        requireLength("nonce", nonce, keyLength)
        val keys = messageKeys(conversationKey, nonce)
        val padded = pad(plaintext.toByteArray(Charsets.UTF_8))
        val ciphertext = chacha20(keys.chachaKey, keys.chachaNonce, padded)
        val mac = hmacSha256(keys.hmacKey, nonce + ciphertext)
        return Base64.toBase64String(byteArrayOf(version) + nonce + ciphertext + mac)
    }

    @Throws(Nip44DecryptException::class)
    fun decrypt(
        privateKeyHex: String,
        publicKeyHex: String,
        payload: String,
    ): String {
        val envelope = decodeEnvelope(payload)
        return decryptEnvelope(conversationKey(privateKeyHex, publicKeyHex), envelope)
    }

    @Throws(Nip44DecryptException::class)
    fun decryptWithConversationKey(
        conversationKey: ByteArray,
        payload: String,
    ): String {
        requireLength("conversation key", conversationKey, keyLength)
        return decryptEnvelope(conversationKey, decodeEnvelope(payload))
    }

    fun conversationKey(
        privateKeyHex: String,
        publicKeyHex: String,
    ): ByteArray {
        val privateScalar = BigInteger(1, decodeHex(privateKeyHex, "private key", keyLength))
        require(privateScalar.signum() > 0 && privateScalar < curveParameters.n) {
            "private key must be a valid secp256k1 scalar"
        }
        val publicX = decodeHex(publicKeyHex, "public key", keyLength)
        val encodedPoint = byteArrayOf(0x02) + publicX
        val publicPoint = try {
            curveParameters.curve.decodePoint(encodedPoint).normalize()
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
        val sharedX = sharedPoint.affineXCoord.getEncoded()
        require(sharedX.size == keyLength) { "ECDH x-coordinate must be 32 bytes" }
        return hmacSha256(salt, sharedX)
    }

    fun messageKeys(
        conversationKey: ByteArray,
        nonce: ByteArray,
    ): Nip44MessageKeys {
        requireLength("conversation key", conversationKey, keyLength)
        requireLength("nonce", nonce, keyLength)
        val expanded = ByteArray(76)
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters.skipExtractParameters(conversationKey, nonce))
        hkdf.generateBytes(expanded, 0, expanded.size)
        return Nip44MessageKeys(
            chachaKey = expanded.copyOfRange(0, 32),
            chachaNonce = expanded.copyOfRange(32, 44),
            hmacKey = expanded.copyOfRange(44, 76),
        )
    }

    fun paddedLength(length: Int): Int {
        require(length > 0) { "length must be positive" }
        if (length <= 32) return 32
        val nextPower = Integer.highestOneBit(length - 1).shl(1)
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((length - 1) / chunk + 1)
    }

    private fun decryptEnvelope(
        conversationKey: ByteArray,
        envelope: ByteArray,
    ): String {
        val nonce = envelope.copyOfRange(1, 33)
        val ciphertext = envelope.copyOfRange(33, envelope.size - 32)
        val mac = envelope.copyOfRange(envelope.size - 32, envelope.size)
        val keys = messageKeys(conversationKey, nonce)
        val expectedMac = hmacSha256(keys.hmacKey, nonce + ciphertext)
        if (!BouncyArrays.constantTimeAreEqual(expectedMac, mac)) {
            throw Nip44DecryptException("MAC verification failed")
        }
        return unpad(chacha20(keys.chachaKey, keys.chachaNonce, ciphertext))
    }

    private fun decodeEnvelope(payload: String): ByteArray {
        if (payload.length > maximumBase64Length) {
            throw Nip44DecryptException("payload too long")
        }
        val envelope = try {
            Base64.decode(normalizeBase64(payload))
        } catch (error: RuntimeException) {
            throw Nip44DecryptException("invalid base64", error)
        }
        if (envelope.isEmpty() || envelope[0] != version) {
            throw Nip44DecryptException("unsupported version")
        }
        if (envelope.size < minimumEnvelopeLength) {
            throw Nip44DecryptException("payload too short")
        }
        if (envelope.size > maximumEnvelopeLength) {
            throw Nip44DecryptException("payload too long")
        }
        return envelope
    }

    private fun normalizeBase64(payload: String): String {
        if (payload.any { character ->
                !(
                    character in 'A'..'Z' ||
                        character in 'a'..'z' ||
                        character in '0'..'9' ||
                        character == '+' ||
                        character == '/' ||
                        character == '-' ||
                        character == '_' ||
                        character == '='
                )
            }
        ) {
            throw IllegalArgumentException("invalid base64 character")
        }
        val firstPadding = payload.indexOf('=')
        if (firstPadding >= 0) {
            val paddingLength = payload.length - firstPadding
            require(paddingLength <= 2 && payload.substring(firstPadding).all { it == '=' }) {
                "invalid base64 padding"
            }
            require(payload.length % 4 == 0) { "invalid padded base64 length" }
        }
        val normalized = payload.replace('-', '+').replace('_', '/')
        val remainder = normalized.length % 4
        require(remainder == 0) { "invalid base64 length" }
        return normalized
    }

    private fun pad(plaintext: ByteArray): ByteArray {
        require(plaintext.isNotEmpty() && plaintext.size <= maxPlaintextLength) {
            "NIP-44 plaintext must be 1..65535 bytes, got ${plaintext.size}"
        }
        val output = ByteArray(2 + paddedLength(plaintext.size))
        output[0] = (plaintext.size ushr 8).toByte()
        output[1] = plaintext.size.toByte()
        plaintext.copyInto(output, destinationOffset = 2)
        return output
    }

    @Throws(Nip44DecryptException::class)
    private fun unpad(padded: ByteArray): String {
        if (padded.size < 2) throw Nip44DecryptException("padded payload too short")
        val length = ((padded[0].toInt() and 0xff) shl 8) or (padded[1].toInt() and 0xff)
        if (
            length <= 0 ||
            length > maxPlaintextLength ||
            padded.size != 2 + paddedLength(length)
        ) {
            throw Nip44DecryptException("invalid length prefix")
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(padded, 2, length)).toString()
        } catch (error: Exception) {
            throw Nip44DecryptException("invalid UTF-8 plaintext", error)
        }
    }

    private fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(key))
        hmac.update(data, 0, data.size)
        return ByteArray(hmac.macSize).also { output -> hmac.doFinal(output, 0) }
    }

    private fun chacha20(
        key: ByteArray,
        nonce: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val cipher = ChaCha7539Engine()
        cipher.init(true, ParametersWithIV(KeyParameter(key), nonce))
        return ByteArray(data.size).also { output ->
            cipher.processBytes(data, 0, data.size, output, 0)
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
            val high = asciiHexDigit(value[index * 2])
            val low = asciiHexDigit(value[index * 2 + 1])
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
