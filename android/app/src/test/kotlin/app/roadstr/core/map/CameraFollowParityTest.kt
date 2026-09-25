package app.roadstr.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFollowParityTest {
    private val origin = CameraFollowState(45.0, 9.0, 17.0, 0.0)

    @Test
    fun `easing caps a large correction and takes shortest arc`() {
        val large = requireNotNull(
            CameraFollowEasing.step(
                origin,
                origin.copy(rotationDegrees = 180.0),
                100,
            ),
        )
        assertEquals(9.0, large.rotationDegrees, 0.0001)

        val seam = requireNotNull(
            CameraFollowEasing.step(
                origin.copy(rotationDegrees = 350.0),
                origin.copy(rotationDegrees = 10.0),
                100,
            ),
        )
        assertTrue(seam.rotationDegrees > 350.0)
    }

    @Test
    fun `position and zoom converge without overshooting`() {
        val target = CameraFollowState(45.001, 9.001, 18.0, 20.0)
        val step = requireNotNull(CameraFollowEasing.step(origin, target, 16))
        assertTrue(step.latitude in origin.latitude..target.latitude)
        assertTrue(step.longitude in origin.longitude..target.longitude)
        assertTrue(step.zoom in origin.zoom..target.zoom)
        assertFalse(CameraFollowEasing.hasCaughtUp(step, target))
        assertTrue(CameraFollowEasing.hasCaughtUp(target, target))

        assertNull(
            CameraFollowEasing.step(
                origin,
                origin.copy(latitude = Double.NaN),
                16,
            ),
        )
    }

    @Test
    fun `navigation center shifts in heading direction and clamps poles`() {
        val north = CameraFollowEasing.navigationCameraCenter(45.0, 9.0, 0.0, 17.0)
        assertTrue(north.latitude > 45.0)
        assertEquals(9.0, north.longitude, 0.0000001)
        val east = CameraFollowEasing.shiftByMeters(45.0, 9.0, 90.0, 100.0)
        assertEquals(45.0, east.latitude, 0.0000001)
        assertTrue(east.longitude > 9.0)
        assertEquals(
            89.9,
            CameraFollowEasing.shiftByMeters(89.89, 0.0, 0.0, 10_000.0).latitude,
            0.0,
        )
    }

    @Test
    fun `frame gate throttles tiny travel but sends turns and timed crawl`() {
        val gate = CameraFrameGate()
        assertTrue(gate.shouldSend(origin, 0))
        gate.markSent(origin, 0)

        val tiny = origin.copy(latitude = origin.latitude + 0.05 / 111_320.0)
        assertFalse(gate.shouldSend(tiny, 20))
        assertTrue(gate.shouldSend(tiny, 151))

        val turn = origin.copy(rotationDegrees = 1.0)
        assertTrue(gate.shouldSend(turn, 20))
        assertTrue(gate.hasVisibleChange(turn))
        gate.markSent(turn, 20)
        assertFalse(gate.hasVisibleChange(turn))
        gate.reset()
        assertTrue(gate.shouldSend(turn, 21))
    }
}
