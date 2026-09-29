package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTransitOverlaySessionTest {
    private val accent = 0xFF8B5CF6
    private val secondary = 0xFF757575

    private fun leg(
        mode: NativeTransitMode = NativeTransitMode.Bus,
        color: Long? = 0xFF123456,
    ) = NativeTransitLeg(
        mode = mode,
        points = listOf(NativeMapPoint(45.0, 9.0), NativeMapPoint(45.1, 9.1)),
        routeColorArgb = color,
    )

    @Test
    fun `session starts empty with unfenced revision and theme`() {
        val state = NativeTransitOverlaySession(accent, secondary).state.value

        assertEquals(NativeTransitOverlaySession.NO_TRANSIT_REVISION, state.revision)
        assertEquals(0, state.itineraryCount)
        assertEquals(null, state.selectedItineraryIndex)
        assertTrue(state.legs.isEmpty())
        assertEquals(accent, state.accentArgb)
        assertEquals(secondary, state.textSecondaryArgb)
    }

    @Test
    fun `newer plans publish selected legs and stale replacements are ignored`() {
        val session = NativeTransitOverlaySession(accent, secondary)
        val sourcePoints = leg().points.toMutableList()
        val source = NativeTransitItinerary(listOf(leg().copy(points = sourcePoints)))

        assertTrue(session.submitItineraries(2, listOf(source)))
        sourcePoints.clear()
        assertFalse(session.submitItineraries(2, listOf(NativeTransitItinerary(listOf(leg())))))
        assertFalse(session.submitItineraries(1, listOf(NativeTransitItinerary(listOf(leg())))))
        assertEquals(2, session.state.value.legs.first().points.size)
        assertNotSame(sourcePoints, session.state.value.legs.first().points)
    }

    @Test
    fun `selection changes geometry only for the current revision`() {
        val session = NativeTransitOverlaySession(accent, secondary)
        val bus = NativeTransitItinerary(listOf(leg(NativeTransitMode.Bus)))
        val ferry = NativeTransitItinerary(listOf(leg(NativeTransitMode.Ferry)))
        session.submitItineraries(7, listOf(bus, ferry))

        assertFalse(session.selectItinerary(6, 1))
        assertFalse(session.selectItinerary(7, 2))
        assertTrue(session.selectItinerary(7, 1))
        assertFalse(session.selectItinerary(7, 1))
        assertEquals(1, session.state.value.selectedItineraryIndex)
        assertEquals(NativeTransitMode.Ferry, session.state.value.legs.single().mode)
    }

    @Test
    fun `clear removes geometry and fences late provider callbacks`() {
        val session = NativeTransitOverlaySession(accent, secondary)
        session.submitItineraries(2, listOf(NativeTransitItinerary(listOf(leg()))))

        assertTrue(session.clear(4))
        assertFalse(session.submitItineraries(3, listOf(NativeTransitItinerary(listOf(leg())))))
        assertEquals(4, session.state.value.revision)
        assertEquals(0, session.state.value.itineraryCount)
        assertTrue(session.state.value.legs.isEmpty())
    }

    @Test
    fun `theme updates recolor selected geometry without changing provider state`() {
        val session = NativeTransitOverlaySession(accent, secondary)
        session.submitItineraries(
            3,
            listOf(NativeTransitItinerary(listOf(leg(NativeTransitMode.Walk, null)))),
        )

        assertTrue(session.updateTheme(0xFFF7931A, 0xFF8888A8))
        assertFalse(session.updateTheme(0xFFF7931A, 0xFF8888A8))
        assertEquals(3, session.state.value.revision)
        assertEquals(0xFFF7931A, session.state.value.accentArgb)
        assertEquals(0xFF8888A8, session.state.value.textSecondaryArgb)
        assertEquals(1, NativeTransitOverlayCompiler.compile(session.state.value).featureCount)
    }

    @Test
    fun `submission validates revisions choices geometry colors and collection bounds`() {
        val session = NativeTransitOverlaySession(accent, secondary)
        val itinerary = NativeTransitItinerary(listOf(leg()))

        assertThrows(IllegalArgumentException::class.java) {
            session.submitItineraries(-1, listOf(itinerary))
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.submitItineraries(1, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.submitItineraries(1, listOf(NativeTransitItinerary(emptyList())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.submitItineraries(1, listOf(itinerary), selectedIndex = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.submitItineraries(
                1,
                List(NativeTransitOverlayCompiler.MAX_ITINERARIES + 1) { itinerary },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.submitItineraries(
                1,
                listOf(
                    NativeTransitItinerary(
                        List(NativeTransitOverlayCompiler.MAX_LEGS_PER_ITINERARY + 1) { leg() },
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.submitItineraries(
                1,
                listOf(NativeTransitItinerary(listOf(leg(color = 0x1_0000_0000)))),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            session.submitItineraries(
                1,
                listOf(
                    NativeTransitItinerary(
                        listOf(
                            leg().copy(
                                points = listOf(
                                    NativeMapPoint(45.0, Double.NaN),
                                    NativeMapPoint(45.1, 9.1),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}
