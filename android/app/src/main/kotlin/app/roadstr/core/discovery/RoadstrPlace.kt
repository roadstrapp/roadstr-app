package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoPoint
import java.net.URI

enum class OsmElementType(val letter: Char) {
    NODE('n'),
    WAY('w'),
    RELATION('r'),
}

data class OsmRef(val type: OsmElementType, val id: Long)

/** Where a fact about a place came from; the card shows it, never hides it. */
enum class PlaceSource {
    OPEN_STREET_MAP,
    GEOCODER,
    WEBSITE,
    SEARXNG,
}

/** A web result that backs up a place, kept as evidence and never as a location. */
data class WebEvidence(val title: String, val host: String, val url: URI, val snippet: String)

/**
 * A place Roadstr can pin, inspect and navigate to. Built from provider data
 * through adapters; it never carries a provider's raw JSON.
 */
data class RoadstrPlace(
    val id: String,
    val osm: OsmRef?,
    val name: String,
    val category: PlaceCategory?,
    val position: GeoPoint,
    val address: String?,
    val distanceMeters: Double?,
    val openingHours: String?,
    val phone: String?,
    val website: URI?,
    val cuisine: String?,
    val tags: Map<String, String>,
    val sources: Set<PlaceSource>,
    val webEvidence: List<WebEvidence> = emptyList(),
    val confidence: Double = 1.0,
) {
    // A place name and its position are what the user searched for.
    override fun toString(): String = "RoadstrPlace(id=$id)"

    companion object {
        fun idFor(osm: OsmRef?, name: String, position: GeoPoint): String =
            if (osm != null) {
                "osm:${osm.type.letter}:${osm.id}"
            } else {
                "geo:%.4f,%.4f:%08x".format(
                    java.util.Locale.ROOT,
                    position.latitude,
                    position.longitude,
                    name.hashCode(),
                )
            }
    }
}

/** Which OSM tags a place keeps, and how much of each. */
object PlaceTagPolicy {
    const val MAX_TAGS = 48
    const val MAX_VALUE_CHARS = 160

    private val exact = setOf(
        "name", "brand", "operator", "opening_hours", "phone", "contact:phone", "website",
        "contact:website", "cuisine", "wheelchair", "outdoor_seating", "takeaway", "delivery",
        "drive_through", "internet_access", "capacity", "fee", "charge", "access", "description",
        "maxstay", "parking", "stars", "smoking", "payment:bitcoin", "payment:lightning",
        "amenity", "shop", "tourism", "leisure", "railway", "aeroway", "historic", "office",
        "craft", "healthcare", "natural",
    )
    private val prefixes = listOf("addr:", "diet:", "fuel:", "socket:", "name:", "description:")
    private val control = Regex("[\\u0000-\\u001f]")

    fun keeps(key: String): Boolean = key in exact || prefixes.any { key.startsWith(it) }

    fun clamp(value: String): String? {
        val clean = value.replace(control, " ").trim().take(MAX_VALUE_CHARS)
        return clean.ifEmpty { null }
    }

    fun filter(tags: Map<*, *>): Map<String, String> {
        val kept = LinkedHashMap<String, String>()
        for ((key, value) in tags) {
            if (kept.size >= MAX_TAGS) break
            if (key !is String || value !is String || !keeps(key)) continue
            clamp(value)?.let { kept[key] = it }
        }
        return kept
    }
}
