package app.roadstr.feature.transit

import app.roadstr.core.network.TransitDeparture
import app.roadstr.core.network.TransitLine
import app.roadstr.core.network.TransitMode
import app.roadstr.core.network.TransitProvider
import app.roadstr.core.network.TransitResponsePoint
import app.roadstr.core.network.TransitScopedId
import app.roadstr.core.network.TransitServiceState
import app.roadstr.core.network.TransitStopBoard
import app.roadstr.core.network.TransitStopReference
import app.roadstr.service.transit.NativeTransitData
import app.roadstr.service.transit.NativeTransitDataFailure
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTransitStopBoardPresentationTest {
    @Test
    fun `board keeps scheduled and realtime values visibly distinct`() {
        val session = NativeTransitStopBoardSession(ZoneId.of("UTC"), Locale.ENGLISH)
        assertTrue(session.begin(1, stop))

        assertTrue(session.submit(1, NativeTransitData(board())))

        val snapshot = session.state.value
        assertEquals(NativeTransitStopBoardStatus.Ready, snapshot.status)
        assertEquals("10:05 AM", snapshot.departures.single().expectedTimeLabel)
        assertEquals("10:00 AM", snapshot.departures.single().scheduledTimeLabel)
        assertEquals(5L, snapshot.departures.single().delayMinutes)
        assertEquals("2", snapshot.departures.single().platform)
        assertEquals("1", snapshot.departures.single().scheduledPlatform)
        assertTrue(snapshot.departures.single().realtime)
    }

    @Test
    fun `late board response cannot replace a newer stop`() {
        val session = NativeTransitStopBoardSession(ZoneId.of("UTC"), Locale.ENGLISH)
        assertTrue(session.begin(1, stop))
        val other = stop.copy(id = TransitScopedId("transitous", "other"), name = "Other")
        assertTrue(session.begin(2, other))

        assertFalse(session.submit(1, NativeTransitData(board())))
        assertEquals("Other", session.state.value.stop!!.name)
        assertEquals(NativeTransitStopBoardStatus.Loading, session.state.value.status)
    }

    @Test
    fun `failure is explicit and snapshot closes cleanly`() {
        val session = NativeTransitStopBoardSession()
        session.begin(1, stop)

        assertTrue(session.submit(1, NativeTransitDataFailure("timeout", transient = true)))
        assertEquals(NativeTransitStopBoardStatus.Failure, session.state.value.status)
        assertTrue(session.hide(1))
        assertEquals(NativeTransitStopBoardStatus.Hidden, session.state.value.status)
    }

    private fun board() = TransitStopBoard(
        stop = stop,
        departures = listOf(
            TransitDeparture(
                stop = stop,
                tripId = TransitScopedId("transitous", "trip"),
                line = TransitLine(
                    id = TransitScopedId("transitous", "route"),
                    shortName = "R1",
                    longName = "Airport train",
                    mode = TransitMode.Rail,
                    agencyId = null,
                    agencyName = "Rail",
                    colorArgb = null,
                    textColorArgb = null,
                ),
                direction = "Airport",
                scheduledTime = Instant.parse("2026-10-11T10:00:00Z"),
                expectedTime = Instant.parse("2026-10-11T10:05:00Z"),
                scheduledPlatform = "1",
                expectedPlatform = "2",
                realtime = true,
                state = TransitServiceState.Delayed,
                alerts = emptyList(),
                nextStops = emptyList(),
            ),
        ),
        updatedAt = Instant.parse("2026-10-11T09:55:00Z"),
        previousPageCursor = null,
        nextPageCursor = null,
    )

    private companion object {
        val stop = TransitStopReference(
            id = TransitScopedId("transitous", "central"),
            name = "Central",
            position = TransitResponsePoint(45.0, 9.0),
        )
    }
}
