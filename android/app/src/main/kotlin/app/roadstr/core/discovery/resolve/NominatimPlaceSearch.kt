package app.roadstr.core.discovery.resolve

import app.roadstr.core.discovery.AreaGeocoding
import app.roadstr.core.discovery.OsmElementType
import app.roadstr.core.discovery.OsmRef
import app.roadstr.core.discovery.PlaceCatalog
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.discovery.PlaceTagPolicy
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.UrlEncoding
import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.SearchProviderHttpMethod
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.core.search.OsmPlaceDetailsProtocol
import java.util.Locale

/**
 * Looks a business up by name near a town on Nominatim, for a web result that no place the
 * search found explains. The request carries a name taken from a public page title and a town,
 * never what the user typed; the answer is turned into the same place model as everything else.
 */
object NominatimPlaceSearch {
    private const val ENDPOINT = "https://nominatim.openstreetmap.org/search"
    private const val MAX_TEXT_CHARS = 120
    private const val LIMIT = 3

    fun request(name: String, locality: String?, near: GeoPoint, languageCode: String): SearchProviderRequest? {
        val text = listOfNotNull(name.trim().ifEmpty { null }, locality?.trim()?.ifEmpty { null })
            .joinToString(" ").take(MAX_TEXT_CHARS)
        if (name.isBlank()) return null
        val language = languageCode.lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "en"
        return SearchProviderRequest(
            method = SearchProviderHttpMethod.Get,
            uri = "$ENDPOINT?q=${UrlEncoding.encode(text)}&format=jsonv2&limit=$LIMIT&addressdetails=1" +
                "&extratags=1&namedetails=0&accept-language=$language${AreaGeocoding.viewbox(near)}",
            headers = mapOf("User-Agent" to AreaGeocoding.USER_AGENT),
        )
    }

    fun parse(body: String, origin: GeoPoint?, languageCode: String): List<RoadstrPlace> {
        val rows = try {
            BoundedJsonParser(body).parse() as? List<*>
        } catch (_: RuntimeException) {
            null
        } ?: return emptyList()
        return rows.asSequence()
            .mapNotNull { row -> (row as? Map<*, *>)?.let { place(it, origin, languageCode) } }
            .take(LIMIT)
            .toList()
    }

    private fun place(row: Map<*, *>, origin: GeoPoint?, languageCode: String): RoadstrPlace? {
        val position = position(row) ?: return null
        val osm = osmRef(row) ?: return null
        val tags = tags(row)
        val name = PlaceTagPolicy.clamp(row["name"] as? String ?: "") ?: return null
        tags["name"] = name
        val category = PlaceCatalog.categorize(tags)
        val details = OsmPlaceDetailsProtocol.parse(tags, languageCode.lowercase(Locale.ROOT))
        return RoadstrPlace(
            id = RoadstrPlace.idFor(osm, name, position),
            osm = osm,
            name = name,
            category = category,
            position = position,
            address = details?.address,
            distanceMeters = origin?.let { GeoMath.distanceMeters(it, position) },
            openingHours = tags["opening_hours"],
            phone = tags["contact:phone"] ?: tags["phone"],
            website = (tags["contact:website"] ?: tags["website"])?.let { UrlEncoding.safeHttps(it) },
            cuisine = details?.cuisine,
            tags = tags,
            sources = setOf(PlaceSource.GEOCODER, PlaceSource.OPEN_STREET_MAP),
        )
    }

    /** The tags the geocoder returns, through the usual whitelist, plus its class and type as a tag. */
    private fun tags(row: Map<*, *>): MutableMap<String, String> {
        val kept = LinkedHashMap<String, String>()
        (row["extratags"] as? Map<*, *>)?.let { kept.putAll(PlaceTagPolicy.filter(it)) }
        val kind = row["category"] as? String ?: row["class"] as? String
        val type = row["type"] as? String
        if (kind != null && type != null && PlaceTagPolicy.keeps(kind)) {
            PlaceTagPolicy.clamp(type)?.let { kept[kind] = it }
        }
        (row["address"] as? Map<*, *>)?.let { address ->
            address["road"].asText()?.let { kept["addr:street"] = it }
            address["house_number"].asText()?.let { kept["addr:housenumber"] = it }
            address["postcode"].asText()?.let { kept["addr:postcode"] = it }
            listOf("city", "town", "village", "municipality", "hamlet")
                .firstNotNullOfOrNull { address[it].asText() }?.let { kept["addr:city"] = it }
        }
        return kept
    }

    private fun Any?.asText(): String? = (this as? String)?.let { PlaceTagPolicy.clamp(it) }

    private fun position(row: Map<*, *>): GeoPoint? {
        val latitude = (row["lat"] as? String)?.toDoubleOrNull() ?: return null
        val longitude = (row["lon"] as? String)?.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return GeoPoint(latitude, longitude)
    }

    private fun osmRef(row: Map<*, *>): OsmRef? {
        val type = when (row["osm_type"] as? String) {
            "node" -> OsmElementType.NODE
            "way" -> OsmElementType.WAY
            "relation" -> OsmElementType.RELATION
            else -> return null
        }
        val id = (row["osm_id"] as? Number)?.toLong()?.takeIf { it > 0 } ?: return null
        return OsmRef(type, id)
    }
}
