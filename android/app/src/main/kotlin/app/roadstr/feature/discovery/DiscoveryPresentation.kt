package app.roadstr.feature.discovery

import app.roadstr.core.discovery.DiscoveryNotice
import app.roadstr.core.discovery.PlaceCategoryNames
import app.roadstr.core.discovery.RankedPlace
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.search.OsmPlaceDetails
import app.roadstr.core.search.OsmPlaceDetailsProtocol
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.feature.map.NativeMapPointOverlayKind
import app.roadstr.feature.map.NativeMapPointOverlayMarker
import app.roadstr.feature.search.NativeSearchNotice

/** The places of the latest discovery search, tied to the search revision that produced them. */
data class NativeDiscoverySnapshot(val revision: Long, val places: List<RankedPlace>) {
    companion object {
        val Empty = NativeDiscoverySnapshot(-1L, emptyList())
    }
}

/** Turns discovery results into what the existing search list, map and place sheet already show. */
object DiscoveryPresentation {
    private const val PIN_PREFIX = "discovery:"
    private const val FALLBACK_EMOJI = "📍"

    fun title(place: RoadstrPlace, locale: String): String =
        place.name.ifEmpty { categoryName(place, locale) }

    fun categoryName(place: RoadstrPlace, locale: String): String =
        place.category?.let { PlaceCategoryNames.of(it, locale) }.orEmpty()

    /** A row of the existing results list: localised category and address under the name. */
    fun searchResult(ranked: RankedPlace, locale: String): SearchResult {
        val place = ranked.place
        val title = title(place, locale)
        val category = categoryName(place, locale)
        val detail = listOfNotNull(category.ifEmpty { null }, place.address).joinToString(" · ")
        return SearchResult(
            displayName = listOfNotNull(title, detail.ifEmpty { null }).joinToString(", "),
            shortName = title,
            position = SearchResponsePoint(place.position.latitude, place.position.longitude),
            openingHours = place.openingHours,
            distanceM = place.distanceMeters,
            emojiOverride = place.category?.emoji ?: FALLBACK_EMOJI,
            categoryLabelOverride = detail,
        )
    }

    fun notice(notices: Set<DiscoveryNotice>): NativeSearchNotice? = when {
        DiscoveryNotice.FEW_TAGGED in notices -> NativeSearchNotice.FewTagged
        DiscoveryNotice.WIDENED in notices -> NativeSearchNotice.Widened
        DiscoveryNotice.ROUTE_UNSUPPORTED in notices -> NativeSearchNotice.RouteUnsupported
        DiscoveryNotice.AREA_FALLBACK in notices -> NativeSearchNotice.AreaFallback
        DiscoveryNotice.OPEN_HOURS_UNKNOWN in notices -> NativeSearchNotice.OpenHoursUnknown
        else -> null
    }

    fun pins(places: List<RankedPlace>): List<NativeMapPointOverlayMarker> =
        places.map { ranked ->
            NativeMapPointOverlayMarker(
                id = pinId(ranked.place),
                point = NativeMapPoint(ranked.place.position.latitude, ranked.place.position.longitude),
                kind = NativeMapPointOverlayKind.DiscoveryResult,
            )
        }

    fun pinId(place: RoadstrPlace): String = PIN_PREFIX + place.id

    fun placeForPin(markerId: String, places: List<RankedPlace>): RoadstrPlace? =
        places.firstOrNull { pinId(it.place) == markerId }?.place

    /** The result whose position a list row carries. */
    fun placeAt(latitude: Double, longitude: Double, places: List<RankedPlace>): RoadstrPlace? =
        places.firstOrNull {
            it.place.position.latitude == latitude && it.place.position.longitude == longitude
        }?.place

    fun details(place: RoadstrPlace, locale: String): OsmPlaceDetails? =
        OsmPlaceDetailsProtocol.parse(place.tags, locale)
}
