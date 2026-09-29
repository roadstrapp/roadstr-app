package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
    fun `alternative preview highlights one route and mutes the others`() {
        val session = NativeRouteOverlaySession(accent)
        val candidates = listOf(
            candidate(longitudeOffset = 0.0, restricted = false),
            candidate(longitudeOffset = 1.0, restricted = true),
            candidate(longitudeOffset = 2.0, restricted = false),
        )

        assertTrue(session.submitAlternatives(3, candidates, selectedIndex = 1))

        val state = session.state.value
        val payload = state.snapshot.payload()
        assertEquals(3L, state.revision)
        assertEquals(3, state.alternativeCount)
        assertEquals(1, state.selectedAlternativeIndex)
        assertEquals(2, payload.alternativeFeatureCount)
        assertTrue(payload.activeGeoJson.contains("[10.0,45.0]"))
        assertTrue(payload.activeGeoJson.contains("\"restricted\":true"))
        assertFalse(payload.alternativesGeoJson.contains("[10.0,45.0]"))
    }

    @Test
    fun `alternative selection rejects stale callbacks and changes highlighted geometry`() {
        val session = NativeRouteOverlaySession(accent)
        val candidates = listOf(
            candidate(longitudeOffset = 0.0, restricted = false),
            candidate(longitudeOffset = 1.0, restricted = false),
        )
        session.submitAlternatives(5, candidates, selectedIndex = 0)

        assertFalse(session.selectAlternative(4, selectedIndex = 1))
        assertTrue(session.selectAlternative(5, selectedIndex = 1))
        assertFalse(session.selectAlternative(5, selectedIndex = 1))

        val state = session.state.value
        assertEquals(1, state.selectedAlternativeIndex)
        assertTrue(state.snapshot.activeGeoJson().contains("[10.0,45.0]"))
        assertTrue(state.snapshot.alternativesGeoJson().contains("[9.0,45.0]"))
    }

    @Test
    fun `progress starts only after the selected alternative is committed`() {
        val session = NativeRouteOverlaySession(accent)
        session.submitAlternatives(
            8,
            listOf(
                candidate(longitudeOffset = 0.0, restricted = false),
                candidate(longitudeOffset = 1.0, restricted = false),
            ),
            selectedIndex = 1,
        )

        assertFalse(session.updateProgress(8, 1.0, cursorRestricted = false))
        assertFalse(session.commitSelectedAlternative(7))
        assertTrue(session.commitSelectedAlternative(8))
        assertTrue(session.updateProgress(8, 1.0, cursorRestricted = false))

        val state = session.state.value
        assertEquals(null, state.selectedAlternativeIndex)
        assertEquals(0, state.alternativeCount)
        assertEquals(0, state.snapshot.payload().alternativeFeatureCount)
        assertTrue(state.progressMeters > 0.0)
    }

    @Test
    fun `direct route replacement clears an alternative preview`() {
        val session = NativeRouteOverlaySession(accent)
        session.submitAlternatives(
            1,
            listOf(
                candidate(longitudeOffset = 0.0, restricted = false),
                candidate(longitudeOffset = 1.0, restricted = false),
            ),
            selectedIndex = 0,
        )

        assertTrue(session.submitRoute(2, points, listOf(false, false, false)))

        val state = session.state.value
        assertEquals(null, state.selectedAlternativeIndex)
        assertEquals(0, state.alternativeCount)
        assertEquals(0, state.snapshot.payload().alternativeFeatureCount)
    }

    @Test
    fun `session rejects pathological restriction runs before publishing`() {
        val session = NativeRouteOverlaySession(accent)
        val count = NativeRouteOverlayCompiler.MAX_ROUTE_RUNS * 2 + 2
        val densePoints = List(count) { index ->
            NativeMapPoint(latitude = 45.0, longitude = 9.0 + index * 0.000001)
        }

        assertThrows(IllegalArgumentException::class.java) {
            session.submitRoute(
                revision = 1,
                points = densePoints,
                restricted = List(count) { index -> index % 2 == 0 },
            )
        }
        assertEquals(-1L, session.state.value.revision)
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

    private fun NativeRouteOverlaySnapshot.alternativesGeoJson(): String =
        NativeRouteOverlayCompiler.compile(this).alternativesGeoJson

    private fun NativeRouteOverlaySnapshot.payload(): NativeRouteOverlayPayload =
        NativeRouteOverlayCompiler.compile(this)

    private fun candidate(longitudeOffset: Double, restricted: Boolean): NativeRouteCandidate =
        NativeRouteCandidate(
            points = listOf(
                NativeMapPoint(latitude = 45.0, longitude = 9.0 + longitudeOffset),
                NativeMapPoint(latitude = 45.1, longitude = 9.1 + longitudeOffset),
            ),
            restricted = listOf(restricted, restricted),
        )
}
