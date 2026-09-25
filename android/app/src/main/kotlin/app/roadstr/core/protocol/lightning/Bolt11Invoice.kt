package app.roadstr.core.protocol.lightning

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Strict BOLT-11 subset used before an invoice reaches an automatic NWC wallet.
 * Signature verification belongs to the Lightning node; this parser verifies
 * Bech32 integrity and the fields Roadstr binds to its LNURL/zap request.
 */
class Bolt11Invoice private constructor(
    val amountMillisatoshi: Long,
    val createdAtUnixSeconds: Long,
    val expirySeconds: Int,
    val paymentHash: ByteArray,
    val descriptionHash: ByteArray?,
) {
    fun isExpiredAt(unixSeconds: Long): Boolean =
        unixSeconds >= createdAtUnixSeconds + expirySeconds

    fun preimageMatches(preimageHex: String): Boolean {
        val preimage = hexBytes(preimageHex) ?: return false
        if (preimage.size != HASH_BYTES) return false
        return constantTimeEquals(sha256(preimage), paymentHash)
    }

    fun descriptionMatches(description: String): Boolean {
        val expected = descriptionHash ?: return false
        return constantTimeEquals(sha256(description.toByteArray(Charsets.UTF_8)), expected)
    }

    companion object {
        private const val MAX_INVOICE_LENGTH = 8192
        private const val SIGNATURE_WORDS = 104
        private const val TIMESTAMP_WORDS = 7
        private const val HASH_BYTES = 32
        private val longMax = BigInteger.valueOf(Long.MAX_VALUE)

        fun parseOrNull(raw: String, requireMainnet: Boolean = true): Bolt11Invoice? {
            return try {
                val invoice = raw.trim().lowercase()
                if (invoice.length !in 20..MAX_INVOICE_LENGTH) return null
                val decoded = Bech32.decodeOrNull(invoice) ?: return null
                val humanReadablePart = decoded.humanReadablePart.lowercase()
                if (!humanReadablePart.startsWith("ln")) return null
                val networkPrefix = networkPrefix(humanReadablePart) ?: return null
                if (requireMainnet && networkPrefix != "lnbc") return null
                val amount = parseAmountMillisatoshi(
                    humanReadablePart.substring(networkPrefix.length),
                ) ?: return null
                if (amount <= 0) return null

                val words = decoded.data
                if (words.size < TIMESTAMP_WORDS + 3 + SIGNATURE_WORDS) return null
                var createdAt = 0L
                for (index in 0 until TIMESTAMP_WORDS) {
                    createdAt = (createdAt shl 5) or words[index].toLong()
                }

                var paymentHash: ByteArray? = null
                var descriptionHash: ByteArray? = null
                var expiry = 3600
                var cursor = TIMESTAMP_WORDS
                val taggedEnd = words.size - SIGNATURE_WORDS
                while (cursor + 3 <= taggedEnd) {
                    val type = words[cursor++]
                    val length = (words[cursor++] shl 5) or words[cursor++]
                    if (cursor + length > taggedEnd) return null
                    val field = words.subList(cursor, cursor + length)
                    cursor += length
                    when (type) {
                        1 -> {
                            if (paymentHash != null) return null
                            val bytes = convertBits(field, 5, 8, pad = false) ?: return null
                            if (bytes.size != HASH_BYTES) return null
                            paymentHash = ByteArray(bytes.size) { bytes[it].toByte() }
                        }

                        23 -> {
                            if (descriptionHash != null) return null
                            val bytes = convertBits(field, 5, 8, pad = false) ?: return null
                            if (bytes.size != HASH_BYTES) return null
                            descriptionHash = ByteArray(bytes.size) { bytes[it].toByte() }
                        }

                        6 -> {
                            var value = 0
                            for (word in field) {
                                if (value > (Int.MAX_VALUE shr 5)) return null
                                value = (value shl 5) or word
                            }
                            expiry = value
                        }
                    }
                }
                val requiredPaymentHash = paymentHash ?: return null
                if (cursor != taggedEnd || expiry <= 0) return null
                Bolt11Invoice(
                    amountMillisatoshi = amount,
                    createdAtUnixSeconds = createdAt,
                    expirySeconds = expiry,
                    paymentHash = requiredPaymentHash,
                    descriptionHash = descriptionHash,
                )
            } catch (_: RuntimeException) {
                null
            }
        }

        /** Shared 5-to-8 conversion for BOLT-11 and future lud06 parsing. */
        fun convertFiveBitWords(words: List<Int>): List<Int>? =
            convertBits(words, 5, 8, pad = false)

        private fun networkPrefix(humanReadablePart: String): String? =
            listOf("lnbcrt", "lntbs", "lntb", "lnsb", "lnbc")
                .firstOrNull(humanReadablePart::startsWith)

        private fun parseAmountMillisatoshi(raw: String): Long? {
            if (raw.isEmpty()) return null
            val match = Regex("^(\\d+)([munp]?)$").matchEntire(raw) ?: return null
            val value = match.groupValues[1].toBigIntegerOrNull() ?: return null
            if (value <= BigInteger.ZERO) return null
            val millisatoshi = when (match.groupValues[2]) {
                "m" -> value * BigInteger.valueOf(100_000_000L)
                "u" -> value * BigInteger.valueOf(100_000L)
                "n" -> value * BigInteger.valueOf(100L)
                "p" -> {
                    if (value.mod(BigInteger.TEN) != BigInteger.ZERO) return null
                    value / BigInteger.TEN
                }

                else -> value * BigInteger.valueOf(100_000_000_000L)
            }
            return millisatoshi.takeIf { it <= longMax }?.toLong()
        }

        private fun convertBits(
            input: List<Int>,
            fromBits: Int,
            toBits: Int,
            pad: Boolean,
        ): List<Int>? {
            var accumulator = 0
            var bitCount = 0
            val result = mutableListOf<Int>()
            val maxValue = (1 shl toBits) - 1
            val maxAccumulator = (1 shl (fromBits + toBits - 1)) - 1
            for (value in input) {
                if (value < 0 || value shr fromBits != 0) return null
                accumulator = ((accumulator shl fromBits) or value) and maxAccumulator
                bitCount += fromBits
                while (bitCount >= toBits) {
                    bitCount -= toBits
                    result += (accumulator shr bitCount) and maxValue
                }
            }
            if (pad) {
                if (bitCount > 0) result += (accumulator shl (toBits - bitCount)) and maxValue
            } else if (
                bitCount >= fromBits ||
                ((accumulator shl (toBits - bitCount)) and maxValue) != 0
            ) {
                return null
            }
            return result
        }

        private fun hexBytes(raw: String): ByteArray? {
            val value = raw.trim().lowercase()
            if (value.isEmpty() || value.length % 2 != 0 || value.any { it.digitToIntOrNull(16) == null }) {
                return null
            }
            return ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }

        private fun sha256(input: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(input)

        private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean {
            if (first.size != second.size) return false
            var difference = 0
            for (index in first.indices) {
                difference = difference or (first[index].toInt() xor second[index].toInt())
            }
            return difference == 0
        }
    }
}

private data class DecodedBech32(val humanReadablePart: String, val data: List<Int>)

/** Original Bech32 checksum variant required by BOLT-11 (not Bech32m). */
private object Bech32 {
    private const val CHECKSUM_LENGTH = 6
    private const val CHECKSUM_CONSTANT = 1
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val reverseCharset = IntArray(128) { -1 }.also { table ->
        CHARSET.forEachIndexed { index, character -> table[character.code] = index }
    }

    fun decodeOrNull(value: String): DecodedBech32? {
        if (value.any { it.code !in 33..126 }) return null
        val separator = value.lastIndexOf('1')
        if (separator < 1 || separator + CHECKSUM_LENGTH + 1 > value.length) return null
        val humanReadablePart = value.substring(0, separator)
        val encodedData = value.substring(separator + 1)
        val dataWithChecksum = ArrayList<Int>(encodedData.length)
        for (character in encodedData) {
            if (character.code >= reverseCharset.size) return null
            val decoded = reverseCharset[character.code]
            if (decoded < 0) return null
            dataWithChecksum += decoded
        }
        if (polymod(expandHumanReadablePart(humanReadablePart) + dataWithChecksum) != CHECKSUM_CONSTANT) {
            return null
        }
        return DecodedBech32(
            humanReadablePart,
            dataWithChecksum.dropLast(CHECKSUM_LENGTH),
        )
    }

    private fun expandHumanReadablePart(value: String): List<Int> = buildList(value.length * 2 + 1) {
        value.forEach { add(it.code shr 5) }
        add(0)
        value.forEach { add(it.code and 31) }
    }

    private fun polymod(values: List<Int>): Int {
        val generators = intArrayOf(
            0x3b6a57b2,
            0x26508e6d,
            0x1ea119fa,
            0x3d4233dd,
            0x2a1462b3,
        )
        var checksum = 1
        for (value in values) {
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            for (index in generators.indices) {
                if ((top ushr index) and 1 != 0) checksum = checksum xor generators[index]
            }
        }
        return checksum
    }
}
