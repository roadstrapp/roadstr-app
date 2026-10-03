package app.roadstr.feature.voice

import app.roadstr.feature.route.NativeRouteTransportMode
import kotlin.math.roundToInt

data class NativeNavigationVoiceThresholds(
    val farMeters: Int,
    val nearMeters: Int,
)

/** Deterministic counterpart of Flutter's speed- and mode-aware cue policy. */
object NativeNavigationGuidance {
    fun thresholds(
        speedKilometresPerHour: Double,
        mode: NativeRouteTransportMode,
    ): NativeNavigationVoiceThresholds {
        if (mode == NativeRouteTransportMode.Walking) {
            return NativeNavigationVoiceThresholds(farMeters = 60, nearMeters = 15)
        }
        if (mode == NativeRouteTransportMode.Cycling) {
            return NativeNavigationVoiceThresholds(farMeters = 150, nearMeters = 30)
        }
        val speed = speedKilometresPerHour
            .takeIf(Double::isFinite)
            ?.coerceIn(0.0, 160.0)
            ?: 0.0
        val roadProgress = ((speed - 45.0) / 55.0).coerceIn(0.0, 1.0)
        val highSpeedProgress = ((speed - 100.0) / 60.0).coerceIn(0.0, 1.0)
        return NativeNavigationVoiceThresholds(
            farMeters = roundToTen(150.0 + 650.0 * roadProgress),
            nearMeters = roundToTen(40.0 + 80.0 * roadProgress + 140.0 * highSpeedProgress),
        )
    }

    fun spokenDistanceMeters(remainingMeters: Double, imminentBelowMeters: Int): Int {
        if (!remainingMeters.isFinite() || remainingMeters <= imminentBelowMeters) return 0
        return (remainingMeters / 50.0).roundToInt() * 50
    }

    private fun roundToTen(value: Double): Int = (value / 10.0).roundToInt() * 10
}
