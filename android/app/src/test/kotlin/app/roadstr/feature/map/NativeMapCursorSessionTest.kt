package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMapCursorSessionTest {
    private val point = NativeMapPoint(latitude = 45.0, longitude = 9.0)

    @Test
    fun `cursor starts hidden with the Flutter default violet`() {
        val state = NativeMapCursorSession().state.value

        assertEquals(-1L, state.sequence)
        assertNull(state.point)
        assertEquals(0xFF8B3DFF, state.colorArgb)
    }

    @Test
    fun `new positions publish while stale sequences are ignored`() {
        val session = NativeMapCursorSession()

        assertTrue(session.submitPosition(1, point))
        assertFalse(session.submitPosition(1, NativeMapPoint(46.0, 10.0)))
        assertFalse(session.submitPosition(0, NativeMapPoint(47.0, 11.0)))
        assertEquals(point, session.state.value.point)
    }

    @Test
    fun `position validation rejects invalid sequence and WGS84 coordinates`() {
        val session = NativeMapCursorSession()

        assertThrows(IllegalArgumentException::class.java) {
            session.submitPosition(-1, point)
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.submitPosition(1, NativeMapPoint(Double.NaN, 9.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.submitPosition(1, NativeMapPoint(45.0, 181.0))
        }
    }

    @Test
    fun `clear hides the cursor and fences late fixes`() {
        val session = NativeMapCursorSession()
        session.submitPosition(2, point)

        assertTrue(session.clear(3))
        assertFalse(session.submitPosition(2, point))
        assertFalse(session.clear(3))
        assertNull(session.state.value.point)
        assertEquals(3L, session.state.value.sequence)
    }

    @Test
    fun `color updates preserve position and reject ARGB overflow`() {
        val session = NativeMapCursorSession()
        session.submitPosition(1, point)

        assertTrue(session.updateColor(0xFFFF9500))
        assertFalse(session.updateColor(0xFFFF9500))
        assertEquals(point, session.state.value.point)
        assertEquals(1L, session.state.value.sequence)
        assertThrows(IllegalArgumentException::class.java) {
            session.updateColor(0x1_0000_0000)
        }
    }

    @Test
    fun `top-down visual frame matches Flutter shadow geometry`() {
        val frame = NativeMapCursorVisualPolicy.frame(0.0)

        assertEquals(0.0, frame.pitchFraction, 0.0)
        assertEquals(1.0, frame.flatYScale, 0.0)
        assertEquals(36.0, frame.shadowCenterY, 0.0)
        assertEquals(1.0, frame.shadowScaleX, 0.0)
        assertEquals(0.5, frame.shadowScaleY, 0.0)
        assertEquals(0.35, frame.shadowAlpha, 0.0)
    }

    @Test
    fun `pitch frame clamps to Flutter maximum and flattens on the map plane`() {
        val frame = NativeMapCursorVisualPolicy.frame(90.0)

        assertEquals(1.0, frame.pitchFraction, 0.0)
        assertEquals(0.5, frame.flatYScale, 0.0000001)
        assertEquals(50.0, frame.shadowCenterY, 0.0)
        assertEquals(0.9, frame.shadowScaleX, 0.0)
        assertEquals(0.8, frame.shadowScaleY, 0.0)
        assertEquals(0.30, frame.shadowAlpha, 0.0000001)
        assertEquals(0.0, NativeMapCursorVisualPolicy.frame(-5.0).pitchFraction, 0.0)
    }

    @Test
    fun `HSL lightness treatment preserves alpha and moves brightness`() {
        val lighter = NativeMapCursorVisualPolicy.adjustLightness(0x80808080, 0.18)
        val darker = NativeMapCursorVisualPolicy.adjustLightness(0x80808080, -0.10)

        assertEquals(0x80, lighter ushr 24)
        assertEquals(0x80, darker ushr 24)
        assertTrue((lighter and 0xFF) > 0x80)
        assertTrue((darker and 0xFF) < 0x80)
        assertThrows(IllegalArgumentException::class.java) {
            NativeMapCursorVisualPolicy.frame(Double.NaN)
        }
    }
}
