package app.roadstr.core.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Roadstr's small-distance WGS84 geometry contract.
 *
 * This mirrors lib/utils/geo.dart: equirectangular metres for local distance
 * and projections, and a great-circle initial bearing. It intentionally does
 * not introduce a different geodesy library during the parity phase.
 */
object GeoMath {
    const val metresPerDegree = 111320.0
    private const val degreesToRadians = Math.PI / 180.0

    private fun cosLatitude(latitude: Double): Double =
        cos(latitude * degreesToRadians)

    fun pointInPolygon(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        if (polygon.size < 3) return false
        val x = point.longitude
        val y = point.latitude
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude
            if ((yi > y) != (yj > y) &&
                x < (xj - xi) * (y - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dx = (b.longitude - a.longitude) * metresPerDegree * cosLatitude(a.latitude)
        val dy = (b.latitude - a.latitude) * metresPerDegree
        return hypot(dx, dy)
    }

    fun projectOnSegment(point: GeoPoint, a: GeoPoint, b: GeoPoint): SegmentProjection {
        val cosLat = cosLatitude(point.latitude)
        val dx = (b.longitude - a.longitude) * metresPerDegree * cosLat
        val dy = (b.latitude - a.latitude) * metresPerDegree
        val px = (point.longitude - a.longitude) * metresPerDegree * cosLat
        val py = (point.latitude - a.latitude) * metresPerDegree
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) {
            0.0
        } else {
            ((px * dx + py * dy) / lengthSquared).coerceIn(0.0, 1.0)
        }
        val ex = px - t * dx
        val ey = py - t * dy
        return SegmentProjection(hypot(ex, ey), t)
    }

    fun distanceToSegmentMeters(point: GeoPoint, a: GeoPoint, b: GeoPoint): Double =
        projectOnSegment(point, a, b).distanceMeters

    fun distanceToPolylineMeters(point: GeoPoint, polyline: List<GeoPoint>): Double {
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until polyline.size - 1) {
            val distance = projectOnSegment(point, polyline[i], polyline[i + 1]).distanceMeters
            if (distance < best) best = distance
        }
        return best
    }

    fun bearingBetween(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = from.latitude * degreesToRadians
        val lat2 = to.latitude * degreesToRadians
        val deltaLongitude = (to.longitude - from.longitude) * degreesToRadians
        val y = sin(deltaLongitude) * cos(lat2)
        val x = cos(lat1) * sin(lat2) -
            sin(lat1) * cos(lat2) * cos(deltaLongitude)
        return (atan2(y, x) / degreesToRadians + 360.0) % 360.0
    }
}
