package app.roadstr.feature.navigation

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.RouteProgress
import app.roadstr.core.navigation.OffRouteDetector
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeRouteOverlaySession
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.voice.NativeNavigationGuidance
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NativeActiveNavigationSnapshot(
    val revision: Long,
    val active: Boolean,
    val rerouting: Boolean,
    val arrived: Boolean,
    val progressMeters: Double,
    val routePointIndex: Int,
    val displayedStepIndex: Int?,
    val voiceMuted: Boolean,
    val rerouteRequest: NativeNavigationRerouteRequest?,
    val voiceCue: NativeNavigationVoiceCue?,
) {
    companion object {
        const val NO_NAVIGATION_REVISION = -1L

        fun inactive(revision: Long = NO_NAVIGATION_REVISION) =
            NativeActiveNavigationSnapshot(
                revision = revision,
                active = false,
                rerouting = false,
                arrived = false,
                progressMeters = 0.0,
                routePointIndex = 0,
                displayedStepIndex = null,
                voiceMuted = false,
                rerouteRequest = null,
                voiceCue = null,
            )
    }
}

data class NativeNavigationRerouteRequest(
    val sequence: Long,
    val revision: Long,
    val origin: NativeMapPoint,
    val destination: NativeMapPoint,
    val mode: NativeRouteTransportMode,
    val speedKilometresPerHour: Double,
    val headingDegrees: Double?,
    val straightLineDistanceMeters: Double,
)

data class NativeNavigationVoiceCue(
    val sequence: Long,
    val instruction: String,
    val distanceMeters: Int,
)

/**
 * Foreground, value-only turn-by-turn owner for the standalone road-test app.
 *
 * It projects monotonic GPS progress into the existing route overlay and HUD.
 * It owns no sensor, network service, speech engine, settings or persistence.
 * Reroutes leave this boundary as bounded value requests and return as parsed
 * routes; arrival is decided from true GPS-to-destination distance, never from
 * provider route progress alone.
 */
class NativeActiveNavigationSession(
    private val hudSession: NativeNavigationHudSession,
    private val overlaySession: NativeRouteOverlaySession,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(NativeActiveNavigationSnapshot.inactive())

    private var revision = NativeActiveNavigationSnapshot.NO_NAVIGATION_REVISION
    private var route: RoutingParsedRoute? = null
    private var destination: NativeMapPoint? = null
    private var mode = NativeRouteTransportMode.Driving
    private var points = emptyList<GeoPoint>()
    private var cumulative = emptyList<Double>()
    private var stepCumulative = emptyList<Double>()
    private var routePointIndex = 0
    private var routeSegmentIndex = 0
    private var passedStepIndex = 0
    private var progressMeters = 0.0
    private var lastFixSequence = NO_FIX_SEQUENCE
    private var speedKilometresPerHour = 0.0
    private var altitudeMeters: Double? = null
    private var voiceMuted = false
    private var nowLabel = ""
    private var minimumDestinationDistanceMeters = Double.POSITIVE_INFINITY
    private var rerouteSequence = 0L
    private var pendingReroute: NativeNavigationRerouteRequest? = null
    private var voiceCueSequence = 0L
    private var voiceCue: NativeNavigationVoiceCue? = null
    private val farAnnouncedSteps = mutableSetOf<Int>()
    private val nearAnnouncedSteps = mutableSetOf<Int>()
    private val offRouteDetector = OffRouteDetector()

    val state: StateFlow<NativeActiveNavigationSnapshot> = _state.asStateFlow()

    fun start(
        revision: Long,
        route: RoutingParsedRoute,
        destination: NativeMapPoint,
        mode: NativeRouteTransportMode,
        nowLabel: String,
    ): Boolean = synchronized(lock) {
        require(revision >= 0) { "Navigation revision must be non-negative" }
        if (revision <= this.revision) return false
        require(destination.latitude.isFinite() && destination.latitude in -90.0..90.0)
        require(destination.longitude.isFinite() && destination.longitude in -180.0..180.0)
        require(mode != NativeRouteTransportMode.Transit) {
            "Foreground road navigation does not accept transit mode"
        }
        val prepared = prepare(route)

        val input = input(
            route = route,
            stepCumulative = prepared.stepCumulative,
            geometryTotalMeters = prepared.cumulative.last(),
            progressMeters = 0.0,
            passedStepIndex = 0,
            speedKilometresPerHour = 0.0,
            altitudeMeters = null,
            voiceMuted = false,
        )
        if (!hudSession.show(revision, input, nowLabel)) return false

        this.revision = revision
        this.route = route
        this.destination = destination
        this.mode = mode
        points = prepared.points
        cumulative = prepared.cumulative
        stepCumulative = prepared.stepCumulative
        routePointIndex = 0
        routeSegmentIndex = 0
        passedStepIndex = 0
        progressMeters = 0.0
        lastFixSequence = NO_FIX_SEQUENCE
        speedKilometresPerHour = 0.0
        altitudeMeters = null
        voiceMuted = false
        this.nowLabel = nowLabel.take(MAX_NOW_LABEL_CHARS)
        minimumDestinationDistanceMeters = Double.POSITIVE_INFINITY
        pendingReroute = null
        resetVoiceCues()
        offRouteDetector.reset()
        publish()
        true
    }

    fun submitFix(
        sequence: Long,
        point: NativeMapPoint,
        speedMetersPerSecond: Double,
        altitudeMeters: Double,
        accuracyMeters: Double = 0.0,
        headingDegrees: Double? = null,
    ): Boolean = synchronized(lock) {
        val currentRoute = route ?: return false
        if (sequence <= lastFixSequence) return false
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0)
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0)
        require(speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0)
        require(altitudeMeters.isFinite())
        require(accuracyMeters.isFinite() && accuracyMeters >= 0.0)
        require(headingDegrees == null || headingDegrees.isFinite())

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
        if (hasArrived(point, accuracyMeters)) {
            finishArrival()
            return true
        }
        maybePublishVoiceCue(currentRoute)
        if (pendingReroute == null) {
            maybeRequestReroute(
                point = point,
                accuracyMeters = accuracyMeters,
                headingDegrees = headingDegrees,
            )
        }
        publish()
        hudUpdated || overlayUpdated
    }

    fun completeReroute(
        requestSequence: Long,
        route: RoutingParsedRoute,
    ): Boolean = synchronized(lock) {
        val request = pendingReroute
            ?.takeIf { value -> value.sequence == requestSequence }
            ?: return false
        val nextRevision = revision + 1L
        val prepared = prepare(route)
        if (
            !overlaySession.submitRoute(
                revision = nextRevision,
                points = route.polyline.map { value ->
                    NativeMapPoint(value.latitude, value.longitude)
                },
                restricted = List(route.polyline.size) { false },
            )
        ) {
            return false
        }
        val nextInput = input(
            route = route,
            stepCumulative = prepared.stepCumulative,
            geometryTotalMeters = prepared.cumulative.last(),
            progressMeters = 0.0,
            passedStepIndex = 0,
            speedKilometresPerHour = speedKilometresPerHour,
            altitudeMeters = altitudeMeters,
            voiceMuted = voiceMuted,
        )
        if (!hudSession.show(nextRevision, nextInput, nowLabel)) {
            overlaySession.clearRoute(nextRevision)
            finishStopped(nextRevision)
            return false
        }
        revision = nextRevision
        this.route = route
        points = prepared.points
        cumulative = prepared.cumulative
        stepCumulative = prepared.stepCumulative
        routePointIndex = 0
        routeSegmentIndex = 0
        passedStepIndex = 0
        progressMeters = 0.0
        lastFixSequence = NO_FIX_SEQUENCE
        pendingReroute = null
        resetVoiceCues()
        minimumDestinationDistanceMeters = GeoMath.distanceMeters(
            GeoPoint(request.origin.latitude, request.origin.longitude),
            GeoPoint(request.destination.latitude, request.destination.longitude),
        )
        offRouteDetector.reset()
        publish()
        true
    }

    fun failReroute(requestSequence: Long): Boolean = synchronized(lock) {
        if (pendingReroute?.sequence != requestSequence) return false
        pendingReroute = null
        offRouteDetector.reset()
        publish()
        true
    }

    fun dismissArrival(revision: Long): Boolean = synchronized(lock) {
        if (revision != this.revision || !_state.value.arrived) return false
        _state.value = NativeActiveNavigationSnapshot.inactive(revision)
        true
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
        finishStopped(revision)
        true
    }

    private fun prepare(route: RoutingParsedRoute): PreparedNavigationRoute {
        require(route.polyline.size >= 2) { "Navigation route requires drawable geometry" }
        require(route.steps.isNotEmpty()) { "Navigation route requires at least one step" }
        require(route.totalDistanceM.isFinite() && route.totalDistanceM > 0.0)
        require(route.totalDurationS.isFinite() && route.totalDurationS > 0.0)
        val preparedPoints = route.polyline.map { value ->
            require(
                value.latitude.isFinite() && value.latitude in -90.0..90.0 &&
                    value.longitude.isFinite() && value.longitude in -180.0..180.0,
            )
            GeoPoint(value.latitude, value.longitude)
        }
        val preparedCumulative = RouteProgress.cumulativeDistances(preparedPoints)
        require(preparedCumulative.last() > 0.0)
        val stepIndices = RouteProgress.nearestIndicesAlong(
            polyline = preparedPoints,
            points = route.steps.map { value ->
                GeoPoint(value.location.latitude, value.location.longitude)
            },
        )
        return PreparedNavigationRoute(
            points = preparedPoints,
            cumulative = preparedCumulative,
            stepCumulative = stepIndices.map(preparedCumulative::get),
        )
    }

    private fun hasArrived(point: NativeMapPoint, accuracyMeters: Double): Boolean {
        val currentRoute = route ?: return false
        val target = destination ?: return false
        val distance = GeoMath.distanceMeters(point.toGeoPoint(), target.toGeoPoint())
        minimumDestinationDistanceMeters = min(minimumDestinationDistanceMeters, distance)
        val onLastStep = passedStepIndex >= currentRoute.steps.lastIndex
        val normalRadius = if (mode == NativeRouteTransportMode.Walking) {
            WALKING_ARRIVAL_RADIUS_METERS
        } else {
            ROAD_ARRIVAL_RADIUS_METERS
        }
        val effectiveRadius = if (onLastStep) {
            (accuracyMeters * OffRouteDetector.ACCURACY_MARGIN).coerceIn(
                MIN_LAST_STEP_ARRIVAL_RADIUS_METERS,
                MAX_LAST_STEP_ARRIVAL_RADIUS_METERS,
            )
        } else {
            normalRadius
        }
        if (distance < effectiveRadius) return true
        return onLastStep &&
            minimumDestinationDistanceMeters <= CLOSEST_APPROACH_RADIUS_METERS &&
            distance > minimumDestinationDistanceMeters + MOVING_AWAY_MARGIN_METERS
    }

    private fun maybeRequestReroute(
        point: NativeMapPoint,
        accuracyMeters: Double,
        headingDegrees: Double?,
    ) {
        val currentRoute = route ?: return
        val target = destination ?: return
        if (speedKilometresPerHour < MIN_REROUTE_SPEED_KMH) return
        val distance = nearestActiveRouteDistance(point.toGeoPoint()) ?: return
        val deviated = offRouteDetector.sawDeviation(distance, accuracyMeters)
        val reversed = if (
            !deviated &&
            distance >= OffRouteDetector.NOISE_FLOOR_METERS &&
            speedKilometresPerHour > REVERSE_DIRECTION_MIN_SPEED_KMH &&
            headingDegrees != null
        ) {
            currentRoute.steps.getOrNull(passedStepIndex + 1)?.let { step ->
                val expected = GeoMath.bearingBetween(point.toGeoPoint(), step.location.toGeoPoint())
                angularDifference(headingDegrees, expected) > REVERSE_DIRECTION_THRESHOLD_DEGREES
            } ?: false
        } else {
            false
        }
        if (!deviated && !reversed) return
        pendingReroute = NativeNavigationRerouteRequest(
            sequence = ++rerouteSequence,
            revision = revision,
            origin = point,
            destination = target,
            mode = mode,
            speedKilometresPerHour = speedKilometresPerHour,
            headingDegrees = headingDegrees?.let(::normalizeDegrees),
            straightLineDistanceMeters = GeoMath.distanceMeters(
                point.toGeoPoint(),
                target.toGeoPoint(),
            ),
        )
    }

    private fun nearestActiveRouteDistance(point: GeoPoint): Double? {
        if (points.size < 2) return null
        val lastSegment = points.size - 2
        val start = max(0, routeSegmentIndex - ROUTE_SEGMENT_BACK_WINDOW)
        val end = min(lastSegment, routeSegmentIndex + ROUTE_SEGMENT_AHEAD_WINDOW)
        var bestDistance = Double.POSITIVE_INFINITY
        var bestIndex = start

        fun scan(from: Int, through: Int) {
            for (index in from..through) {
                val distance = GeoMath.distanceToSegmentMeters(
                    point,
                    points[index],
                    points[index + 1],
                )
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
        }

        scan(start, end)
        if (
            bestDistance > ROUTE_SEGMENT_RECOVERY_METERS &&
            (start > 0 || end < lastSegment)
        ) {
            bestDistance = Double.POSITIVE_INFINITY
            scan(0, lastSegment)
        }
        routeSegmentIndex = bestIndex
        return bestDistance
    }

    private fun finishArrival() {
        val completedRevision = revision
        hudSession.hide(completedRevision)
        overlaySession.clearRoute(completedRevision)
        clearRuntime()
        _state.value = NativeActiveNavigationSnapshot(
            revision = completedRevision,
            active = false,
            rerouting = false,
            arrived = true,
            progressMeters = 0.0,
            routePointIndex = 0,
            displayedStepIndex = null,
            voiceMuted = false,
            rerouteRequest = null,
            voiceCue = null,
        )
    }

    private fun finishStopped(revision: Long) {
        this.revision = revision
        clearRuntime()
        _state.value = NativeActiveNavigationSnapshot.inactive(revision)
    }

    private fun clearRuntime() {
        route = null
        destination = null
        mode = NativeRouteTransportMode.Driving
        points = emptyList()
        cumulative = emptyList()
        stepCumulative = emptyList()
        routePointIndex = 0
        routeSegmentIndex = 0
        passedStepIndex = 0
        progressMeters = 0.0
        lastFixSequence = NO_FIX_SEQUENCE
        speedKilometresPerHour = 0.0
        altitudeMeters = null
        voiceMuted = false
        nowLabel = ""
        minimumDestinationDistanceMeters = Double.POSITIVE_INFINITY
        pendingReroute = null
        resetVoiceCues()
        offRouteDetector.reset()
    }

    private fun maybePublishVoiceCue(currentRoute: RoutingParsedRoute) {
        if (voiceMuted) return
        val stepIndex = passedStepIndex + 1
        val step = currentRoute.steps.getOrNull(stepIndex) ?: return
        val maneuverProgress = stepCumulative.getOrNull(stepIndex) ?: return
        val remaining = (maneuverProgress - progressMeters).coerceAtLeast(0.0)
        val thresholds = NativeNavigationGuidance.thresholds(speedKilometresPerHour, mode)
        val distance = when {
            remaining < thresholds.nearMeters + NEAR_TRIGGER_MARGIN_METERS &&
                nearAnnouncedSteps.add(stepIndex) -> 0

            remaining < thresholds.farMeters + FAR_TRIGGER_MARGIN_METERS &&
                remaining >= thresholds.nearMeters + FAR_NEAR_GAP_METERS &&
                farAnnouncedSteps.add(stepIndex) -> NativeNavigationGuidance.spokenDistanceMeters(
                remainingMeters = remaining,
                imminentBelowMeters = thresholds.nearMeters,
            )

            else -> return
        }
        val instruction = step.instruction.trim().take(MAX_VOICE_INSTRUCTION_CHARS)
        if (instruction.isEmpty()) return
        voiceCue = NativeNavigationVoiceCue(++voiceCueSequence, instruction, distance)
    }

    private fun resetVoiceCues() {
        voiceCue = null
        farAnnouncedSteps.clear()
        nearAnnouncedSteps.clear()
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
            rerouting = pendingReroute != null,
            arrived = false,
            progressMeters = progressMeters,
            routePointIndex = routePointIndex,
            displayedStepIndex = hudSession.state.value.current?.index,
            voiceMuted = voiceMuted,
            rerouteRequest = pendingReroute,
            voiceCue = voiceCue,
        )
    }

    private fun NativeMapPoint.toGeoPoint() = GeoPoint(latitude, longitude)

    private fun app.roadstr.core.network.RoutingResponsePoint.toGeoPoint() =
        GeoPoint(latitude, longitude)

    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    private fun angularDifference(left: Double, right: Double): Double {
        val difference = kotlin.math.abs(normalizeDegrees(left) - normalizeDegrees(right))
        return min(difference, 360.0 - difference)
    }

    private data class PreparedNavigationRoute(
        val points: List<GeoPoint>,
        val cumulative: List<Double>,
        val stepCumulative: List<Double>,
    )

    companion object {
        const val STEP_ADVANCE_TOLERANCE_METERS = 15.0
        const val MAX_NOW_LABEL_CHARS = 80
        const val WALKING_ARRIVAL_RADIUS_METERS = 15.0
        const val ROAD_ARRIVAL_RADIUS_METERS = 40.0
        const val MIN_LAST_STEP_ARRIVAL_RADIUS_METERS = 10.0
        const val MAX_LAST_STEP_ARRIVAL_RADIUS_METERS = 40.0
        const val CLOSEST_APPROACH_RADIUS_METERS = 60.0
        const val MOVING_AWAY_MARGIN_METERS = 20.0
        const val MIN_REROUTE_SPEED_KMH = 1.0
        const val REVERSE_DIRECTION_MIN_SPEED_KMH = 20.0
        const val REVERSE_DIRECTION_THRESHOLD_DEGREES = 135.0
        const val ROUTE_SEGMENT_BACK_WINDOW = 100
        const val ROUTE_SEGMENT_AHEAD_WINDOW = 500
        const val ROUTE_SEGMENT_RECOVERY_METERS = 100.0
        const val FAR_TRIGGER_MARGIN_METERS = 20.0
        const val NEAR_TRIGGER_MARGIN_METERS = 30.0
        const val FAR_NEAR_GAP_METERS = 20.0
        const val MAX_VOICE_INSTRUCTION_CHARS = 1_000
        private const val MAX_SPEED_KMH = 1_000.0
        private const val NO_FIX_SEQUENCE = -1L
    }
}
