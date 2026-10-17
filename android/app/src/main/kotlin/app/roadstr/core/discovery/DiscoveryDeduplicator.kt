package app.roadstr.core.discovery

import app.roadstr.core.geo.GeoMath

/** Provider-neutral, conservative POI deduplication. An address alone is never enough. */
object DiscoveryDeduplicator {
    const val MAX_SAME_PLACE_METERS = 20.0

    fun merge(values: Iterable<RoadstrPlace>): List<RoadstrPlace> {
        val result = ArrayList<RoadstrPlace>()
        val osmIndex = HashMap<OsmRef, Int>()
        for (candidate in values) {
            val existingIndex = candidate.osm?.let(osmIndex::get)
                ?: result.indexOfFirst { sameWithoutStableId(it, candidate) }.takeIf { it >= 0 }
            if (existingIndex == null) {
                candidate.osm?.let { osmIndex[it] = result.size }
                result += candidate
            } else {
                result[existingIndex] = combine(result[existingIndex], candidate)
            }
        }
        return result
    }

    private fun sameWithoutStableId(first: RoadstrPlace, second: RoadstrPlace): Boolean {
        if (first.osm != null || second.osm != null) return false
        if (first.category == null || first.category != second.category) return false
        val firstName = TextNormalizer.normalize(first.name)
        val secondName = TextNormalizer.normalize(second.name)
        if (firstName.isEmpty() || firstName != secondName) return false
        return GeoMath.distanceMeters(first.position, second.position) <= MAX_SAME_PLACE_METERS
    }

    private fun combine(first: RoadstrPlace, second: RoadstrPlace): RoadstrPlace {
        val preferred = if (completeness(second) > completeness(first)) second else first
        val other = if (preferred === first) second else first
        return preferred.copy(
            sources = preferred.sources + other.sources,
            webEvidence = (preferred.webEvidence + other.webEvidence).distinctBy { it.url },
            tags = other.tags + preferred.tags,
            confidence = maxOf(preferred.confidence, other.confidence),
        )
    }

    private fun completeness(value: RoadstrPlace): Int = listOf(
        value.name.isNotBlank(),
        value.address != null,
        value.openingHours != null,
        value.phone != null,
        value.website != null,
        value.cuisine != null,
    ).count { it }
}
