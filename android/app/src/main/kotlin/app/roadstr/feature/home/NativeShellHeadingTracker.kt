package app.roadstr.feature.home

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.navigation.HeadingFilter
import app.roadstr.core.navigation.RouteLocalBearing
import app.roadstr.feature.map.NativeMapPoint

/**
 * Turns raw GPS fixes into the bearing the camera and navigation use.
 *
 * Mirrors the Flutter heading resolution in maplibre_map_screen.dart: course
 * over ground only while moving and measured across a displacement larger
 * than the fix's own uncertainty, the provider bearing as fallback, and the
 * last good bearing held otherwise. Feeding the raw provider bearing straight
 * to the camera, with 0 standing in for "no bearing", turned the heading-up
 * map to north at every stop and let GPS noise swing it at walking pace.
 */
class NativeShellHeadingTracker(
    private val filter: HeadingFilter = HeadingFilter(),
) {
    var headingDegrees: Double = 0.0
        private set

    /** Above walking pace with hysteresis: the only time the map is dead-reckoned. */
    val isMoving: Boolean get() = filter.isMoving

    private var origin: GeoPoint? = null

    /**
     * The map was turned by the compass at a standstill: that is now the
     * bearing the next fix measures from, as the Flutter screen reads it back
     * from the camera.
     */
    fun adopt(degrees: Double) {
        if (degrees.isFinite()) headingDegrees = degrees
    }

    /** New navigation session: forget any half-confirmed reversal. */
    fun resetReversal() = filter.reset()

    fun update(
        point: NativeMapPoint,
        speedMetersPerSecond: Double,
        accuracyMeters: Double,
        providerHeadingDegrees: Double?,
        navigating: Boolean,
        routeLocalBearingAt: ((GeoPoint) -> RouteLocalBearing?)? = null,
    ): Double {
        val speedKmh = (speedMetersPerSecond * 3.6).takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val moving = filter.updateMotion(speedKmh)
        val accuracy = accuracyMeters.takeIf { it.isFinite() && it > 0.0 }
            ?: DEFAULT_ACCURACY_METERS
        val to = GeoPoint(point.latitude, point.longitude)
        val from = origin
        headingDegrees = filter.resolve(
            currentDegrees = headingDegrees,
            from = from,
            to = to,
            speedKilometresPerHour = speedKmh,
            accuracyMeters = accuracy,
            providerHeadingDegrees = providerHeadingDegrees,
            navigating = navigating,
            routeLocalBearingAt = routeLocalBearingAt,
        )
        origin = when {
            !moving -> null
            from == null || HeadingFilter.hasReliableMovement(from, to, accuracy) -> to
            else -> from
        }
        return headingDegrees
    }

    private companion object {
        const val DEFAULT_ACCURACY_METERS = 20.0
    }
}
