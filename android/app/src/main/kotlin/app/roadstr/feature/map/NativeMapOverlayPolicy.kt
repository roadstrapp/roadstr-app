package app.roadstr.feature.map

import app.roadstr.core.map.ViewportWindow

data class NativeMapPoint(
    val latitude: Double,
    val longitude: Double,
)

data class NativeRouteRun(
    val points: List<NativeMapPoint>,
    val restricted: Boolean,
)

data class NativeMapMarker(
    val id: String,
    val point: NativeMapPoint,
    val minimumZoom: Double = Double.NEGATIVE_INFINITY,
)

/** Rendering-engine-independent route and marker decisions for MapLibre. */
object NativeMapOverlayPolicy {
    /**
     * Splits a route at ZTL classification changes while sharing the boundary
     * point, matching the Flutter line-layer behavior without rendering code.
     */
    fun splitRouteByRestriction(
        points: List<NativeMapPoint>,
        restricted: List<Boolean>,
    ): List<NativeRouteRun> {
        require(points.size == restricted.size) {
            "Route points and restriction flags must have equal length"
        }
        if (points.size < 2) return emptyList()

        val runs = mutableListOf<NativeRouteRun>()
        var start = 0
        var classification = restricted[0]
        for (index in 1 until points.size) {
            if (restricted[index] == classification) continue

            // The transition coordinate belongs to both drawable runs. If a
            // classification changes on adjacent points, there is no honest
            // two-point run for the old class; drop that degenerate fragment
            // rather than looping or asking MapLibre to draw a point as a
            // line.
            val end = index - 1
            if (end - start >= 1) {
                runs += NativeRouteRun(
                    points = points.subList(start, end + 1).toList(),
                    restricted = classification,
                )
            }
            start = end
            classification = restricted[index]
        }
        runs += NativeRouteRun(
            points = points.subList(start, points.size).toList(),
            restricted = classification,
        )
        return runs
    }

    /** Returns marker order unchanged while applying zoom and viewport gates. */
    fun visibleMarkers(
        markers: Iterable<NativeMapMarker>,
        viewport: ViewportWindow?,
        zoom: Double,
    ): List<NativeMapMarker> = markers.filter { marker ->
        zoom >= marker.minimumZoom &&
            (viewport == null || viewport.contains(marker.point.latitude, marker.point.longitude))
    }
}
