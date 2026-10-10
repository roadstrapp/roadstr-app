package app.roadstr.feature.performance

import app.roadstr.feature.map.NativeMapEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class NativePerformanceProfileTest {
    @Test
    fun `third profile is the default and all runtime targets are exact`() {
        assertEquals(NativePerformanceProfile.ThreeDEco, NativePerformanceProfile.Default)
        assertEquals(
            listOf(
                listOf(1, 30, 15, 1, 5),
                listOf(2, 60, 30, 1, 10),
                listOf(3, 45, 45, 1, 15),
                listOf(4, 60, 60, 2, 20),
            ),
            NativePerformanceProfile.entries.map {
                listOf(it.level, it.maximumFps, it.trackingHertz, it.navigationGpsHertz, it.orientationHertz)
            },
        )
        assertEquals(NativeMapEngine.LegacyRaster, NativePerformanceProfile.Eco.mapEngine)
        assertEquals(NativeMapEngine.MapLibre, NativePerformanceProfile.ThreeDEco.mapEngine)
    }

    @Test
    fun `legacy engines migrate without changing dimensionality`() {
        assertEquals(NativePerformanceProfile.Balanced, NativePerformanceProfile.fromStorage(null, "osm"))
        assertEquals(NativePerformanceProfile.ThreeDEco, NativePerformanceProfile.fromStorage(null, "maplibre"))
        assertEquals(NativePerformanceProfile.Eco, NativePerformanceProfile.fromStorage("eco", "maplibre"))
    }

    @Test
    fun `3d eco GPS boost uses hysteresis while performance stays at two hertz`() {
        val policy = NativePerformanceRuntimePolicy
        assertEquals(1_000L, policy.gpsIntervalMillis(NativePerformanceProfile.ThreeDEco, true, 3.0, 5.0, false))
        assertEquals(500L, policy.gpsIntervalMillis(NativePerformanceProfile.ThreeDEco, true, 10.0, 5.0, false))
        assertEquals(500L, policy.gpsIntervalMillis(NativePerformanceProfile.ThreeDEco, true, 5.0, 5.0, true))
        assertEquals(1_000L, policy.gpsIntervalMillis(NativePerformanceProfile.ThreeDEco, true, 3.0, 5.0, true))
        assertEquals(500L, policy.gpsIntervalMillis(NativePerformanceProfile.Performance, true, 0.0, 5.0, false))
        assertEquals(1_000L, policy.gpsIntervalMillis(NativePerformanceProfile.Performance, false, 20.0, 50.0, true))
    }
}
