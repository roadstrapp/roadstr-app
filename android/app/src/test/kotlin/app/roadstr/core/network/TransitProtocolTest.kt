package app.roadstr.core.network

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitProtocolTest {
    @Test
    fun `request preserves Flutter endpoints UTC and access walking budget`() {
        val request = TransitProtocol.planRequest(
            from = TransitRequestPoint(52.5186, 13.4081),
            to = TransitRequestPoint(48.1372, 11.5756),
            departure = Instant.parse("2026-08-19T06:00:00Z"),
            endpoint = "https://example.test/plan?stale=1",
        )

        assertEquals(
            "https://example.test/plan?" +
                "fromPlace=52.5186%2C13.4081&toPlace=48.1372%2C11.5756&" +
                "time=2026-08-19T06%3A00%3A00.000Z&numItineraries=3&" +
                "maxPreTransitTime=1800&maxPostTransitTime=1800",
            request.uri,
        )
        assertEquals("Roadstr/1.0 (navigation app)", request.headers["User-Agent"])
        assertFalse(request.uri.contains("stale=1"))
    }

    @Test
    fun `request rejects invalid coordinates user info and remote cleartext`() {
        assertThrows(IllegalArgumentException::class.java) {
            TransitProtocol.planRequest(
                TransitRequestPoint(91.0, 9.0),
                TransitRequestPoint(45.0, 9.0),
                Instant.EPOCH,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransitProtocol.planRequest(
                TransitRequestPoint(45.0, 9.0),
                TransitRequestPoint(45.1, 9.1),
                Instant.EPOCH,
                endpoint = "https://secret@example.test/plan",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransitProtocol.planRequest(
                TransitRequestPoint(45.0, 9.0),
                TransitRequestPoint(45.1, 9.1),
                Instant.EPOCH,
                endpoint = "http://example.test/plan",
            )
        }
    }

    @Test
    fun `mode catalogue is total and preserves the three street modes`() {
        assertEquals(21, TransitMode.entries.size)
        assertEquals(TransitMode.RegionalRail, TransitMode.fromWire(" regional_rail "))
        assertEquals(TransitMode.Other, TransitMode.fromWire("TELEPORT"))
        assertEquals(TransitMode.Other, TransitMode.fromWire(42))
        assertEquals(
            setOf(TransitMode.Walk, TransitMode.Bike, TransitMode.Car),
            TransitMode.entries.filterNot(TransitMode::isTransit).toSet(),
        )
    }

    @Test
    fun `real Berlin fixture parses legs operator colors and precision seven geometry`() {
        val result = TransitProtocol.parsePlan(fixture()) as TransitParsedPlan

        assertEquals(2, result.itineraries.size)
        val itinerary = result.itineraries.first()
        assertEquals(780, itinerary.durationSeconds)
        assertEquals(0, itinerary.transfers)
        assertEquals(listOf(TransitMode.Walk, TransitMode.Metro, TransitMode.Walk), itinerary.legs.map(TransitLeg::mode))
        val ride = itinerary.transitLegs.single()
        assertEquals("S3", ride.displayLine)
        assertEquals("S-Bahn Berlin GmbH", ride.agencyName)
        assertEquals(0xFF0066AD, ride.routeColorArgb)
        assertEquals(0xFFFFFFFF, ride.routeTextColorArgb)
        assertTrue(ride.geometry.isNotEmpty())
        assertTrue(ride.geometry.first().latitude in 52.0..53.0)
        assertTrue(ride.geometry.first().longitude in 13.0..14.0)
    }

    @Test
    fun `real Berlin fixture preserves sentinels and itinerary helpers`() {
        val itinerary = (TransitProtocol.parsePlan(fixture()) as TransitParsedPlan)
            .itineraries.first()

        assertNull(itinerary.legs.first().fromName)
        assertNull(itinerary.legs.last().toName)
        assertEquals("S+U Berlin Hauptbahnhof", itinerary.boarding!!.name)
        assertEquals(itinerary.transitLegs.first().startTime, itinerary.boarding!!.time)
        assertEquals(itinerary.legs.first().durationSeconds, itinerary.accessWalkSeconds)
        assertEquals(327.0, itinerary.walkingDistanceMeters, 0.001)
        assertFalse(itinerary.isWalkOnly)
        assertFalse(itinerary.isFullyRealTime)
    }

    @Test
    fun `polyline precision and truncation match the Flutter decoder`() {
        val decoded = BoundedJsonParser(fixture()).parse() as Map<*, *>
        val itineraries = decoded["itineraries"] as List<*>
        val first = itineraries.first() as Map<*, *>
        val legs = first["legs"] as List<*>
        val ride = legs[1] as Map<*, *>
        val geometry = ride["legGeometry"] as Map<*, *>
        val encoded = geometry["points"] as String

        val correct = TransitProtocol.decodePolyline(encoded, precision = 7)
        val wrong = TransitProtocol.decodePolyline(encoded, precision = 5)
        val clipped = TransitProtocol.decodePolyline(
            encoded.substring(0, encoded.length / 2),
            precision = 7,
        )

        assertTrue(correct.first().latitude in 52.0..53.0)
        assertTrue(wrong.first().latitude > 5_000)
        assertTrue(clipped.isNotEmpty())
        assertTrue(clipped.size < correct.size)
        assertTrue(TransitProtocol.decodePolyline("", 7).isEmpty())
    }

    @Test
    fun `parser distinguishes no coverage from malformed provider data`() {
        assertEquals(TransitParsedUnavailable, TransitProtocol.parsePlan("{}"))
        assertEquals(
            TransitParsedUnavailable,
            TransitProtocol.parsePlan(
                """{"itineraries":[{"duration":60,"startTime":"2026-08-19T08:00:00Z","endTime":"2026-08-19T08:01:00Z","legs":[{"mode":"WALK","duration":60,"startTime":"2026-08-19T08:00:00Z","endTime":"2026-08-19T08:01:00Z"}]}]}""",
            ),
        )
        assertThrows(TransitResponseException::class.java) { TransitProtocol.parsePlan("[]") }
        assertThrows(TransitResponseException::class.java) { TransitProtocol.parsePlan("not-json") }
    }

    @Test
    fun `ragged records degrade locally while structural and collection limits fail closed`() {
        val parsed = TransitProtocol.parsePlan(
            """{"itineraries":[{"duration":600,"startTime":"2026-08-19T08:00:00Z","endTime":"2026-08-19T08:10:00Z","legs":[{"mode":"TELEPORT","duration":600,"startTime":"2026-08-19T08:00:00Z","endTime":"2026-08-19T08:10:00Z","distance":null,"routeColor":"not-a-colour","agencyName":"   ","from":{"name":"START"}}]}]}""",
        ) as TransitParsedPlan
        val leg = parsed.itineraries.single().legs.single()
        assertEquals(TransitMode.Other, leg.mode)
        assertNull(leg.distanceMeters)
        assertNull(leg.routeColorArgb)
        assertNull(leg.agencyName)
        assertNull(leg.fromName)

        val tooMany = List(TransitProtocol.MAX_RESPONSE_ITINERARIES + 1) { "{}" }
            .joinToString(",")
        assertThrows(TransitResponseException::class.java) {
            TransitProtocol.parsePlan("{\"itineraries\":[$tooMany]}")
        }
        assertThrows(TransitResponseException::class.java) {
            TransitProtocol.decodePolyline("?".repeat(TransitProtocol.MAX_ENCODED_GEOMETRY_CHARS + 1), 7)
        }
    }

    private fun fixture(): String {
        var current: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (current != null) {
            val candidate = File(current, "test/fixtures/transit_plan_berlin.json")
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile
        }
        error("Unable to locate transit_plan_berlin.json")
    }

}
