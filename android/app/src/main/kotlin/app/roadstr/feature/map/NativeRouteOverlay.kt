package app.roadstr.feature.map

import kotlin.math.roundToInt

/** Value-only snapshot consumed by the private MapLibre route renderer. */
data class NativeRouteOverlaySnapshot(
    val activeRuns: List<NativeRouteRun>,
    val completedPoints: List<NativeMapPoint>,
    val accentArgb: Long,
    val alternativeRoutes: List<List<NativeMapPoint>> = emptyList(),
) {
    companion object {
        fun empty(accentArgb: Long): NativeRouteOverlaySnapshot =
            NativeRouteOverlaySnapshot(
                activeRuns = emptyList(),
                completedPoints = emptyList(),
                accentArgb = accentArgb,
            )
    }
}

data class NativeRouteOverlayPayload(
    val activeGeoJson: String,
    val completedGeoJson: String,
    val alternativesGeoJson: String,
    val activeFeatureCount: Int,
    val alternativeFeatureCount: Int,
    val pointCount: Int,
    val accentArgb: Long,
)

/** Pure, bounded GeoJSON compiler for MapLibre route sources. */
object NativeRouteOverlayCompiler {
    const val MAX_ROUTE_POINTS = 250_000
    const val MAX_ROUTE_RUNS = 4_096
    const val MAX_ROUTE_ALTERNATIVES = 8
    private const val EMPTY_FEATURE_COLLECTION =
        "{\"type\":\"FeatureCollection\",\"features\":[]}"

    fun compile(snapshot: NativeRouteOverlaySnapshot): NativeRouteOverlayPayload {
        require(snapshot.accentArgb in 0L..0xFFFF_FFFFL) {
            "Route accent must be a 32-bit ARGB value"
        }
        require(snapshot.activeRuns.size <= MAX_ROUTE_RUNS) {
            "Route has too many classified runs"
        }
        require(snapshot.alternativeRoutes.size <= MAX_ROUTE_ALTERNATIVES) {
            "Route has too many alternatives"
        }
        require(snapshot.completedPoints.isEmpty() || snapshot.completedPoints.size >= 2) {
            "Completed route must be empty or drawable"
        }

        var pointCount = snapshot.completedPoints.size.toLong()
        require(pointCount <= MAX_ROUTE_POINTS.toLong()) { "Route has too many overlay points" }
        snapshot.activeRuns.forEach { run ->
            require(run.points.size >= 2) { "Every active route run must be drawable" }
            pointCount += run.points.size.toLong()
            require(pointCount <= MAX_ROUTE_POINTS.toLong()) { "Route has too many overlay points" }
        }
        snapshot.alternativeRoutes.forEach { alternative ->
            require(alternative.size >= 2) { "Every alternative route must be drawable" }
            pointCount += alternative.size.toLong()
            require(pointCount <= MAX_ROUTE_POINTS.toLong()) { "Route has too many overlay points" }
        }

        snapshot.completedPoints.forEach(::requireValidPoint)
        snapshot.activeRuns.forEach { run -> run.points.forEach(::requireValidPoint) }
        snapshot.alternativeRoutes.forEach { route -> route.forEach(::requireValidPoint) }

        return NativeRouteOverlayPayload(
            activeGeoJson = featureCollection(snapshot.activeRuns),
            completedGeoJson = completedFeatureCollection(snapshot.completedPoints),
            alternativesGeoJson = alternativesFeatureCollection(snapshot.alternativeRoutes),
            activeFeatureCount = snapshot.activeRuns.size,
            alternativeFeatureCount = snapshot.alternativeRoutes.size,
            pointCount = pointCount.toInt(),
            accentArgb = snapshot.accentArgb,
        )
    }

    private fun featureCollection(runs: List<NativeRouteRun>): String {
        if (runs.isEmpty()) return EMPTY_FEATURE_COLLECTION
        return buildString {
            append("{\"type\":\"FeatureCollection\",\"features\":[")
            runs.forEachIndexed { index, run ->
                if (index > 0) append(',')
                append("{\"type\":\"Feature\",\"properties\":{\"restricted\":")
                append(run.restricted)
                append("},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
                appendCoordinates(run.points)
                append("]}}")
            }
            append("]}")
        }
    }

    private fun completedFeatureCollection(points: List<NativeMapPoint>): String {
        if (points.isEmpty()) return EMPTY_FEATURE_COLLECTION
        return buildString {
            append(
                "{\"type\":\"FeatureCollection\",\"features\":[" +
                    "{\"type\":\"Feature\",\"properties\":{}," +
                    "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[",
            )
            appendCoordinates(points)
            append("]}}]}")
        }
    }

    private fun alternativesFeatureCollection(routes: List<List<NativeMapPoint>>): String {
        if (routes.isEmpty()) return EMPTY_FEATURE_COLLECTION
        return buildString {
            append("{\"type\":\"FeatureCollection\",\"features\":[")
            routes.forEachIndexed { index, points ->
                if (index > 0) append(',')
                append(
                    "{\"type\":\"Feature\",\"properties\":{}," +
                        "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[",
                )
                appendCoordinates(points)
                append("]}}")
            }
            append("]}")
        }
    }

    private fun StringBuilder.appendCoordinates(points: List<NativeMapPoint>) {
        points.forEachIndexed { index, point ->
            if (index > 0) append(',')
            append('[')
            append(canonicalNumber(point.longitude))
            append(',')
            append(canonicalNumber(point.latitude))
            append(']')
        }
    }

    private fun requireValidPoint(point: NativeMapPoint) {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
            "Route latitude is outside the WGS84 range"
        }
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
            "Route longitude is outside the WGS84 range"
        }
    }

    private fun canonicalNumber(value: Double): String =
        if (value == 0.0) "0" else value.toString()
}

/** Flutter MapLibre's physical-pixel correction for route line widths. */
object NativeRouteLayerMetrics {
    const val HALO_LOGICAL_WIDTH = 18.0
    const val CORE_LOGICAL_WIDTH = 9.0
    const val COMPLETED_LOGICAL_WIDTH = 9.0
    const val ALTERNATIVE_LOGICAL_WIDTH = 7.0

    fun widthPixels(logicalWidth: Double, displayDensity: Float): Float {
        require(logicalWidth.isFinite() && logicalWidth > 0.0) {
            "Logical line width must be finite and positive"
        }
        require(displayDensity.isFinite() && displayDensity > 0f) {
            "Display density must be finite and positive"
        }
        return (logicalWidth / displayDensity)
            .roundToInt()
            .coerceIn(1, 999)
            .toFloat()
    }
}

/** Rejects stale asynchronous style callbacks after reload or disposal. */
internal class NativeMapStyleGenerationGate {
    private var generation = 0L
    private var active = true

    fun next(): Long {
        check(active) { "Style gate is disposed" }
        generation += 1
        return generation
    }

    fun accepts(candidate: Long): Boolean = active && candidate == generation

    fun dispose() {
        active = false
    }
}
