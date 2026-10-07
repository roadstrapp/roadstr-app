package app.roadstr.core.protocol.nostr

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.FixedPointCombMultiplier

/** BIP-340 over secp256k1, with strict 32-byte NIP-01 hash entry points. */
object NostrSchnorr {
    private val parameters = requireNotNull(SECNamedCurves.getByName("secp256k1"))
    private val fieldPrime: BigInteger = parameters.curve.field.characteristic
    private val curveOrder: BigInteger = parameters.n
    private val secureRandom = SecureRandom()
    private const val scalarLength = 32
    private const val signatureLength = 64
    private const val BLIND_BITS = 64

    /** Derives the 32-byte x-only BIP-340 public key. */
    fun publicKey(privateKeyHex: String): String {
        val scalar = privateScalar(privateKeyHex)
        return generatorMultiply(scalar).xBytes().toHex()
    }

    /** Signs a NIP-01 event id with fresh BIP-340 auxiliary randomness. */
    fun signHash(
        privateKeyHex: String,
        eventIdHex: String,
    ): String {
        val auxiliaryRandom = ByteArray(scalarLength).also(secureRandom::nextBytes)
        return signHashWithAux(privateKeyHex, eventIdHex, auxiliaryRandom.toHex())
    }

    /** Deterministic NIP-01 signing entry point intended for fixture replay. */
    fun signHashWithAux(
        privateKeyHex: String,
        eventIdHex: String,
        auxiliaryRandomHex: String,
    ): String = sign(
        privateKeyHex = privateKeyHex,
        message = decodeHex(eventIdHex, "event id", scalarLength),
        auxiliaryRandom = decodeHex(auxiliaryRandomHex, "auxiliary randomness", scalarLength),
    )

    /** Signs a complete draft only when its claimed author matches the key. */
    fun signEvent(
        draft: NostrEventDraft,
        privateKeyHex: String,
    ): Map<String, Any?> {
        require(draft.pubkey == publicKey(privateKeyHex)) {
            "event pubkey does not match the private key"
        }
        return draft.toWireMap(signature = signHash(privateKeyHex, draft.id()))
    }

    /** Deterministic complete-event signing for fixture replay. */
    fun signEventWithAux(
        draft: NostrEventDraft,
        privateKeyHex: String,
        auxiliaryRandomHex: String,
    ): Map<String, Any?> {
        require(draft.pubkey == publicKey(privateKeyHex)) {
            "event pubkey does not match the private key"
        }
        return draft.toWireMap(
            signature = signHashWithAux(privateKeyHex, draft.id(), auxiliaryRandomHex),
        )
    }

    /** Strict NIP-01 verification: malformed untrusted input returns false. */
    fun verifyHash(
        publicKeyHex: String,
        eventIdHex: String,
        signatureHex: String,
    ): Boolean = try {
        verify(
            publicKeyHex = publicKeyHex,
            message = decodeHex(eventIdHex, "event id", scalarLength),
            signatureHex = signatureHex,
        )
    } catch (_: RuntimeException) {
        false
    }

    /** Recomputes the canonical event id before paying the BIP-340 cost. */
    fun verifyEvent(
        draft: NostrEventDraft,
        claimedEventIdHex: String,
        signatureHex: String,
    ): Boolean =
        draft.id() == claimedEventIdHex &&
            verifyHash(draft.pubkey, claimedEventIdHex, signatureHex)

    /**
     * Generic BIP-340 signing used to replay the upstream arbitrary-length
     * vectors. Roadstr production code should call [signHash].
     */
    fun sign(
        privateKeyHex: String,
        message: ByteArray,
        auxiliaryRandom: ByteArray,
    ): String {
        val d0 = privateScalar(privateKeyHex)
        require(auxiliaryRandom.size == scalarLength) {
            "auxiliary randomness must be 32 bytes"
        }
        val publicPoint = generatorMultiply(d0)
        val publicKey = publicPoint.xBytes()
        val d = if (publicPoint.hasEvenY()) d0 else curveOrder.subtract(d0)
        val maskedSecret = d.toFixedBytes().xor(taggedHash("BIP0340/aux", auxiliaryRandom))
        val nonceInput = maskedSecret + publicKey + message
        val k0 = BigInteger(1, taggedHash("BIP0340/nonce", nonceInput)).mod(curveOrder)
        check(k0.signum() != 0) { "BIP-340 nonce generation failed" }

        val noncePoint = generatorMultiply(k0)
        val k = if (noncePoint.hasEvenY()) k0 else curveOrder.subtract(k0)
        val r = noncePoint.xBytes()
        val challenge = BigInteger(
            1,
            taggedHash("BIP0340/challenge", r + publicKey + message),
        ).mod(curveOrder)
        val s = blindedSum(k, challenge, d)
        val signature = (r + s.toFixedBytes()).toHex()
        check(verify(publicKey.toHex(), message, signature)) {
            "generated BIP-340 signature did not verify"
        }
        return signature
    }

    /** Generic BIP-340 verification used by the official fixture suite. */
    fun verify(
        publicKeyHex: String,
        message: ByteArray,
        signatureHex: String,
    ): Boolean {
        return try {
            val publicKey = decodeHex(publicKeyHex, "public key", scalarLength)
            val signature = decodeHex(signatureHex, "signature", signatureLength)
            val rBytes = signature.copyOfRange(0, scalarLength)
            val r = BigInteger(1, rBytes)
            val s = BigInteger(1, signature.copyOfRange(scalarLength, signatureLength))
            if (r >= fieldPrime || s >= curveOrder) return false

            val publicPoint = liftEvenY(publicKey) ?: return false
            val challenge = BigInteger(
                1,
                taggedHash("BIP0340/challenge", rBytes + publicKey + message),
            ).mod(curveOrder)
            val candidate = generatorMultiply(s)
                .subtract(publicPoint.multiply(challenge))
                .normalize()
            !candidate.isInfinity && candidate.hasEvenY() && candidate.xValue() == r
        } catch (_: RuntimeException) {
            false
        }
    }

    /**
     * k + e·d mod n, computed on operands offset by random multiples of n. BigInteger's multiply and
     * division take time that follows the value and length of what they are given, so the nonce and the
     * private scalar are never given as they are: the result is the same, the operands are not.
     */
    private fun blindedSum(k: BigInteger, e: BigInteger, d: BigInteger): BigInteger {
        val blindedK = k.add(curveOrder.multiply(randomBlind()))
        val blindedD = d.add(curveOrder.multiply(randomBlind()))
        return blindedK.add(e.multiply(blindedD)).mod(curveOrder)
    }

    /** A random factor of fixed bit length, so the blinded operands also have a fixed length. */
    private fun randomBlind(): BigInteger =
        BigInteger(BLIND_BITS, secureRandom).setBit(BLIND_BITS - 1)

    private fun privateScalar(privateKeyHex: String): BigInteger {
        val scalar = BigInteger(
            1,
            decodeHex(privateKeyHex, "private key", scalarLength),
        )
        require(scalar.signum() > 0 && scalar < curveOrder) {
            "private key must be a valid secp256k1 scalar"
        }
        return scalar
    }

    private fun generatorMultiply(scalar: BigInteger): ECPoint =
        FixedPointCombMultiplier().multiply(parameters.g, scalar).normalize()

    private fun liftEvenY(xBytes: ByteArray): ECPoint? {
        val x = BigInteger(1, xBytes)
        if (x >= fieldPrime) return null
        return try {
            parameters.curve.decodePoint(byteArrayOf(0x02) + xBytes)
                .normalize()
                .takeIf { point -> !point.isInfinity && point.isValid && point.hasEvenY() }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun taggedHash(tag: String, data: ByteArray): ByteArray {
        val tagHash = sha256(tag.toByteArray(Charsets.US_ASCII))
        return sha256(tagHash + tagHash + data)
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun ECPoint.hasEvenY(): Boolean = !affineYCoord.toBigInteger().testBit(0)

    private fun ECPoint.xValue(): BigInteger = affineXCoord.toBigInteger()

    private fun ECPoint.xBytes(): ByteArray = xValue().toFixedBytes()

    private fun BigInteger.toFixedBytes(): ByteArray {
        require(signum() >= 0 && bitLength() <= scalarLength * 8) {
            "integer does not fit in 32 bytes"
        }
        val encoded = toByteArray()
        val unsigned = if (encoded.size == scalarLength + 1 && encoded[0] == 0.toByte()) {
            encoded.copyOfRange(1, encoded.size)
        } else {
            encoded
        }
        require(unsigned.size <= scalarLength) { "integer does not fit in 32 bytes" }
        return ByteArray(scalarLength).also { output ->
            unsigned.copyInto(output, destinationOffset = scalarLength - unsigned.size)
        }
    }

    private fun ByteArray.xor(other: ByteArray): ByteArray {
        require(size == other.size) { "XOR inputs must have equal length" }
        return ByteArray(size) { index ->
            (this[index].toInt() xor other[index].toInt()).toByte()
        }
    }

    private fun decodeHex(
        value: String,
        name: String,
        expectedBytes: Int? = null,
    ): ByteArray {
        require(value.length % 2 == 0) { "$name must contain an even number of hex characters" }
        if (expectedBytes != null) {
            require(value.length == expectedBytes * 2) {
                "$name must contain ${expectedBytes * 2} hexadecimal characters"
            }
        }
        return ByteArray(value.length / 2) { index ->
            val high = asciiHexDigit(value[index * 2])
            val low = asciiHexDigit(value[index * 2 + 1])
            require(high >= 0 && low >= 0) { "$name contains non-hexadecimal characters" }
            ((high shl 4) or low).toByte()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
