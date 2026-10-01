package app.roadstr.feature.map

import app.roadstr.feature.settings.NativeSettingsMapEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMapEngineCompatibilityTest {
    @Test
    fun `persisted engine values preserve the Flutter choice and safe default`() {
        assertEquals(NativeMapEngine.MapLibre, NativeMapEngine.fromStorage(null))
        assertEquals(NativeMapEngine.MapLibre, NativeMapEngine.fromStorage("unknown"))
        assertEquals(NativeMapEngine.MapLibre, NativeMapEngine.fromStorage("maplibre"))
        assertEquals(NativeMapEngine.LegacyRaster, NativeMapEngine.fromStorage("osm"))
    }

    @Test
    fun `native settings and renderer storage values cannot drift`() {
        assertEquals(
            NativeSettingsMapEngine.entries.map { it.storageValue },
            NativeMapEngine.entries.map { it.storageValue },
        )
    }

    @Test
    fun `profiles retain both shipped camera and zoom semantics`() {
        val modern = NativeMapEngineCompatibility.mapLibre
        val legacy = NativeMapEngineCompatibility.legacyRaster

        assertEquals(17.0, modern.initialZoom, 0.0)
        assertEquals(40.0, modern.initialPitchDegrees, 0.0)
        assertEquals(NativeMapHostContract.INITIAL_ZOOM, modern.initialZoom, 0.0)
        assertEquals(NativeMapHostContract.INITIAL_TILT, modern.initialPitchDegrees, 0.0)
        assertEquals(0.0, modern.minimumZoom, 0.0)
        assertEquals(25.5, modern.maximumZoom, 0.0)
        assertTrue(modern.tiltGesturesEnabled)

        assertEquals(6.0, legacy.initialZoom, 0.0)
        assertEquals(2.0, legacy.minimumZoom, 0.0)
        assertEquals(19.0, legacy.maximumZoom, 0.0)
        assertEquals(0.0, legacy.initialPitchDegrees, 0.0)
        assertEquals(0.0, legacy.maximumPitchDegrees, 0.0)
        assertFalse(legacy.tiltGesturesEnabled)
    }

    @Test
    fun `legacy profile flattens pitch and clamps zoom without changing bearing`() {
        val input = NativeMapCameraCommand(
            sequence = 7,
            center = NativeMapPoint(45.0, 9.0),
            zoom = 21.0,
            bearingDegrees = 123.0,
            pitchDegrees = 55.0,
            motion = NativeMapCameraMotion.Ease,
            durationMillis = 450,
        )

        val constrained = NativeMapEngineCompatibility.legacyRaster.constrain(input)

        assertEquals(19.0, constrained.zoom, 0.0)
        assertEquals(0.0, constrained.pitchDegrees, 0.0)
        assertEquals(123.0, constrained.bearingDegrees, 0.0)
        assertEquals(input.center, constrained.center)
        assertEquals(input.sequence, constrained.sequence)
    }

    @Test
    fun `both modes share admitted custom raster and dark recoloring`() {
        val tileUrl = "https://tiles.example/{z}/{x}/{y}.png"
        val modern = NativeMapEngineCompatibility.rasterStyle(
            NativeMapEngine.MapLibre,
            dark = true,
            tileUrl = tileUrl,
        )
        val legacy = NativeMapEngineCompatibility.rasterStyle(
            NativeMapEngine.LegacyRaster,
            dark = true,
            tileUrl = tileUrl,
        )

        assertEquals(modern, legacy)
        assertTrue(legacy.contains(tileUrl))
        assertTrue(legacy.contains("raster-hue-rotate"))
    }
}
