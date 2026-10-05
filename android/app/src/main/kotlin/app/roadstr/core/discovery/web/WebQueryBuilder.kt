package app.roadstr.core.discovery.web

import app.roadstr.core.discovery.LocationConstraint
import app.roadstr.core.discovery.NaturalPlaceQuery
import app.roadstr.core.discovery.QueryIntent
import app.roadstr.core.geo.GeoPoint

/** What a web search is told about the search: the typed words, and where the user is only by town. */
data class WebSearchContext(
    val parsed: NaturalPlaceQuery,
    val device: GeoPoint?,
    val destination: GeoPoint?,
    val languageCode: String,
) {
    override fun toString(): String = "WebSearchContext"
}

object WebQueryBuilder {
    const val MAX_CHARS = SearxngRequests.MAX_QUERY_CHARS

    /**
     * The point whose town should be appended, or null when the typed words already say where
     * ("in Florence", "near the station") or the search follows a route.
     */
    fun localityPoint(context: WebSearchContext): GeoPoint? {
        if (context.parsed.intent != QueryIntent.FIND_PLACE) return null
        return when (context.parsed.location) {
            LocationConstraint.CurrentLocation, LocationConstraint.MapCenter -> context.device
            LocationConstraint.Destination -> context.destination ?: context.device
            else -> null
        }
    }

    /** The typed words without "near me" style connectors, plus a town name when position matters. */
    fun build(parsed: NaturalPlaceQuery, locality: String?): String {
        val base = parsed.classicQuery.ifBlank { parsed.rawText }.trim()
        val text = if (locality.isNullOrBlank()) base else "$base $locality"
        return text.take(MAX_CHARS).trim()
    }
}
