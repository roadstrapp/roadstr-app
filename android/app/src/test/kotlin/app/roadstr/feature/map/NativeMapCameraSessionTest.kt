package app.roadstr.feature.map

import app.roadstr.core.map.CameraFollowEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMapCameraSessionTest {
    private val point = NativeMapPoint(latitude = 45.0, longitude = 9.0)

    @Test
    fun `session starts in dormant follow mode without a command`() {
        val state = NativeMapCameraSession().state.value

        assertTrue(state.followEnabled)
        assertTrue(state.headingUp)
        assertFalse(state.navigating)
        assertFalse(state.frameActive)
        assertEquals(-1L, state.lastFixSequence)
        assertEquals(null, state.command)
    }

    @Test
    fun `fixes are validated and stale sequences are ignored`() {
        val session = NativeMapCameraSession()

        assertTrue(session.submitFix(1, point, 90.0, 10.0, 1_000))
        assertFalse(session.submitFix(1, point, 180.0, 10.0, 1_001))
        assertThrows(IllegalArgumentException::class.java) {
            session.submitFix(
                2,
                NativeMapPoint(Double.NaN, 9.0),
                0.0,
                0.0,
                1_002,
            )
        }
        assertEquals(1L, session.state.value.lastFixSequence)
    }

    @Test
    fun `first free-drive frame emits exact move and then sleeps`() {
        val session = NativeMapCameraSession()
        assertTrue(session.submitFix(1, point, 90.0, 0.0, 1_000))

        assertTrue(session.advanceFrame(1_000))

        val state = session.state.value
        val command = requireNotNull(state.command)
        assertEquals(point, command.center)
        assertEquals(17.0, command.zoom, 0.0)
        assertEquals(90.0, command.bearingDegrees, 0.0)
        assertEquals(40.0, command.pitchDegrees, 0.0)
        assertEquals(NativeMapCameraMotion.Move, command.motion)
        assertFalse(state.frameActive)
    }

    @Test
    fun `configuration-only pitch change emits one fresh command`() {
        val session = NativeMapCameraSession()
        session.submitFix(1, point, 90.0, 0.0, 1_000)
        session.advanceFrame(1_000)

        assertTrue(
            session.configure(
                headingUp = true,
                navigating = false,
                zoom = 17.0,
                pitchDegrees = 45.0,
                screenHeightPixels = 800.0,
            ),
        )
        assertTrue(session.advanceFrame(1_033))

        val state = session.state.value
        assertEquals(45.0, requireNotNull(state.command).pitchDegrees, 0.0)
        assertEquals(2L, state.command?.sequence)
        assertFalse(state.frameActive)
    }

    @Test
    fun `heading-up navigation keeps fix visible with viewport padding and active frames`() {
        val session = NativeMapCameraSession()
        assertTrue(
            session.configure(
                headingUp = true,
                navigating = true,
                zoom = 17.0,
                pitchDegrees = NativeMapCameraSession.NAVIGATION_PITCH,
                screenHeightPixels = 800.0,
                measuredForwardShiftMeters = 100.0,
            ),
        )
        assertTrue(session.submitFix(1, point, 0.0, 0.0, 1_000))
        assertTrue(session.advanceFrame(1_000))

        val state = session.state.value
        val command = requireNotNull(state.command)
        assertEquals(point.latitude, command.center.latitude, 0.0000001)
        assertEquals(point.longitude, command.center.longitude, 0.0000001)
        assertEquals(55.0, command.pitchDegrees, 0.0)
        // 45% of the 800 px screen: the vehicle at 72.5% of the height, like the Flutter map.
        assertEquals(360.0, command.paddingTopPixels, 0.0)
        assertEquals(0.0, command.paddingBottomPixels, 0.0)
        assertTrue(state.frameActive)
    }

    @Test
    fun `north-up navigation preserves fix center and zero bearing`() {
        val session = NativeMapCameraSession()
        assertTrue(
            session.configure(
                headingUp = false,
                navigating = true,
                zoom = 17.0,
                pitchDegrees = NativeMapCameraSession.NAVIGATION_PITCH,
                screenHeightPixels = 800.0,
            ),
        )
        assertTrue(session.submitFix(1, point, 271.0, 0.0, 1_000))
        assertTrue(session.advanceFrame(1_000))

        val command = requireNotNull(session.state.value.command)
        assertEquals(point, command.center)
        assertEquals(0.0, command.bearingDegrees, 0.0)
    }

    @Test
    fun `dead reckoning stops after three seconds`() {
        val session = NativeMapCameraSession()
        session.configure(
            headingUp = false,
            navigating = true,
            zoom = 17.0,
            pitchDegrees = 55.0,
            screenHeightPixels = 800.0,
        )
        session.submitFix(1, point, 90.0, 10.0, 1_000)

        assertTrue(session.advanceFrame(10_000))

        val expected = CameraFollowEasing.shiftByMeters(45.0, 9.0, 90.0, 30.0)
        val command = requireNotNull(session.state.value.command)
        assertEquals(expected.latitude, command.center.latitude, 0.0000001)
        assertEquals(expected.longitude, command.center.longitude, 0.0000001)
    }

    @Test
    fun `gesture detaches follow and explicit recenter emits eased command`() {
        val session = NativeMapCameraSession()
        session.submitFix(1, point, 45.0, 0.0, 1_000)
        session.advanceFrame(1_000)

        assertTrue(session.onUserGesture())
        assertFalse(session.state.value.followEnabled)
        assertTrue(
            session.submitFix(
                2,
                NativeMapPoint(45.001, 9.001),
                45.0,
                0.0,
                2_000,
            ),
        )
        assertFalse(session.advanceFrame(2_000))
        assertTrue(session.recenter(2_000))

        val state = session.state.value
        val command = requireNotNull(state.command)
        assertTrue(state.followEnabled)
        assertEquals(NativeMapCameraMotion.Ease, command.motion)
        assertEquals(NativeMapCameraSession.RECENTER_DURATION_MILLIS, command.durationMillis)
        assertEquals(2L, command.sequence)
    }

    @Test
    fun `follow easing retains the ninety degree per second turn cap`() {
        val session = NativeMapCameraSession()
        session.configure(
            headingUp = true,
            navigating = true,
            zoom = 17.0,
            pitchDegrees = 55.0,
            screenHeightPixels = 800.0,
            measuredForwardShiftMeters = 0.0,
        )
        session.submitFix(1, point, 0.0, 0.0, 1_000)
        session.advanceFrame(1_000)
        session.submitFix(2, point, 180.0, 0.0, 1_100)

        assertTrue(session.advanceFrame(1_100))

        assertEquals(9.0, requireNotNull(session.state.value.command).bearingDegrees, 0.0001)
    }

    @Test
    fun `the vehicle is drawn at the fix and then advances with each camera frame`() {
        val session = NativeMapCameraSession()
        session.configure(true, true, 17.0, 55.0, 2_400.0)
        // Heading due north at 20 m/s.
        assertTrue(session.submitFix(1, point, 0.0, 20.0, 1_000))
        assertEquals(point, session.state.value.displayPoint)
        val first = session.state.value.displaySequence

        assertTrue(session.advanceFrame(1_000))
        assertEquals(point, session.state.value.displayPoint)

        // Half a second later the camera glides ahead, and the cursor with it.
        assertTrue(session.advanceFrame(1_500))
        val drawn = requireNotNull(session.state.value.displayPoint)
        assertTrue(drawn.latitude > point.latitude)
        assertEquals(point.longitude, drawn.longitude, 1e-6)
        assertTrue(session.state.value.displaySequence > first)
    }

    @Test
    fun `a stopped vehicle is never advanced`() {
        val session = NativeMapCameraSession()
        assertTrue(session.submitFix(1, point, 90.0, 0.0, 1_000))
        session.advanceFrame(1_000)
        session.advanceFrame(3_000)

        assertEquals(point, session.state.value.displayPoint)
    }

    @Test
    fun `the compass turns a standing heading-up map and null gives the bearing back to the fix`() {
        val session = NativeMapCameraSession()
        assertTrue(session.submitFix(1, point, 90.0, 0.0, 1_000))
        session.advanceFrame(1_000)
        assertEquals(90.0, requireNotNull(session.state.value.command).bearingDegrees, 0.0)

        assertTrue(session.setCompassHeading(200.0))
        assertFalse(session.setCompassHeading(200.0))
        assertTrue(session.state.value.frameActive)
        session.recenter(2_000)
        assertEquals(200.0, requireNotNull(session.state.value.command).bearingDegrees, 0.0)

        assertTrue(session.setCompassHeading(null))
        session.recenter(3_000)
        assertEquals(90.0, requireNotNull(session.state.value.command).bearingDegrees, 0.0)
    }

    @Test
    fun `north-up ignores the compass`() {
        val session = NativeMapCameraSession()
        session.configure(false, false, 17.0, 40.0, 2_400.0)
        session.submitFix(1, point, 90.0, 0.0, 1_000)
        session.setCompassHeading(123.0)
        session.recenter(1_000)

        assertEquals(0.0, requireNotNull(session.state.value.command).bearingDegrees, 0.0)
    }

    @Test
    fun `navigation keeps the vehicle low using the measured padding, and only heading-up`() {
        val session = NativeMapCameraSession()
        session.configure(true, true, 17.0, 55.0, 2_000.0)
        session.submitFix(1, point, 0.0, 0.0, 1_000)
        session.recenter(1_000)
        // Before the bottom panel is measured: the fixed fallback of 45% of the height.
        assertEquals(900.0, requireNotNull(session.state.value.command).paddingTopPixels, 0.0)

        // Measured: the padding is exactly what the shell worked out.
        assertTrue(session.setNavigationTopPadding(1_100.0))
        assertFalse(session.setNavigationTopPadding(1_100.0))
        session.advanceFrame(1_100)
        assertEquals(1_100.0, requireNotNull(session.state.value.command).paddingTopPixels, 0.0)

        // North-up in navigation keeps the vehicle in the middle, as Flutter does.
        session.configure(false, true, 17.0, 55.0, 2_000.0)
        session.recenter(2_000)
        assertEquals(0.0, requireNotNull(session.state.value.command).paddingTopPixels, 0.0)
    }

    @Test
    fun `a free-drive map never pads the vehicle off centre`() {
        val session = NativeMapCameraSession()
        session.setNavigationTopPadding(1_100.0)
        session.submitFix(1, point, 0.0, 0.0, 1_000)
        session.recenter(1_000)

        assertEquals(0.0, requireNotNull(session.state.value.command).paddingTopPixels, 0.0)
    }

    @Test
    fun `an invalid padding is ignored`() {
        val session = NativeMapCameraSession()
        assertFalse(session.setNavigationTopPadding(Double.NaN))
        assertFalse(session.setNavigationTopPadding(-5.0))
    }
}
