package app.roadstr.feature.map

import app.roadstr.core.geo.GeoMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/** Immutable state exposed by the native route-to-map boundary. */
data class NativeRouteOverlaySessionState(
    val revision: Long,
    val progressMeters: Double,
    val totalDistanceMeters: Double,
    val snapshot: NativeRouteOverlaySnapshot,
)

/**
 * Revision-safe route projector for the MapLibre overlay.
 *
 * Routing responses and location ticks can arrive out of order. This class
 * keeps those races outside the renderer: only a newer route revision can
 * replace the geometry, progress never moves backwards, and every emitted
 * snapshot is already bounded and drawable.
 */
class NativeRouteOverlaySession(initialAccentArgb: Long) {
    private val lock = Any()
    private val _state = MutableStateFlow(initialState(initialAccentArgb))

    private var accentArgb = initialAccentArgb
    private var currentRevision = NO_ROUTE_REVISION
    private var route: PreparedRoute? = null
    private var progressMeters = 0.0
    private var cursorRestricted = false

    val state: StateFlow<NativeRouteOverlaySessionState> = _state.asStateFlow()

    /**
     * Accepts a complete normalized route only if [revision] is newer than
     * the currently visible route. The caller owns request-generation values.
     */
    fun submitRoute(
        revision: Long,
        points: List<NativeMapPoint>,
        restricted: List<Boolean>,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Route revision must be non-negative" }
        if (revision <= currentRevision) return false
        val prepared = prepareRoute(points, restricted)
        currentRevision = revision
        route = prepared
        progressMeters = 0.0
        cursorRestricted = false
        publish(prepared.snapshot(progressMeters, cursorRestricted, accentArgb))
        true
    }

    /**
     * Advances the visible route without allowing an old GPS tick to rewind
     * the completed/resting split. The cursor classification is supplied by
     * the future ZTL policy, matching Flutter's point-at-cursor check.
     */
    fun updateProgress(
        revision: Long,
        progressMeters: Double,
        cursorRestricted: Boolean,
    ): Boolean = synchronized(lock) {
        require(progressMeters.isFinite() && progressMeters >= 0.0) {
            "Route progress must be finite and non-negative"
        }
        if (revision != currentRevision || route == null) return false
        val clamped = min(progressMeters, route!!.totalDistanceMeters)
        if (clamped < this.progressMeters) return false
        if (clamped == this.progressMeters && cursorRestricted == this.cursorRestricted) {
            return false
        }
        this.progressMeters = clamped
        this.cursorRestricted = cursorRestricted
        publish(route!!.snapshot(clamped, cursorRestricted, accentArgb))
        true
    }

    /** Replaces ZTL classifications without replacing the route geometry. */
    fun updateRestrictions(
        revision: Long,
        restricted: List<Boolean>,
        cursorRestricted: Boolean,
    ): Boolean = synchronized(lock) {
        val currentRoute = route ?: return false
        if (revision != currentRevision || restricted.size != currentRoute.points.size) {
            return false
        }
        val updated = currentRoute.copy(restricted = restricted.toList())
        route = updated
        this.cursorRestricted = cursorRestricted
        publish(updated.snapshot(progressMeters, cursorRestricted, accentArgb))
        true
    }

    /** Clears the current route and fences off late callbacks at [revision]. */
    fun clearRoute(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Route revision must be non-negative" }
        if (revision < currentRevision) return false
        currentRevision = revision
        route = null
        progressMeters = 0.0
        cursorRestricted = false
        publish(emptySnapshot())
        true
    }

    /** Updates theme color while preserving the route and progress state. */
    fun updateAccent(accentArgb: Long): Boolean = synchronized(lock) {
        if (this.accentArgb == accentArgb) return false
        validateAccent(accentArgb)
        this.accentArgb = accentArgb
        val currentRoute = route
        publish(currentRoute?.snapshot(progressMeters, cursorRestricted, accentArgb) ?: emptySnapshot())
        true
    }

    private fun publish(snapshot: NativeRouteOverlaySnapshot) {
        _state.value = NativeRouteOverlaySessionState(
            revision = currentRevision,
            progressMeters = progressMeters,
            totalDistanceMeters = route?.totalDistanceMeters ?: 0.0,
            snapshot = snapshot,
        )
    }

    private fun prepareRoute(
        points: List<NativeMapPoint>,
        restricted: List<Boolean>,
    ): PreparedRoute {
        require(points.size == restricted.size) {
            "Route points and restriction flags must have equal length"
        }
        require(points.size >= 2) { "Route must contain at least two points" }
        require(points.size <= MAX_SESSION_ROUTE_POINTS) {
            "Route has too many overlay points"
        }
        // Reuse the compiler's coordinate/ARGB/line guards before retaining
        // any route data. The active snapshot is intentionally empty here;
        // restrictions are projected again after progress is known.
        NativeRouteOverlayCompiler.compile(
            NativeRouteOverlaySnapshot(
                activeRuns = listOf(NativeRouteRun(points, restricted = false)),
                completedPoints = emptyList(),
                accentArgb = accentArgb,
            ),
        )
        val copiedPoints = points.toList()
        val cumulative = MutableList(copiedPoints.size) { 0.0 }
        for (index in 1 until copiedPoints.size) {
            cumulative[index] = cumulative[index - 1] + GeoMath.distanceMeters(
                copiedPoints[index - 1].toGeoPoint(),
                copiedPoints[index].toGeoPoint(),
            )
        }
        require(cumulative.last().isFinite()) { "Route distance must be finite" }
        return PreparedRoute(
            points = copiedPoints,
            restricted = restricted.toList(),
            cumulativeMeters = cumulative,
        )
    }

    private fun emptySnapshot(): NativeRouteOverlaySnapshot =
        NativeRouteOverlaySnapshot.empty(accentArgb)

    private fun initialState(accentArgb: Long): NativeRouteOverlaySessionState {
        validateAccent(accentArgb)
        return NativeRouteOverlaySessionState(
            revision = NO_ROUTE_REVISION,
            progressMeters = 0.0,
            totalDistanceMeters = 0.0,
            snapshot = NativeRouteOverlaySnapshot.empty(accentArgb),
        )
    }

    private fun validateAccent(accentArgb: Long) {
        require(accentArgb in 0L..0xFFFF_FFFFL) {
            "Route accent must be a 32-bit ARGB value"
        }
    }

    private data class PreparedRoute(
        val points: List<NativeMapPoint>,
        val restricted: List<Boolean>,
        val cumulativeMeters: List<Double>,
    ) {
        val totalDistanceMeters: Double
            get() = cumulativeMeters.last()

        fun snapshot(
            progressMeters: Double,
            cursorRestricted: Boolean,
            accentArgb: Long,
        ): NativeRouteOverlaySnapshot {
            if (progressMeters <= 0.0) {
                return NativeRouteOverlaySnapshot(
                    activeRuns = NativeMapOverlayPolicy.splitRouteByRestriction(
                        points,
                        restricted,
                    ),
                    completedPoints = emptyList(),
                    accentArgb = accentArgb,
                )
            }
            if (totalDistanceMeters <= 0.0 || progressMeters >= totalDistanceMeters) {
                return NativeRouteOverlaySnapshot(
                    activeRuns = emptyList(),
                    completedPoints = points,
                    accentArgb = accentArgb,
                )
            }

            var segment = 0
            while (
                segment + 1 < points.size &&
                cumulativeMeters[segment + 1] < progressMeters
            ) {
                segment++
            }
            val start = points[segment]
            val end = points[segment + 1]
            val span = cumulativeMeters[segment + 1] - cumulativeMeters[segment]
            val fraction = if (span <= 0.0) {
                0.0
            } else {
                ((progressMeters - cumulativeMeters[segment]) / span).coerceIn(0.0, 1.0)
            }
            val cursor = NativeMapPoint(
                latitude = start.latitude + (end.latitude - start.latitude) * fraction,
                longitude = start.longitude + (end.longitude - start.longitude) * fraction,
            )
            val completed = buildList {
                addAll(points.take(segment + 1))
                add(cursor)
            }
            val atNextVertex = fraction >= 1.0
            val remainingStart = if (atNextVertex) segment + 2 else segment + 1
            val remaining = buildList {
                add(cursor)
                addAll(points.drop(remainingStart))
            }
            val remainingFlags = buildList {
                add(cursorRestricted)
                addAll(restricted.drop(remainingStart))
            }
            return NativeRouteOverlaySnapshot(
                activeRuns = if (remaining.size >= 2) {
                    NativeMapOverlayPolicy.splitRouteByRestriction(remaining, remainingFlags)
                } else {
                    emptyList()
                },
                completedPoints = completed,
                accentArgb = accentArgb,
            )
        }
    }

    private fun NativeMapPoint.toGeoPoint() =
        app.roadstr.core.geo.GeoPoint(latitude = latitude, longitude = longitude)

    companion object {
        private const val NO_ROUTE_REVISION = -1L
        // Route runs share transition vertices. Reserve the compiler's full
        // run budget so completed + active sources remain below one payload
        // point ceiling even when every classified run changes.
        const val MAX_SESSION_ROUTE_POINTS =
            NativeRouteOverlayCompiler.MAX_ROUTE_POINTS -
                NativeRouteOverlayCompiler.MAX_ROUTE_RUNS
    }
}
