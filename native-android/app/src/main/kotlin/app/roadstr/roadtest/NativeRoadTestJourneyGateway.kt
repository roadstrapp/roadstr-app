package app.roadstr.roadtest

import android.content.Context
import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingProviderConfigProtocol
import app.roadstr.core.network.RoutingProviderConfiguration
import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.network.RoutingAvoidanceMode
import app.roadstr.feature.home.NativeShellJourneyGateway
import app.roadstr.feature.place.NativePlaceArticleInput
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.route.NativeRouteWeatherPresentation
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpMethod
import app.roadstr.service.network.NativeHttpRequest
import app.roadstr.service.routing.NativeRoutingQuery
import app.roadstr.service.routing.NativeAvoidanceRoutingQuery
import app.roadstr.service.routing.NativeRoutingService
import app.roadstr.service.discovery.CoarseLocality
import app.roadstr.service.discovery.HostPacer
import app.roadstr.service.discovery.NativeDiscoveryService
import app.roadstr.service.discovery.NativeLanHttpTransport
import app.roadstr.service.discovery.NativePlaceLookup
import app.roadstr.service.discovery.NativeSearxngProvider
import app.roadstr.service.search.NativeSearchPhase
import app.roadstr.core.discovery.DiscoveryOutcome
import app.roadstr.core.discovery.DiscoveryRequest
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.discovery.web.ConnectionTest
import app.roadstr.core.discovery.web.WebDiscoveryMode
import app.roadstr.core.discovery.web.WebDiscoveryOutcome
import app.roadstr.core.discovery.web.WebDiscoveryRequest
import app.roadstr.core.discovery.web.WebDiscoverySettings
import app.roadstr.core.discovery.web.WebQueryBuilder
import app.roadstr.core.discovery.web.WebSearchContext
import app.roadstr.service.search.NativeSearchQuery
import app.roadstr.service.search.NativeSearchService
import java.util.Locale
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Live, credential-free OSM/OSRM composition used only by the road-test APK. */
class NativeRoadTestJourneyGateway(
    context: Context,
    private val transport: NativeBoundedHttpClient = NativeBoundedHttpClient(),
    /** The provider, key and server the user picked in settings, read per request. */
    private val routingConfiguration: () -> RoutingProviderConfiguration = { OSRM_CONFIGURATION },
    /** What the user chose about web results, read per request so a change applies at once. */
    private val webSettings: () -> WebDiscoverySettings = { WebDiscoverySettings() },
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) : NativeShellJourneyGateway {
    private val history = NativeRoadTestSearchHistoryStore(context, names)
    private val searchService = NativeSearchService(transport)
    private val routingService = NativeRoutingService(transport)
    private val speedLimitResolver = NativeRoadTestSpeedLimitResolver(transport)

    override suspend fun probeRoutingServer(server: String, apiKey: String?): Boolean =
        runCatching { routingService.probeGraphHopper(server, apiKey) }.getOrDefault(false)

    override suspend fun search(
        query: String,
        near: SearchResponsePoint?,
        languageCode: String,
        onPartial: (List<SearchResult>) -> Unit,
    ): List<SearchResult> = searchService.search(
        query = NativeSearchQuery(
            query = query,
            near = near,
            languageCode = languageCode,
            phase = NativeSearchPhase.SETTLED,
        ),
        onPartial = onPartial,
    )

    override suspend fun routes(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        via: List<SearchResponsePoint>,
        mode: NativeRouteTransportMode,
        languageCode: String,
        avoidHighwaysAndTolls: Boolean,
        avoidUnpavedRoads: Boolean,
    ): List<RoutingParsedRoute> {
        require(mode != NativeRouteTransportMode.Transit) {
            "Public transport is not available in the road-test routing gateway"
        }
        val standard = routingService.getRoutes(
            NativeRoutingQuery(
                origin = origin.toRoutingPoint(),
                destination = destination.toRoutingPoint(),
                configuration = routingConfiguration(),
                languageCode = languageCode,
                vehicle = mode.wireValue,
                via = via.map { point -> point.toRoutingPoint() },
                requestAlternatives = via.isEmpty(),
            ),
        )
        if (mode != NativeRouteTransportMode.Driving || via.isNotEmpty()) return standard
        val avoidanceMode = when {
            avoidHighwaysAndTolls -> RoutingAvoidanceMode.HIGHWAYS_AND_TOLLS
            avoidUnpavedRoads -> RoutingAvoidanceMode.OFF_ROAD
            else -> return standard
        }
        val avoided = runCatching {
            routingService.getAvoidanceRoute(
                NativeAvoidanceRoutingQuery(
                    origin = origin.toRoutingPoint(),
                    destination = destination.toRoutingPoint(),
                    mode = avoidanceMode,
                    languageCode = languageCode,
                ),
            )
        }.getOrNull() ?: return standard
        return listOf(avoided) + standard
    }

    // One pacer for every Nominatim call, so the place search and the town lookup behind a
    // web query together stay under the service's one request per second.
    private val nominatimPacer = HostPacer(NativeDiscoveryService.NOMINATIM_SPACING_MILLIS)
    private val discovery = NativeDiscoveryService(transport, nominatimPacer = nominatimPacer)
    private val locality = CoarseLocality(transport, nominatimPacer)
    private val webProvider = NativeSearxngProvider(webSettings, transport, NativeLanHttpTransport())
    private val placeLookup = NativePlaceLookup(transport, nominatimPacer)

    override suspend fun discover(request: DiscoveryRequest): DiscoveryOutcome =
        discovery.discover(request)

    override suspend fun webSearch(context: WebSearchContext): WebDiscoveryOutcome {
        // Settings can change between the question and the answer; nothing is looked up when off.
        if (webSettings().mode == WebDiscoveryMode.OFF) return WebDiscoveryOutcome.Disabled
        val town = WebQueryBuilder.localityPoint(context)?.let { locality.nameOf(it, context.languageCode) }
        val text = WebQueryBuilder.build(context.parsed, town)
        val outcome = webProvider.discover(WebDiscoveryRequest(text, context.languageCode))
        // The town travels with the results so that a name can be looked up in the right place.
        return if (outcome is WebDiscoveryOutcome.Results) outcome.copy(locality = town) else outcome
    }

    override suspend fun lookupPlaces(
        name: String,
        locality: String?,
        near: GeoPoint,
        languageCode: String,
    ): List<RoadstrPlace> = placeLookup.find(name, locality, near, languageCode)

    override suspend fun testWebSearch(): ConnectionTest = webProvider.testConnection()

    override suspend fun reverseGeocode(
        point: SearchResponsePoint,
        languageCode: String,
    ) = searchService.reverseGeocode(point)

    override suspend fun wikipediaArticle(
        query: String,
        languageCode: String,
    ): NativePlaceArticleInput? {
        val cleanQuery = query.trim().take(200)
        if (cleanQuery.isEmpty()) return null
        val language = languageCode.lowercase(Locale.ROOT)
            .takeIf { it.matches(Regex("[a-z]{2,12}")) } ?: "en"
        val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.toString())
        val response = transport.execute(
            request = NativeHttpRequest(
                method = NativeHttpMethod.Get,
                uri = "https://$language.wikipedia.org/w/api.php" +
                    "?action=query&generator=search&gsrsearch=$encoded&gsrlimit=1" +
                    "&prop=extracts%7Cpageimages%7Cinfo&exintro=1&explaintext=1" +
                    "&piprop=thumbnail&pithumbsize=640&inprop=url&format=json&origin=*",
                headers = mapOf("User-Agent" to "Roadstr/1.0"),
            ),
            timeout = NetworkTimeoutBudget.Standard,
            responseLimit = NetworkResponseLimit.SmallJson,
        )
        if (response.statusCode != 200) return null
        val root = BoundedJsonParser(response.bodyUtf8).parse() as? Map<*, *> ?: return null
        val pages = ((root["query"] as? Map<*, *>)?.get("pages") as? Map<*, *>)
            ?.values ?: return null
        val page = pages.firstOrNull() as? Map<*, *> ?: return null
        return NativePlaceArticleInput(
            title = page["title"]?.toString() ?: return null,
            extract = page["extract"]?.toString().orEmpty(),
            imageUrl = ((page["thumbnail"] as? Map<*, *>)?.get("source"))?.toString(),
            pageUrl = page["fullurl"]?.toString(),
        )
    }

    override suspend fun speedLimit(point: SearchResponsePoint): Int? =
        speedLimitResolver.resolve(point)

    override suspend fun weather(destination: SearchResponsePoint): NativeRouteWeatherPresentation? {
        // Weather is regional. Match main's privacy boundary: ~1.1 km
        // precision instead of disclosing a street-level destination.
        val latitude = String.format(Locale.ROOT, "%.2f", destination.latitude)
        val longitude = String.format(Locale.ROOT, "%.2f", destination.longitude)
        val response = transport.execute(
            request = NativeHttpRequest(
                method = NativeHttpMethod.Get,
                uri = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,wind_speed_10m" +
                    "&wind_speed_unit=kmh",
                headers = mapOf("User-Agent" to "Roadstr/1.0"),
            ),
            timeout = NetworkTimeoutBudget.Standard,
            responseLimit = NetworkResponseLimit.SmallJson,
        )
        if (response.statusCode != 200) return null
        val root = BoundedJsonParser(response.bodyUtf8).parse() as? Map<*, *> ?: return null
        val current = root["current"] as? Map<*, *> ?: return null
        return NativeRouteWeatherPresentation(
            temperatureCelsius = (current["temperature_2m"] as? Number)?.toDouble() ?: return null,
            weatherCode = (current["weather_code"] as? Number)?.toInt() ?: return null,
            windKilometresPerHour = (current["wind_speed_10m"] as? Number)?.toDouble() ?: return null,
        )
    }

    override suspend fun reroute(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        mode: NativeRouteTransportMode,
        languageCode: String,
        speedKilometresPerHour: Double,
        headingDegrees: Double?,
        straightLineDistanceMeters: Double,
        avoidUnpavedRoads: Boolean,
    ): List<RoutingParsedRoute> {
        require(mode != NativeRouteTransportMode.Transit) {
            "Public transport is not available in the road-test routing gateway"
        }
        val standard = routingService.getRerouteRoutes(
            query = NativeRoutingQuery(
                origin = origin.toRoutingPoint(),
                destination = destination.toRoutingPoint(),
                configuration = routingConfiguration(),
                languageCode = languageCode,
                vehicle = mode.wireValue,
                requestAlternatives = false,
                originBearingDegrees = headingDegrees,
            ),
            speedKilometresPerHour = speedKilometresPerHour,
            straightLineDistanceMeters = straightLineDistanceMeters,
        )
        if (!avoidUnpavedRoads || mode != NativeRouteTransportMode.Driving) return standard
        val avoided = runCatching {
            routingService.getAvoidanceRoute(
                NativeAvoidanceRoutingQuery(
                    origin = origin.toRoutingPoint(),
                    destination = destination.toRoutingPoint(),
                    mode = RoutingAvoidanceMode.OFF_ROAD,
                    languageCode = languageCode,
                ),
            )
        }.getOrNull()
        return if (avoided == null) standard else listOf(avoided)
    }

    override suspend fun loadSearchHistory(): List<SearchHistoryEntry> =
        runCatching(history::read).getOrDefault(emptyList())

    override suspend fun saveSearchHistory(entry: SearchHistoryEntry): List<SearchHistoryEntry> =
        runCatching {
            // Mutations do not turn an authentication/corruption failure into
            // an empty history: preserve the ciphertext for recovery instead
            // of silently overwriting it with one new row.
            val updated = SearchHistoryProtocol.prepend(entry, history.read())
            check(history.write(updated))
            updated
        }.getOrDefault(emptyList())

    override suspend fun clearSearchHistory() {
        check(history.clear())
    }

    private fun SearchResponsePoint.toRoutingPoint() = RoutingRequestPoint(
        latitude = latitude,
        longitude = longitude,
    )

    private companion object {
        val OSRM_CONFIGURATION: RoutingProviderConfiguration = RoutingProviderConfigProtocol.resolve(
            providerKey = "osrm",
            secureApiKey = null,
            legacyApiKey = "",
            graphHopperServer = "",
            deferCredentialReadForOsrm = true,
        )
    }
}
