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
                "maxPreTransitTime=1800&maxPostTransitTime=1800&" +
                "realtimeMode=REALTIME&detailedTransfers=true",
            request.uri,
        )
        assertEquals(TransitProtocol.USER_AGENT, request.headers["User-Agent"])
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
    fun `mode catalogue covers current MOTIS v6 modes and preserves street modes`() {
        assertEquals(28, TransitMode.entries.size)
        assertEquals(TransitMode.RegionalRail, TransitMode.fromWire(" regional_rail "))
        assertEquals(TransitMode.AerialLift, TransitMode.fromWire("AREAL_LIFT"))
        assertEquals(TransitMode.Rental, TransitMode.fromWire("RENTAL"))
        assertEquals(TransitMode.Other, TransitMode.fromWire("TELEPORT"))
        assertEquals(TransitMode.Other, TransitMode.fromWire(42))
        assertEquals(
            setOf(
                TransitMode.Walk,
                TransitMode.Bike,
                TransitMode.Rental,
                TransitMode.Car,
                TransitMode.HeavyGoodsVehicle,
                TransitMode.CarParking,
                TransitMode.CarDropoff,
            ),
            TransitMode.entries.filterNot(TransitMode::isTransit).toSet(),
        )
    }

    @Test
    fun `v6 response preserves realtime schedule platforms scoped IDs stops alerts and provider order`() {
        val provider = TransitProtocol.TRANSITOUS_PROVIDER
        val result = TransitProtocol.parsePlan(
            """{"itineraries":[{"id":"journey-later","duration":900,"startTime":"2026-10-12T00:01:00+02:00","endTime":"2026-10-12T00:16:00+02:00","transfers":0,"legs":[{"mode":"BUS","duration":780,"startTime":"2026-10-12T00:01:00+02:00","endTime":"2026-10-12T00:14:00+02:00","scheduledStartTime":"2026-10-11T23:59:00+02:00","scheduledEndTime":"2026-10-12T00:12:00+02:00","realTime":true,"scheduled":true,"routeId":"route-10","tripId":"trip-10","agencyId":"agency","displayName":"10","agencyName":"Night Bus","headsign":"Central Station","cancelled":false,"source":"feed.zip:42","wheelchairAccessible":"ACCESSIBLE","from":{"name":"Old Town","stopId":"stop-a","lat":45.0,"lon":9.0,"tz":"Europe/Rome","scheduledDeparture":"2026-10-11T23:59:00+02:00","departure":"2026-10-12T00:01:00+02:00","scheduledTrack":"1","track":"2"},"to":{"name":"Central Station","stopId":"stop-c","lat":45.2,"lon":9.2,"scheduledArrival":"2026-10-12T00:12:00+02:00","arrival":"2026-10-12T00:14:00+02:00"},"intermediateStops":[{"name":"Market","stopId":"stop-b","lat":45.1,"lon":9.1,"cancelled":true}],"alerts":[{"code":"delay","headerText":"Diversion","descriptionText":"Use temporary stops","severityLevel":"WARNING","effect":"DETOUR","url":"https://example.test/alert"}],"legGeometry":{"precision":6,"points":""}}]},{"id":"journey-first","duration":1200,"startTime":"2026-10-12T00:05:00+02:00","endTime":"2026-10-12T00:25:00+02:00","legs":[{"mode":"TRAM","duration":1200,"startTime":"2026-10-12T00:05:00+02:00","endTime":"2026-10-12T00:25:00+02:00","scheduledStartTime":"2026-10-12T00:05:00+02:00","scheduledEndTime":"2026-10-12T00:25:00+02:00","realTime":false,"scheduled":true,"from":{"name":"A","lat":45.0,"lon":9.0},"to":{"name":"B","lat":45.2,"lon":9.2},"legGeometry":{"precision":6,"points":""}}]}]}""",
            provider = provider,
            searchedAt = Instant.parse("2026-10-11T21:50:00Z"),
        ) as TransitParsedPlan

        assertEquals(listOf("journey-later", "journey-first"), result.itineraries.map { it.id!!.value })
        assertEquals(provider, result.provider)
        val ride = result.itineraries.first().transitLegs.single()
        assertEquals(120L, ride.departureDelaySeconds)
        assertEquals("2", ride.from!!.expectedPlatform)
        assertEquals("1", ride.from!!.scheduledPlatform)
        assertEquals("transitous", ride.tripId!!.providerId)
        assertEquals("Market", ride.intermediateStops.single().name)
        assertTrue(ride.intermediateStops.single().cancelled)
        assertEquals(TransitAccessibility.Accessible, ride.accessibility)
        assertEquals("Diversion", ride.alerts.single().header)
        assertEquals(TransitRealtimeState.Realtime, result.itineraries.first().realtimeState)
        assertEquals(Instant.parse("2026-10-11T22:01:00Z"), ride.startTime)
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
