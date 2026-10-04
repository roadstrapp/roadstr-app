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
        assertEquals(160.0, command.paddingTopPixels, 0.0)
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
}
