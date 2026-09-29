package app.roadstr.feature.map

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

        val segments = mutableListOf<List<NativeMapPoint>>()
        var current: MutableList<NativeMapPoint>? = null
        routePoints.forEachIndexed { index, point ->
            val inJam = jams.any { jam ->
                NativeMapDistance.roundedVincentyMeters(point, jam) < PROXIMITY_METERS
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

}
