package app.roadstr.core.network

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class RoutingAvoidanceMode {
    HIGHWAYS_AND_TOLLS,
    OFF_ROAD,
}

enum class RoutingAvoidanceAttempt {
    HARD,
    SOFT,
    TRACKS,
}

enum class RoutingAvoidanceAttemptOutcome {
    SUCCESS,
    ROUTING_FAILURE,
}

data class RoutingAvoidanceDecision(
    val nextAttempt: RoutingAvoidanceAttempt? = null,
    val finalRoute: RoutingParsedRoute? = null,
    val propagateFailure: Boolean = false,
)

/** Socket-free state machine for Valhalla hard-to-soft and track avoidance. */
class RoutingAvoidanceProtocol(mode: RoutingAvoidanceMode) {
    private var expectedAttempt = if (mode == RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS) {
        RoutingAvoidanceAttempt.HARD
    } else {
        RoutingAvoidanceAttempt.TRACKS
    }
    private var completed = false

    val initialAttempt: RoutingAvoidanceAttempt
        get() = expectedAttempt

    val isCompleted: Boolean
        get() = completed

    fun accept(
        attempt: RoutingAvoidanceAttempt,
        outcome: RoutingAvoidanceAttemptOutcome,
        route: RoutingParsedRoute? = null,
    ): RoutingAvoidanceDecision {
        if (completed || attempt != expectedAttempt) return NONE

        if (
            attempt == RoutingAvoidanceAttempt.HARD &&
            outcome == RoutingAvoidanceAttemptOutcome.ROUTING_FAILURE
        ) {
            expectedAttempt = RoutingAvoidanceAttempt.SOFT
            return RoutingAvoidanceDecision(nextAttempt = RoutingAvoidanceAttempt.SOFT)
        }

        completed = true
        if (outcome == RoutingAvoidanceAttemptOutcome.ROUTING_FAILURE) {
            return RoutingAvoidanceDecision(propagateFailure = true)
        }
        checkNotNull(route) { "A successful avoidance attempt requires a route" }
        return RoutingAvoidanceDecision(finalRoute = route)
    }

    private companion object {
        val NONE = RoutingAvoidanceDecision()
    }
}

data class RoutingRetimePlan(
    val cumulativeDistancesMeters: List<Double>,
    val seaCrossings: List<Int>,
    val sampleIndices: List<Int>,
    val waypoints: String,
    val shapeLengthMeters: Double,
)

/** Pure counterpart of Roadstr's per-leg OSRM avoidance-route re-timing. */
object RoutingRetimePolicy {
    const val SPACING_METERS = 5_000.0
    const val MAX_WAYPOINTS = 120
    const val MAX_ROAD_STEP_METERS = 25_000.0

    fun buildPlan(route: RoutingParsedRoute): RoutingRetimePlan? {
        val line = route.polyline
        if (line.size < 2 || route.totalDistanceM <= 0.0) return null

        val cumulative = MutableList(line.size) { 0.0 }
        val crossings = MutableList(line.size) { 0 }
        for (index in 1 until line.size) {
            val step = GeoMath.distanceMeters(line[index - 1].geoPoint(), line[index].geoPoint())
            if (!step.isFinite()) return null
            cumulative[index] = cumulative[index - 1] + step
            crossings[index] = crossings[index - 1] +
                if (step > MAX_ROAD_STEP_METERS) 1 else 0
        }
        val shapeLength = cumulative.last()
        if (!shapeLength.isFinite() || shapeLength <= 0.0) return null

        val sampleCount = (shapeLength / SPACING_METERS)
            .roundToInt()
            .coerceIn(3, MAX_WAYPOINTS - 1) + 1
        val indices = (0 until sampleCount).map { index ->
            (index * (line.size - 1).toDouble() / (sampleCount - 1)).roundToInt()
        }
        val waypoints = indices.joinToString(";") { index ->
            val point = line[index]
            "${point.longitude.fixed(5)},${point.latitude.fixed(5)}"
        }

        return RoutingRetimePlan(
            cumulativeDistancesMeters = cumulative.toList(),
            seaCrossings = crossings.toList(),
            sampleIndices = indices,
            waypoints = waypoints,
            shapeLengthMeters = shapeLength,
        )
    }

    fun apply(
        route: RoutingParsedRoute,
        plan: RoutingRetimePlan,
        legs: List<OsrmRetimeLeg>?,
    ): RoutingParsedRoute {
        if (legs == null || legs.size != plan.sampleIndices.size - 1) return route

        var seconds = 0.0
        var verifiedMeters = 0.0
        var ferryMeters = 0.0
        legs.forEachIndexed { index, leg ->
            val start = plan.sampleIndices[index]
            val end = plan.sampleIndices[index + 1]
            val arcMeters = plan.cumulativeDistancesMeters[end] -
                plan.cumulativeDistancesMeters[start]
            if (arcMeters <= 0.0) return@forEachIndexed

            val legMeters = leg.distanceM
            val legSeconds = leg.durationS
            if (
                legMeters == null || legSeconds == null ||
                !legMeters.isFinite() || !legSeconds.isFinite()
            ) {
                return route
            }

            val crossesWater = plan.seaCrossings[end] > plan.seaCrossings[start]
            if (
                !crossesWater &&
                abs(legMeters - arcMeters) <= max(150.0, 0.2 * arcMeters)
            ) {
                seconds += legSeconds
                verifiedMeters += arcMeters
            } else {
                seconds += route.totalDurationS * arcMeters / plan.shapeLengthMeters
                if (crossesWater) ferryMeters += arcMeters
            }
        }

        val roadLength = plan.shapeLengthMeters - ferryMeters
        if (verifiedMeters < 0.5 * roadLength || seconds <= 0.0) return route

        return route.copy(
            totalDurationS = seconds,
            fromAvoidanceRouter = true,
        )
    }

    private fun RoutingResponsePoint.geoPoint() = GeoPoint(latitude, longitude)

    private fun Double.fixed(digits: Int): String =
        String.format(Locale.ROOT, "%.${digits}f", this)
}
