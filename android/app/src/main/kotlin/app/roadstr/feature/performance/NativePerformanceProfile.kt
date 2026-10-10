package app.roadstr.feature.performance

import androidx.annotation.StringRes
import app.roadstr.R
import app.roadstr.feature.map.NativeMapEngine
import kotlin.math.roundToLong

enum class NativePerformanceIcon {
    BatteryFull,
    BatteryMedium,
    Layers,
    Bolt,
}

/**
 * Single source of truth for Roadstr's manually selected energy/performance policy.
 *
 * The renderer, frame cap and lightweight update cadences always travel together,
 * so no screen or runtime owner can create a conflicting combination.
 */
enum class NativePerformanceProfile(
    val storageValue: String,
    val level: Int,
    @StringRes val nameResource: Int,
    @StringRes val descriptionResource: Int,
    val icon: NativePerformanceIcon,
    val mapEngine: NativeMapEngine,
    val maximumFps: Int,
    val trackingHertz: Int,
    val navigationGpsHertz: Int,
    val boostedNavigationGpsHertz: Int,
    val orientationHertz: Int,
) {
    Eco(
        storageValue = "eco",
        level = 1,
        nameResource = R.string.native_performance_eco,
        descriptionResource = R.string.native_performance_eco_desc,
        icon = NativePerformanceIcon.BatteryFull,
        mapEngine = NativeMapEngine.LegacyRaster,
        maximumFps = 30,
        trackingHertz = 15,
        navigationGpsHertz = 1,
        boostedNavigationGpsHertz = 1,
        orientationHertz = 5,
    ),
    Balanced(
        storageValue = "balanced",
        level = 2,
        nameResource = R.string.native_performance_balanced,
        descriptionResource = R.string.native_performance_balanced_desc,
        icon = NativePerformanceIcon.BatteryMedium,
        mapEngine = NativeMapEngine.LegacyRaster,
        maximumFps = 60,
        trackingHertz = 30,
        navigationGpsHertz = 1,
        boostedNavigationGpsHertz = 1,
        orientationHertz = 10,
    ),
    ThreeDEco(
        storageValue = "3d_eco",
        level = 3,
        nameResource = R.string.native_performance_3d_eco,
        descriptionResource = R.string.native_performance_3d_eco_desc,
        icon = NativePerformanceIcon.Layers,
        mapEngine = NativeMapEngine.MapLibre,
        maximumFps = 45,
        trackingHertz = 45,
        navigationGpsHertz = 1,
        boostedNavigationGpsHertz = 2,
        orientationHertz = 15,
    ),
    Performance(
        storageValue = "performance",
        level = 4,
        nameResource = R.string.native_performance_performance,
        descriptionResource = R.string.native_performance_performance_desc,
        icon = NativePerformanceIcon.Bolt,
        mapEngine = NativeMapEngine.MapLibre,
        maximumFps = 60,
        trackingHertz = 60,
        navigationGpsHertz = 2,
        boostedNavigationGpsHertz = 2,
        orientationHertz = 20,
    );

    val trackingIntervalMillis: Long
        get() = (1_000.0 / trackingHertz).roundToLong().coerceAtLeast(1L)

    val orientationIntervalMicros: Int
        get() = (1_000_000.0 / orientationHertz).roundToLong().toInt().coerceAtLeast(1)

    companion object {
        val Default: NativePerformanceProfile = ThreeDEco

        fun fromLevel(level: Int): NativePerformanceProfile =
            entries.firstOrNull { it.level == level } ?: Default

        /** New value wins; otherwise migrate the previous renderer without changing its dimensionality. */
        fun fromStorage(value: Any?, legacyMapEngine: Any? = null): NativePerformanceProfile {
            entries.firstOrNull { it.storageValue == value?.toString() }?.let { return it }
            return when (legacyMapEngine?.toString()) {
                NativeMapEngine.LegacyRaster.storageValue -> Balanced
                NativeMapEngine.MapLibre.storageValue -> ThreeDEco
                else -> Default
            }
        }
    }
}

/** Runtime-only GPS policy; it never changes the person's selected profile. */
object NativePerformanceRuntimePolicy {
    const val ONE_HERTZ_MILLIS = 1_000L
    const val TWO_HERTZ_MILLIS = 500L

    fun gpsIntervalMillis(
        profile: NativePerformanceProfile,
        navigating: Boolean,
        speedMetersPerSecond: Double,
        accuracyMeters: Double,
        wasBoosted: Boolean,
    ): Long {
        if (!navigating) return ONE_HERTZ_MILLIS
        if (profile == NativePerformanceProfile.Performance) return TWO_HERTZ_MILLIS
        if (profile != NativePerformanceProfile.ThreeDEco) return ONE_HERTZ_MILLIS

        val enterBoost = speedMetersPerSecond >= 10.0 || accuracyMeters >= 25.0
        val retainBoost = wasBoosted && (speedMetersPerSecond >= 4.0 || accuracyMeters >= 18.0)
        return if (enterBoost || retainBoost) TWO_HERTZ_MILLIS else ONE_HERTZ_MILLIS
    }
}
