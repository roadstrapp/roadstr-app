package app.roadstr.core.protocol.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Nip58ProtocolTest {
    private val issuer = "11".repeat(32)
    private val recipient = "22".repeat(32)
    private val reference = Nip58BadgeReference(issuer, "roadstr.journey.ignition.v1")

    @Test
    fun `definition keeps stable id and requires https assets`() {
        val draft = Nip58Protocol.definitionDraft(
            issuer, 1, reference.identifier, "Ignition", "Every journey starts somewhere.",
            "https://assets.roadstr.example/ignition.png",
            listOf("https://assets.roadstr.example/ignition-64.png" to "64x64"),
        )
        val parsed = Nip58Protocol.parseDefinition(draft.toWireMap()) { true }
        assertEquals(reference, parsed?.reference)
        assertEquals("Ignition", parsed?.name)
        assertTrue(parsed?.thumbnailUrls?.isNotEmpty() == true)
    }

    @Test
    fun `award is bound to issuer definition and recipient`() {
        val event = Nip58Protocol.awardDraft(issuer, 2, reference, listOf(recipient)).toWireMap()
        val award = Nip58Protocol.parseAward(event) { true }
        assertNotNull(award)
        assertEquals(reference, award?.reference)
        assertEquals(listOf(recipient), award?.recipients)

        val forged = event.toMutableMap().also { it["pubkey"] = "33".repeat(32) }
        assertNull(Nip58Protocol.parseAward(forged) { true })
    }

    @Test
    fun `profile update preserves all prior badges and unknown list tags`() {
        val prior = listOf(
            listOf("a", "30009:${"33".repeat(32)}:another"),
            listOf("e", "44".repeat(32), "wss://other.example"),
            listOf("a", "30008:${recipient}:road-badges"),
        )
        val award = Nip58BadgeAward("55".repeat(32), reference, listOf(recipient), "wss://relay.example")
        val draft = Nip58Protocol.profileBadgesDraft(recipient, 3, prior, award)

        assertEquals(prior, draft.tags.take(prior.size))
        assertEquals(listOf("a", reference.address), draft.tags[prior.size])
        assertEquals(listOf("e", award.eventId, "wss://relay.example"), draft.tags.last())
        assertEquals(Nip58Protocol.PROFILE_BADGES_KIND, draft.kind)
    }

    @Test
    fun `malformed award and unverified events are rejected`() {
        val valid = Nip58Protocol.awardDraft(issuer, 2, reference, listOf(recipient)).toWireMap()
        assertNull(Nip58Protocol.parseAward(valid) { false })
        val missingRecipient = valid.toMutableMap().also { it["tags"] = listOf(listOf("a", reference.address)) }
        assertNull(Nip58Protocol.parseAward(missingRecipient) { true })
        assertFalse(Nip58Protocol.parseAddress("30009:not-a-key:badge") != null)
    }

    @Test
    fun `definition lookup is bound to issuer and stable identifier`() {
        @Suppress("UNCHECKED_CAST")
        val filter = Nip58Protocol.definitionRequest("sub", reference)[2] as Map<String, Any?>

        assertEquals(listOf(Nip58Protocol.BADGE_DEFINITION_KIND), filter["kinds"])
        assertEquals(listOf(issuer), filter["authors"])
        assertEquals(listOf(reference.identifier), filter["#d"])
    }
}
