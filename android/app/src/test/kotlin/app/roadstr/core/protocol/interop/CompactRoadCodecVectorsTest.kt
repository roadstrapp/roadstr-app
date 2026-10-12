package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.service.nostr.NativeNostrWire
import java.util.Locale
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the published golden vectors (made by the independent Python tool in tools/interop) against
 * the Kotlin codec. The Python side signs with a transcription of the BIP-340 reference code and
 * rebuilds the format from the spec; this side verifies with Bouncy Castle. Agreement between the
 * two is the evidence that the round trip really preserves ID and signature.
 */
class CompactRoadCodecVectorsTest {
    private fun decode(vector: InteropVectors.Vector, tolerance: CompactTolerance = CompactTolerance.STRICT): CompactOutcome<CompactDecoded> =
        when (vector.kind) {
            "report" -> CompactRoadCodec.decodeReport(vector.binary, tolerance)
            else -> {
                val location = requireNotNull(vector.location) { vector.name }
                CompactRoadCodec.decodeConfirmation(vector.binary, location)
            }
        }

    @Test
    fun `every valid vector decodes to exactly the signed event`() {
        assertTrue(InteropVectors.valid.size >= 20)
        for (vector in InteropVectors.valid) {
            val outcome = decode(vector)
            assertTrue("${vector.name}: $outcome", outcome is CompactOutcome.Ok)
            val decoded = (outcome as CompactOutcome.Ok).value
            // Compared as canonical JSON: the parser yields Long where the wire map holds Int.
            assertEquals(vector.name, NostrJson.encode(vector.event), NostrJson.encode(decoded.event))
            assertEquals(vector.name, (vector.flags and 0x04) != 0, decoded.ephemeralHint)
        }
    }

    @Test
    fun `decoded events pass the same verification relay events do`() {
        for (vector in InteropVectors.valid) {
            val decoded = (decode(vector) as CompactOutcome.Ok).value
            assertTrue(vector.name, NativeNostrWire.verify(decoded.event))
        }
    }

    @Test
    fun `every valid event encodes back to exactly the published frame`() {
        for (vector in InteropVectors.valid) {
            val hint = (vector.flags and 0x04) != 0
            val outcome = if (vector.kind == "report") {
                CompactRoadCodec.encodeReport(vector.event, hint)
            } else {
                CompactRoadCodec.encodeConfirmation(vector.event, vector.location, hint)
            }
            assertTrue("${vector.name}: $outcome", outcome is CompactOutcome.Ok)
            assertEquals(
                vector.name,
                InteropVectors.toHex(vector.binary),
                InteropVectors.toHex((outcome as CompactOutcome.Ok).value),
            )
        }
    }

    @Test
    fun `frame sizes are the ones of the format`() {
        for (vector in InteropVectors.valid) {
            val expected = if (vector.kind == "report") 110 else 134
            assertEquals(vector.name, expected, vector.binary.size)
        }
    }

    @Test
    fun `invalid frames fail with the documented reason`() {
        assertTrue(InteropVectors.invalid.size >= 30)
        for (vector in InteropVectors.invalid) {
            val outcome = decode(vector)
            assertEquals(
                vector.name,
                CompactOutcome.Failed(CompactError.valueOf(requireNotNull(vector.expectStrict))),
                outcome,
            )
        }
    }

    @Test
    fun `events that cannot round trip are refused, never approximated`() {
        assertTrue(InteropVectors.nonRepresentable.size >= 10)
        for (vector in InteropVectors.nonRepresentable) {
            val outcome = CompactRoadCodec.encode(vector.event)
            assertEquals(
                vector.name,
                CompactOutcome.Failed(CompactError.valueOf(requireNotNull(vector.expectEncode))),
                outcome,
            )
        }
    }

    @Test
    fun `reference encoder truncation is refused strictly and recovered only with a valid signature`() {
        assertTrue(InteropVectors.referenceTruncation.any { !it.matchesCorrectEncoding })
        for (vector in InteropVectors.referenceTruncation) {
            val strict = CompactRoadCodec.decodeReport(vector.binary)
            val tolerant = CompactRoadCodec.decodeReport(vector.binary, CompactTolerance.RECOVER_REFERENCE_TRUNCATION)
            assertTrue("${vector.name}: $tolerant", tolerant is CompactOutcome.Ok)
            val recovered = (tolerant as CompactOutcome.Ok).value
            assertEquals(vector.name, vector.expectedEventId, recovered.event["id"])
            assertTrue(vector.name, NativeNostrWire.verify(recovered.event))
            if (vector.matchesCorrectEncoding) {
                assertTrue(vector.name, strict is CompactOutcome.Ok)
                assertFalse(vector.name, recovered.recoveredFromTruncation)
            } else {
                assertEquals(vector.name, CompactOutcome.Failed(CompactError.SIGNATURE_INVALID), strict)
                assertTrue(vector.name, recovered.recoveredFromTruncation)
            }
        }
    }

    @Test
    fun `recovery cannot turn a forged or foreign frame into an event`() {
        val base = InteropVectors.valid.first { it.name == "report-paris-police" }.binary
        for (index in base.indices) {
            val damaged = base.copyOf().also { it[index] = (it[index].toInt() xor 0x40).toByte() }
            val outcome = CompactRoadCodec.decodeReport(damaged, CompactTolerance.RECOVER_REFERENCE_TRUNCATION)
            // 0x40 on a coordinate byte moves the value by far more than one unit: not a rounding slip.
            assertTrue("byte $index", outcome is CompactOutcome.Failed)
        }
    }

    @Test
    fun `random signed reports survive the round trip under any default locale`() {
        val previous = Locale.getDefault()
        val random = Random(20_261_011)
        try {
            for (locale in listOf(Locale.ITALY, Locale.GERMANY, Locale.forLanguageTag("ar-EG"), Locale.ROOT)) {
                Locale.setDefault(locale)
                repeat(250) { roundTripRandomReport(random) }
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun roundTripRandomReport(random: Random) {
        val latitude = randomE7(random, CompactCoordinates.MAX_LATITUDE_E7)
        val longitude = randomE7(random, CompactCoordinates.MAX_LONGITUDE_E7)
        val type = CompactRoadType.entries[random.nextInt(CompactRoadType.entries.size)]
        val createdAt = 1_000_000_000L + random.nextInt(1_000_000_000)
        val privateKey = randomPrivateKey(random)
        val draft = CompactRoadCodec.reportDraft(NostrSchnorr.publicKey(privateKey), createdAt, latitude, longitude, type)
        val signed = NostrSchnorr.signEvent(draft, privateKey)

        val frame = (CompactRoadCodec.encodeReport(signed) as CompactOutcome.Ok).value
        val decoded = CompactRoadCodec.decodeReport(frame)
        assertTrue(decoded is CompactOutcome.Ok)
        assertEquals(NostrJson.encode(signed), NostrJson.encode((decoded as CompactOutcome.Ok).value.event))
        assertEquals(latitude, decoded.value.latitudeE7)
        assertEquals(longitude, decoded.value.longitudeE7)
    }

    private fun randomE7(random: Random, limit: Long): Int {
        val magnitude = (random.nextDouble() * (limit + 1)).toLong().coerceAtMost(limit).toInt()
        return if (random.nextBoolean()) magnitude else -magnitude
    }

    /** Below the curve order and never zero: the top bit is cleared and the last byte forced odd. */
    private fun randomPrivateKey(random: Random): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        bytes[0] = (bytes[0].toInt() and 0x7F).toByte()
        bytes[31] = (bytes[31].toInt() or 0x01).toByte()
        return InteropVectors.toHex(bytes)
    }

    @Test
    fun `a truncating encoder is recovered for every coordinate it gets wrong`() {
        val random = Random(7)
        var wrong = 0
        repeat(400) {
            val latitude = randomE7(random, CompactCoordinates.MAX_LATITUDE_E7)
            val longitude = randomE7(random, CompactCoordinates.MAX_LONGITUDE_E7)
            val privateKey = randomPrivateKey(random)
            val publicKey = NostrSchnorr.publicKey(privateKey)
            val draft = CompactRoadCodec.reportDraft(publicKey, 1_700_000_000L, latitude, longitude, CompactRoadType.HAZARD)
            val signed = NostrSchnorr.signEvent(draft, privateKey)
            val exact = (CompactRoadCodec.encodeReport(signed) as CompactOutcome.Ok).value

            val truncated = exact.copyOf()
            truncateLikeReference(truncated, 37, CompactCoordinates.format(latitude))
            truncateLikeReference(truncated, 41, CompactCoordinates.format(longitude))
            if (truncated.contentEquals(exact)) return@repeat
            wrong++
            val recovered = CompactRoadCodec.decodeReport(truncated, CompactTolerance.RECOVER_REFERENCE_TRUNCATION)
            assertTrue("$latitude,$longitude: $recovered", recovered is CompactOutcome.Ok)
            assertEquals(NostrJson.encode(signed), NostrJson.encode((recovered as CompactOutcome.Ok).value.event))
        }
        assertTrue("the simulation must actually hit the bug", wrong > 20)
    }

    /** `(text.toDouble() * 1e7).toLong()` written into the frame, as the reference client does. */
    private fun truncateLikeReference(frame: ByteArray, offset: Int, text: String) {
        val value = (text.toDouble() * 1e7).toLong().toInt()
        for (index in 0 until 4) frame[offset + index] = (value ushr (24 - 8 * index)).toByte()
    }

    @Test
    fun `a report event of Roadstr's own native layout is not the compact layout`() {
        val vector = InteropVectors.nonRepresentable.first { it.name == "tags-in-roadstr-native-order" }
        assertEquals(CompactOutcome.Failed(CompactError.NON_CANONICAL), CompactRoadCodec.encode(vector.event))
    }
}
