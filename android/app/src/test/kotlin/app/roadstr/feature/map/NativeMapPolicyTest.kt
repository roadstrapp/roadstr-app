package app.roadstr.feature.map

import app.roadstr.core.map.ViewportWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMapPolicyTest {
    @Test
    fun `light style preserves raster source and attribution`() {
        val style = NativeMapStyle.rasterStyle(dark = false)

        assertTrue(style.contains("\"version\": 8"))
        assertTrue(style.contains(NativeMapStyle.DEFAULT_TILE_URL))
        assertTrue(style.contains("\"tileSize\": 256"))
        assertTrue(style.contains(NativeMapStyle.OSM_ATTRIBUTION))
        assertFalse(style.contains("raster-hue-rotate"))
    }

    @Test
    fun `dark style carries the exact raster recoloring policy`() {
        val style = NativeMapStyle.rasterStyle(dark = true)

        assertTrue(style.contains("\"raster-hue-rotate\": 180"))
        assertTrue(style.contains("\"raster-brightness-min\": 1"))
        assertTrue(style.contains("\"raster-brightness-max\": 0"))
        assertTrue(style.contains("\"raster-saturation\": -0.5"))
        assertTrue(style.contains("\"raster-contrast\": 0.1"))
    }

    @Test
    fun `tile policy rejects injection malformed and non-loopback cleartext`() {
        assertEquals(
            NativeTileUrlDecision.Accepted,
            NativeTileUrlPolicy.decision("https://tiles.example/{z}/{x}/{y}.png"),
        )
        assertEquals(
            NativeTileUrlDecision.CleartextRejected,
            NativeTileUrlPolicy.decision("http://tiles.example/{z}/{x}/{y}.png"),
        )
        assertEquals(
            NativeTileUrlDecision.Accepted,
            NativeTileUrlPolicy.decision("http://10.0.2.2:8080/{z}/{x}/{y}.png"),
        )
        assertEquals(
            NativeTileUrlDecision.Invalid,
            NativeTileUrlPolicy.decision("https://tiles.example/{z}/{x}/\"bad\".png"),
        )
        assertEquals(
            NativeTileUrlDecision.Invalid,
            NativeTileUrlPolicy.decision("https://tiles.example/{z}/{x}.png"),
        )
    }

    @Test
    fun `style escapes accepted tile template before JSON insertion`() {
        val style = NativeMapStyle.rasterStyle(
            dark = false,
            tileUrl = "https://tiles.example/{z}/{x}/{y}.png?token=a%20b",
        )

        assertTrue(style.contains("token=a%20b"))
        assertFalse(style.contains("\\\"bad"))
    }

    @Test
    fun `route runs share transition point and preserve classification`() {
        val points = listOf(
            NativeMapPoint(45.0, 9.0),
            NativeMapPoint(45.1, 9.1),
            NativeMapPoint(45.2, 9.2),
            NativeMapPoint(45.3, 9.3),
        )

        val runs = NativeMapOverlayPolicy.splitRouteByRestriction(
            points,
            listOf(false, false, true, true),
        )

        assertEquals(2, runs.size)
        assertEquals(listOf(points[0], points[1]), runs[0].points)
        assertEquals(listOf(points[1], points[2], points[3]), runs[1].points)
        assertFalse(runs[0].restricted)
        assertTrue(runs[1].restricted)
    }

    @Test
    fun `short routes produce no drawable runs and mismatched flags fail closed`() {
        assertTrue(
            NativeMapOverlayPolicy.splitRouteByRestriction(
                listOf(NativeMapPoint(45.0, 9.0)),
                listOf(false),
            ).isEmpty(),
        )
        var failed = false
        try {
            NativeMapOverlayPolicy.splitRouteByRestriction(
                listOf(NativeMapPoint(45.0, 9.0), NativeMapPoint(45.1, 9.1)),
                listOf(false),
            )
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `marker culling applies zoom and viewport while retaining order`() {
        val window = ViewportWindow.create(
            centerLatitude = 45.0,
            centerLongitude = 9.0,
            zoom = 17.0,
            bearingDegrees = 0.0,
            pitchDegrees = 0.0,
            screenWidthDp = 392.0,
            screenHeightDp = 800.0,
            margin = 1.0,
            extraMeters = 0.0,
        )
        val markers = listOf(
            NativeMapMarker("first", NativeMapPoint(45.0, 9.0)),
            NativeMapMarker("too-far", NativeMapPoint(46.0, 9.0)),
            NativeMapMarker("zoom-gated", NativeMapPoint(45.0, 9.0), minimumZoom = 18.0),
            NativeMapMarker("last", NativeMapPoint(45.0001, 9.0)),
        )

        assertEquals(
            listOf("first", "last"),
            NativeMapOverlayPolicy.visibleMarkers(markers, window, zoom = 17.0).map { it.id },
        )
        assertEquals(
            listOf("first", "too-far", "last"),
            NativeMapOverlayPolicy.visibleMarkers(markers, viewport = null, zoom = 17.0)
                .map { it.id },
        )
    }
}
