package app.roadstr.feature.savedtransit

import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitLeg
import app.roadstr.core.network.TransitMode
import app.roadstr.core.network.TransitParsedPlan
import app.roadstr.core.network.TransitProtocol
import app.roadstr.core.network.TransitRequestPoint
import app.roadstr.core.network.TransitResponsePoint
import app.roadstr.core.network.TransitScopedId
import app.roadstr.core.network.TransitStop
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSavedTransitJourneyTest {
    @Test
    fun `snapshot round trips all operational journey values for offline reading`() {
        val saved = savedJourney()
        val encoded = NativeSavedTransitProtocol.encode(listOf(saved))
        val decoded = NativeSavedTransitProtocol.decode(encoded)

        assertEquals(listOf(saved), decoded)
        assertEquals(NativeSavedTransitState.SavedSnapshot, decoded.single().state)
        assertTrue(decoded.single().original.legs.single().geometry.isNotEmpty())
        assertEquals("transitous", decoded.single().original.legs.single().tripId?.providerId)
    }

    @Test
    fun `corrupt and future schema snapshots fail closed`() {
        val encoded = NativeSavedTransitProtocol.encode(listOf(savedJourney()))
        assertTrue(NativeSavedTransitProtocol.decode(encoded.dropLast(3) + "bad").isEmpty())

        val framed = Base64.getUrlDecoder().decode(encoded)
        val payload = framed.copyOfRange(0, framed.size - 32)
        payload[7] = 2 // schema int occupies bytes 4..7
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val future = Base64.getUrlEncoder().withoutPadding().encodeToString(payload + digest)
        assertTrue(NativeSavedTransitProtocol.decode(future).isEmpty())
    }

    @Test
    fun `comparison uses scoped trip ids and exposes uncertain provider changes`() {
        val original = savedJourney().original
        val delayed = original.copy(
            legs = listOf(
                original.legs.single().copy(
                    startTime = original.startTime.plusSeconds(120),
                    endTime = original.endTime.plusSeconds(120),
                    cancelled = true,
                ),
            ),
        )
        val kinds = NativeSavedTransitComparator.compare(original, delayed).map { it.kind }.toSet()
        assertTrue(NativeSavedTransitDifferenceKind.DelayChanged in kinds)
        assertTrue(NativeSavedTransitDifferenceKind.CancellationChanged in kinds)

        val foreign = original.copy(id = TransitScopedId("entur", "journey"))
        assertEquals(
            NativeSavedTransitDifferenceKind.UncertainIdentity,
            NativeSavedTransitComparator.compare(original, foreign).single().kind,
        )
    }

    @Test
    fun `separate encrypted store boundary persists updates and survives restart`() {
        var encrypted: String? = null
        val store = NativeSavedTransitStore(
            readEncryptedValue = { encrypted },
            writeEncryptedValue = { encrypted = it; true },
            removeEncryptedValue = { encrypted = null; true },
        )
        val saved = savedJourney()
        val values = requireNotNull(store.upsert(emptyList(), saved))
        assertEquals(values, store.load())

        val updated = NativeSavedTransitProtocol.withUpdate(
            saved,
            saved.original.copy(endTime = saved.original.endTime.plusSeconds(60)),
            Instant.parse("2026-10-12T09:00:00Z"),
        )
        requireNotNull(store.upsert(values, updated))
        assertEquals(NativeSavedTransitState.Updated, store.load().single().state)
    }

    private fun savedJourney(): NativeSavedTransitJourney {
        val start = Instant.parse("2026-10-12T08:00:00Z")
        val from = stop("A", 45.0, 9.0, "1")
        val to = stop("B", 45.1, 9.1, "2")
        val itinerary = TransitItinerary(
            durationSeconds = 1_200,
            startTime = start,
            endTime = start.plusSeconds(1_200),
            transfers = 0,
            legs = listOf(
                TransitLeg(
                    mode = TransitMode.Rail,
                    distanceMeters = 10_000.0,
                    durationSeconds = 1_200,
                    startTime = start,
                    endTime = start.plusSeconds(1_200),
                    fromName = "A",
                    toName = "B",
                    routeShortName = "R1",
                    routeLongName = "Regional 1",
                    agencyName = "Operator",
                    headsign = "B",
                    routeColorArgb = 0xFF112233,
                    routeTextColorArgb = 0xFFFFFFFF,
                    realTime = true,
                    geometry = listOf(
                        TransitResponsePoint(45.0, 9.0),
                        TransitResponsePoint(45.05, 9.05),
                        TransitResponsePoint(45.1, 9.1),
                    ),
                    from = from,
                    to = to,
                    routeId = TransitScopedId("transitous", "route"),
                    tripId = TransitScopedId("transitous", "trip"),
                    intermediateStops = listOf(stop("Middle", 45.05, 9.05, null)),
                ),
            ),
            id = TransitScopedId("transitous", "journey"),
        )
        val plan = TransitParsedPlan(
            itineraries = listOf(itinerary),
            provider = TransitProtocol.TRANSITOUS_PROVIDER,
            searchedAt = start.minusSeconds(30),
            origin = TransitRequestPoint(45.0, 9.0),
            destination = TransitRequestPoint(45.1, 9.1),
            requestedDeparture = start,
        )
        return NativeSavedTransitProtocol.create(
            name = "Morning train",
            originLabel = "A",
            destinationLabel = "B",
            plan = plan,
            itinerary = itinerary,
            timezone = ZoneId.of("Europe/Rome"),
            now = start.minusSeconds(60),
            id = "saved-1",
        )
    }

    private fun stop(name: String, lat: Double, lon: Double, platform: String?) = TransitStop(
        id = TransitScopedId("transitous", "stop-$name"),
        name = name,
        position = TransitResponsePoint(lat, lon),
        timezone = "Europe/Rome",
        scheduledArrival = null,
        expectedArrival = null,
        scheduledDeparture = null,
        expectedDeparture = null,
        scheduledPlatform = platform,
        expectedPlatform = platform,
        cancelled = false,
        alerts = emptyList(),
    )
}
