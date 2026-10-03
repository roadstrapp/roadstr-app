package app.roadstr.feature.navigation

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.geo.RouteProgress
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeRouteOverlaySession
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NativeActiveNavigationSnapshot(
    val revision: Long,
    val active: Boolean,
    val progressMeters: Double,
    val routePointIndex: Int,
    val displayedStepIndex: Int?,
    val voiceMuted: Boolean,
) {
    companion object {
        const val NO_NAVIGATION_REVISION = -1L

        fun inactive(revision: Long = NO_NAVIGATION_REVISION) =
            NativeActiveNavigationSnapshot(
                revision = revision,
                active = false,
                progressMeters = 0.0,
                routePointIndex = 0,
                displayedStepIndex = null,
                voiceMuted = false,
            )
    }
}

/**
 * Foreground, value-only turn-by-turn owner for the standalone road-test app.
 *
 * It projects monotonic GPS progress into the existing route overlay and HUD.
 * It owns no sensor, service, rerouter, speech engine, settings or persistence.
 */
class NativeActiveNavigationSession(
    private val hudSession: NativeNavigationHudSession,
    private val overlaySession: NativeRouteOverlaySession,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeActiveNavigationSnapshot.inactive())

    private var revision = NativeActiveNavigationSnapshot.NO_NAVIGATION_REVISION
    private var route: RoutingParsedRoute? = null
    private var points = emptyList<GeoPoint>()
    private var cumulative = emptyList<Double>()
    private var stepCumulative = emptyList<Double>()
    private var routePointIndex = 0
    private var passedStepIndex = 0
    private var progressMeters = 0.0
    private var lastFixSequence = NO_FIX_SEQUENCE
    private var speedKilometresPerHour = 0.0
    private var altitudeMeters: Double? = null
    private var voiceMuted = false
    private var nowLabel = ""

    val state: StateFlow<NativeActiveNavigationSnapshot> = _state.asStateFlow()

    fun start(
        revision: Long,
        route: RoutingParsedRoute,
        nowLabel: String,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Navigation revision must be non-negative" }
        if (revision <= this.revision) return false
        require(route.polyline.size >= 2) { "Navigation route requires drawable geometry" }
        require(route.steps.isNotEmpty()) { "Navigation route requires at least one step" }
        require(route.totalDistanceM.isFinite() && route.totalDistanceM > 0.0) {
            "Navigation route distance must be finite and positive"
        }
        require(route.totalDurationS.isFinite() && route.totalDurationS > 0.0) {
            "Navigation route duration must be finite and positive"
        }
        val nextPoints = route.polyline.map { point ->
            require(
                point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
                    point.longitude.isFinite() && point.longitude in -180.0..180.0,
            ) { "Navigation route contains an invalid coordinate" }
            GeoPoint(point.latitude, point.longitude)
        }
        val nextCumulative = RouteProgress.cumulativeDistances(nextPoints)
        require(nextCumulative.last() > 0.0) { "Navigation route geometry must have positive length" }
        val stepIndices = RouteProgress.nearestIndicesAlong(
            polyline = nextPoints,
            points = route.steps.map { step ->
                GeoPoint(step.location.latitude, step.location.longitude)
            },
        )
        val nextStepCumulative = stepIndices.map(nextCumulative::get)

        val input = input(
            route = route,
            stepCumulative = nextStepCumulative,
            geometryTotalMeters = nextCumulative.last(),
            progressMeters = 0.0,
            passedStepIndex = 0,
            speedKilometresPerHour = 0.0,
            altitudeMeters = null,
            voiceMuted = false,
        )
        if (!hudSession.show(revision, input, nowLabel)) return false

        this.revision = revision
        this.route = route
        points = nextPoints
        cumulative = nextCumulative
        stepCumulative = nextStepCumulative
        routePointIndex = 0
        passedStepIndex = 0
        progressMeters = 0.0
        lastFixSequence = NO_FIX_SEQUENCE
        speedKilometresPerHour = 0.0
        altitudeMeters = null
        voiceMuted = false
        this.nowLabel = nowLabel.take(MAX_NOW_LABEL_CHARS)
        publish()
        true
    }

    fun submitFix(
        sequence: Long,
        point: NativeMapPoint,
        speedMetersPerSecond: Double,
        altitudeMeters: Double,
    ): Boolean = synchronized(lock) {
        val currentRoute = route ?: return false
        if (sequence <= lastFixSequence) return false
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0)
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0)
        require(speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0)
        require(altitudeMeters.isFinite())

        val nearest = RouteProgress.nearestIndexNear(
            polyline = points,
            position = GeoPoint(point.latitude, point.longitude),
            hint = routePointIndex,
        )
        routePointIndex = max(routePointIndex, nearest)
        progressMeters = max(progressMeters, cumulative[routePointIndex])
        while (
            passedStepIndex + 1 < stepCumulative.size &&
            stepCumulative[passedStepIndex + 1] <= progressMeters + STEP_ADVANCE_TOLERANCE_METERS
        ) {
            passedStepIndex++
        }
        lastFixSequence = sequence
        speedKilometresPerHour = (speedMetersPerSecond * 3.6).coerceAtMost(MAX_SPEED_KMH)
        this.altitudeMeters = altitudeMeters

        val hudUpdated = hudSession.update(
            revision = revision,
            input = input(
                route = currentRoute,
                stepCumulative = stepCumulative,
                geometryTotalMeters = cumulative.last(),
                progressMeters = progressMeters,
                passedStepIndex = passedStepIndex,
                speedKilometresPerHour = speedKilometresPerHour,
                altitudeMeters = altitudeMeters,
                voiceMuted = voiceMuted,
            ),
            nowLabel = nowLabel,
        )
        val overlayUpdated = overlaySession.updateProgress(
            revision = revision,
            progressMeters = progressMeters,
            cursorRestricted = false,
        )
        publish()
        hudUpdated || overlayUpdated
    }

    fun toggleVoice(revision: Long): Boolean = synchronized(lock) {
        val currentRoute = route ?: return false
        if (revision != this.revision) return false
        voiceMuted = !voiceMuted
        val updated = hudSession.update(
            revision = revision,
            input = input(
                route = currentRoute,
                stepCumulative = stepCumulative,
                geometryTotalMeters = cumulative.last(),
                progressMeters = progressMeters,
                passedStepIndex = passedStepIndex,
                speedKilometresPerHour = speedKilometresPerHour,
                altitudeMeters = altitudeMeters,
                voiceMuted = voiceMuted,
            ),
            nowLabel = nowLabel,
        )
        publish()
        updated
    }

    fun stop(revision: Long): Boolean = synchronized(lock) {
        require(revision >= 0) { "Navigation revision must be non-negative" }
        if (revision < this.revision || route == null) return false
        hudSession.hide(revision)
        overlaySession.clearRoute(revision)
        this.revision = revision
        route = null
        points = emptyList()
        cumulative = emptyList()
        stepCumulative = emptyList()
        routePointIndex = 0
        passedStepIndex = 0
        progressMeters = 0.0
        lastFixSequence = NO_FIX_SEQUENCE
        speedKilometresPerHour = 0.0
        altitudeMeters = null
        voiceMuted = false
        nowLabel = ""
        _state.value = NativeActiveNavigationSnapshot.inactive(revision)
        true
    }

    private fun input(
        route: RoutingParsedRoute,
        stepCumulative: List<Double>,
        geometryTotalMeters: Double,
        progressMeters: Double,
        passedStepIndex: Int,
        speedKilometresPerHour: Double,
        altitudeMeters: Double?,
        voiceMuted: Boolean,
    ): NativeNavigationHudInput {
        val displayedStepIndex = if (passedStepIndex + 1 < route.steps.size) {
            passedStepIndex + 1
        } else {
            passedStepIndex.coerceAtMost(route.steps.lastIndex)
        }
        val distanceToManeuver = if (passedStepIndex + 1 < stepCumulative.size) {
            (stepCumulative[passedStepIndex + 1] - progressMeters).coerceAtLeast(0.0)
        } else {
            0.0
        }
        val fraction = (progressMeters / geometryTotalMeters).coerceIn(0.0, 1.0)
        val providerProgress = route.totalDistanceM * fraction
        val remainingFraction = 1.0 - fraction
        val speedLimit = route.speedLimits
            .lastOrNull { value -> value.distFromStartM <= providerProgress }
            ?.speedKmh
        return NativeNavigationHudInput(
            route = route,
            stepIndex = displayedStepIndex,
            distanceToManeuverM = distanceToManeuver,
            remainingDistanceM = route.totalDistanceM * remainingFraction,
            remainingSeconds = route.totalDurationS * remainingFraction,
            speedKmh = speedKilometresPerHour,
            speedLimitKmh = speedLimit,
            altitudeM = altitudeMeters,
            showAltitude = false,
            voiceMuted = voiceMuted,
        )
    }

    private fun publish() {
        _state.value = NativeActiveNavigationSnapshot(
            revision = revision,
            active = route != null,
            progressMeters = progressMeters,
            routePointIndex = routePointIndex,
            displayedStepIndex = hudSession.state.value.current?.index,
            voiceMuted = voiceMuted,
        )
    }

    companion object {
        const val STEP_ADVANCE_TOLERANCE_METERS = 15.0
        const val MAX_NOW_LABEL_CHARS = 80
        private const val MAX_SPEED_KMH = 1_000.0
        private const val NO_FIX_SEQUENCE = -1L
    }
}
