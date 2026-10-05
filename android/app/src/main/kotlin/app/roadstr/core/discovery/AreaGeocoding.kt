package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.SearchProviderHttpMethod
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.util.Locale

/** One Nominatim place that may stand for a city, a town or a district. */
data class GeocodedArea(
    val displayName: String,
    val names: Set<String>,
    val center: GeoPoint,
    val osm: OsmRef?,
    val box: SearchArea.Box?,
    val category: String?,
    val type: String?,
    val importance: Double,
) {
    override fun toString(): String = "GeocodedArea(osm=$osm)"
}

/**
 * Turns a typed place name into a search area through Nominatim: the request, the
 * reading of the answer, the choice of which answer is really that place, and the
 * area to search. The choice refuses a result whose name does not resemble what was
 * typed, so a person's name or a street is never taken for a city.
 */
object AreaGeocoding {
    const val USER_AGENT = "Roadstr/1.0 (+https://github.com/roadstrapp/roadstr-app)"
    private const val ENDPOINT = "https://nominatim.openstreetmap.org/search"
    private const val MAX_TEXT_CHARS = 120
    private const val MAX_ADMIN_DIAGONAL_METERS = 80_000.0
    private const val MAX_BOX_DIAGONAL_METERS = 30_000.0
    private const val STRICT_SIMILARITY = 0.85
    private const val RELAXED_SIMILARITY = 0.7

    private val settlementTypes = setOf(
        "administrative", "city", "town", "village", "hamlet", "suburb", "neighbourhood",
        "quarter", "municipality", "borough", "city_district", "district", "locality",
    )

    fun request(text: String, languageCode: String, near: GeoPoint?): SearchProviderRequest? {
        val clean = text.trim().take(MAX_TEXT_CHARS)
        if (clean.isEmpty()) return null
        val language = languageCode.lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "en"
        val bias = near?.let { viewbox(it) }.orEmpty()
        return SearchProviderRequest(
            method = SearchProviderHttpMethod.Get,
            uri = "$ENDPOINT?q=${UrlEncoding.encode(clean)}&format=jsonv2&limit=5&addressdetails=0" +
                "&namedetails=1&polygon_geojson=0&accept-language=$language$bias",
            headers = mapOf("User-Agent" to USER_AGENT),
        )
    }

    fun parse(body: String): List<GeocodedArea> {
        val rows = try {
            BoundedJsonParser(body).parse() as? List<*>
        } catch (_: RuntimeException) {
            null
        } ?: return emptyList()
        return rows.mapNotNull { row -> (row as? Map<*, *>)?.let(::area) }
    }

    /** The result that is really [typed], or null when nothing resembles it. */
    fun choose(areas: List<GeocodedArea>, typed: String, strict: Boolean): GeocodedArea? {
        val threshold = if (strict) STRICT_SIMILARITY else RELAXED_SIMILARITY
        return areas
            .filter { isSettlement(it) }
            .map { it to similarity(it, typed) }
            .filter { (_, score) -> score >= threshold }
            .maxByOrNull { (area, score) -> score + IMPORTANCE_WEIGHT * area.importance }
            ?.first
    }

    /** For a reference place such as a park or a landmark: any result that resembles the text. */
    fun chooseAny(areas: List<GeocodedArea>, typed: String): GeocodedArea? =
        areas
            .map { it to similarity(it, typed) }
            .filter { (_, score) -> score >= RELAXED_SIMILARITY }
            .maxByOrNull { (area, score) -> score + IMPORTANCE_WEIGHT * area.importance }
            ?.first

    fun toSearchArea(area: GeocodedArea): SearchArea {
        val box = area.box
        val fallback: SearchArea = when {
            box != null && box.diagonalMeters <= MAX_BOX_DIAGONAL_METERS -> box
            else -> SearchArea.Circle(area.center, radiusFor(area))
        }
        val relation = area.osm?.takeIf { it.type == OsmElementType.RELATION }
        val small = box == null || box.diagonalMeters <= MAX_ADMIN_DIAGONAL_METERS
        return if (relation != null && small && relation.id in 1..SearchArea.MAX_RELATION_ID) {
            SearchArea.AdminArea(relation.id, area.center, fallback)
        } else {
            fallback
        }
    }

    private fun radiusFor(area: GeocodedArea): Int = when (area.type) {
        "city", "municipality", "administrative" -> 8_000
        "town" -> 5_000
        else -> 2_500
    }

    private fun isSettlement(area: GeocodedArea): Boolean =
        area.category in setOf("boundary", "place") && area.type in settlementTypes

    private fun similarity(area: GeocodedArea, typed: String): Double {
        val candidates = area.names + area.displayName.substringBefore(',')
        return TextSimilarity.best(typed, candidates)
    }

    private fun area(row: Map<*, *>): GeocodedArea? {
        val latitude = (row["lat"] as? String)?.toDoubleOrNull() ?: return null
        val longitude = (row["lon"] as? String)?.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val names = (row["namedetails"] as? Map<*, *>)?.values.orEmpty()
            .filterIsInstance<String>().mapNotNull { PlaceTagPolicy.clamp(it) }.toSet()
        return GeocodedArea(
            displayName = PlaceTagPolicy.clamp(row["display_name"] as? String ?: "").orEmpty(),
            names = names,
            center = GeoPoint(latitude, longitude),
            osm = osmRef(row),
            box = box(row["boundingbox"]),
            category = row["category"] as? String ?: row["class"] as? String,
            type = row["type"] as? String,
            importance = (row["importance"] as? Number)?.toDouble() ?: 0.0,
        )
    }

    private fun osmRef(row: Map<*, *>): OsmRef? {
        val type = when (row["osm_type"] as? String) {
            "node" -> OsmElementType.NODE
            "way" -> OsmElementType.WAY
            "relation" -> OsmElementType.RELATION
            else -> return null
        }
        val id = (row["osm_id"] as? Number)?.toLong() ?: return null
        return OsmRef(type, id).takeIf { id > 0 }
    }

    private fun box(raw: Any?): SearchArea.Box? {
        val values = (raw as? List<*>)?.map { (it as? String)?.toDoubleOrNull() ?: return null } ?: return null
        if (values.size != 4) return null
        val (south, north, west, east) = values
        return runCatching { SearchArea.Box(south, west, north, east) }.getOrNull()
    }

    private fun viewbox(near: GeoPoint): String {
        val west = near.longitude - 0.5
        val east = near.longitude + 0.5
        val north = near.latitude + 0.5
        val south = near.latitude - 0.5
        return "&viewbox=%.4f,%.4f,%.4f,%.4f&bounded=0".format(Locale.ROOT, west, north, east, south)
    }

    private const val IMPORTANCE_WEIGHT = 0.3

    /** Straight-line distance between two corners, for tests and size limits. */
    internal fun diagonal(box: SearchArea.Box): Double =
        GeoMath.distanceMeters(GeoPoint(box.south, box.west), GeoPoint(box.north, box.east))
}
