package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRouteOverlaySessionTest {
    private val accent = 0xFF8B5CF6
    private val points = listOf(
        NativeMapPoint(latitude = 45.0, longitude = 9.0),
        NativeMapPoint(latitude = 45.0, longitude = 9.01),
        NativeMapPoint(latitude = 45.0, longitude = 9.02),
    )

    @Test
    fun `session starts empty and retains the theme accent`() {
        val state = NativeRouteOverlaySession(accent).state.value

        assertEquals(-1L, state.revision)
        assertEquals(accent, state.snapshot.accentArgb)
        assertEquals(
            "{\"type\":\"FeatureCollection\",\"features\":[]}",
            state.snapshot.activeGeoJson(),
        )
        assertEquals(0.0, state.totalDistanceMeters, 0.0)
    }

    @Test
    fun `new route projects preview runs and fences stale route revisions`() {
        val session = NativeRouteOverlaySession(accent)

        assertTrue(session.submitRoute(4, points, listOf(false, false, true)))
        assertFalse(session.submitRoute(3, points, listOf(true, true, true)))

        val state = session.state.value
        assertEquals(4L, state.revision)
        assertEquals(2, state.snapshot.payload().activeFeatureCount)
        assertEquals(0, state.snapshot.completedPoints.size)
        assertEquals(accent, state.snapshot.accentArgb)
    }

    @Test
    fun `progress interpolates cursor and separates completed and remaining route`() {
        val session = NativeRouteOverlaySession(accent)
        assertTrue(session.submitRoute(1, points, listOf(false, false, false)))
        val total = session.state.value.totalDistanceMeters

        assertTrue(session.updateProgress(1, total / 2.0, cursorRestricted = false))

        val state = session.state.value
        assertEquals(2, state.snapshot.completedPoints.size)
        assertEquals(1, state.snapshot.payload().activeFeatureCount)
        assertEquals(4, state.snapshot.payload().pointCount)
        assertEquals(total / 2.0, state.progressMeters, 0.000001)
        assertTrue(state.snapshot.completedGeoJson().contains("[9.01,45.0]"))
    }

    @Test
    fun `progress is monotonic and only matching route revision may update it`() {
        val session = NativeRouteOverlaySession(accent)
        assertTrue(session.submitRoute(1, points, listOf(false, false, false)))
        val total = session.state.value.totalDistanceMeters

        assertTrue(session.updateProgress(1, total * 0.75, cursorRestricted = false))
        assertFalse(session.updateProgress(1, total * 0.5, cursorRestricted = false))
        assertFalse(session.updateProgress(2, total, cursorRestricted = false))
        assertEquals(total * 0.75, session.state.value.progressMeters, 0.000001)
    }

    @Test
    fun `restriction refresh preserves progress and changes active classification`() {
        val session = NativeRouteOverlaySession(accent)
        assertTrue(session.submitRoute(1, points, listOf(false, false, false)))
        val total = session.state.value.totalDistanceMeters
        assertTrue(session.updateProgress(1, total * 0.25, cursorRestricted = true))

        assertTrue(session.updateRestrictions(1, listOf(false, true, true), cursorRestricted = true))

        val state = session.state.value
        assertEquals(total * 0.25, state.progressMeters, 0.000001)
        assertTrue(state.snapshot.activeGeoJson().contains("\"restricted\":true"))
    }

    @Test
    fun `terminal progress completes route and clear fences same revision`() {
        val session = NativeRouteOverlaySession(accent)
        assertTrue(session.submitRoute(7, points, listOf(false, false, false)))
        val total = session.state.value.totalDistanceMeters

        assertTrue(session.updateProgress(7, total * 2.0, cursorRestricted = false))
        assertEquals(0, session.state.value.snapshot.payload().activeFeatureCount)
        assertEquals(points.size, session.state.value.snapshot.completedPoints.size)
        assertTrue(session.clearRoute(7))
        assertFalse(session.submitRoute(7, points, listOf(false, false, false)))
        assertEquals(0, session.state.value.snapshot.payload().pointCount)
    }

    @Test
    fun `accent update is validated and does not discard route`() {
        val session = NativeRouteOverlaySession(accent)
        assertTrue(session.submitRoute(1, points, listOf(false, false, false)))

        assertTrue(session.updateAccent(0xFFF7931A))
        assertEquals(0xFFF7931A, session.state.value.snapshot.accentArgb)
        assertEquals(1, session.state.value.snapshot.payload().activeFeatureCount)
    }

    private fun NativeRouteOverlaySnapshot.activeGeoJson(): String =
        NativeRouteOverlayCompiler.compile(this).activeGeoJson

    private fun NativeRouteOverlaySnapshot.completedGeoJson(): String =
        NativeRouteOverlayCompiler.compile(this).completedGeoJson

    private fun NativeRouteOverlaySnapshot.payload(): NativeRouteOverlayPayload =
        NativeRouteOverlayCompiler.compile(this)
}
