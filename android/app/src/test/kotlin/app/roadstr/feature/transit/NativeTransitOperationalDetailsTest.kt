package app.roadstr.feature.transit

import app.roadstr.core.network.TransitLine
import app.roadstr.core.network.TransitMode
import app.roadstr.core.network.TransitScopedId
import app.roadstr.core.network.TransitStopReference
import app.roadstr.service.transit.NativeTransitData
import app.roadstr.service.transit.NativeTransitDataUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTransitOperationalDetailsTest {
    private val stop = TransitStopReference(TransitScopedId("transitous", "stop"), "Central", null)

    @Test
    fun `line lookup is revision fenced and bounded`() {
        val session = NativeTransitOperationalSession()
        assertTrue(session.beginLines(1, stop))
        assertFalse(session.submitLines(0, NativeTransitData(emptyList())))
        val lines = List(300) { index ->
            TransitLine(
                TransitScopedId("transitous", "line-$index"),
                index.toString(),
                null,
                TransitMode.Bus,
                null,
                null,
                null,
                null,
            )
        }

        assertTrue(session.submitLines(1, NativeTransitData(lines)))
        assertEquals(NativeTransitOperationalStatus.Lines, session.state.value.status)
        assertEquals(256, session.state.value.lines.size)
    }

    @Test
    fun `unsupported trip detail becomes an explicit failure and can close`() {
        val session = NativeTransitOperationalSession()
        val reference = TransitScopedId("transitous", "trip")
        assertTrue(session.beginTrip(2, "R1 · North", reference))
        assertTrue(session.submitTrip(2, NativeTransitDataUnavailable))
        assertEquals(NativeTransitOperationalStatus.Failure, session.state.value.status)
        assertEquals(reference, session.state.value.tripReference)
        assertTrue(session.hide())
        assertEquals(NativeTransitOperationalStatus.Hidden, session.state.value.status)
    }
}
