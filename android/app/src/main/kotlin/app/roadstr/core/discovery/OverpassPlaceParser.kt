package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.SearchResponseProtocol
import app.roadstr.core.search.OsmPlaceDetailsProtocol
import java.util.Locale

/** Reads an Overpass answer that includes tags into places. */
object OverpassPlaceParser {
    /** Kinds of place that are normally unnamed on the map but still worth listing. */
    private val unnamedOk = setOf(
        PlaceCategory.PARKING, PlaceCategory.ATM, PlaceCategory.TOILETS,
        PlaceCategory.DRINKING_WATER, PlaceCategory.CHARGING_STATION, PlaceCategory.FUEL,
        PlaceCategory.TAXI, PlaceCategory.BUS_STATION,
    )

    fun parse(body: String, origin: GeoPoint?, languageCode: String): List<RoadstrPlace> {
        val elements = try {
            SearchResponseProtocol.parseOverpassElements(body)
        } catch (_: RuntimeException) {
            return emptyList()
        }
        return elements.mapNotNull { place(it, origin, languageCode) }
    }

    private fun place(element: Map<String, Any?>, origin: GeoPoint?, languageCode: String): RoadstrPlace? {
        val osm = osmRef(element) ?: return null
        val position = position(element) ?: return null
        val rawTags = element["tags"] as? Map<*, *> ?: return null
        val tags = PlaceTagPolicy.filter(rawTags)
        val language = languageCode.lowercase(Locale.ROOT)
        val name = tags["name:$language"] ?: tags["name"] ?: tags["brand"] ?: tags["operator"] ?: ""
        val category = PlaceCatalog.categorize(tags)
        if (name.isEmpty() && category !in unnamedOk) return null
        val details = OsmPlaceDetailsProtocol.parse(tags, language)
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
            sources = setOf(PlaceSource.OPEN_STREET_MAP),
        )
    }

    private fun osmRef(element: Map<String, Any?>): OsmRef? {
        val type = when (element["type"] as? String) {
            "node" -> OsmElementType.NODE
            "way" -> OsmElementType.WAY
            "relation" -> OsmElementType.RELATION
            else -> return null
        }
        val id = (element["id"] as? Number)?.toLong() ?: return null
        return if (id > 0) OsmRef(type, id) else null
    }

    private fun position(element: Map<String, Any?>): GeoPoint? {
        val center = element["center"] as? Map<*, *>
        val latitude = (element["lat"] as? Number ?: center?.get("lat") as? Number)?.toDouble()
        val longitude = (element["lon"] as? Number ?: center?.get("lon") as? Number)?.toDouble()
        if (latitude == null || longitude == null) return null
        val valid = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
        return if (valid) GeoPoint(latitude, longitude) else null
    }
}
