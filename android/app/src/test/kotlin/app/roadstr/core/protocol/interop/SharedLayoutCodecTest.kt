package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.NostrSchnorr
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared layout and the new frame, end to end with real BIP-340 signatures. */
class SharedLayoutCodecTest {
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val pubkey = NostrSchnorr.publicKey(key.toHex())
    private val createdAt = 1_700_000_000L

    private fun sign(draft: app.roadstr.core.protocol.nostr.NostrEventDraft) =
        NostrSchnorr.signEvent(draft, key.toHex())

    @Test
    fun `report round-trips through the frame with its id intact`() {
        val event = sign(
            CompactRoadCodec.reportDraft(
                pubkey, createdAt, 48_856_614, 2_352_222, CompactRoadType.POLICE,
            ),
        )
        val frame = (CompactRoadCodec.encodeReport(event) as CompactOutcome.Ok).value
        assertEquals(117, frame.size)
        val decoded = (CompactRoadCodec.decodeReport(frame) as CompactOutcome.Ok).value
        assertEquals(event["id"], decoded.event["id"])
        assertEquals(48_856_614, decoded.latitudeE6)
        assertEquals(CompactRoadType.POLICE, decoded.type)
    }

    @Test
    fun `a 46 byte comment fits in 163 bytes and 47 is refused`() {
        val fits = sign(
            CompactRoadCodec.reportDraft(
                pubkey, createdAt, 48_856_614, 2_352_222, CompactRoadType.HAZARD,
                comment = "a".repeat(46),
            ),
        )
        val frame = (CompactRoadCodec.encodeReport(fits) as CompactOutcome.Ok).value
        assertEquals(163, frame.size)
        val tooLong = sign(
            CompactRoadCodec.reportDraft(
                pubkey, createdAt, 48_856_614, 2_352_222, CompactRoadType.HAZARD,
                comment = "a".repeat(47),
            ),
        )
        assertEquals(CompactError.COMMENT_TOO_LONG, (CompactRoadCodec.encodeReport(tooLong) as CompactOutcome.Failed).error)
    }

    @Test
    fun `a comment with a control character is never framed`() {
        val event = sign(
            CompactRoadCodec.reportDraft(
                pubkey, createdAt, 48_856_614, 2_352_222, CompactRoadType.HAZARD,
                comment = "left\nlane",
            ),
        )
        assertEquals(CompactError.NON_CANONICAL, (CompactRoadCodec.encodeReport(event) as CompactOutcome.Failed).error)
    }

    @Test
    fun `a vote is self-contained`() {
        val report = sign(
            CompactRoadCodec.reportDraft(pubkey, createdAt, 48_856_614, 2_352_222, CompactRoadType.POLICE),
        )
        val vote = sign(
            CompactRoadCodec.confirmationDraft(pubkey, createdAt + 60, report["id"] as String, true),
        )
        val frame = (CompactRoadCodec.encodeConfirmation(vote) as CompactOutcome.Ok).value
        assertEquals(134, frame.size)
        val decoded = (CompactRoadCodec.decodeConfirmation(frame) as CompactOutcome.Ok).value
        assertEquals(vote["id"], decoded.event["id"])
        assertTrue(decoded.stillThere)
    }

    @Test
    fun `maxspeed is carried only when it is canonical`() {
        val event = sign(
            CompactRoadCodec.reportDraft(
                pubkey, createdAt, 48_856_614, 2_352_222, CompactRoadType.SPEED_CAMERA,
                speedLimitKmh = 50,
            ),
        )
        val frame = (CompactRoadCodec.encodeReport(event) as CompactOutcome.Ok).value
        val decoded = (CompactRoadCodec.decodeReport(frame) as CompactOutcome.Ok).value
        assertTrue((decoded.event["tags"] as List<*>).contains(listOf("maxspeed", "50")))
    }

    @Test
    fun `the deprecated layout cannot be framed`() {
        val event = sign(
            CompactRoadCodec.reportDraft(pubkey, createdAt, 48_856_614, 2_352_222, CompactRoadType.POLICE),
        )
        @Suppress("UNCHECKED_CAST")
        val tags = event["tags"] as List<List<String>>
        val withAlt = event + ("tags" to (tags + listOf(listOf("alt", "Roadstr: police report"))))
        assertEquals(CompactError.NON_CANONICAL, (CompactRoadCodec.encodeReport(withAlt) as CompactOutcome.Failed).error)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
