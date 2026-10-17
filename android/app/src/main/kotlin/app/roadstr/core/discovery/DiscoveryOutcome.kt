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
    /** Not a place search: run the existing search instead. */
    data object NotApplicable : DiscoveryOutcome

    /** Discovery was applicable, but its data source could not answer. */
    data class Unavailable(val reason: DiscoveryUnavailableReason) : DiscoveryOutcome

    /** The search worked and nothing matched. */
    data class Empty(val notices: Set<DiscoveryNotice>) : DiscoveryOutcome

    data class Found(
        val places: List<RankedPlace>,
        val notices: Set<DiscoveryNotice>,
        val area: SearchArea,
    ) : DiscoveryOutcome
}

enum class DiscoveryUnavailableReason {
    /** No network is available and this checkout has no offline POI index. */
    OFFLINE_NO_INDEX,

    /** The device is online, but every applicable geographic provider failed. */
    PROVIDER_UNAVAILABLE,

    /** Connectivity disappeared or the request failed before a provider answer was received. */
    NETWORK_ERROR,
}

fun interface PlaceDiscovery {
    suspend fun discover(request: DiscoveryRequest): DiscoveryOutcome
}
