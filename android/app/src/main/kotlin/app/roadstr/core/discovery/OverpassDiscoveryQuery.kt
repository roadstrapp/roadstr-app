package app.roadstr.core.discovery

import java.util.Locale

/**
 * Builds the one Overpass query a discovery search needs. Every tag key and value
 * comes from the catalogue (validated at construction), never from typed text, so
 * the only user-dependent parts are numbers formatted by this class.
 */
object OverpassDiscoveryQuery {
    const val DEFAULT_LIMIT = 80
    const val DEFAULT_TIMEOUT_SECONDS = 12
    private const val AREA_ID_OFFSET = 3_600_000_000L

    fun build(
        categories: List<PlaceCategory>,
        attributes: Set<PlaceAttribute>,
        cuisines: Set<Cuisine>,
        area: SearchArea,
        limit: Int = DEFAULT_LIMIT,
        timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    ): String {
        require(categories.isNotEmpty()) { "A discovery query needs a category" }
        require(limit in 1..500) { "Limit out of range" }
        require(timeoutSeconds in 1..60) { "Timeout out of range" }
        val prefix = (area as? SearchArea.AdminArea)
            ?.let { "area(${AREA_ID_OFFSET + it.relationId})->.a;" }
            .orEmpty()
        val extra = attributes.joinToString("") { attributeFilter(it) } + cuisineFilter(cuisines)
        val where = areaFilter(area)
        val statements = categories.flatMap { it.alternatives }.joinToString("") { alternative ->
            "nwr" + alternative.joinToString("") { tagFilter(it) } + extra + where + ";"
        }
        return "[out:json][timeout:$timeoutSeconds];$prefix($statements);out tags center $limit;"
    }

    private fun tagFilter(match: TagMatch): String {
        val values = match.values
        return when {
            values != null && values.size == 1 -> "[\"${match.key}\"=\"${values.single()}\"]"
            values != null -> "[\"${match.key}\"~\"^(${values.sorted().joinToString("|")})$\"]"
            match.regex != null -> "[\"${match.key}\"~\"${match.regex}\"]"
            else -> "[\"${match.key}\"]"
        }
    }

    private fun attributeFilter(attribute: PlaceAttribute): String =
        tagFilter(TagMatch(attribute.key, attribute.values))

    private fun cuisineFilter(cuisines: Set<Cuisine>): String {
        if (cuisines.isEmpty()) return ""
        val values = cuisines.flatMap { it.values }.toSortedSet().joinToString("|")
        return "[\"cuisine\"~\"(^|;)($values)(;|$)\"]"
    }

    private fun areaFilter(area: SearchArea): String = when (area) {
        is SearchArea.Circle ->
            "(around:${area.radiusMeters},${number(area.center.latitude)},${number(area.center.longitude)})"
        is SearchArea.Box ->
            "(${number(area.south)},${number(area.west)},${number(area.north)},${number(area.east)})"
        is SearchArea.AdminArea -> "(area.a)"
        // One polyline: around:<buffer>,lat,lon,lat,lon,... matches everything within the buffer of the line.
        is SearchArea.Corridor -> "(around:${area.bufferMeters}," +
            area.points.joinToString(",") { "${number(it.latitude)},${number(it.longitude)}" } + ")"
    }

    private fun number(value: Double): String {
        require(value.isFinite()) { "Coordinate must be finite" }
        return String.format(Locale.ROOT, "%.6f", value)
    }
}
