package app.roadstr.feature.transit

import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitLeg
import app.roadstr.core.network.TransitMode
import app.roadstr.core.network.TransitResponsePoint
import app.roadstr.core.network.TransitScopedId
import app.roadstr.core.network.TransitStop
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTransitNavigationSessionTest {
    @Test
    fun `waiting never assumes boarding and manual confirmations drive an underground trip`() {
        val session = NativeTransitNavigationSession()
        assertTrue(session.start(1, directTransit()))
        assertEquals(NativeTransitNavigationStage.WaitingToBoard, session.state.value.stage)

        session.tick(Instant.parse("2026-10-11T10:03:00Z"))
        assertEquals(NativeTransitNavigationStage.WaitingToBoard, session.state.value.stage)
        assertTrue(session.confirmBoarded())
        assertEquals(NativeTransitNavigationStage.OnBoard, session.state.value.stage)

        assertTrue(session.tick(Instant.parse("2026-10-11T10:18:30Z")))
        assertEquals(NativeTransitNavigationStage.ApproachingAlightingStop, session.state.value.stage)
        assertTrue(session.confirmAlighted())
        assertEquals(NativeTransitNavigationStage.Arrived, session.state.value.stage)
        assertFalse(session.state.value.active)
    }

    @Test
    fun `walk transfer and final walk advance without auto boarding`() {
        val session = NativeTransitNavigationSession()
        assertTrue(session.start(2, transferJourney()))
        assertEquals(NativeTransitNavigationStage.ApproachStop, session.state.value.stage)

        assertTrue(session.confirmWalkComplete())
        assertEquals(NativeTransitNavigationStage.WaitingToBoard, session.state.value.stage)
        assertTrue(session.confirmBoarded())
        assertTrue(session.confirmAlighted())
        assertEquals(NativeTransitNavigationStage.TransferWalk, session.state.value.stage)
        assertTrue(session.confirmWalkComplete())
        assertEquals(NativeTransitNavigationStage.WaitingForConnection, session.state.value.stage)
        assertTrue(session.confirmBoarded())
        assertTrue(session.confirmAlighted())
        assertEquals(NativeTransitNavigationStage.FinalWalk, session.state.value.stage)
        assertTrue(session.confirmWalkComplete())
        assertEquals(NativeTransitNavigationStage.Arrived, session.state.value.stage)
    }

    @Test
    fun `GPS only completes walks and warns for alighting`() {
        val session = NativeTransitNavigationSession()
        session.start(3, transferJourney())

        assertFalse(session.submitLocation(TransitResponsePoint(45.0, 9.0), Instant.EPOCH))
        assertTrue(session.submitLocation(TransitResponsePoint(45.001, 9.001), Instant.EPOCH))
        assertEquals(NativeTransitNavigationStage.WaitingToBoard, session.state.value.stage)
        assertFalse(session.submitLocation(TransitResponsePoint(45.1, 9.1), Instant.EPOCH))
    }

    @Test
    fun `missed trip requests replan without silently replacing itinerary`() {
        val session = NativeTransitNavigationSession()
        session.start(4, directTransit())

        assertTrue(session.markMissed())
        assertEquals(NativeTransitNavigationStage.MissedTrip, session.state.value.stage)
        assertEquals(1L, session.state.value.replanSequence)
        assertEquals("M1", session.state.value.line)
        assertTrue(session.requestVerification())
        assertEquals(1L, session.state.value.verifySequence)
    }

    @Test
    fun `checkpoint restores background session without replaying voice cue`() {
        val journey = directTransit()
        val original = NativeTransitNavigationSession()
        original.start(5, journey)
        original.confirmBoarded()
        val checkpoint = requireNotNull(original.checkpoint())
        val restored = NativeTransitNavigationSession()

        assertTrue(restored.restore(checkpoint, journey))
        assertEquals(NativeTransitNavigationStage.OnBoard, restored.state.value.stage)
        assertEquals(null, restored.state.value.voiceCue)
        assertTrue(restored.suspend())
        assertEquals(NativeTransitNavigationStage.Suspended, restored.state.value.stage)
        assertTrue(restored.resume())
        assertEquals(NativeTransitNavigationStage.OnBoard, restored.state.value.stage)
    }

    @Test
    fun `voice cue sequence changes only on meaningful transitions`() {
        val session = NativeTransitNavigationSession()
        session.start(6, directTransit())
        val first = session.state.value.voiceCue!!.sequence

        assertFalse(session.tick(Instant.parse("2026-10-11T10:00:00Z")))
        assertEquals(first, session.state.value.voiceCue!!.sequence)
        session.confirmBoarded()
        val second = session.state.value.voiceCue!!.sequence
        assertTrue(second > first)
        assertFalse(session.confirmBoarded())
        assertEquals(second, session.state.value.voiceCue!!.sequence)
    }

    @Test
    fun `passed and skipped intermediate stops update the HUD without auto alighting`() {
        val base = Instant.parse("2026-10-11T10:00:00Z")
        val leg = transitLeg("M1", "A", "D", 0, 1_200).copy(
            intermediateStops = listOf(
                stop("B", 45.02, 9.02).copy(
                    scheduledArrival = base.plusSeconds(300),
                    expectedArrival = base.plusSeconds(360),
                ),
                stop("C", 45.05, 9.05).copy(
                    scheduledArrival = base.plusSeconds(600),
                    expectedArrival = base.plusSeconds(660),
                ),
            ),
        )
        val session = NativeTransitNavigationSession()
        session.start(
            7,
            TransitItinerary(1_200, base, base.plusSeconds(1_200), 0, listOf(leg), TransitScopedId("transitous", "stops")),
        )
        session.confirmBoarded()

        assertEquals("B", session.state.value.nextStop)
        assertEquals(3, session.state.value.stopsRemaining)
        assertTrue(session.tick(base.plusSeconds(700)))
        assertEquals("D", session.state.value.nextStop)
        assertEquals(1, session.state.value.stopsRemaining)
        assertEquals(NativeTransitNavigationStage.OnBoard, session.state.value.stage)
        assertTrue(session.confirmAlighted())
        assertEquals(NativeTransitNavigationStage.Arrived, session.state.value.stage)
    }

    private fun directTransit() = TransitItinerary(
        durationSeconds = 1_200,
        startTime = Instant.parse("2026-10-11T10:00:00Z"),
        endTime = Instant.parse("2026-10-11T10:20:00Z"),
        transfers = 0,
        legs = listOf(transitLeg("M1", "A", "B", 0, 1_200)),
        id = TransitScopedId("transitous", "direct"),
    )

    private fun transferJourney(): TransitItinerary {
        val start = Instant.parse("2026-10-11T10:00:00Z")
        val walk1 = walkLeg("Origin", "A", 0, 300, TransitResponsePoint(45.001, 9.001))
        val train1 = transitLeg("M1", "A", "B", 300, 900)
        val transfer = walkLeg("B", "C", 900, 1_020, TransitResponsePoint(45.101, 9.101))
        val train2 = transitLeg("M2", "C", "D", 1_020, 1_620)
        val finalWalk = walkLeg("D", "Destination", 1_620, 1_800, TransitResponsePoint(45.201, 9.201))
        return TransitItinerary(
            durationSeconds = 1_800,
            startTime = start,
            endTime = start.plusSeconds(1_800),
            transfers = 1,
            legs = listOf(walk1, train1, transfer, train2, finalWalk),
            id = TransitScopedId("transitous", "transfer"),
        )
    }

    private fun transitLeg(line: String, from: String, to: String, start: Long, end: Long): TransitLeg =
        leg(TransitMode.Subway, from, to, start, end).copy(
            routeShortName = line,
            headsign = to,
            tripId = TransitScopedId("transitous", "trip-$line"),
            intermediateStops = listOf(stop("Middle", 45.05, 9.05)),
        )

    private fun walkLeg(
        from: String,
        to: String,
        start: Long,
        end: Long,
        target: TransitResponsePoint,
    ): TransitLeg = leg(TransitMode.Walk, from, to, start, end).copy(
        to = stop(to, target.latitude, target.longitude),
        geometry = listOf(target),
    )

    private fun leg(mode: TransitMode, from: String, to: String, start: Long, end: Long): TransitLeg {
        val base = Instant.parse("2026-10-11T10:00:00Z")
        return TransitLeg(
            mode = mode,
            distanceMeters = 500.0,
            durationSeconds = end - start,
            startTime = base.plusSeconds(start),
            endTime = base.plusSeconds(end),
            fromName = from,
            toName = to,
            routeShortName = null,
            routeLongName = null,
            agencyName = null,
            headsign = null,
            routeColorArgb = null,
            routeTextColorArgb = null,
            realTime = false,
            geometry = emptyList(),
            from = stop(from, 45.0 + start / 10_000.0, 9.0 + start / 10_000.0),
            to = stop(to, 45.0 + end / 10_000.0, 9.0 + end / 10_000.0),
        )
    }

    private fun stop(name: String, lat: Double, lon: Double) = TransitStop(
        id = TransitScopedId("transitous", name.lowercase()),
        name = name,
        position = TransitResponsePoint(lat, lon),
        timezone = "Europe/Rome",
        scheduledArrival = null,
        expectedArrival = null,
        scheduledDeparture = null,
        expectedDeparture = null,
        scheduledPlatform = null,
        expectedPlatform = null,
        cancelled = false,
        alerts = emptyList(),
    )
}
