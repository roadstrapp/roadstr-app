package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeTransitOverlayTest {
    private val line = listOf(
        NativeMapPoint(45.0, 9.0),
        NativeMapPoint(45.1, 9.1),
    )

    private fun snapshot(legs: List<NativeTransitLeg>) = NativeTransitOverlaySnapshot(
        revision = 4,
        selectedItineraryIndex = 0,
        itineraryCount = 1,
        legs = legs,
        accentArgb = 0xFF8B5CF6,
        textSecondaryArgb = 0xFF757575,
    )

    @Test
    fun `mode catalogue preserves every Flutter wire name and street classification`() {
        assertEquals(21, NativeTransitMode.entries.size)
        assertEquals(NativeTransitMode.RegionalFastRail, NativeTransitMode.fromWire(" regional_fast_rail "))
        assertEquals(NativeTransitMode.Other, NativeTransitMode.fromWire("future-mode"))
        assertEquals(NativeTransitMode.Other, NativeTransitMode.fromWire(null))
        assertEquals(
            setOf(NativeTransitMode.Walk, NativeTransitMode.Bike, NativeTransitMode.Car),
            NativeTransitMode.entries.filterNot { it.isTransit }.toSet(),
        )
    }

    @Test
    fun `compiler emits ordered per-leg operator fallback and walking colors`() {
        val payload = NativeTransitOverlayCompiler.compile(
            snapshot(
                listOf(
                    NativeTransitLeg(NativeTransitMode.Walk, line),
                    NativeTransitLeg(NativeTransitMode.Bus, line, 0xFF123456),
                    NativeTransitLeg(NativeTransitMode.Rail, line),
                ),
            ),
        )

        assertEquals(3, payload.featureCount)
        assertEquals(6, payload.pointCount)
        assertTrue(payload.geoJson.contains("\"color\":\"rgba(117,117,117,0.7)\",\"transit\":false"))
        assertTrue(payload.geoJson.contains("\"color\":\"rgba(18,52,86,1)\",\"transit\":true"))
        assertTrue(payload.geoJson.contains("\"color\":\"rgba(139,92,246,1)\",\"transit\":true"))
        assertTrue(
            payload.geoJson.indexOf("rgba(117,117,117,0.7)") <
                payload.geoJson.indexOf("rgba(18,52,86,1)"),
        )
    }

    @Test
    fun `compiler skips non-drawable legs but accounts for their bounded points`() {
        val payload = NativeTransitOverlayCompiler.compile(
            snapshot(
                listOf(
                    NativeTransitLeg(NativeTransitMode.Walk, emptyList()),
                    NativeTransitLeg(NativeTransitMode.Bus, listOf(line.first())),
                    NativeTransitLeg(NativeTransitMode.Ferry, line),
                ),
            ),
        )

        assertEquals(1, payload.featureCount)
        assertEquals(3, payload.pointCount)
        assertEquals(1, "\"type\":\"Feature\"".toRegex().findAll(payload.geoJson).count())
    }

    @Test
    fun `compiler canonicalizes signed zero and preserves alpha`() {
        val payload = NativeTransitOverlayCompiler.compile(
            snapshot(
                listOf(
                    NativeTransitLeg(
                        NativeTransitMode.Tram,
                        listOf(NativeMapPoint(-0.0, 0.0), NativeMapPoint(0.1, -0.1)),
                        routeColorArgb = 0x80112233,
                    ),
                ),
            ),
        )

        assertTrue(payload.geoJson.contains("rgba(17,34,51,0.501961)"))
        assertTrue(payload.geoJson.contains("[0,0],[-0.1,0.1]"))
        assertFalse(payload.geoJson.contains("-0.0"))
    }

    @Test
    fun `compiler rejects invalid coordinates colors and metadata bounds`() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeTransitOverlayCompiler.compile(
                snapshot(
                    listOf(
                        NativeTransitLeg(
                            NativeTransitMode.Bus,
                            listOf(NativeMapPoint(91.0, 9.0), line.last()),
                        ),
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeTransitOverlayCompiler.compile(
                snapshot(listOf(NativeTransitLeg(NativeTransitMode.Bus, line, 0x1_0000_0000))),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeTransitOverlayCompiler.compile(
                snapshot(emptyList()).copy(
                    itineraryCount = NativeTransitOverlayCompiler.MAX_ITINERARIES + 1,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeTransitOverlayCompiler.compile(
                snapshot(emptyList()),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeTransitOverlayCompiler.compile(
                snapshot(
                    List(NativeTransitOverlayCompiler.MAX_LEGS_PER_ITINERARY + 1) {
                        NativeTransitLeg(NativeTransitMode.Bus, line)
                    },
                ),
            )
        }
    }

    @Test
    fun `renderer constants retain Flutter transit widths alpha and density correction`() {
        assertEquals(7.0, NativeTransitLayerMetrics.TRANSIT_LOGICAL_WIDTH, 0.0)
        assertEquals(4.0, NativeTransitLayerMetrics.STREET_LOGICAL_WIDTH, 0.0)
        assertEquals(0.7, NativeMapTransitRenderer.STREET_ALPHA, 0.0)
        assertEquals(2f, NativeTransitLayerMetrics.widthPixels(7.0, 3f), 0f)
        assertEquals(1f, NativeTransitLayerMetrics.widthPixels(4.0, 3f), 0f)
        assertEquals("roadstr-transit-source", NativeMapTransitRenderer.SOURCE_ID)
        assertEquals("roadstr-transit-legs", NativeMapTransitRenderer.LAYER_ID)
    }
}
