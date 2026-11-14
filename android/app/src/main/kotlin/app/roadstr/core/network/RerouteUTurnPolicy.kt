package app.roadstr.core.network

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.navigation.CountryRuralSpeedData
import app.roadstr.core.navigation.HeadingFilter

/**
 * Bans U-turns from automatic reroutes on roads where nobody may turn round.
 *
 * A reroute "reverses" when it starts with a U-turn: a "Make a U-turn" step close to the start, or a
 * first leg that points back against the driving direction (what a snap to the opposite carriageway
 * produces). Whether that is forbidden is decided from the OpenStreetMap tags of the road under the
 * car by [RoadUTurnClassifier] (motorway, trunk, their links, `motorroad`, `expressway`); only when
 * OSM could not say does the speed stand in for it, against a threshold that depends on the
 * country, see [forbidsUTurn]. A forbidden reversal is discarded and the router is asked again with
 * a wider bearing window, which still excludes the opposite carriageway, so the new route carries
 * on to the next exit or junction and comes back from there.
 */
object RerouteUTurnPolicy {
    /** Lowest fallback threshold: nobody turns round above this, whatever the country's rural limit. */
    const val MIN_FALLBACK_SPEED_KMH = 60

    /** Second bearing tolerance: still rules out the opposite carriageway (180 degrees away). */
    const val WIDE_BEARING_TOLERANCE_DEGREES = 90

    /** A U-turn step this close to the start is the reroute's first move. */
    const val IMMEDIATE_UTURN_WINDOW_METERS = 1_000.0

    /** First leg more than this far from the driving direction is a reversal. */
    const val REVERSAL_ANGLE_DEGREES = 120.0

    /** The first leg's direction is read this far from its start, past GPS and snapping noise. */
    private const val DIRECTION_SAMPLE_METERS = 40.0

    /**
     * The speed above which the car is taken to be on a road where it cannot turn round, when OSM
     * could not say. It is the country's default limit for an ordinary road outside built-up areas
     * (Italy: 90 km/h), from [CountryRuralSpeedData]; the United States are told apart by state, a
     * country with no value gets the world median (90), and nothing goes below [MIN_FALLBACK_SPEED_KMH].
     */
    fun fallbackSpeedKmh(countryCode: String?, admin1Code: String? = null): Int {
        val country = countryCode?.uppercase()
        val state = if (country == "US") admin1Code?.uppercase()?.let(CountryRuralSpeedData.byUsState::get) else null
        val rural = state ?: country?.let(CountryRuralSpeedData.byCountry::get)
        return maxOf(rural ?: CountryRuralSpeedData.WORLD_MEDIAN_KMH, MIN_FALLBACK_SPEED_KMH)
    }

    /**
     * Whether a reroute must not start with a U-turn.
     *
     * 1. [roadForbidsUTurn], from the OSM tags of the road under the car, when known: it decides.
     * 2. Otherwise, above [thresholdKmh] and over the speed limit OSM gives for the road.
     * 3. Otherwise, with no limit to compare, above [thresholdKmh].
     */
    fun forbidsUTurn(
        roadForbidsUTurn: Boolean?,
        speedKilometresPerHour: Double,
        speedLimitKilometresPerHour: Int?,
        thresholdKmh: Int = fallbackSpeedKmh(null),
    ): Boolean {
        if (roadForbidsUTurn != null) return roadForbidsUTurn
        if (!speedKilometresPerHour.isFinite() || speedKilometresPerHour <= thresholdKmh) return false
        return speedLimitKilometresPerHour == null ||
            speedKilometresPerHour > speedLimitKilometresPerHour
    }

    /**
     * Whether [route] starts by turning round. Without a heading only an explicit U-turn step
     * counts, since the direction of the first leg has nothing to be compared with.
     */
    fun reversesDirection(route: RoutingParsedRoute, headingDegrees: Double?): Boolean =
        startsWithUTurnStep(route) ||
            (headingDegrees != null && headingDegrees.isFinite() && firstLegOpposesHeading(route, headingDegrees))

    fun withoutReversals(
        routes: List<RoutingParsedRoute>,
        headingDegrees: Double?,
    ): List<RoutingParsedRoute> = routes.filterNot { reversesDirection(it, headingDegrees) }

    private fun startsWithUTurnStep(route: RoutingParsedRoute): Boolean {
        var travelled = 0.0
        for (step in route.steps) {
            if (travelled > IMMEDIATE_UTURN_WINDOW_METERS) return false
            if (step.modifier.contains("uturn") || step.direction.contains("uturn")) return true
            travelled += step.distanceM
        }
        return false
    }

    private fun firstLegOpposesHeading(route: RoutingParsedRoute, headingDegrees: Double): Boolean {
        val points = route.polyline
        if (points.size < 2) return false
        val start = points.first().toGeoPoint()
        for (index in 1 until points.size) {
            val point = points[index].toGeoPoint()
            if (GeoMath.distanceMeters(start, point) < DIRECTION_SAMPLE_METERS) continue
            val bearing = GeoMath.bearingBetween(start, point)
            return bearing.isFinite() &&
                HeadingFilter.angleBetween(bearing, headingDegrees) > REVERSAL_ANGLE_DEGREES
        }
        return false
    }

    private fun RoutingResponsePoint.toGeoPoint() = GeoPoint(latitude, longitude)
}
