package app.roadstr.feature.map

import kotlin.math.abs

/** Typed gestures emitted by the native map without attaching product data. */
sealed interface NativeMapInteraction {
    data class RoadEventTap(
        val markerId: String,
        val markerRevision: Long,
    ) : NativeMapInteraction

    data class MapTap(val point: NativeMapPoint) : NativeMapInteraction

    data class MapLongPress(val point: NativeMapPoint) : NativeMapInteraction
}

data class NativeMapScreenPoint(val x: Double, val y: Double)

/** Pure Flutter-parity hit-testing and map-gesture decisions. */
internal object NativeMapInteractionPolicy {
    const val ALTERNATIVE_TAP_THRESHOLD_METERS = 60.0

    fun tap(
        point: NativeMapPoint,
        marker: NativeMapPointOverlayMarker?,
        markerRevision: Long,
    ): NativeMapInteraction {
        requireValidPoint(point)
        if (marker != null) {
            require(marker.kind.opensRoadEventDetail) { "Only road events accept marker taps" }
            require(marker.id.isNotBlank() && marker.id.length <= NativeMapPointOverlayPolicy.MAX_ID_LENGTH) {
                "Marker tap id is invalid"
            }
            require(markerRevision >= 0) { "Marker tap revision must be non-negative" }
            return NativeMapInteraction.RoadEventTap(marker.id, markerRevision)
        }
        return NativeMapInteraction.MapTap(point)
    }

    fun longPress(point: NativeMapPoint): NativeMapInteraction.MapLongPress {
        requireValidPoint(point)
        return NativeMapInteraction.MapLongPress(point)
    }

    /**
     * Matches Flutter marker GestureDetector rectangles. Reverse traversal
     * gives the last painted overlapping road event the first tap claim.
     */
    fun hitRoadEvent(
        snapshot: NativeMapPointOverlaySnapshot,
        zoom: Double,
        density: Double,
        tap: NativeMapScreenPoint,
        project: (NativeMapPoint) -> NativeMapScreenPoint?,
    ): NativeMapPointOverlayMarker? {
        require(density.isFinite() && density > 0.0) { "Display density must be positive" }
        require(tap.x.isFinite() && tap.y.isFinite()) { "Map tap must have finite screen coordinates" }
        return NativeMapPointOverlayPolicy.visibleAtZoom(snapshot, zoom)
            .asReversed()
            .firstOrNull { marker ->
                if (!marker.kind.opensRoadEventDetail) return@firstOrNull false
                val center = project(marker.point) ?: return@firstOrNull false
                if (!center.x.isFinite() || !center.y.isFinite()) return@firstOrNull false
                val halfSize = marker.kind.sizeDp * density / 2.0
                abs(tap.x - center.x) <= halfSize && abs(tap.y - center.y) <= halfSize
            }
    }

    /** Exact port of Flutter's first-wins nearest-alternative vertex scan. */
    fun nearestAlternative(
        tap: NativeMapPoint,
        alternatives: List<List<NativeMapPoint>>,
    ): Int {
        requireValidPoint(tap)
        require(alternatives.size <= NativeRouteOverlayCompiler.MAX_ROUTE_ALTERNATIVES) {
            "Route has too many alternatives"
        }
        val pointCount = alternatives.sumOf { it.size.toLong() }
        require(pointCount <= NativeRouteOverlaySession.MAX_SESSION_ROUTE_POINTS.toLong()) {
            "Route alternatives have too many interaction points"
        }
        alternatives.forEach { route -> route.forEach(::requireValidPoint) }

        var nearestIndex = -1
        var nearestMeters = ALTERNATIVE_TAP_THRESHOLD_METERS
        alternatives.forEachIndexed { index, route ->
            route.forEach { point ->
                val meters = NativeMapDistance.roundedVincentyMeters(tap, point)
                if (meters < nearestMeters) {
                    nearestMeters = meters
                    nearestIndex = index
                }
            }
        }
        return nearestIndex
    }

    private fun requireValidPoint(point: NativeMapPoint) {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
            "Map interaction latitude is outside the WGS84 range"
        }
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
            "Map interaction longitude is outside the WGS84 range"
        }
    }
}
