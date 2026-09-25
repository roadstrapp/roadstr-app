package app.roadstr.core.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

data class GroundExtents(val forward: Double, val backward: Double, val halfWidth: Double)

class ViewportWindow private constructor(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val forwardMeters: Double,
    val backwardMeters: Double,
    val halfWidthMeters: Double,
    bearingDegrees: Double,
) {
    private val sineBearing = sin(bearingDegrees * DEGREES_TO_RADIANS)
    private val cosineBearing = cos(bearingDegrees * DEGREES_TO_RADIANS)
    private val metersPerDegreeLongitude =
        METERS_PER_DEGREE_LATITUDE * abs(cos(centerLatitude * DEGREES_TO_RADIANS))

    fun contains(latitude: Double, longitude: Double): Boolean {
        val north = (latitude - centerLatitude) * METERS_PER_DEGREE_LATITUDE
        val east = (longitude - centerLongitude) * metersPerDegreeLongitude
        val ahead = east * sineBearing + north * cosineBearing
        val aside = east * cosineBearing - north * sineBearing
        return ahead <= forwardMeters &&
            ahead >= -backwardMeters &&
            abs(aside) <= halfWidthMeters
    }

    companion object {
        private const val DEGREES_TO_RADIANS = PI / 180
        private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
        private const val TAN_HALF_FIELD_OF_VIEW = 1.0 / 3.0
        private const val MAX_PITCH_DEGREES = 60.0

        fun create(
            centerLatitude: Double,
            centerLongitude: Double,
            zoom: Double,
            bearingDegrees: Double,
            pitchDegrees: Double,
            screenWidthDp: Double,
            screenHeightDp: Double,
            margin: Double = 1.5,
            extraMeters: Double = 100.0,
        ): ViewportWindow {
            val metersPerDp = 78_271.51696 *
                abs(cos(centerLatitude * DEGREES_TO_RADIANS)) / 2.0.pow(zoom)
            val extents = groundExtentsDp(pitchDegrees, screenWidthDp, screenHeightDp)
            return ViewportWindow(
                centerLatitude,
                centerLongitude,
                extents.forward * metersPerDp * margin + extraMeters,
                extents.backward * metersPerDp * margin + extraMeters,
                extents.halfWidth * metersPerDp * margin + extraMeters,
                bearingDegrees,
            )
        }

        fun groundExtentsDp(
            pitchDegrees: Double,
            screenWidthDp: Double,
            screenHeightDp: Double,
        ): GroundExtents {
            val pitch = pitchDegrees.coerceIn(0.0, MAX_PITCH_DEGREES) * DEGREES_TO_RADIANS
            val halfFieldOfView = atan(TAN_HALF_FIELD_OF_VIEW)
            val toCenter = 1.5 * screenHeightDp
            val height = toCenter * cos(pitch)
            val forward = height * (tan(pitch + halfFieldOfView) - tan(pitch))
            val backward = height * (tan(pitch) - tan(pitch - halfFieldOfView))
            val farSlant = height / cos(pitch + halfFieldOfView)
            val halfWidth = farSlant * (screenWidthDp / screenHeightDp) * TAN_HALF_FIELD_OF_VIEW
            return GroundExtents(forward, backward, halfWidth)
        }
    }
}
