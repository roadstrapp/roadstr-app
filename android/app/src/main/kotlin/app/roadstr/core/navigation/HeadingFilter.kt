package app.roadstr.core.navigation

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import kotlin.math.abs

data class RouteLocalBearing(val distanceMeters: Double, val bearingDegrees: Double)

/** GPS travel-heading filter and route tangent smoothing, independent of Android location APIs. */
class HeadingFilter(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var pendingReversal: Double? = null
    private var heldTicks = 0
    private var moving = false
    private var lastMotionAtMillis: Long? = null
    private var pendingRouteSnap: Double? = null

    fun reset() {
        pendingReversal = null
        heldTicks = 0
        pendingRouteSnap = null
    }

    fun updateMotion(speedKilometresPerHour: Double): Boolean {
        lastMotionAtMillis = nowMillis()
        if (!speedKilometresPerHour.isFinite() || speedKilometresPerHour < 0) {
            moving = false
            return false
        }
        if (moving) {
            if (speedKilometresPerHour < MOVING_EXIT_KMH) moving = false
        } else if (speedKilometresPerHour > MOVING_ENTER_KMH) {
            moving = true
        }
        return moving
    }

    val isMoving: Boolean
        get() {
            val last = lastMotionAtMillis ?: return false
            if (nowMillis() - last > MOTION_STALENESS_MILLIS) return false
            return moving
        }

    fun resolve(
        currentDegrees: Double,
        from: GeoPoint?,
        to: GeoPoint,
        speedKilometresPerHour: Double,
        accuracyMeters: Double,
        providerHeadingDegrees: Double?,
        navigating: Boolean,
        routeLocalBearingAt: ((GeoPoint) -> RouteLocalBearing?)? = null,
    ): Double {
        val fallback = if (
            providerHeadingDegrees != null &&
            providerHeadingDegrees.isFinite() &&
            providerHeadingDegrees > 0
        ) {
            normalize(providerHeadingDegrees)
        } else {
            currentDegrees
        }
        if (from == null || !usesTravelHeading(speedKilometresPerHour)) return fallback
        if (!hasReliableMovement(from, to, accuracyMeters)) return fallback

        val bearing = GeoMath.bearingBetween(from, to)
        if (!bearing.isFinite()) return currentDegrees
        if (!navigating || angleBetween(bearing, currentDegrees) <= REVERSAL_DEGREES) {
            resetReversal()
            return towardRoute(
                bearing,
                to,
                speedKilometresPerHour,
                navigating,
                routeLocalBearingAt,
            )
        }
        if (contradictsRoute(bearing, to, routeLocalBearingAt)) {
            heldTicks++
            pendingReversal = null
            return currentDegrees
        }
        val pending = pendingReversal
        if (pending != null && angleBetween(bearing, pending) <= AGREEMENT_DEGREES) {
            resetReversal()
            return towardRoute(
                bearing,
                to,
                speedKilometresPerHour,
                navigating,
                routeLocalBearingAt,
            )
        }
        pendingReversal = bearing
        return currentDegrees
    }

    private fun resetReversal() {
        pendingReversal = null
        heldTicks = 0
    }

    private fun towardRoute(
        heading: Double,
        at: GeoPoint,
        speedKilometresPerHour: Double,
        navigating: Boolean,
        routeLocalBearingAt: ((GeoPoint) -> RouteLocalBearing?)?,
    ): Double {
        if (!navigating || speedKilometresPerHour <= SMOOTHING_MIN_SPEED_KMH) {
            pendingRouteSnap = null
            return heading
        }
        val local = routeLocalBearingAt?.invoke(at)
        if (local == null || local.distanceMeters > ON_ROUTE_METERS) {
            pendingRouteSnap = null
            return heading
        }
        val delta = signedDelta(local.bearingDegrees, heading)
        if (abs(delta) > SNAP_TO_ROUTE_DEGREES) {
            val pending = pendingRouteSnap
            if (pending != null && angleBetween(local.bearingDegrees, pending) <= AGREEMENT_DEGREES) {
                pendingRouteSnap = null
                return normalize(heading + signedDelta(local.bearingDegrees, heading) * SMOOTHING_FACTOR)
            }
            pendingRouteSnap = local.bearingDegrees
            return heading
        }
        pendingRouteSnap = null
        return normalize(heading + delta * SMOOTHING_FACTOR)
    }

    private fun contradictsRoute(
        bearing: Double,
        at: GeoPoint,
        routeLocalBearingAt: ((GeoPoint) -> RouteLocalBearing?)?,
    ): Boolean {
        if (heldTicks >= MAX_HELD_TICKS) return false
        val local = routeLocalBearingAt?.invoke(at) ?: return false
        if (local.distanceMeters > ON_ROUTE_METERS) return false
        return angleBetween(bearing, local.bearingDegrees) > AGAINST_ROUTE_DEGREES
    }

    companion object {
        const val MIN_TRAVEL_HEADING_SPEED_KMH = 3.0
        const val MOVING_ENTER_KMH = 5.0
        const val MOVING_EXIT_KMH = 2.0
        private const val MOTION_STALENESS_MILLIS = 6_000L
        private const val MIN_MOVE_METERS = 8.0
        private const val REVERSAL_DEGREES = 100.0
        private const val AGREEMENT_DEGREES = 45.0
        private const val AGAINST_ROUTE_DEGREES = 100.0
        private const val ON_ROUTE_METERS = 35.0
        private const val MAX_HELD_TICKS = 5
        private const val SMOOTHING_MIN_SPEED_KMH = 5.0
        private const val SNAP_TO_ROUTE_DEGREES = 110.0
        private const val SMOOTHING_FACTOR = 0.35

        fun usesTravelHeading(speedKilometresPerHour: Double): Boolean =
            speedKilometresPerHour.isFinite() && speedKilometresPerHour > MIN_TRAVEL_HEADING_SPEED_KMH

        fun hasReliableMovement(from: GeoPoint, to: GeoPoint, accuracyMeters: Double): Boolean =
            GeoMath.distanceMeters(from, to) > reliabilityFloor(accuracyMeters)

        private fun reliabilityFloor(accuracyMeters: Double): Double {
            val scaled = accuracyMeters * 0.8
            return if (scaled > MIN_MOVE_METERS) scaled else MIN_MOVE_METERS
        }

        fun angleBetween(first: Double, second: Double): Double {
            val difference = abs(first - second) % 360.0
            return if (difference > 180) 360 - difference else difference
        }

        private fun signedDelta(target: Double, from: Double): Double {
            var delta = normalize(target - from)
            if (delta > 180) delta -= 360
            return delta
        }

        private fun normalize(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
    }
}
