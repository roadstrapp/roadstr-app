package app.roadstr.service.discovery

import app.roadstr.core.discovery.AreaGeocoding
import app.roadstr.core.discovery.DiscoveryNotice
import app.roadstr.core.discovery.DiscoveryOutcome
import app.roadstr.core.discovery.DiscoveryRanking
import app.roadstr.core.discovery.DiscoveryRequest
import app.roadstr.core.discovery.GeocodedArea
import app.roadstr.core.discovery.LocationConstraint
import app.roadstr.core.discovery.NaturalPlaceQuery
import app.roadstr.core.discovery.OverpassDiscoveryQuery
import app.roadstr.core.discovery.OverpassPlaceParser
import app.roadstr.core.discovery.PlaceDiscovery
import app.roadstr.core.discovery.QueryIntent
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.SearchArea
import app.roadstr.core.discovery.TextNormalizer
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.SearchProviderProtocol
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeSearchHttpTransport
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Answers "vegan restaurants in Florence" style searches from OpenStreetMap: at most
 * one Nominatim lookup for the place, one Overpass query, and one widening. Every
 * failure becomes [DiscoveryOutcome.NotApplicable] so the existing search still runs.
 */
class NativeDiscoveryService(
    private val transport: NativeSearchHttpTransport,
    private val overpassMirrors: List<String> = SearchProviderProtocol.overpassMirrors,
    private val nominatimPacer: HostPacer = HostPacer(NOMINATIM_SPACING_MILLIS),
    private val areaCache: TtlCache<String, List<GeocodedArea>> = TtlCache(32, AREA_TTL_MILLIS),
    private val overpassCache: TtlCache<String, String> = TtlCache(16, OVERPASS_TTL_MILLIS),
) : PlaceDiscovery {
    private val preferredMirror = AtomicInteger(0)

    init {
        require(overpassMirrors.isNotEmpty()) { "At least one Overpass mirror is required" }
    }

    override suspend fun discover(request: DiscoveryRequest): DiscoveryOutcome {
        if (request.query.intent != QueryIntent.FIND_PLACE) return DiscoveryOutcome.NotApplicable
        val notices = LinkedHashSet<DiscoveryNotice>()
        val area = resolveArea(request, notices) ?: return DiscoveryOutcome.NotApplicable
        return search(request, area, notices)
    }

    private suspend fun search(
        request: DiscoveryRequest,
        start: SearchArea,
        notices: MutableSet<DiscoveryNotice>,
    ): DiscoveryOutcome {
        var area = start
        var places = fetch(request, area) ?: return DiscoveryOutcome.NotApplicable
        if (places.isEmpty() && area is SearchArea.AdminArea) {
            area = area.fallback
            notices += DiscoveryNotice.AREA_FALLBACK
            places = fetch(request, area) ?: return DiscoveryOutcome.NotApplicable
        }
        val circle = area as? SearchArea.Circle
        if (places.size < MIN_RESULTS && circle != null && circle.radiusMeters < WIDE_RADIUS_METERS) {
            area = SearchArea.Circle(circle.center, WIDE_RADIUS_METERS)
            notices += DiscoveryNotice.WIDENED
            places = fetch(request, area) ?: return DiscoveryOutcome.NotApplicable
        }
        return outcome(request, area, places, notices)
    }

    private fun outcome(
        request: DiscoveryRequest,
        area: SearchArea,
        places: List<RoadstrPlace>,
        notices: MutableSet<DiscoveryNotice>,
    ): DiscoveryOutcome {
        val query = request.query
        val ranked = DiscoveryRanking.rank(places, query, area.center, request.device, request.now)
        if (query.attributes.isNotEmpty() && ranked.size < FEW_TAGGED_BELOW) {
            notices += DiscoveryNotice.FEW_TAGGED
        }
        if (query.openNow && ranked.any { it.open == app.roadstr.core.time.OpenState.UNKNOWN }) {
            notices += DiscoveryNotice.OPEN_HOURS_UNKNOWN
        }
        return if (ranked.isEmpty()) {
            DiscoveryOutcome.Empty(notices)
        } else {
            DiscoveryOutcome.Found(ranked, notices, area)
        }
    }

    private suspend fun resolveArea(
        request: DiscoveryRequest,
        notices: MutableSet<DiscoveryNotice>,
    ): SearchArea? {
        val query = request.query
        val guess = query.residualPlaceGuess
        if (!query.locationExplicit && guess != null) {
            geocodeNamed(request, guess, emptyList(), strict = true)?.let { return it }
        }
        return when (val location = query.location) {
            LocationConstraint.CurrentLocation -> around(request.device ?: request.mapCenter)
            LocationConstraint.MapCenter -> around(request.mapCenter ?: request.device)
            LocationConstraint.Destination -> around(request.destination ?: request.device)
            LocationConstraint.RouteCorridor -> {
                notices += DiscoveryNotice.ROUTE_UNSUPPORTED
                around(request.device ?: request.mapCenter)
            }
            is LocationConstraint.NamedPlace ->
                geocodeNamed(request, location.text, location.alternatives, strict = false)
            is LocationConstraint.NearReference -> referenceArea(request, location)
        }
    }

    private fun around(point: GeoPoint?): SearchArea? =
        point?.let { SearchArea.Circle(it, DEFAULT_RADIUS_METERS) }

    private suspend fun geocodeNamed(
        request: DiscoveryRequest,
        text: String,
        alternatives: List<String>,
        strict: Boolean,
    ): SearchArea? {
        for (candidate in listOf(text) + alternatives) {
            val areas = geocode(request, candidate)
            val chosen = AreaGeocoding.choose(areas, candidate, strict) ?: continue
            return AreaGeocoding.toSearchArea(chosen)
        }
        return null
    }

    private suspend fun referenceArea(
        request: DiscoveryRequest,
        reference: LocationConstraint.NearReference,
    ): SearchArea? {
        val point = if (reference.generic && reference.category != null) {
            nearestOfCategory(request, reference)
        } else {
            geocode(request, reference.text).let { AreaGeocoding.chooseAny(it, reference.text)?.center }
        }
        return point?.let { SearchArea.Circle(it, REFERENCE_RADIUS_METERS) }
    }

    private suspend fun nearestOfCategory(
        request: DiscoveryRequest,
        reference: LocationConstraint.NearReference,
    ): GeoPoint? {
        val origin = request.device ?: request.mapCenter ?: return null
        val category = requireNotNull(reference.category)
        val body = overpass(
            OverpassDiscoveryQuery.build(
                listOf(category), emptySet(), emptySet(),
                SearchArea.Circle(origin, DEFAULT_RADIUS_METERS), limit = REFERENCE_LIMIT,
            ),
        ) ?: return null
        val places = OverpassPlaceParser.parse(body, origin, request.languageCode)
        return places.minByOrNull { it.distanceMeters ?: Double.MAX_VALUE }?.position
    }

    private suspend fun geocode(request: DiscoveryRequest, text: String): List<GeocodedArea> {
        val key = TextNormalizer.normalize(text) + "|" + request.languageCode
        areaCache.get(key)?.let { return it }
        val near = request.device ?: request.mapCenter
        val call = AreaGeocoding.request(text, request.languageCode, near) ?: return emptyList()
        val response = guarded {
            nominatimPacer.paced { transport.execute(call, NOMINATIM_LIMITS) }
        }
        if (response == null || response.statusCode != HTTP_OK) return emptyList()
        return AreaGeocoding.parse(response.bodyUtf8).also { areaCache.put(key, it) }
    }

    private suspend fun fetch(request: DiscoveryRequest, area: SearchArea): List<RoadstrPlace>? {
        val query = request.query
        val text = OverpassDiscoveryQuery.build(query.categories, query.attributes, query.cuisines, area)
        val body = overpass(text) ?: return null
        val origin = request.device ?: area.center
        return OverpassPlaceParser.parse(body, origin, request.languageCode)
    }

    /** One Overpass query through the mirrors, trying the next one when a mirror fails. */
    private suspend fun overpass(query: String): String? {
        overpassCache.get(query)?.let { return it }
        val first = Math.floorMod(preferredMirror.get(), overpassMirrors.size)
        for (offset in overpassMirrors.indices) {
            val index = (first + offset) % overpassMirrors.size
            val call = SearchProviderProtocol.overpass(overpassMirrors[index], query)
            val response = guarded { transport.execute(call, OVERPASS_LIMITS) }
            val body = response?.takeIf { it.statusCode == HTTP_OK }?.bodyUtf8
            if (body != null && !isRuntimeError(body)) {
                preferredMirror.set(index)
                overpassCache.put(query, body)
                return body
            }
            preferredMirror.compareAndSet(index, (index + 1) % overpassMirrors.size)
        }
        return null
    }

    /** Overpass reports a busy or timed-out server inside a 200 answer with a remark. */
    private fun isRuntimeError(body: String): Boolean =
        body.contains("\"remark\"") && !body.contains("\"elements\":[{")

    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        null
    }

    companion object {
        const val NOMINATIM_SPACING_MILLIS = 1_100L
        const val AREA_TTL_MILLIS = 24L * 60 * 60 * 1000
        const val OVERPASS_TTL_MILLIS = 5L * 60 * 1000
        const val DEFAULT_RADIUS_METERS = 5_000
        const val WIDE_RADIUS_METERS = 12_000
        const val REFERENCE_RADIUS_METERS = 1_500
        const val REFERENCE_LIMIT = 20
        const val MIN_RESULTS = 3
        const val FEW_TAGGED_BELOW = 3
        private const val HTTP_OK = 200

        val NOMINATIM_LIMITS = NativeHttpRequestLimits(
            timeoutMillis = 5_000L,
            maxResponseBytes = NetworkResponseLimit.Route.bytes,
        )
        val OVERPASS_LIMITS = NativeHttpRequestLimits(
            timeoutMillis = 12_000L,
            maxResponseBytes = NetworkResponseLimit.AreaQuery.bytes,
        )
    }
}
