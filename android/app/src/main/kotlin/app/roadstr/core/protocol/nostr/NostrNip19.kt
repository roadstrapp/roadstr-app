package app.roadstr.core.protocol.nostr

enum class NostrNip19Kind(val hrp: String) {
    PUBLIC_KEY("npub"),
    PRIVATE_KEY("nsec"),
}

data class NostrNip19Key(
    val kind: NostrNip19Kind,
    val hex: String,
) {
    // An nsec decodes to this type; its string form must not carry the key.
    override fun toString(): String = "NostrNip19Key(kind=$kind, hex=<redacted>)"
}

/** Strict dependency-free NIP-19 codec for Roadstr's fixed-size key formats. */
object NostrNip19 {
    private const val KEY_BYTES = 32
    private const val MAX_BASIC_KEY_LENGTH = 90
    private const val CHECKSUM_LENGTH = 6
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val HEX_KEY = Regex("^[0-9a-fA-F]{64}$")
    private val GENERATORS = intArrayOf(
        0x3b6a57b2,
        0x26508e6d,
        0x1ea119fa,
        0x3d4233dd,
        0x2a1462b3,
    )

    fun encodePublicKey(hex: String): String = encode(NostrNip19Kind.PUBLIC_KEY, hex)

    fun encodePrivateKey(hex: String): String = encode(NostrNip19Kind.PRIVATE_KEY, hex)

    fun decode(encoded: String): NostrNip19Key {
        try {
            if (encoded.length > MAX_BASIC_KEY_LENGTH) invalidKey()
            val lower = encoded.lowercase()
            val upper = encoded.uppercase()
            if (encoded != lower && encoded != upper) invalidKey()

            val separator = encoded.lastIndexOf('1')
            if (separator <= 0 || encoded.length - separator - 1 < CHECKSUM_LENGTH) {
                invalidKey()
            }
            val normalized = lower
            val hrp = normalized.substring(0, separator)
            if (hrp.any { character -> character.code !in 33..126 }) invalidKey()
            val encodedWords = normalized.substring(separator + 1)
            val words = encodedWords.map { character ->
                CHARSET.indexOf(character).takeIf { index -> index >= 0 } ?: invalidKey()
            }
            if (!verifyChecksum(hrp, words)) invalidKey()
            val payloadWords = words.dropLast(CHECKSUM_LENGTH)
            val bytes = convertBits(payloadWords, 5, 8, pad = false)
            if (bytes.size != KEY_BYTES) invalidKey()
            val kind = when (hrp) {
                "npub" -> NostrNip19Kind.PUBLIC_KEY
                "nsec" -> NostrNip19Kind.PRIVATE_KEY
                else -> invalidKey()
            }
            return NostrNip19Key(kind, bytes.toHex())
        } catch (_: InvalidNip19KeyException) {
            throw IllegalArgumentException("Invalid NIP-19 key")
        } catch (_: RuntimeException) {
            throw IllegalArgumentException("Invalid NIP-19 key")
        }
    }

    fun decodePublicKey(encoded: String): String {
        val decoded = decode(encoded)
        require(decoded.kind == NostrNip19Kind.PUBLIC_KEY) { "Expected npub" }
        return decoded.hex
    }

    fun decodePrivateKey(encoded: String): String {
        val decoded = decode(encoded)
        require(decoded.kind == NostrNip19Kind.PRIVATE_KEY) { "Expected nsec" }
        return decoded.hex
    }

    private fun encode(kind: NostrNip19Kind, hex: String): String {
        require(HEX_KEY.matches(hex)) { "Invalid hexadecimal Nostr key" }
        val bytes = hex.chunked(2).map { byte -> byte.toInt(16) }
        val words = convertBits(bytes, 8, 5, pad = true)
        val checksummed = words + createChecksum(kind.hrp, words)
        val result = buildString {
            append(kind.hrp)
            append('1')
            for (word in checksummed) append(CHARSET[word])
        }
        require(result.length <= MAX_BASIC_KEY_LENGTH)
        return result
    }

    private fun convertBits(
        data: List<Int>,
        fromBits: Int,
        toBits: Int,
        pad: Boolean,
    ): List<Int> {
        var accumulator = 0
        var bitCount = 0
        val output = mutableListOf<Int>()
        val outputMask = (1 shl toBits) - 1
        val maxAccumulator = (1 shl (fromBits + toBits - 1)) - 1
        for (value in data) {
            if (value < 0 || value >= (1 shl fromBits)) invalidKey()
            accumulator = ((accumulator shl fromBits) or value) and maxAccumulator
            bitCount += fromBits
            while (bitCount >= toBits) {
                bitCount -= toBits
                output += (accumulator shr bitCount) and outputMask
            }
        }
        if (pad) {
            if (bitCount > 0) {
                output += (accumulator shl (toBits - bitCount)) and outputMask
            }
        } else if (
            bitCount >= fromBits ||
            ((accumulator shl (toBits - bitCount)) and outputMask) != 0
        ) {
            invalidKey()
        }
        return output
    }

    private fun verifyChecksum(hrp: String, words: List<Int>): Boolean =
        polymod(expandHrp(hrp) + words) == 1

    private fun createChecksum(hrp: String, words: List<Int>): List<Int> {
        val value = polymod(expandHrp(hrp) + words + List(CHECKSUM_LENGTH) { 0 }) xor 1
        return List(CHECKSUM_LENGTH) { index ->
            (value shr (5 * (5 - index))) and 31
        }
    }

    private fun expandHrp(hrp: String): List<Int> =
        hrp.map { character -> character.code shr 5 } +
            listOf(0) +
            hrp.map { character -> character.code and 31 }

    private fun polymod(values: List<Int>): Int {
        var checksum = 1
        for (value in values) {
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            for (index in GENERATORS.indices) {
                if (((top ushr index) and 1) == 1) {
                    checksum = checksum xor GENERATORS[index]
                }
            }
        }
        return checksum
    }

    private fun List<Int>.toHex(): String = joinToString("") { byte ->
        byte.toString(16).padStart(2, '0')
    }

    private fun invalidKey(): Nothing = throw InvalidNip19KeyException()
}

private class InvalidNip19KeyException : RuntimeException()
