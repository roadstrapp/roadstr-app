package app.roadstr.core.map

import kotlin.math.PI
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportWindowParityTest {
    private val width = 392.0
    private val height = 800.0

    @Test
    fun `tilt expands forward view and clamps at MapLibre maximum`() {
        val flat = ViewportWindow.groundExtentsDp(0.0, width, height)
        val tilted = ViewportWindow.groundExtentsDp(55.0, width, height)
        val maximum = ViewportWindow.groundExtentsDp(60.0, width, height)
        val beyond = ViewportWindow.groundExtentsDp(89.0, width, height)
        assertEquals(flat.forward, flat.backward, 0.000001)
        assertTrue(tilted.forward > tilted.backward * 2)
        assertTrue(tilted.forward > flat.forward)
        assertEquals(maximum.forward, beyond.forward, 0.0)
        assertTrue(beyond.forward.isFinite())
    }

    @Test
    fun `window bounds rotate with camera and scale with zoom`() {
        fun window(bearing: Double, zoom: Double = 17.0) = ViewportWindow.create(
            centerLatitude = 45.0,
            centerLongitude = 9.0,
            zoom = zoom,
            bearingDegrees = bearing,
            pitchDegrees = 55.0,
            screenWidthDp = width,
            screenHeightDp = height,
            margin = 1.0,
            extraMeters = 0.0,
        )
        val northFacing = window(0.0)
        assertTrue(northFacing.contains(45.0, 9.0))
        val metersPerDegreeLongitude = 111_320.0 * cos(45.0 * PI / 180)
        val distance = northFacing.halfWidthMeters * 2
        val northLatitude = 45.0 + distance / 111_320.0
        val eastLongitude = 9.0 + distance / metersPerDegreeLongitude
        assertTrue(northFacing.contains(northLatitude, 9.0))
        assertFalse(northFacing.contains(45.0, eastLongitude))

        val eastFacing = window(90.0)
        assertTrue(eastFacing.contains(45.0, eastLongitude))
        assertFalse(eastFacing.contains(northLatitude, 9.0))

        val zoomedOut = window(0.0, zoom = 15.0)
        assertEquals(4.0, zoomedOut.forwardMeters / northFacing.forwardMeters, 0.05)
        assertEquals(4.0, zoomedOut.halfWidthMeters / northFacing.halfWidthMeters, 0.05)
    }
}
