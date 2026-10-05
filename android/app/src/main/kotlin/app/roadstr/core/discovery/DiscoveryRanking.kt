package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.time.OpenState
import app.roadstr.core.time.OpeningHours
import java.time.LocalDateTime

/** Something worth telling the user about how complete the answer is. */
enum class DiscoveryNotice {
    /** The tag filter matched only a few places: OpenStreetMap is sparse for it here. */
    FEW_TAGGED,

    /** Nothing was close, so the search was widened. */
    WIDENED,

    /** The named place could not be located exactly; a circle around it was used. */
    AREA_FALLBACK,

    /** Along-the-route search is not available; the search ran around the position instead. */
    ROUTE_UNSUPPORTED,

    /** "Open now" was asked but some places give no usable hours. */
    OPEN_HOURS_UNKNOWN,
}

/**
 * "Open now" is only trusted near the device: the evaluator reads a local time with
 * no time zone, so far from here a place would be judged by the wrong clock.
 */
object OpenNowPolicy {
    const val TRUSTED_RADIUS_METERS = 100_000.0

    fun state(openingHours: String?, now: LocalDateTime, distanceFromDevice: Double?): OpenState {
        if (openingHours.isNullOrBlank()) return OpenState.UNKNOWN
        if (distanceFromDevice == null || distanceFromDevice > TRUSTED_RADIUS_METERS) return OpenState.UNKNOWN
        return OpeningHours.evaluate(openingHours, now).state
    }
}

data class RankedPlace(val place: RoadstrPlace, val score: Double, val open: OpenState)

/** Deterministic, testable ranking: near, open, well described and matching the leftover words. */
object DiscoveryRanking {
    private const val DISTANCE_SCALE_METERS = 2_000.0
    private const val MIN_TERM_CHARS = 3

    fun rank(
        places: List<RoadstrPlace>,
        query: NaturalPlaceQuery,
        anchor: GeoPoint?,
        device: GeoPoint?,
        now: LocalDateTime,
        limit: Int = 25,
    ): List<RankedPlace> {
        val scored = places.mapNotNull { place -> score(place, query, anchor, device, now) }
        return scored
            .sortedWith(
                compareByDescending<RankedPlace> { it.score }
                    .thenBy { it.place.distanceMeters ?: Double.MAX_VALUE }
                    .thenBy { it.place.id },
            )
            .take(limit)
    }

    private fun score(
        place: RoadstrPlace,
        query: NaturalPlaceQuery,
        anchor: GeoPoint?,
        device: GeoPoint?,
        now: LocalDateTime,
    ): RankedPlace? {
        if (hasNegativeAttribute(place, query)) return null
        val deviceDistance = device?.let { GeoMath.distanceMeters(it, place.position) }
        val open = OpenNowPolicy.state(place.openingHours, now, deviceDistance)
        if (query.openNow && open == OpenState.CLOSED) return null
        val distance = anchor?.let { GeoMath.distanceMeters(it, place.position) }
        val score = DISTANCE_WEIGHT * nearness(distance) +
            OPEN_WEIGHT * openness(open) +
            COMPLETE_WEIGHT * completeness(place) +
            TERM_WEIGHT * termMatch(place, query.residualTerms)
        return RankedPlace(place.copy(distanceMeters = deviceDistance ?: distance), score, open)
    }

    private fun hasNegativeAttribute(place: RoadstrPlace, query: NaturalPlaceQuery): Boolean =
        query.attributes.any { attribute -> place.tags[attribute.key] in attribute.negativeValues }

    private fun nearness(distance: Double?): Double =
        if (distance == null) NEUTRAL else 1.0 / (1.0 + distance / DISTANCE_SCALE_METERS)

    private fun openness(open: OpenState): Double = when (open) {
        OpenState.OPEN -> 1.0
        OpenState.UNKNOWN -> NEUTRAL
        OpenState.CLOSED -> 0.0
    }

    private fun completeness(place: RoadstrPlace): Double {
        val present = listOf(
            place.name.isNotEmpty(),
            place.openingHours != null,
            place.phone != null,
            place.website != null,
            place.address != null,
        ).count { it }
        return present / 5.0
    }

    private fun termMatch(place: RoadstrPlace, terms: List<String>): Double {
        val wanted = terms.map { TextNormalizer.normalize(it) }.filter { it.length >= MIN_TERM_CHARS }
        if (wanted.isEmpty()) return NEUTRAL
        val haystack = listOfNotNull(
            place.name, place.tags["brand"], place.tags["operator"], place.cuisine,
            place.tags["description"],
        ).joinToString(" ") { TextNormalizer.normalize(it) }
        return wanted.count { haystack.contains(it) }.toDouble() / wanted.size
    }

    private const val NEUTRAL = 0.5
    private const val DISTANCE_WEIGHT = 0.55
    private const val OPEN_WEIGHT = 0.15
    private const val COMPLETE_WEIGHT = 0.15
    private const val TERM_WEIGHT = 0.15
}
