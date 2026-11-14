package app.roadstr.core.network

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.protocol.nostr.BoundedJsonParser

/**
 * Whether the road under a position is one where nobody may turn round, read from OpenStreetMap
 * data: `highway=motorway|trunk` and their `_link` ramps, `motorroad=yes`, `expressway=yes`.
 *
 * Two sources describe the same OSM tags: Valhalla's `locate` (its road class, already derived from
 * `highway`) and an Overpass `way(around)` query with `out tags geom`. Both are parsed here without
 * any I/O so the decision is testable; the caller does the requests.
 */
object RoadUTurnClassifier {
    /** A road near the position and whether it forbids turning round. */
    data class NearbyRoad(val distanceMeters: Double, val forbidsUTurn: Boolean)

    /** Roads closer than this to the nearest one are the same carriageway, or its ramp. */
    private const val TIE_METERS = 4.0

    /** Beyond this the nearest road is not the one the vehicle is on. */
    const val MAX_DISTANCE_METERS = 60.0

    private val FORBIDDEN_HIGHWAY = setOf("motorway", "motorway_link", "trunk", "trunk_link")
    private val FORBIDDEN_VALHALLA_CLASS = setOf("motorway", "trunk")

    fun forbidsUTurnByOsmTags(tags: Map<*, *>): Boolean =
        tags["highway"]?.toString() in FORBIDDEN_HIGHWAY ||
            tags["motorroad"]?.toString() == "yes" ||
            tags["expressway"]?.toString() == "yes"

    /** Valhalla keeps `motorway_link` as class `motorway` with the link flag, and marks ramps by `use`. */
    fun forbidsUTurnByValhalla(classification: String?, use: String?): Boolean =
        classification in FORBIDDEN_VALHALLA_CLASS || use == "ramp"

    /**
     * True when the nearest road (together with any road tied with it) forbids a U-turn, false when it
     * does not, null when no road is close enough to say. A nearer service road or parallel street
     * never borrows the verdict of a more distant motorway.
     */
    fun decide(roads: List<NearbyRoad>): Boolean? {
        val nearest = roads.minOfOrNull { it.distanceMeters } ?: return null
        if (nearest > MAX_DISTANCE_METERS) return null
        return roads.any { it.distanceMeters <= nearest + TIE_METERS && it.forbidsUTurn }
    }

    /** Body of Valhalla `/locate` with `verbose=true`. Null when it is not the expected shape. */
    fun parseValhallaLocate(body: String): List<NearbyRoad>? {
        val root = runCatching { BoundedJsonParser(body).parse() }.getOrNull() as? List<*> ?: return null
        val location = root.firstOrNull() as? Map<*, *> ?: return emptyList()
        val edges = location["edges"] as? List<*> ?: return emptyList()
        return edges.mapNotNull { raw ->
            val edge = raw as? Map<*, *> ?: return@mapNotNull null
            val distance = (edge["distance"] as? Number)?.toDouble() ?: return@mapNotNull null
            val details = edge["edge"] as? Map<*, *>
            val classification = ((details?.get("classification") as? Map<*, *>)?.get("classification"))
                ?.toString()
            val use = details?.get("use")?.toString()
            if (classification == null) return@mapNotNull null
            NearbyRoad(distance, forbidsUTurnByValhalla(classification, use))
        }
    }

    /** Body of an Overpass `way[highway](around:...);out tags geom;` answer. */
    fun parseOverpassWays(body: String, position: GeoPoint): List<NearbyRoad>? {
        val root = runCatching { BoundedJsonParser(body).parse() }.getOrNull() as? Map<*, *> ?: return null
        val elements = root["elements"] as? List<*> ?: return emptyList()
        return elements.mapNotNull { raw ->
            val way = raw as? Map<*, *> ?: return@mapNotNull null
            val tags = way["tags"] as? Map<*, *> ?: return@mapNotNull null
            val geometry = (way["geometry"] as? List<*>)?.mapNotNull { node ->
                val point = node as? Map<*, *> ?: return@mapNotNull null
                val lat = (point["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                val lon = (point["lon"] as? Number)?.toDouble() ?: return@mapNotNull null
                GeoPoint(lat, lon)
            } ?: return@mapNotNull null
            if (geometry.size < 2) return@mapNotNull null
            val distance = geometry.zipWithNext { a, b -> GeoMath.distanceToSegmentMeters(position, a, b) }.min()
            NearbyRoad(distance, forbidsUTurnByOsmTags(tags))
        }
    }
}
