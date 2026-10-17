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
    ROUTE_AHEAD,

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

enum class DiscoveryBand {
    BEST_LOCAL,
    OTHER_LOCAL,
    EXTERNAL,
}

enum class DiscoverySort {
    RELEVANCE,
    DISTANCE,
    ALPHABETICAL,
}

data class RankedPlace(
    val place: RoadstrPlace,
    val score: Double,
    val open: OpenState,
    val band: DiscoveryBand = DiscoveryBand.BEST_LOCAL,
    val verifiedAttributes: Boolean = false,
)

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
        /** For an "along my route" search: places are judged by the detour they cost, not by distance. */
        route: List<GeoPoint>? = null,
        /** IDs returned by the strict geographic query. Null means every result is local. */
        localIds: Set<String>? = null,
        /** IDs returned by the fully constrained query. Null means no loose second pass ran. */
        exactIds: Set<String>? = null,
        sort: DiscoverySort = DiscoverySort.RELEVANCE,
    ): List<RankedPlace> {
        val scored = places.mapNotNull { place ->
            score(place, query, anchor, device, now, route, localIds, exactIds)
        }
        val withinBand = when (sort) {
            DiscoverySort.RELEVANCE -> compareByDescending<RankedPlace> { it.score }
                .thenBy { it.place.distanceMeters ?: Double.MAX_VALUE }
                .thenBy { it.place.id }
            DiscoverySort.DISTANCE -> compareBy<RankedPlace> { it.place.distanceMeters ?: Double.MAX_VALUE }
                .thenByDescending { it.score }
                .thenBy { it.place.id }
            DiscoverySort.ALPHABETICAL -> compareBy<RankedPlace> {
                TextNormalizer.normalize(it.place.name)
            }.thenBy { it.place.id }
        }
        return scored
            // Geography is deliberately compared before semantics: an excellent
            // result outside an explicit city never jumps above a local result.
            .sortedWith(compareBy<RankedPlace> { it.band.ordinal }.then(withinBand))
            .take(limit)
    }

    private fun score(
        place: RoadstrPlace,
        query: NaturalPlaceQuery,
        anchor: GeoPoint?,
        device: GeoPoint?,
        now: LocalDateTime,
        route: List<GeoPoint>?,
        localIds: Set<String>?,
        exactIds: Set<String>?,
    ): RankedPlace? {
        if (hasNegativeAttribute(place, query)) return null
        val deviceDistance = device?.let { GeoMath.distanceMeters(it, place.position) }
        val open = OpenNowPolicy.state(place.openingHours, now, deviceDistance)
        if (query.openNow && open == OpenState.CLOSED) return null
        val onRoute = route?.let { RouteCorridor.position(place.position, it) }
        val distance = onRoute?.detourMeters ?: anchor?.let { GeoMath.distanceMeters(it, place.position) }
        val category = categoryMatch(place, query)
        val attributes = attributeMatch(place, query)
        val score = CATEGORY_WEIGHT * category +
            ATTRIBUTE_WEIGHT * attributes +
            DISTANCE_WEIGHT * nearness(distance) +
            OPEN_WEIGHT * openness(open) +
            COMPLETE_WEIGHT * completeness(place) +
            TERM_WEIGHT * termMatch(place, query.residualTerms)
        // Along a route what matters to the driver is how far ahead a place is.
        val shown = onRoute?.alongMeters ?: deviceDistance ?: distance
        val band = when {
            localIds != null && place.id !in localIds -> DiscoveryBand.EXTERNAL
            exactIds != null && place.id !in exactIds -> DiscoveryBand.OTHER_LOCAL
            else -> DiscoveryBand.BEST_LOCAL
        }
        return RankedPlace(
            place = place.copy(distanceMeters = shown),
            score = score,
            open = open,
            band = band,
            verifiedAttributes = query.attributes.isNotEmpty() && attributes == 1.0,
        )
    }

    private fun categoryMatch(place: RoadstrPlace, query: NaturalPlaceQuery): Double = when {
        place.category == null -> 0.0
        place.category in query.categories -> 1.0
        else -> 0.25
    }

    private fun attributeMatch(place: RoadstrPlace, query: NaturalPlaceQuery): Double {
        if (query.attributes.isEmpty()) return NEUTRAL
        return query.attributes.count { attribute ->
            place.tags[attribute.key] in attribute.values
        }.toDouble() / query.attributes.size
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
    private const val CATEGORY_WEIGHT = 0.25
    private const val ATTRIBUTE_WEIGHT = 0.20
    private const val DISTANCE_WEIGHT = 0.30
    private const val OPEN_WEIGHT = 0.10
    private const val COMPLETE_WEIGHT = 0.05
    private const val TERM_WEIGHT = 0.10
}
