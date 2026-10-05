package app.roadstr.roadtest

import android.content.Context
import org.json.JSONArray
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
import app.roadstr.service.search.NativeSearchPhase
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
) : NativeShellJourneyGateway {
    private val historyPreferences = NativeRoadTestProtectedPreferences(
        context = context,
        preferencesName = HISTORY_PREFERENCES,
        keyAlias = HISTORY_KEY_ALIAS,
    )
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

    private fun readSearchHistory(): List<SearchHistoryEntry> {
        val stored = historyPreferences.read(HISTORY_KEY) ?: return emptyList()
        val encoded = JSONArray(stored).let { array ->
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    array.optString(index, null)?.let(::add)
                }
            }
        }
        return SearchHistoryProtocol.decodeStored(encoded)
            .take(SearchHistoryProtocol.MAX_STORED_ITEMS)
    }

    override suspend fun loadSearchHistory(): List<SearchHistoryEntry> =
        runCatching(::readSearchHistory).getOrDefault(emptyList())

    override suspend fun saveSearchHistory(entry: SearchHistoryEntry): List<SearchHistoryEntry> =
        runCatching {
            // Mutations do not turn an authentication/corruption failure into
            // an empty history: preserve the ciphertext for recovery instead
            // of silently overwriting it with one new row.
            val updated = SearchHistoryProtocol.prepend(entry, readSearchHistory())
            val array = JSONArray()
            SearchHistoryProtocol.encodeStored(updated).forEach(array::put)
            check(historyPreferences.write(HISTORY_KEY, array.toString()))
            updated
        }.getOrDefault(emptyList())

    override suspend fun clearSearchHistory() {
        check(historyPreferences.remove(HISTORY_KEY))
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

        const val HISTORY_PREFERENCES = "roadtest_search_history"
        const val HISTORY_KEY = "entries"
        const val HISTORY_KEY_ALIAS = "app.roadstr.roadtest.search-history.v1"
    }
}
