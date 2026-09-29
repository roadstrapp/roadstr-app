package app.roadstr.feature.transit

import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitLeg
import app.roadstr.core.network.TransitMode
import app.roadstr.core.network.TransitParsedPlan
import app.roadstr.core.network.TransitResponsePoint
import app.roadstr.feature.map.NativeTransitOverlaySession
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTransitPresentationTest {
    @Test
    fun `duration and clock retain the compact Flutter card format`() {
        assertEquals("13m", NativeTransitPresenter.formatDuration(780))
        assertEquals("1h 5m", NativeTransitPresenter.formatDuration(3_900))
        assertEquals(
            "08:05",
            NativeTransitPresenter.formatClock(
                Instant.parse("2026-01-10T08:05:00Z"),
                ZoneId.of("UTC"),
            ),
        )
        assertEquals(
            "09:05",
            NativeTransitPresenter.formatClock(
                Instant.parse("2026-01-10T08:05:00Z"),
                ZoneId.of("Europe/Rome"),
            ),
        )
    }

    @Test
    fun `walking distance preserves metric and imperial thresholds`() {
        assertEquals("0 m", NativeTransitPresenter.formatDistance(49.9, imperial = false))
        assertEquals("50 m", NativeTransitPresenter.formatDistance(50.4, imperial = false))
        assertEquals("1000 m", NativeTransitPresenter.formatDistance(999.6, imperial = false))
        assertEquals("1.0 km", NativeTransitPresenter.formatDistance(1_000.0, imperial = false))
        assertEquals("0 ft", NativeTransitPresenter.formatDistance(49.9, imperial = true))
        assertEquals("330 ft", NativeTransitPresenter.formatDistance(100.0, imperial = true))
        assertEquals("0.1 mi", NativeTransitPresenter.formatDistance(152.4, imperial = true))
        assertEquals("10 mi", NativeTransitPresenter.formatDistance(16_093.44, imperial = true))
    }

    @Test
    fun `card projection preserves boarding legs schedule and operator colors`() {
        val card = NativeTransitPresenter.itinerary(
            itinerary = itinerary(
                transitColor = 0xFFFF_FF00,
                transitTextColor = null,
                realTime = false,
            ),
            accentArgb = ACCENT,
            imperial = false,
            zoneId = UTC,
        )

        assertEquals("13m", card.durationLabel)
        assertEquals("08:00", card.startTimeLabel)
        assertEquals("08:13", card.endTimeLabel)
        assertEquals("Central", card.boardingName)
        assertEquals("08:03", card.boardingTimeLabel)
        assertEquals(3, card.accessWalkMinutes)
        assertEquals("327 m", card.walkingDistanceLabel)
        assertTrue(card.scheduledTimes)
        assertEquals(listOf(TransitMode.Walk, TransitMode.Bus), card.legs.map { it.mode })
        assertNull(card.legs.first().line)
        assertEquals("42", card.legs.last().line)
        assertEquals(0xFFFF_FF00, card.legs.last().backgroundArgb)
        assertEquals(0xFF00_0000, card.legs.last().foregroundArgb)
    }

    @Test
    fun `missing operator colors use accent and dark colors use white text`() {
        val card = NativeTransitPresenter.itinerary(
            itinerary = itinerary(
                transitColor = null,
                transitTextColor = null,
                realTime = true,
            ),
            accentArgb = 0xFF20_2040,
            imperial = false,
            zoneId = UTC,
        )

        assertEquals(0xFF20_2040, card.legs.last().backgroundArgb)
        assertEquals(0xFFFF_FFFF, card.legs.last().foregroundArgb)
        assertFalse(card.scheduledTimes)
    }

    @Test
    fun `presentation keeps only the eight alternatives supported by the map`() {
        val plan = TransitParsedPlan(List(10) { itinerary(durationSeconds = 600L + it) })

        val cards = NativeTransitPresenter.plan(plan, ACCENT, imperial = false, zoneId = UTC)

        assertEquals(8, cards.size)
        assertThrows(IllegalArgumentException::class.java) {
            NativeTransitPresenter.plan(
                TransitParsedPlan(emptyList()),
                ACCENT,
                imperial = false,
                zoneId = UTC,
            )
        }
    }

    @Test
    fun `session begins once and bounds the destination without publishing provider data`() {
        val session = session()

        assertTrue(session.begin(4, "  ${"x".repeat(1_100)}  "))
        assertFalse(session.begin(4, "late"))
        assertEquals(NativeTransitUiStatus.Loading, session.state.value.status)
        assertEquals(1_000, session.state.value.destinationLabel!!.length)
        assertTrue(session.state.value.itineraries.isEmpty())
    }

    @Test
    fun `accepted plan and selection update UI and map at the same revision`() {
        val overlay = overlay()
        val session = session(overlay)
        val plan = TransitParsedPlan(
            listOf(
                itinerary(durationSeconds = 900),
                itinerary(durationSeconds = 1_200, transitColor = 0xFF00_66AD),
            ),
        )
        assertTrue(session.begin(7, "Station"))

        assertTrue(session.submitPlan(7, plan))
        assertEquals(NativeTransitUiStatus.Ready, session.state.value.status)
        assertEquals(0, session.state.value.selectedIndex)
        assertEquals(0, overlay.state.value.selectedItineraryIndex)
        assertTrue(session.select(7, 1))
        assertEquals(1, session.state.value.selectedIndex)
        assertEquals(1, overlay.state.value.selectedItineraryIndex)
        assertFalse(session.select(6, 0))
        assertFalse(session.select(7, 1))
    }

    @Test
    fun `unavailable and failure states clear map geometry and reject duplicate outcomes`() {
        val overlay = overlay()
        val session = session(overlay)
        assertTrue(session.submitPlan(1, TransitParsedPlan(listOf(itinerary()))))
        assertTrue(session.begin(2, "Destination"))
        assertEquals(1, overlay.state.value.itineraryCount)

        assertTrue(session.submitUnavailable(2))
        assertEquals(NativeTransitUiStatus.Unavailable, session.state.value.status)
        assertEquals(0, overlay.state.value.itineraryCount)
        assertFalse(session.submitFailure(2))

        assertTrue(session.begin(3, "Destination"))
        assertTrue(session.submitFailure(3))
        assertEquals(NativeTransitUiStatus.Failure, session.state.value.status)
        assertTrue(session.state.value.itineraries.isEmpty())
    }

    @Test
    fun `clear fences late callbacks and hides the panel`() {
        val session = session()
        assertTrue(session.begin(9))
        assertTrue(session.clear(9))

        assertEquals(NativeTransitUiStatus.Hidden, session.state.value.status)
        assertEquals(9, session.state.value.revision)
        assertFalse(session.submitPlan(9, TransitParsedPlan(listOf(itinerary()))))
        assertFalse(session.submitFailure(8))
    }

    @Test
    fun `appearance changes reproject values without changing selection or revision`() {
        val session = session()
        val plan = TransitParsedPlan(
            listOf(
                itinerary(transitColor = null),
                itinerary(transitColor = null, durationSeconds = 1_000),
            ),
        )
        assertTrue(session.submitPlan(12, plan, selectedIndex = 1))

        assertTrue(session.updatePresentation(0xFF11_2233, imperial = true))

        val state = session.state.value
        assertEquals(12, state.revision)
        assertEquals(1, state.selectedIndex)
        assertEquals("0.2 mi", state.itineraries.first().walkingDistanceLabel)
        assertEquals(0xFF11_2233, state.itineraries.first().legs.last().backgroundArgb)
        assertFalse(session.updatePresentation(0xFF11_2233, imperial = true))
    }

    private fun session(
        overlay: NativeTransitOverlaySession = overlay(),
    ) = NativeTransitJourneySession(
        overlaySession = overlay,
        initialAccentArgb = ACCENT,
        zoneId = UTC,
    )

    private fun overlay() = NativeTransitOverlaySession(
        initialAccentArgb = ACCENT,
        initialTextSecondaryArgb = 0xFF75_7575,
    )

    private fun itinerary(
        durationSeconds: Long = 780,
        transitColor: Long? = 0xFF00_66AD,
        transitTextColor: Long? = 0xFFFF_FFFF,
        realTime: Boolean = false,
    ): TransitItinerary = TransitItinerary(
        durationSeconds = durationSeconds,
        startTime = Instant.parse("2026-01-10T08:00:00Z"),
        endTime = Instant.parse("2026-01-10T08:13:00Z"),
        transfers = 0,
        legs = listOf(
            leg(
                mode = TransitMode.Walk,
                durationSeconds = 180,
                distanceMeters = 327.0,
                fromName = null,
                toName = "Central",
                startTime = Instant.parse("2026-01-10T08:00:00Z"),
                endTime = Instant.parse("2026-01-10T08:03:00Z"),
            ),
            leg(
                mode = TransitMode.Bus,
                durationSeconds = 600,
                distanceMeters = null,
                fromName = "Central",
                toName = "Destination",
                startTime = Instant.parse("2026-01-10T08:03:00Z"),
                endTime = Instant.parse("2026-01-10T08:13:00Z"),
                routeColorArgb = transitColor,
                routeTextColorArgb = transitTextColor,
                realTime = realTime,
            ),
        ),
    )

    private fun leg(
        mode: TransitMode,
        durationSeconds: Long,
        distanceMeters: Double?,
        fromName: String?,
        toName: String?,
        startTime: Instant,
        endTime: Instant,
        routeColorArgb: Long? = null,
        routeTextColorArgb: Long? = null,
        realTime: Boolean = false,
    ) = TransitLeg(
        mode = mode,
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        startTime = startTime,
        endTime = endTime,
        fromName = fromName,
        toName = toName,
        routeShortName = if (mode.isTransit) "42" else null,
        routeLongName = null,
        agencyName = if (mode.isTransit) "Roadstr Transit" else null,
        headsign = if (mode.isTransit) "Destination" else null,
        routeColorArgb = routeColorArgb,
        routeTextColorArgb = routeTextColorArgb,
        realTime = realTime,
        geometry = listOf(
            TransitResponsePoint(44.0, 12.0),
            TransitResponsePoint(44.1, 12.1),
        ),
    )

    companion object {
        private const val ACCENT = 0xFF8B_5CF6L
        private val UTC = ZoneId.of("UTC")
    }
}
