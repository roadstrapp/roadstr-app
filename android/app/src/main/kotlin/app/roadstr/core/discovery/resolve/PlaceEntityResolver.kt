package app.roadstr.core.discovery.resolve

import app.roadstr.core.discovery.PlaceCategory
import app.roadstr.core.discovery.PlaceSource
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.SearchArea
import app.roadstr.core.discovery.TextNormalizer
import app.roadstr.core.discovery.web.WebResult
import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import kotlin.coroutines.cancellation.CancellationException

/** How far from its centre a search area reaches, for deciding what is still "in the area". */
fun SearchArea.reachMeters(): Double = when (this) {
    is SearchArea.Circle -> radiusMeters.toDouble()
    is SearchArea.Box -> diagonalMeters / 2
    is SearchArea.AdminArea -> maxOf(fallback.reachMeters(), ADMIN_REACH_METERS)
    is SearchArea.Corridor -> lengthMeters / 2 + bufferMeters
}

private const val ADMIN_REACH_METERS = 10_000.0

/** What the resolver is told about the search the results belong to. */
data class ResolveContext(
    val center: GeoPoint,
    val radiusMeters: Double,
    val locality: String?,
    val languageCode: String,
    val categories: Set<PlaceCategory> = emptySet(),
) {
    // The centre and the town are where the user is looking.
    override fun toString(): String = "ResolveContext"
}

/** A web result tied to a place, with the reasons and where each fact came from. */
data class WebPlaceMatch(
    val place: RoadstrPlace,
    val evidence: List<Evidence>,
    val confidence: Double,
    val provenance: Set<PlaceSource>,
    /** True when the place came from a lookup and is not one of the search's own results. */
    val isNew: Boolean = false,
) {
    val matchClass: MatchClass get() = Confidence.classify(confidence)

    override fun toString(): String = "WebPlaceMatch(confidence=$confidence)"
}

data class ResolvedWebResult(val result: WebResult, val match: WebPlaceMatch?) {
    val matchClass: MatchClass get() = match?.matchClass ?: MatchClass.WEB_ONLY
}

/** Finds the places a name might refer to near a town; the service behind it keeps to the geocoder's rules. */
fun interface PlaceLookup {
    suspend fun find(name: String, locality: String?, near: GeoPoint, languageCode: String): List<RoadstrPlace>
}

/**
 * Ties web results to places without ever inventing one. First against the places the search
 * already found (website, phone, address, name, all free); then, for at most [maxLookups] of
 * the rest, by looking the name up near the town. A brand name alone never links two places:
 * chains with several branches stay unlinked unless the website, phone or address tells which.
 */
class PlaceEntityResolver(
    private val lookup: PlaceLookup? = null,
    private val maxLookups: Int = MAX_LOOKUPS,
) {
    suspend fun resolve(
        results: List<WebResult>,
        known: List<RoadstrPlace>,
        context: ResolveContext,
    ): List<ResolvedWebResult> {
        val index = KnownPlaces(known)
        val matched = results.map { ResolvedWebResult(it, matchKnown(it, index, context)) }
        return lookupTheRest(matched, context)
    }

    private fun matchKnown(result: WebResult, index: KnownPlaces, context: ResolveContext): WebPlaceMatch? {
        val best = index.places.mapNotNull { place -> score(result, place, index, context) }
            .maxWithOrNull(compareBy<WebPlaceMatch> { it.confidence }.thenBy { -(distance(it.place, context)) })
        return best?.takeIf { it.confidence >= Confidence.CANDIDATE_THRESHOLD }
    }

    private fun score(
        result: WebResult,
        place: RoadstrPlace,
        index: KnownPlaces,
        context: ResolveContext,
    ): WebPlaceMatch? {
        val evidence = ArrayList<Evidence>()
        val text = "${result.title} ${result.snippet}"
        val osmLinked = TextEvidence.osmRefOf(result.url)
        if (osmLinked != null && osmLinked == place.osm) evidence += Evidence(EvidenceKind.OSM_ID)
        val hostEvidence = hostEvidence(result.host, place, index)
        hostEvidence?.let(evidence::add)
        if (TextEvidence.phoneIn(place.phone, text)) evidence += Evidence(EvidenceKind.PHONE)
        if (TextEvidence.addressIn(place.tags, text)) evidence += Evidence(EvidenceKind.ADDRESS_EXACT)
        nameEvidence(result, place, index)?.let(evidence::add)
        if (evidence.isEmpty()) return null
        if (place.category != null && place.category in context.categories) evidence += Evidence(EvidenceKind.CATEGORY)
        return match(place, evidence, hostEvidence != null)
    }

    /** The place's own website is strong evidence, but only as strong as it is unique among the candidates. */
    private fun hostEvidence(host: String, place: RoadstrPlace, index: KnownPlaces): Evidence? {
        val placeHost = place.website?.host ?: return null
        val key = HostMatching.siteKey(placeHost) ?: return null
        if (key != HostMatching.siteKey(host)) return null
        val shared = index.placesWithSite(key) > 1
        return Evidence(EvidenceKind.WEBSITE_HOST, if (shared) SHARED_SITE_WEIGHT else EvidenceKind.WEBSITE_HOST.weight)
    }

    /** A title that carries the place's name; weaker when several places share that name. */
    private fun nameEvidence(result: WebResult, place: RoadstrPlace, index: KnownPlaces): Evidence? {
        val names = listOfNotNull(place.name.ifEmpty { null }, place.tags["brand"])
        val similarity = names.maxOfOrNull { WebTitleNames.similarity(result.title, it) } ?: 0.0
        if (similarity < NAME_THRESHOLD) return null
        val weight = if (index.placesNamed(place.name) > 1) CHAIN_NAME_WEIGHT else EvidenceKind.NAME_LOCALITY.weight
        return Evidence(EvidenceKind.NAME_LOCALITY, weight)
    }

    private suspend fun lookupTheRest(matched: List<ResolvedWebResult>, context: ResolveContext): List<ResolvedWebResult> {
        val finder = lookup ?: return matched
        var used = 0
        val tried = HashSet<String>()
        return matched.map { row ->
            if (row.matchClass != MatchClass.WEB_ONLY || used >= maxLookups) return@map row
            val guess = WebTitleNames.guess(row.result.title) ?: return@map row
            if (!tried.add(TextNormalizer.normalize(guess))) return@map row
            used += 1
            val found = candidates(finder, guess, context)
            val best = found.mapNotNull { accept(row.result, it, context) }.maxByOrNull { it.confidence }
            if (best == null || best.confidence < Confidence.CANDIDATE_THRESHOLD) row else row.copy(match = best)
        }
    }

    private suspend fun candidates(finder: PlaceLookup, guess: String, context: ResolveContext): List<RoadstrPlace> =
        try {
            finder.find(guess, context.locality, context.center, context.languageCode)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

    /** A looked-up place counts only if it is close in name and place, and agrees on category or website. */
    private fun accept(result: WebResult, place: RoadstrPlace, context: ResolveContext): WebPlaceMatch? {
        val similarity = WebTitleNames.similarity(result.title, place.name)
        if (similarity < LOOKUP_NAME_THRESHOLD) return null
        val reach = if (context.radiusMeters > 0) context.radiusMeters else DEFAULT_REACH_METERS
        if (distance(place, context) > reach) return null
        val hostAgrees = place.website?.host?.let { HostMatching.sameSite(it, result.host) } == true
        val categoryAgrees = place.category != null && place.category in context.categories
        if (!hostAgrees && !categoryAgrees) return null
        val evidence = arrayListOf(Evidence(EvidenceKind.NAME_LOCALITY))
        if (hostAgrees) evidence += Evidence(EvidenceKind.WEBSITE_HOST)
        if (categoryAgrees) evidence += Evidence(EvidenceKind.CATEGORY)
        val text = "${result.title} ${result.snippet}"
        if (TextEvidence.phoneIn(place.phone, text)) evidence += Evidence(EvidenceKind.PHONE)
        if (TextEvidence.addressIn(place.tags, text)) evidence += Evidence(EvidenceKind.ADDRESS_EXACT)
        return match(place, evidence, hostAgrees, isNew = true)
    }

    private fun match(
        place: RoadstrPlace,
        evidence: List<Evidence>,
        viaWebsite: Boolean,
        isNew: Boolean = false,
    ): WebPlaceMatch {
        val provenance = LinkedHashSet(place.sources)
        provenance += PlaceSource.SEARXNG
        if (viaWebsite) provenance += PlaceSource.WEBSITE
        return WebPlaceMatch(place, evidence, Confidence.combine(evidence), provenance, isNew)
    }

    private fun distance(place: RoadstrPlace, context: ResolveContext): Double =
        GeoMath.distanceMeters(context.center, place.position)

    private class KnownPlaces(val places: List<RoadstrPlace>) {
        private val bySite = places.mapNotNull { it.website?.host?.let(HostMatching::siteKey) }
            .groupingBy { it }.eachCount()
        private val byName = places.map { TextNormalizer.normalize(it.name) }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount()

        fun placesWithSite(key: String): Int = bySite[key] ?: 0

        fun placesNamed(name: String): Int = byName[TextNormalizer.normalize(name)] ?: 0
    }

    companion object {
        const val MAX_LOOKUPS = 3
        private const val NAME_THRESHOLD = 0.85
        private const val LOOKUP_NAME_THRESHOLD = 0.75
        private const val SHARED_SITE_WEIGHT = 0.3
        private const val CHAIN_NAME_WEIGHT = 0.15
        private const val DEFAULT_REACH_METERS = 30_000.0
    }
}
