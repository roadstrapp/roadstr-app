package app.roadstr.core.geo

import kotlin.math.max
import kotlin.math.min

/** Route-progress algorithms kept engine-independent for parity testing. */
object RouteProgress {
    fun cumulativeDistances(polyline: List<GeoPoint>): List<Double> {
        val cumulative = MutableList(polyline.size) { 0.0 }
        for (i in 1 until polyline.size) {
            cumulative[i] = cumulative[i - 1] +
                GeoMath.distanceMeters(polyline[i - 1], polyline[i])
        }
        return cumulative
    }

    fun nearestIndex(polyline: List<GeoPoint>, position: GeoPoint): Int {
        var best = 0
        var bestDistance = Double.POSITIVE_INFINITY
        for (i in polyline.indices) {
            val distance = GeoMath.distanceMeters(polyline[i], position)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    fun nearestIndexNear(
        polyline: List<GeoPoint>,
        position: GeoPoint,
        hint: Int,
        back: Int = 30,
        ahead: Int = 600,
        acceptMeters: Double = 60.0,
    ): Int {
        if (polyline.isEmpty()) return 0
        val from = max(0, hint - back)
        val to = min(polyline.lastIndex, hint + ahead)
        if (from <= to) {
            var best = -1
            var bestDistance = Double.POSITIVE_INFINITY
            for (i in from..to) {
                val distance = GeoMath.distanceMeters(polyline[i], position)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            if (best >= 0 && bestDistance <= acceptMeters) return best
        }
        return nearestIndex(polyline, position)
    }

    fun nearestIndicesAlong(
        polyline: List<GeoPoint>,
        points: List<GeoPoint>,
        exactMeters: Double = 1.0,
        acceptMeters: Double = 60.0,
        maxAhead: Int = 4000,
    ): List<Int> {
        if (points.isEmpty()) return emptyList()
        if (polyline.isEmpty()) return List(points.size) { 0 }

        val result = mutableListOf<Int>()
        var cursor = 0
        for (point in points) {
            var best = -1
            var bestDistance = Double.POSITIVE_INFINITY
            val end = min(polyline.lastIndex, cursor + maxAhead)
            for (i in cursor..end) {
                val distance = GeoMath.distanceMeters(polyline[i], point)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                    if (distance <= exactMeters) break
                }
            }
            val index = if (best >= 0 && bestDistance <= acceptMeters) {
                best
            } else {
                nearestIndex(polyline, point)
            }
            result += index
            cursor = index
        }
        return result
    }
}
