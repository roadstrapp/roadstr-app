package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalRoadReportAdapterTest {
    private val privateKey = "00".repeat(31) + "07"

    @Test
    fun `every valid report vector is recognised and normalised without losing anything`() {
        for (vector in InteropVectors.valid.filter { it.kind == "report" }) {
            val event = vector.event
            assertEquals(vector.name, RoadEventLayout.COMPACT_CANONICAL, ExternalRoadReportAdapter.layoutOf(event))
            val report = (ExternalRoadReportAdapter.adaptReport(event) as CompactOutcome.Ok).value
            assertEquals(vector.name, event["id"], report.eventId)
            assertEquals(vector.name, event["pubkey"], report.pubkey)
            assertEquals(vector.name, event["sig"], report.signature)
            assertEquals(vector.name, event["created_at"], report.createdAt)
            assertEquals(vector.name, CompactRoadCodec.RELAY_TTL_SECONDS, report.relayExpiration - report.createdAt)
            assertTrue(vector.name, report.compactRepresentable)
        }
    }

    @Test
    fun `every valid confirmation vector keeps the reference, the verdict and the signature`() {
        for (vector in InteropVectors.valid.filter { it.kind == "confirmation" }) {
            val confirmation = (ExternalRoadReportAdapter.adaptConfirmation(vector.event) as CompactOutcome.Ok).value
            assertEquals(vector.name, vector.location!!.eventId, confirmation.referencedEventId)
            assertEquals(vector.name, vector.event["sig"], confirmation.signature)
            assertEquals(vector.name, vector.event["pubkey"], confirmation.pubkey)
            assertEquals(vector.name, vector.location!!.latitudeE7, confirmation.latitudeE7)
            assertEquals(vector.name, vector.location!!.longitudeE7, confirmation.longitudeE7)
            val status = (vector.event["tags"] as List<*>).map { it as List<*> }.first { it[0] == "status" }[1]
            assertEquals(vector.name, status == "still_there", confirmation.stillThere)
        }
    }

    @Test
    fun `a Roadstr native report is not mistaken for the compact layout`() {
        val draft = RoadstrNostrEvents.report(
            pubkey = NostrSchnorr.publicKey(privateKey),
            createdAt = 1_700_000_000,
            latitude = 48.856614,
            longitude = 2.3522219,
            category = "police",
            expiresAt = 1_700_014_400,
            content = "",
        )
        val native = NostrSchnorr.signEvent(draft, privateKey)
        assertEquals(RoadEventLayout.ROADSTR_NATIVE, ExternalRoadReportAdapter.layoutOf(native))
        assertEquals(CompactOutcome.Failed(CompactError.NON_CANONICAL), ExternalRoadReportAdapter.adaptReport(native))
    }

    @Test
    fun `a canonical report with a comment is interoperable but cannot travel in a frame`() {
        val vector = InteropVectors.nonRepresentable.first { it.name == "content-is-not-empty" }
        assertEquals(RoadEventLayout.COMPACT_CANONICAL, ExternalRoadReportAdapter.layoutOf(vector.event))
        val report = (ExternalRoadReportAdapter.adaptReport(vector.event) as CompactOutcome.Ok).value
        assertEquals("Checking seatbelts", report.comment)
        assertFalse(report.compactRepresentable)
    }

    @Test
    fun `alt is optional on the wire but needed to rebuild the event`() {
        val vector = InteropVectors.nonRepresentable.first { it.name == "alt-missing" }
        val report = (ExternalRoadReportAdapter.adaptReport(vector.event) as CompactOutcome.Ok).value
        assertFalse(report.compactRepresentable)
    }

    @Test
    fun `similar looking events are left to the native reader`() {
        for (name in listOf("alt-differs", "extra-tag", "tags-in-roadstr-native-order", "expiration-is-type-ttl", "latitude-with-six-decimals", "unknown-type")) {
            val vector = InteropVectors.nonRepresentable.first { it.name == name }
            assertEquals(name, RoadEventLayout.OTHER, ExternalRoadReportAdapter.layoutOf(vector.event))
        }
    }

    @Test
    fun `a decimal comma written by the reference client is readable but never encodable`() {
        val vector = InteropVectors.tolerated.single()
        assertEquals(RoadEventLayout.COMPACT_CANONICAL, ExternalRoadReportAdapter.layoutOf(vector.event))
        val report = (ExternalRoadReportAdapter.adaptReport(vector.event) as CompactOutcome.Ok).value
        assertTrue(report.decimalCommaVariant)
        assertFalse(report.compactRepresentable)
        assertEquals(CompactCoordinates.degrees(report.latitudeE7), 49.0765652, 0.0)
        assertEquals(CompactOutcome.Failed(CompactError.NON_CANONICAL), CompactRoadCodec.encode(vector.event))
    }

    @Test
    fun `the comma allowance does not loosen anything else`() {
        val vector = InteropVectors.tolerated.single()
        @Suppress("UNCHECKED_CAST")
        val tags = (vector.event["tags"] as List<List<String>>)
        fun withTags(changed: List<List<String>>) = vector.event + ("tags" to changed)
        // Six decimals with a comma: still not the format.
        val shortComma = tags.map { if (it[0] == "lat") listOf("lat", "49,076565") else it }
        assertEquals(RoadEventLayout.OTHER, ExternalRoadReportAdapter.layoutOf(withTags(shortComma)))
        // A comma in the expiration, or any other tag, is not tolerated.
        val badExpiration = tags.map { if (it[0] == "expiration") listOf("expiration", "1,701209600") else it }
        assertEquals(RoadEventLayout.OTHER, ExternalRoadReportAdapter.layoutOf(withTags(badExpiration)))
        // Geohashes must still belong to the coordinates.
        val wrongHash = tags.map { if (it == listOf("g", "u2tz")) listOf("g", "u2ty") else it }
        assertEquals(RoadEventLayout.OTHER, ExternalRoadReportAdapter.layoutOf(withTags(wrongHash)))
    }

    @Test
    fun `malformed input never throws`() {
        val broken = listOf(
            emptyMap(),
            mapOf("kind" to 1315L, "tags" to "nope"),
            mapOf("kind" to 1315L, "tags" to listOf(listOf(1L, 2L))),
            mapOf("kind" to "1315", "tags" to emptyList<Any>()),
        )
        for (event in broken) {
            assertEquals(RoadEventLayout.OTHER, ExternalRoadReportAdapter.layoutOf(event))
            assertTrue(ExternalRoadReportAdapter.adaptReport(event) is CompactOutcome.Failed)
            assertTrue(ExternalRoadReportAdapter.adaptConfirmation(event) is CompactOutcome.Failed)
        }
    }
}
