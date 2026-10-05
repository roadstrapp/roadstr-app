package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoPoint
import java.time.LocalDateTime

/** Everything one discovery search needs to know about where the user is and what they typed. */
data class DiscoveryRequest(
    val query: NaturalPlaceQuery,
    val device: GeoPoint?,
    val mapCenter: GeoPoint?,
    val destination: GeoPoint?,
    val languageCode: String,
    val now: LocalDateTime,
    /** The route being driven, for "along my route"; empty when there is none. */
    val route: List<GeoPoint> = emptyList(),
) {
    // Positions and the typed text stay out of logs.
    override fun toString(): String = "DiscoveryRequest(intent=${query.intent})"
}

sealed interface DiscoveryOutcome {
    /** Not a place search, or it could not be answered: run the existing search instead. */
    data object NotApplicable : DiscoveryOutcome

    /** The search worked and nothing matched. */
    data class Empty(val notices: Set<DiscoveryNotice>) : DiscoveryOutcome

    data class Found(
        val places: List<RankedPlace>,
        val notices: Set<DiscoveryNotice>,
        val area: SearchArea,
    ) : DiscoveryOutcome
}

fun interface PlaceDiscovery {
    suspend fun discover(request: DiscoveryRequest): DiscoveryOutcome
}
