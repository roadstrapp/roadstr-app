package app.roadstr.feature.voice

import app.roadstr.feature.route.NativeRouteTransportMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeNavigationGuidanceTest {
    @Test
    fun `walking and cycling use their fixed safety windows`() {
        assertEquals(
            NativeNavigationVoiceThresholds(60, 15),
            NativeNavigationGuidance.thresholds(120.0, NativeRouteTransportMode.Walking),
        )
        assertEquals(
            NativeNavigationVoiceThresholds(150, 30),
            NativeNavigationGuidance.thresholds(120.0, NativeRouteTransportMode.Cycling),
        )
    }

    @Test
    fun `driving windows scale through motorway speed`() {
        assertEquals(
            NativeNavigationVoiceThresholds(150, 40),
            NativeNavigationGuidance.thresholds(45.0, NativeRouteTransportMode.Driving),
        )
        assertEquals(
            NativeNavigationVoiceThresholds(800, 120),
            NativeNavigationGuidance.thresholds(100.0, NativeRouteTransportMode.Driving),
        )
        assertEquals(
            NativeNavigationVoiceThresholds(800, 260),
            NativeNavigationGuidance.thresholds(160.0, NativeRouteTransportMode.Driving),
        )
    }

    @Test
    fun `spoken distance uses live remaining route distance`() {
        assertEquals(200, NativeNavigationGuidance.spokenDistanceMeters(176.0, 40))
        assertEquals(0, NativeNavigationGuidance.spokenDistanceMeters(40.0, 40))
        assertEquals(0, NativeNavigationGuidance.spokenDistanceMeters(Double.NaN, 40))
    }
}
