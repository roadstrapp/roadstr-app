package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint

/**
 * The geometry of "along my route": which part of a route is still ahead, a polyline that is short
 * enough for one Overpass request, and how far out of the way a place is. Everything here is
 * local, deterministic and bounded; none of it asks a server anything.
 */
object RouteCorridor {
    const val DEFAULT_AHEAD_METERS = 30_000.0
    const val DEFAULT_BUFFER_METERS = 1_000
    const val MIN_AHEAD_METERS = 200.0
    const val MAX_OFF_ROUTE_METERS = 3_000.0
    private const val FIRST_TOLERANCE_METERS = 10.0
    private const val TOLERANCE_GROWTH = 1.5
    private const val MAX_SIMPLIFY_ROUNDS = 40

    /** A simplified line and the most any dropped point can be away from it. */
    data class Simplified(val points: List<GeoPoint>, val errorBoundMeters: Double)

    /** How a place sits against a route: the side step and the distance along it. */
    data class Position(val offRouteMeters: Double, val alongMeters: Double) {
        /** Out and back again, the proxy for the detour the place costs. */
        val detourMeters: Double get() = 2 * offRouteMeters
    }

    fun lengthMeters(points: List<GeoPoint>): Double {
        var total = 0.0
        for (i in 0 until points.size - 1) total += GeoMath.distanceMeters(points[i], points[i + 1])
        return total
    }

    /** The point [meters] along [points], clamped to its ends. */
    fun pointAlong(points: List<GeoPoint>, meters: Double): GeoPoint {
        require(points.isNotEmpty()) { "No points" }
        var remaining = meters.coerceAtLeast(0.0)
        for (i in 0 until points.size - 1) {
            val length = GeoMath.distanceMeters(points[i], points[i + 1])
            if (remaining <= length && length > 0.0) return interpolate(points[i], points[i + 1], remaining / length)
            remaining -= length
        }
        return points.last()
    }

    /**
     * The part of [route] ahead of [from], at most [aheadMeters] long, ready for one query; null when
     * the route is unusable, [from] is far from it, or too little of it is left.
     */
    fun ahead(
        route: List<GeoPoint>,
        from: GeoPoint,
        aheadMeters: Double = DEFAULT_AHEAD_METERS,
        bufferMeters: Int = DEFAULT_BUFFER_METERS,
    ): SearchArea.Corridor? {
        if (route.size < 2 || route.any { !it.latitude.isFinite() || !it.longitude.isFinite() }) return null
        val start = nearestSegment(route, from) ?: return null
        if (start.distanceMeters > MAX_OFF_ROUTE_METERS) return null
        val line = cut(route, start.index, start.fraction, aheadMeters)
        if (lengthMeters(line) < MIN_AHEAD_METERS) return null
        val simplified = simplify(line, SearchArea.MAX_CORRIDOR_VERTICES)
        return SearchArea.Corridor(simplified.points, bufferMeters)
    }

    /**
     * Douglas–Peucker with a tolerance that grows until at most [maxVertices] points remain. The ends
     * are always kept, and no dropped point is further than the returned error bound from the line.
     */
    fun simplify(points: List<GeoPoint>, maxVertices: Int): Simplified {
        require(maxVertices >= 2) { "A line needs two points" }
        if (points.size <= maxVertices) return Simplified(points, 0.0)
        var tolerance = FIRST_TOLERANCE_METERS
        repeat(MAX_SIMPLIFY_ROUNDS) {
            val kept = douglasPeucker(points, tolerance)
            if (kept.size <= maxVertices) return Simplified(kept, tolerance)
            tolerance *= TOLERANCE_GROWTH
        }
        return decimate(points, maxVertices)
    }

    /** Where [place] is against [route], from the nearest stretch of it. */
    fun position(place: GeoPoint, route: List<GeoPoint>): Position? {
        val nearest = nearestSegment(route, place) ?: return null
        var along = 0.0
        for (i in 0 until nearest.index) along += GeoMath.distanceMeters(route[i], route[i + 1])
        along += GeoMath.distanceMeters(route[nearest.index], route[nearest.index + 1]) * nearest.fraction
        return Position(nearest.distanceMeters, along)
    }

    private data class Nearest(val index: Int, val fraction: Double, val distanceMeters: Double)

    private fun nearestSegment(route: List<GeoPoint>, point: GeoPoint): Nearest? {
        if (route.size < 2) return null
        var best: Nearest? = null
        for (i in 0 until route.size - 1) {
            val projection = GeoMath.projectOnSegment(point, route[i], route[i + 1])
            if (best == null || projection.distanceMeters < best.distanceMeters) {
                best = Nearest(i, projection.t, projection.distanceMeters)
            }
        }
        return best
    }

    /** From [fraction] of segment [index], forward, until [aheadMeters] have been walked. */
    private fun cut(route: List<GeoPoint>, index: Int, fraction: Double, aheadMeters: Double): List<GeoPoint> {
        val line = arrayListOf(interpolate(route[index], route[index + 1], fraction))
        var walked = 0.0
        for (i in index + 1 until route.size) {
            val last = line.last()
            val step = GeoMath.distanceMeters(last, route[i])
            if (walked + step >= aheadMeters) {
                val share = if (step == 0.0) 0.0 else (aheadMeters - walked) / step
                line += interpolate(last, route[i], share)
                return line
            }
            walked += step
            line += route[i]
        }
        return line
    }

    private fun douglasPeucker(points: List<GeoPoint>, toleranceMeters: Double): List<GeoPoint> {
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val pending = ArrayDeque<IntArray>()
        pending.addLast(intArrayOf(0, points.lastIndex))
        while (pending.isNotEmpty()) {
            val (from, to) = pending.removeLast()
            var worst = -1
            var worstDistance = toleranceMeters
            for (i in from + 1 until to) {
                val distance = GeoMath.distanceToSegmentMeters(points[i], points[from], points[to])
                if (distance > worstDistance) {
                    worstDistance = distance
                    worst = i
                }
            }
            if (worst < 0) continue
            keep[worst] = true
            pending.addLast(intArrayOf(from, worst))
            pending.addLast(intArrayOf(worst, to))
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    /** The last resort: evenly spaced points, with the error measured instead of promised. */
    private fun decimate(points: List<GeoPoint>, maxVertices: Int): Simplified {
        val step = (points.size - 1).toDouble() / (maxVertices - 1)
        val kept = (0 until maxVertices).map { points[Math.round(it * step).toInt().coerceIn(0, points.lastIndex)] }
        val worst = points.maxOf { GeoMath.distanceToPolylineMeters(it, kept) }
        return Simplified(kept, worst)
    }

    private fun interpolate(a: GeoPoint, b: GeoPoint, fraction: Double): GeoPoint = GeoPoint(
        a.latitude + (b.latitude - a.latitude) * fraction,
        a.longitude + (b.longitude - a.longitude) * fraction,
    )
}
