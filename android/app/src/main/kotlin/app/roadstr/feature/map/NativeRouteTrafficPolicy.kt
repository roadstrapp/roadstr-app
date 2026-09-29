package app.roadstr.feature.map

import app.roadstr.core.geo.GeoPoint
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Pure port of Flutter's route-point proximity segmentation for traffic jams. */
object NativeRouteTrafficPolicy {
    const val PROXIMITY_METERS = 400.0
    const val MAX_TRAFFIC_JAMS = 2_048
    const val MAX_DISTANCE_CHECKS = 1_000_000L

    fun normalizeJamPoints(points: List<NativeMapPoint>): List<NativeMapPoint> {
        require(points.size <= MAX_TRAFFIC_JAMS) { "Route traffic feed contains too many jams" }
        points.forEach(::requireValidPoint)
        return points.toList()
    }

    /**
     * Includes the point before and after a jam run, matching Flutter's visual
     * continuity at both red-overlay boundaries.
     */
    fun segments(
        routePoints: List<NativeMapPoint>,
        jamPoints: List<NativeMapPoint>,
    ): List<List<NativeMapPoint>> {
        routePoints.forEach(::requireValidPoint)
        val jams = normalizeJamPoints(jamPoints)
        if (routePoints.isEmpty() || jams.isEmpty()) return emptyList()
        require(withinDistanceBudget(routePoints.size, jams.size)) {
            "Route traffic projection exceeds the distance-check budget"
        }

        val jamGeoPoints = jams.map { it.toGeoPoint() }
        val segments = mutableListOf<List<NativeMapPoint>>()
        var current: MutableList<NativeMapPoint>? = null
        routePoints.forEachIndexed { index, point ->
            val pointGeo = point.toGeoPoint()
            val inJam = jamGeoPoints.any { jam ->
                roundedVincentyMeters(pointGeo, jam) < PROXIMITY_METERS
            }
            if (inJam) {
                if (current == null) {
                    current = mutableListOf()
                    if (index > 0) current!!.add(routePoints[index - 1])
                }
                current!!.add(point)
            } else if (current != null) {
                current!!.add(point)
                addDrawableSegment(segments, current!!)
                current = null
            }
        }
        current?.let { addDrawableSegment(segments, it) }
        return segments
    }

    fun withinDistanceBudget(routePointCount: Int, jamPointCount: Int): Boolean =
        routePointCount.toLong() * jamPointCount.toLong() <= MAX_DISTANCE_CHECKS

    private fun addDrawableSegment(
        destination: MutableList<List<NativeMapPoint>>,
        segment: List<NativeMapPoint>,
    ) {
        if (segment.size < 2) return
        require(destination.size < NativeRouteOverlayCompiler.MAX_TRAFFIC_SEGMENTS) {
            "Route has too many traffic segments"
        }
        destination += segment.toList()
    }

    private fun requireValidPoint(point: NativeMapPoint) {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
            "Route traffic latitude is outside the WGS84 range"
        }
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
            "Route traffic longitude is outside the WGS84 range"
        }
    }

    private fun NativeMapPoint.toGeoPoint() = GeoPoint(latitude = latitude, longitude = longitude)

    /** Matches latlong2's default rounded WGS-84 Vincenty distance. */
    private fun roundedVincentyMeters(first: GeoPoint, second: GeoPoint): Double {
        val flattening = 1 / 298.257223563
        val semiMajor = 6_378_137.0
        val semiMinor = 6_356_752.314245
        val latitude1 = Math.toRadians(first.latitude)
        val latitude2 = Math.toRadians(second.latitude)
        val longitudeDifference = Math.toRadians(second.longitude - first.longitude)
        val reduced1 = atan((1 - flattening) * tan(latitude1))
        val reduced2 = atan((1 - flattening) * tan(latitude2))
        val sine1 = sin(reduced1)
        val cosine1 = cos(reduced1)
        val sine2 = sin(reduced2)
        val cosine2 = cos(reduced2)
        var lambda = longitudeDifference
        var sineSigma: Double
        var cosineSigma: Double
        var sigma: Double
        var cosineSquaredAlpha: Double
        var cosineDoubleSigma: Double
        var iterations = 200
        do {
            val previous = lambda
            val sineLambda = sin(lambda)
            val cosineLambda = cos(lambda)
            sineSigma = sqrt(
                (cosine2 * sineLambda) * (cosine2 * sineLambda) +
                    (cosine1 * sine2 - sine1 * cosine2 * cosineLambda) *
                    (cosine1 * sine2 - sine1 * cosine2 * cosineLambda),
            )
            if (sineSigma == 0.0) return 0.0
            cosineSigma = sine1 * sine2 + cosine1 * cosine2 * cosineLambda
            sigma = atan2(sineSigma, cosineSigma)
            val sineAlpha = cosine1 * cosine2 * sineLambda / sineSigma
            cosineSquaredAlpha = 1 - sineAlpha * sineAlpha
            cosineDoubleSigma = cosineSigma - 2 * sine1 * sine2 / cosineSquaredAlpha
            if (cosineDoubleSigma.isNaN()) cosineDoubleSigma = 0.0
            val coefficient = flattening / 16 * cosineSquaredAlpha *
                (4 + flattening * (4 - 3 * cosineSquaredAlpha))
            lambda = longitudeDifference + (1 - coefficient) * flattening * sineAlpha *
                (sigma + coefficient * sineSigma *
                    (cosineDoubleSigma + coefficient * cosineSigma *
                        (-1 + 2 * cosineDoubleSigma * cosineDoubleSigma)))
            if (abs(lambda - previous) <= 1e-12) break
        } while (--iterations > 0)
        if (iterations == 0) error("Distance calculation failed to converge")
        val uSquared = cosineSquaredAlpha *
            (semiMajor * semiMajor - semiMinor * semiMinor) / (semiMinor * semiMinor)
        val a = 1 + uSquared / 16_384 *
            (4096 + uSquared * (-768 + uSquared * (320 - 175 * uSquared)))
        val b = uSquared / 1024 *
            (256 + uSquared * (-128 + uSquared * (74 - 47 * uSquared)))
        val deltaSigma = b * sineSigma *
            (cosineDoubleSigma + b / 4 *
                (cosineSigma * (-1 + 2 * cosineDoubleSigma * cosineDoubleSigma) -
                    b / 6 * cosineDoubleSigma *
                    (-3 + 4 * sineSigma * sineSigma) *
                    (-3 + 4 * cosineSigma * cosineSigma)))
        return floor(semiMinor * a * (sigma - deltaSigma) + 0.5)
    }
}
