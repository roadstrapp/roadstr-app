package app.roadstr.feature.map

import app.roadstr.core.geo.GeoMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/** Immutable state exposed by the native route-to-map boundary. */
data class NativeRouteOverlaySessionState(
    val revision: Long,
    val trafficRevision: Long,
    val progressMeters: Double,
    val totalDistanceMeters: Double,
    val selectedAlternativeIndex: Int?,
    val alternativeCount: Int,
    val snapshot: NativeRouteOverlaySnapshot,
)

data class NativeRouteCandidate(
    val points: List<NativeMapPoint>,
    val restricted: List<Boolean>,
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
    private var alternatives: List<PreparedRoute> = emptyList()
    private var selectedAlternativeIndex: Int? = null
    private var progressMeters = 0.0
    private var cursorRestricted = false
    private var trafficRevision = NO_TRAFFIC_REVISION
    private var trafficJamPoints: List<NativeMapPoint> = emptyList()
    private var trafficSegments: List<List<NativeMapPoint>> = emptyList()

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
        val nextTrafficSegments = projectTraffic(prepared)
        currentRevision = revision
        route = prepared
        alternatives = emptyList()
        selectedAlternativeIndex = null
        progressMeters = 0.0
        cursorRestricted = false
        trafficSegments = nextTrafficSegments
        publish(currentSnapshot())
        true
    }

    /**
     * Installs a bounded route-choice preview. The selected candidate receives
     * the active/ZTL treatment while every other candidate remains muted.
     */
    fun submitAlternatives(
        revision: Long,
        candidates: List<NativeRouteCandidate>,
        selectedIndex: Int,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Route revision must be non-negative" }
        if (revision <= currentRevision) return false
        require(candidates.isNotEmpty()) { "Route alternatives must not be empty" }
        require(candidates.size <= NativeRouteOverlayCompiler.MAX_ROUTE_ALTERNATIVES) {
            "Route has too many alternatives"
        }
        require(selectedIndex in candidates.indices) { "Selected route is outside the alternatives" }
        validateCombinedPointCount(candidates)
        val prepared = candidates.map { candidate ->
            prepareRoute(candidate.points, candidate.restricted)
        }
        val nextTrafficSegments = projectTraffic(prepared[selectedIndex])
        currentRevision = revision
        alternatives = prepared
        selectedAlternativeIndex = selectedIndex
        route = prepared[selectedIndex]
        progressMeters = 0.0
        cursorRestricted = false
        trafficSegments = nextTrafficSegments
        publish(currentSnapshot())
        true
    }

    /** Changes the highlighted preview without accepting stale UI callbacks. */
    fun selectAlternative(revision: Long, selectedIndex: Int): Boolean = synchronized(lock) {
        if (revision != currentRevision) return false
        selectAlternativeLocked(selectedIndex)
    }

    /** Selects the closest candidate vertex within Flutter's strict 60 m tap gate. */
    fun selectAlternativeAt(revision: Long, tap: NativeMapPoint): Boolean = synchronized(lock) {
        if (revision != currentRevision || alternatives.isEmpty()) return false
        val selectedIndex = NativeMapInteractionPolicy.nearestAlternative(
            tap = tap,
            alternatives = alternatives.map { it.points },
        )
        if (selectedIndex < 0) return false
        selectAlternativeLocked(selectedIndex)
    }

    private fun selectAlternativeLocked(selectedIndex: Int): Boolean {
        if (selectedIndex !in alternatives.indices || selectedAlternativeIndex == selectedIndex) {
            return false
        }
        val selectedRoute = alternatives[selectedIndex]
        val nextTrafficSegments = projectTraffic(selectedRoute)
        selectedAlternativeIndex = selectedIndex
        route = selectedRoute
        progressMeters = 0.0
        cursorRestricted = false
        trafficSegments = nextTrafficSegments
        publish(currentSnapshot())
        return true
    }

    /** Replaces active traffic-jam coordinates without changing route state. */
    fun submitTraffic(
        revision: Long,
        jamPoints: List<NativeMapPoint>,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Route traffic revision must be non-negative" }
        if (revision <= trafficRevision) return false
        val normalized = NativeRouteTrafficPolicy.normalizeJamPoints(jamPoints)
        val nextSegments = route?.let { projectTraffic(it, normalized) }
            ?: emptyList()
        trafficRevision = revision
        trafficJamPoints = normalized
        trafficSegments = nextSegments
        publish(currentSnapshot())
        true
    }

    /** Clears traffic geometry and fences late event-cache callbacks. */
    fun clearTraffic(revision: Long): Boolean = submitTraffic(revision, emptyList())

    /** Commits the highlighted candidate and removes muted preview geometry. */
    fun commitSelectedAlternative(revision: Long): Boolean = synchronized(lock) {
        if (revision != currentRevision || selectedAlternativeIndex == null) return false
        alternatives = emptyList()
        selectedAlternativeIndex = null
        publish(currentSnapshot())
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
        if (revision != currentRevision || route == null || alternatives.isNotEmpty()) return false
        val clamped = min(progressMeters, route!!.totalDistanceMeters)
        if (clamped < this.progressMeters) return false
        if (clamped == this.progressMeters && cursorRestricted == this.cursorRestricted) {
            return false
        }
        this.progressMeters = clamped
        this.cursorRestricted = cursorRestricted
        publish(currentSnapshot())
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
        selectedAlternativeIndex?.let { selected ->
            alternatives = alternatives.toMutableList().also { it[selected] = updated }
        }
        this.cursorRestricted = cursorRestricted
        publish(currentSnapshot())
        true
    }

    /** Clears the current route and fences off late callbacks at [revision]. */
    fun clearRoute(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Route revision must be non-negative" }
        if (revision < currentRevision) return false
        currentRevision = revision
        route = null
        alternatives = emptyList()
        selectedAlternativeIndex = null
        progressMeters = 0.0
        cursorRestricted = false
        trafficSegments = emptyList()
        publish(emptySnapshot())
        true
    }

    /** Updates theme color while preserving the route and progress state. */
    fun updateAccent(accentArgb: Long): Boolean = synchronized(lock) {
        if (this.accentArgb == accentArgb) return false
        validateAccent(accentArgb)
        this.accentArgb = accentArgb
        publish(currentSnapshot())
        true
    }

    private fun publish(snapshot: NativeRouteOverlaySnapshot) {
        _state.value = NativeRouteOverlaySessionState(
            revision = currentRevision,
            trafficRevision = trafficRevision,
            progressMeters = progressMeters,
            totalDistanceMeters = route?.totalDistanceMeters ?: 0.0,
            selectedAlternativeIndex = selectedAlternativeIndex,
            alternativeCount = alternatives.size,
            snapshot = snapshot,
        )
    }

    private fun currentSnapshot(): NativeRouteOverlaySnapshot {
        val currentRoute = route ?: return emptySnapshot()
        val selected = selectedAlternativeIndex
        val muted = if (selected == null) {
            emptyList()
        } else {
            alternatives.mapIndexedNotNull { index, alternative ->
                alternative.points.takeUnless { index == selected }
            }
        }
        return currentRoute.snapshot(
            progressMeters = progressMeters,
            cursorRestricted = cursorRestricted,
            accentArgb = accentArgb,
            alternativeRoutes = muted,
            trafficSegments = trafficSegments,
        )
    }

    private fun validateCombinedPointCount(routes: List<NativeRouteCandidate>) {
        val total = routes.sumOf { it.points.size.toLong() }
        require(total <= MAX_SESSION_ROUTE_POINTS.toLong()) {
            "Route alternatives have too many overlay points"
        }
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
        // Reuse the compiler's coordinate/ARGB/line/run guards before
        // retaining route data. This prevents a pathological classification
        // sequence from reaching the renderer as thousands of tiny runs.
        NativeRouteOverlayCompiler.compile(
            NativeRouteOverlaySnapshot(
                activeRuns = NativeMapOverlayPolicy.splitRouteByRestriction(points, restricted),
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

    private fun projectTraffic(
        preparedRoute: PreparedRoute,
        jams: List<NativeMapPoint> = trafficJamPoints,
    ): List<List<NativeMapPoint>> {
        if (!NativeRouteTrafficPolicy.withinDistanceBudget(preparedRoute.points.size, jams.size)) {
            return emptyList()
        }
        return NativeRouteTrafficPolicy.segments(preparedRoute.points, jams)
    }

    private fun emptySnapshot(): NativeRouteOverlaySnapshot =
        NativeRouteOverlaySnapshot.empty(accentArgb)

    private fun initialState(accentArgb: Long): NativeRouteOverlaySessionState {
        validateAccent(accentArgb)
        return NativeRouteOverlaySessionState(
            revision = NO_ROUTE_REVISION,
            trafficRevision = NO_TRAFFIC_REVISION,
            progressMeters = 0.0,
            totalDistanceMeters = 0.0,
            selectedAlternativeIndex = null,
            alternativeCount = 0,
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
            alternativeRoutes: List<List<NativeMapPoint>>,
            trafficSegments: List<List<NativeMapPoint>>,
        ): NativeRouteOverlaySnapshot {
            if (progressMeters <= 0.0) {
                return NativeRouteOverlaySnapshot(
                    activeRuns = NativeMapOverlayPolicy.splitRouteByRestriction(
                        points,
                        restricted,
                    ),
                    completedPoints = emptyList(),
                    accentArgb = accentArgb,
                    alternativeRoutes = alternativeRoutes,
                    trafficSegments = trafficSegments,
                )
            }
            if (totalDistanceMeters <= 0.0 || progressMeters >= totalDistanceMeters) {
                return NativeRouteOverlaySnapshot(
                    activeRuns = emptyList(),
                    completedPoints = points,
                    accentArgb = accentArgb,
                    alternativeRoutes = alternativeRoutes,
                    trafficSegments = trafficSegments,
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
                alternativeRoutes = alternativeRoutes,
                trafficSegments = trafficSegments,
            )
        }
    }

    private fun NativeMapPoint.toGeoPoint() =
        app.roadstr.core.geo.GeoPoint(latitude = latitude, longitude = longitude)

    companion object {
        private const val NO_ROUTE_REVISION = -1L
        private const val NO_TRAFFIC_REVISION = -1L
        // Leaves room for duplicated classification boundaries and the worst
        // alternating traffic runs in one bounded compiler payload.
        const val MAX_SESSION_ROUTE_POINTS = 80_000
    }
}
