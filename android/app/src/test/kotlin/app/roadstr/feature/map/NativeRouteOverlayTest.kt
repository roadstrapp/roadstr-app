package app.roadstr.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRouteOverlayTest {
    @Test
    fun `empty snapshot emits bounded empty feature collections`() {
        val payload = NativeRouteOverlayCompiler.compile(
            NativeRouteOverlaySnapshot.empty(accentArgb = 0xFF8B5CF6),
        )

        val empty = "{\"type\":\"FeatureCollection\",\"features\":[]}"
        assertEquals(empty, payload.activeGeoJson)
        assertEquals(empty, payload.completedGeoJson)
        assertEquals(empty, payload.alternativesGeoJson)
        assertEquals(0, payload.activeFeatureCount)
        assertEquals(0, payload.alternativeFeatureCount)
        assertEquals(0, payload.pointCount)
    }

    @Test
    fun `compiler emits unselected alternatives in stable route order`() {
        val first = listOf(NativeMapPoint(45.0, 9.0), NativeMapPoint(45.1, 9.1))
        val second = listOf(NativeMapPoint(46.0, 10.0), NativeMapPoint(46.1, 10.1))

        val payload = NativeRouteOverlayCompiler.compile(
            NativeRouteOverlaySnapshot(
                activeRuns = emptyList(),
                completedPoints = emptyList(),
                accentArgb = 0xFF8B5CF6,
                alternativeRoutes = listOf(first, second),
            ),
        )

        assertEquals(2, payload.alternativeFeatureCount)
        assertEquals(4, payload.pointCount)
        assertTrue(payload.alternativesGeoJson.contains("[9.0,45.0],[9.1,45.1]"))
        assertTrue(
            payload.alternativesGeoJson.indexOf("[9.0,45.0]") <
                payload.alternativesGeoJson.indexOf("[10.0,46.0]"),
        )
    }

    @Test
    fun `compiler preserves route order restriction and GeoJSON longitude latitude order`() {
        val payload = NativeRouteOverlayCompiler.compile(
            NativeRouteOverlaySnapshot(
                activeRuns = listOf(
                    NativeRouteRun(
                        points = listOf(
                            NativeMapPoint(latitude = 45.0, longitude = 9.0),
                            NativeMapPoint(latitude = 45.1, longitude = 9.1),
                        ),
                        restricted = false,
                    ),
                    NativeRouteRun(
                        points = listOf(
                            NativeMapPoint(latitude = 45.1, longitude = 9.1),
                            NativeMapPoint(latitude = 45.2, longitude = 9.2),
                        ),
                        restricted = true,
                    ),
                ),
                completedPoints = listOf(
                    NativeMapPoint(latitude = 44.9, longitude = 8.9),
                    NativeMapPoint(latitude = 45.0, longitude = 9.0),
                ),
                accentArgb = 0xFFF7931A,
            ),
        )

        assertEquals(2, payload.activeFeatureCount)
        assertEquals(6, payload.pointCount)
        assertTrue(payload.activeGeoJson.indexOf("\"restricted\":false") >= 0)
        assertTrue(
            payload.activeGeoJson.indexOf("\"restricted\":false") <
                payload.activeGeoJson.indexOf("\"restricted\":true"),
        )
        assertTrue(payload.activeGeoJson.contains("[9.0,45.0],[9.1,45.1]"))
        assertTrue(payload.completedGeoJson.contains("[8.9,44.9],[9.0,45.0]"))
        assertEquals(0xFFF7931A, payload.accentArgb)
    }

    @Test
    fun `compiler canonicalizes signed zero without changing finite precision`() {
        val payload = NativeRouteOverlayCompiler.compile(
            NativeRouteOverlaySnapshot(
                activeRuns = listOf(
                    NativeRouteRun(
                        listOf(
                            NativeMapPoint(-0.0, 0.0),
                            NativeMapPoint(0.000001, -0.000001),
                        ),
                        restricted = false,
                    ),
                ),
                completedPoints = emptyList(),
                accentArgb = 0xFF8B5CF6,
            ),
        )

        assertTrue(payload.activeGeoJson.contains("[0,0],[-1.0E-6,1.0E-6]"))
        assertFalse(payload.activeGeoJson.contains("-0.0"))
    }

    @Test
    fun `compiler rejects invalid coordinates singleton lines and ARGB overflow`() {
        val validLine = listOf(NativeMapPoint(45.0, 9.0), NativeMapPoint(45.1, 9.1))

        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteOverlayCompiler.compile(
                NativeRouteOverlaySnapshot(
                    activeRuns = listOf(NativeRouteRun(listOf(validLine.first()), false)),
                    completedPoints = emptyList(),
                    accentArgb = 0xFF8B5CF6,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteOverlayCompiler.compile(
                NativeRouteOverlaySnapshot(
                    activeRuns = listOf(
                        NativeRouteRun(
                            listOf(NativeMapPoint(Double.NaN, 9.0), validLine.last()),
                            false,
                        ),
                    ),
                    completedPoints = emptyList(),
                    accentArgb = 0xFF8B5CF6,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteOverlayCompiler.compile(
                NativeRouteOverlaySnapshot(
                    activeRuns = listOf(NativeRouteRun(validLine, false)),
                    completedPoints = emptyList(),
                    accentArgb = 0x1_0000_0000,
                ),
            )
        }
    }

    @Test
    fun `compiler enforces route run and point ceilings before allocation`() {
        val point = NativeMapPoint(45.0, 9.0)
        val line = listOf(point, point)

        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteOverlayCompiler.compile(
                NativeRouteOverlaySnapshot(
                    activeRuns = List(NativeRouteOverlayCompiler.MAX_ROUTE_RUNS + 1) {
                        NativeRouteRun(line, false)
                    },
                    completedPoints = emptyList(),
                    accentArgb = 0xFF8B5CF6,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteOverlayCompiler.compile(
                NativeRouteOverlaySnapshot(
                    activeRuns = emptyList(),
                    completedPoints = emptyList(),
                    accentArgb = 0xFF8B5CF6,
                    alternativeRoutes = List(
                        NativeRouteOverlayCompiler.MAX_ROUTE_ALTERNATIVES + 1,
                    ) { line },
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteOverlayCompiler.compile(
                NativeRouteOverlaySnapshot(
                    activeRuns = emptyList(),
                    completedPoints = List(NativeRouteOverlayCompiler.MAX_ROUTE_POINTS + 1) { point },
                    accentArgb = 0xFF8B5CF6,
                ),
            )
        }
    }

    @Test
    fun `native widths retain Flutter physical pixel correction and colors`() {
        assertEquals(6f, NativeRouteLayerMetrics.widthPixels(18.0, 3f), 0f)
        assertEquals(3f, NativeRouteLayerMetrics.widthPixels(9.0, 3f), 0f)
        assertEquals(7f, NativeRouteLayerMetrics.widthPixels(18.0, 2.625f), 0f)
        assertEquals(0xFFE53935, NativeMapRouteRenderer.ZTL_RED_ARGB)
        assertEquals(0xFF9E9E9E, NativeMapRouteRenderer.COMPLETED_GREY_ARGB)
        assertEquals(0x99757575, NativeMapRouteRenderer.ALTERNATIVE_GREY_ARGB)
        assertEquals(7f, NativeRouteLayerMetrics.ALTERNATIVE_LOGICAL_WIDTH.toFloat(), 0f)
        assertEquals(0.28, NativeMapRouteRenderer.HALO_ALPHA, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteLayerMetrics.widthPixels(Double.NaN, 3f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeRouteLayerMetrics.widthPixels(9.0, 0f)
        }
    }

    @Test
    fun `style generation gate rejects stale and post-dispose callbacks`() {
        val gate = NativeMapStyleGenerationGate()
        val first = gate.next()
        val second = gate.next()

        assertFalse(gate.accepts(first))
        assertTrue(gate.accepts(second))
        gate.dispose()
        assertFalse(gate.accepts(second))
        assertThrows(IllegalStateException::class.java) { gate.next() }
    }
}
