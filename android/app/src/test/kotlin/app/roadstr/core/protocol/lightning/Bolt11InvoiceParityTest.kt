package app.roadstr.core.protocol.lightning

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Bolt11InvoiceParityTest {
    private val description = "{\"kind\":9734,\"content\":\"road zap\"}"
    private val preimage = ByteArray(32) { it.toByte() }
    private val preimageHex = preimage.joinToString("") { "%02x".format(it) }
    private val createdAt = 1_704_067_200L

    @Test
    fun `invoice binds amount expiry preimage and description`() {
        val raw = invoice(createdAt = createdAt, preimage = preimage, description = description)
        val parsed = requireNotNull(Bolt11Invoice.parseOrNull(raw))
        assertEquals(1_000L, parsed.amountMillisatoshi)
        assertEquals(createdAt, parsed.createdAtUnixSeconds)
        assertEquals(600, parsed.expirySeconds)
        assertTrue(parsed.preimageMatches(preimageHex))
        assertFalse(parsed.preimageMatches("00".repeat(32)))
        assertTrue(parsed.descriptionMatches(description))
        assertFalse(parsed.descriptionMatches("$description "))
        assertFalse(parsed.isExpiredAt(createdAt + 599))
        assertTrue(parsed.isExpiredAt(createdAt + 600))
    }

    @Test
    fun `checksum network amount and required hash are strict`() {
        val valid = invoice(createdAt = createdAt, preimage = preimage, description = description)
        val replacement = if (valid.last() == 'q') 'p' else 'q'
        assertNull(Bolt11Invoice.parseOrNull(valid.dropLast(1) + replacement))
        assertNull(
            Bolt11Invoice.parseOrNull(
                invoice("lntb10n", createdAt, preimage, description),
            ),
        )
        assertNull(
            Bolt11Invoice.parseOrNull(
                invoice("lnbc", createdAt, preimage, description),
            ),
        )
        assertNull(
            Bolt11Invoice.parseOrNull(
                invoice(createdAt = createdAt, preimage = preimage, description = description, includePaymentHash = false),
            ),
        )
    }

    @Test
    fun `testnet is opt-in and duplicate critical fields are rejected`() {
        val testnet = invoice("lntb10n", createdAt, preimage, description)
        assertEquals(1_000L, Bolt11Invoice.parseOrNull(testnet, requireMainnet = false)?.amountMillisatoshi)

        val hashWords = toFiveBits(sha256(preimage))
        val duplicate = invoice(
            createdAt = createdAt,
            preimage = preimage,
            description = description,
            extraTags = tag(1, hashWords),
        )
        assertNull(Bolt11Invoice.parseOrNull(duplicate))
    }

    @Test
    fun `pico bitcoin must resolve to a whole millisatoshi`() {
        assertNull(
            Bolt11Invoice.parseOrNull(
                invoice("lnbc11p", createdAt, preimage, description),
            ),
        )
        assertEquals(
            1L,
            Bolt11Invoice.parseOrNull(
                invoice("lnbc10p", createdAt, preimage, description),
            )?.amountMillisatoshi,
        )
    }

    private fun invoice(
        humanReadablePart: String = "lnbc10n",
        createdAt: Long,
        preimage: ByteArray,
        description: String,
        includePaymentHash: Boolean = true,
        extraTags: List<Int> = emptyList(),
    ): String {
        val words = mutableListOf<Int>()
        for (shift in 30 downTo 0 step 5) words += ((createdAt shr shift) and 31).toInt()
        if (includePaymentHash) words += tag(1, toFiveBits(sha256(preimage)))
        words += tag(23, toFiveBits(sha256(description.toByteArray())))
        words += tag(6, listOf(18, 24))
        words += extraTags
        words += List(104) { 0 }
        return encodeBech32(humanReadablePart, words)
    }

    private fun tag(type: Int, value: List<Int>): List<Int> =
        listOf(type, value.size shr 5, value.size and 31) + value

    private fun toFiveBits(bytes: ByteArray): List<Int> {
        var accumulator = 0
        var bits = 0
        val result = mutableListOf<Int>()
        for (byte in bytes) {
            accumulator = (accumulator shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                result += (accumulator shr bits) and 31
            }
        }
        if (bits > 0) result += (accumulator shl (5 - bits)) and 31
        return result
    }

    private fun encodeBech32(humanReadablePart: String, data: List<Int>): String {
        val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        val expanded = humanReadablePart.map { it.code shr 5 } +
            listOf(0) + humanReadablePart.map { it.code and 31 }
        val values = expanded + data + List(6) { 0 }
        val checksumValue = polymod(values) xor 1
        val checksum = List(6) { index -> (checksumValue shr (5 * (5 - index))) and 31 }
        return humanReadablePart + "1" + (data + checksum).joinToString("") { charset[it].toString() }
    }

    private fun polymod(values: List<Int>): Int {
        val generators = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
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

    private fun sha256(value: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value)
}
