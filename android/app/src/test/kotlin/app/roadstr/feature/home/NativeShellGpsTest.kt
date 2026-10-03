package app.roadstr.feature.home

import app.roadstr.feature.map.NativeMapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeShellGpsTest {
    @Test
    fun `active snapshot requires a validated fix`() {
        val fix = validFix()

        val snapshot = NativeShellGpsSnapshot(NativeShellGpsPhase.Active, fix)

        assertEquals(fix, snapshot.fix)
        assertThrows(IllegalArgumentException::class.java) {
            NativeShellGpsSnapshot(NativeShellGpsPhase.Active)
        }
    }

    @Test
    fun `fix rejects invalid map and motion values`() {
        assertThrows(IllegalArgumentException::class.java) {
            validFix(point = NativeMapPoint(91.0, 9.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validFix(speedMetersPerSecond = -0.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validFix(accuracyMeters = Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `paused snapshot may retain the last in-memory fix`() {
        val fix = validFix(sequence = 7L)

        val snapshot = NativeShellGpsSnapshot(NativeShellGpsPhase.Paused, fix)

        assertEquals(7L, snapshot.fix?.sequence)
        assertEquals(NativeShellGpsPhase.Paused, snapshot.phase)
    }

    private fun validFix(
        sequence: Long = 0L,
        point: NativeMapPoint = NativeMapPoint(45.0, 9.0),
        speedMetersPerSecond: Double = 4.0,
        accuracyMeters: Double = 5.0,
    ): NativeShellGpsFix = NativeShellGpsFix(
        sequence = sequence,
        point = point,
        speedMetersPerSecond = speedMetersPerSecond,
        accuracyMeters = accuracyMeters,
        headingDegrees = 90.0,
        altitudeMeters = 120.0,
        receivedAtElapsedRealtimeMillis = 1_000L,
    )
}
