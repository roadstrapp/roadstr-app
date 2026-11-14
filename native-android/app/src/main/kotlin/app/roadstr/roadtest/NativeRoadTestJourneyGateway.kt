package app.roadstr.roadtest

import android.content.Context
import app.roadstr.core.search.SearchHistoryEntry
import app.roadstr.core.search.SearchHistoryProtocol
import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.RerouteUTurnPolicy
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingProviderConfigProtocol
import app.roadstr.core.network.RoutingProviderConfiguration
import app.roadstr.core.network.RoutingRequestPoint
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.core.network.SearchProviderProtocol
import app.roadstr.core.network.RoutingAvoidanceMode
import app.roadstr.feature.home.NativeShellJourneyGateway
import app.roadstr.feature.transit.humanCategory
import app.roadstr.feature.transit.stringResourceId
import app.roadstr.feature.place.NativePlaceArticleInput
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.route.NativeRouteWeatherPresentation
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpMethod
import app.roadstr.service.network.NativeHttpRequest
import app.roadstr.service.routing.NativeRoutingQuery
import app.roadstr.service.routing.NativeAvoidanceRoutingQuery
import app.roadstr.service.routing.NativeRoutingException
import app.roadstr.service.routing.NativeRoutingFailureKind
import app.roadstr.service.routing.NativeRoutingService
import app.roadstr.service.routing.LocalRoutingDataset
import app.roadstr.service.routing.OfflineRoutingPreferences
import app.roadstr.service.routing.RoutingEngine
import app.roadstr.service.routing.RoutingEngineFailureKind
import app.roadstr.service.routing.RoutingEngineOutcome
import app.roadstr.service.routing.RoutingEngineRequest
import app.roadstr.service.routing.RoutingEngineSelection
import app.roadstr.service.routing.RoutingEngineSelectionInput
import app.roadstr.service.routing.RoutingEngineSelector
import app.roadstr.service.routing.NativeRoutingSelectionException
import app.roadstr.service.offline.InstalledOfflinePackage
import app.roadstr.service.offline.OfflineCoverageIndex
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
import app.roadstr.service.transit.NativeTransitResult
import app.roadstr.service.transit.NativeTransitProviderRouter
import app.roadstr.service.transit.NativeTransitQuery
import app.roadstr.service.transit.NativeEnturTransitService
import app.roadstr.service.transit.NativeTransitService
import app.roadstr.service.transit.NativeUnavailableTransitProvider
import app.roadstr.service.transit.NativeTransitData
import app.roadstr.service.transit.NativeTransitDataResult
import app.roadstr.service.transit.NativeTransitDeparturesQuery
import app.roadstr.service.transit.NativeTransitStopSearchQuery
import app.roadstr.service.transit.NativeTransitReferenceQuery
import app.roadstr.core.network.TransitStopBoard
import app.roadstr.core.network.TransitStopReference
import app.roadstr.core.network.TransitRequestPoint
import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitScopedId
import java.time.Instant
import java.util.Locale
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import app.roadstr.core.geo.CityCountryIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Live, credential-free OSM/search/routing/transit composition for the road-test APK. */
class NativeRoadTestJourneyGateway(
    context: Context,
    private val transport: NativeBoundedHttpClient = NativeBoundedHttpClient(),
    /** The provider, key and server the user picked in settings, read per request. */
    private val routingConfiguration: () -> RoutingProviderConfiguration = { OSRM_CONFIGURATION },
    /** What the user chose about web results, read per request so a change applies at once. */
    private val webSettings: () -> WebDiscoverySettings = { WebDiscoverySettings() },
    private val offlineSettings: () -> OfflineRoutingPreferences = { OfflineRoutingPreferences() },
    private val installedPackages: () -> List<InstalledOfflinePackage> = { emptyList() },
    private val networkAvailable: () -> Boolean = { true },
    private val localRoutingEngine: RoutingEngine? = null,
    private val overpassEndpoint: String? = null,
    names: NativeLiveStoreNames = NativeLiveStoreNames(),
) : NativeShellJourneyGateway {
    private val context = context.applicationContext
    private val history = NativeRoadTestSearchHistoryStore(context, names)
    private val searchService = NativeSearchService(transport)
    private val routingService = NativeRoutingService(transport)
    private val enturTransit = NativeEnturTransitService(transport)
    private val transitous = NativeTransitService(transport)
    private val speedLimitResolver = NativeRoadTestSpeedLimitResolver(transport)
    @Volatile private var lastEngineId = routingConfiguration().provider.name.lowercase(Locale.ROOT)
    @Volatile private var lastProviderId = lastEngineId

    override fun routingProviderId(): String = lastProviderId

    override fun routingEngineId(): String = lastEngineId

    override suspend fun probeRoutingServer(server: String, apiKey: String?): Boolean =
        runCatching { routingService.probeGraphHopper(server, apiKey) }.getOrDefault(false)

    override suspend fun search(
        query: String,
        near: SearchResponsePoint?,
        languageCode: String,
        onPartial: (List<SearchResult>) -> Unit,
    ): List<SearchResult> = coroutineScope {
        val places = async {
            searchService.search(
                query = NativeSearchQuery(
                    query = query,
                    near = near,
                    languageCode = languageCode,
                    phase = NativeSearchPhase.SETTLED,
                ),
                onPartial = onPartial,
            )
        }
        val stops = async {
            transitRouter().searchStops(
                NativeTransitStopSearchQuery(
                    query = query,
                    location = near?.let { TransitRequestPoint(it.latitude, it.longitude) },
                    languageCode = languageCode,
                ),
            )
        }
        val geographical = places.await()
        val operational = (stops.await() as? NativeTransitData)?.value.orEmpty().mapNotNull(::transitSearchResult)
        mergeGenericSearchResults(geographical, operational)
    }

    override suspend fun transitDepartures(
        stop: TransitStopReference,
        at: Instant,
        languageCode: String,
    ): NativeTransitDataResult<TransitStopBoard> = transitRouter().departures(
        NativeTransitDeparturesQuery(stop, at, languageCode),
    )

    override suspend fun transitLines(
        stop: TransitStopReference,
        languageCode: String,
    ) = transitRouter().lines(
        app.roadstr.service.transit.NativeTransitStopQuery(stop, languageCode),
    )

    override suspend fun transitTrip(
        reference: TransitScopedId,
        languageCode: String,
    ) = transitRouter().trip(
        NativeTransitReferenceQuery(reference, languageCode),
    )

    override suspend fun nearbyTransitStops(
        near: SearchResponsePoint,
        languageCode: String,
    ): List<SearchResult> = (transitRouter().nearbyStops(
        TransitRequestPoint(near.latitude, near.longitude),
        languageCode,
    ) as? NativeTransitData)?.value.orEmpty().mapNotNull(::transitSearchResult)

    override suspend fun transitRefresh(
        reference: TransitScopedId,
        languageCode: String,
    ): NativeTransitDataResult<TransitItinerary> = transitRouter().refreshItinerary(
        NativeTransitReferenceQuery(reference, languageCode),
    )

    override suspend fun transitReplan(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        earliestDeparture: Instant,
        providerId: String,
        languageCode: String,
    ): NativeTransitResult = transitRouter().replan(
        NativeTransitQuery(
            from = TransitRequestPoint(origin.latitude, origin.longitude),
            to = TransitRequestPoint(destination.latitude, destination.longitude),
            departure = earliestDeparture,
            languageCode = languageCode,
        ),
        providerId = providerId,
    )

    private fun transitSearchResult(
        result: app.roadstr.core.network.TransitStopSearchResult,
    ): SearchResult? {
        val point = result.stop.position ?: return null
        return SearchResult(
            displayName = listOfNotNull(result.stop.name, result.locality).joinToString(", "),
            shortName = result.stop.name,
            position = SearchResponsePoint(point.latitude, point.longitude),
            featureClass = "public_transport",
            type = "stop",
            city = result.locality,
            emojiOverride = "◉",
            categoryLabelOverride = result.modes
                .map { context.getString(it.humanCategory().stringResourceId()) }
                .distinct()
                .joinToString(" · "),
            transitStop = result.stop,
        )
    }

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
        val local = localSelection(listOf(origin) + via + destination, mode)
        if (local != null) {
            return localRoutes(
                origin,
                destination,
                via,
                mode,
                languageCode,
                avoidHighwaysAndTolls,
                avoidUnpavedRoads,
                local.installed,
            )
        }
        return onlineRoutes(
            origin,
            destination,
            via,
            mode,
            languageCode,
            avoidHighwaysAndTolls,
            avoidUnpavedRoads,
        )
    }

    private class LocalSelection(val installed: InstalledOfflinePackage?)

    /**
     * The on-device engine when offline routing is on and covers [points]; null when the trip goes
     * online (offline routing off, or allowed to fall back). Throws when neither may answer.
     */
    private fun localSelection(points: List<SearchResponsePoint>, mode: NativeRouteTransportMode): LocalSelection? {
        val preferences = offlineSettings()
        if (!preferences.enabled) return null
        val installed = installedPackages()
        val coverage = OfflineCoverageIndex(installed)
        val covering = coverage.coveringPackages(points.map { it.toRoutingPoint() })
        val selection = RoutingEngineSelector.select(
            RoutingEngineSelectionInput(
                offlineRoutingEnabled = true,
                coverage = coverage.coversRoute(points.map { it.toRoutingPoint() }),
                networkAvailable = networkAvailable(),
                onlineFallbackAllowed = preferences.onlineFallbackAllowed,
                mode = mode,
            ),
        )
        return when (selection) {
            RoutingEngineSelection.Local -> LocalSelection(installed.firstOrNull { it.artifact.id in covering })
            RoutingEngineSelection.Online -> null
            is RoutingEngineSelection.Unavailable -> throw NativeRoutingSelectionException(selection.reason)
        }
    }

    override suspend fun transitPlan(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        languageCode: String,
    ): NativeTransitResult = transitRouter().plan(
        NativeTransitQuery(
            from = TransitRequestPoint(origin.latitude, origin.longitude),
            to = TransitRequestPoint(destination.latitude, destination.longitude),
            departure = Instant.now(),
            languageCode = languageCode,
        ),
    )

    /** Avoids accidental provider calls when Android reports that the network is unavailable. */
    private fun transitRouter(): NativeTransitProviderRouter {
        val providers = if (networkAvailable()) listOf(enturTransit, transitous) else listOf(NativeUnavailableTransitProvider)
        return NativeTransitProviderRouter(providers)
    }

    private suspend fun onlineRoutes(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        via: List<SearchResponsePoint>,
        mode: NativeRouteTransportMode,
        languageCode: String,
        avoidHighwaysAndTolls: Boolean,
        avoidUnpavedRoads: Boolean,
    ): List<RoutingParsedRoute> {
        val configuration = routingConfiguration()
        val standard = routingService.getRoutes(
            NativeRoutingQuery(
                origin = origin.toRoutingPoint(),
                destination = destination.toRoutingPoint(),
                configuration = configuration,
                languageCode = languageCode,
                vehicle = mode.wireValue,
                via = via.map { point -> point.toRoutingPoint() },
                requestAlternatives = via.isEmpty(),
            ),
        )
        lastProviderId = configuration.provider.name.lowercase(Locale.ROOT)
        lastEngineId = lastProviderId
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

    private suspend fun localRoutes(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        via: List<SearchResponsePoint>,
        mode: NativeRouteTransportMode,
        languageCode: String,
        avoidHighwaysAndTolls: Boolean,
        avoidUnpavedRoads: Boolean,
        installed: InstalledOfflinePackage?,
        originHeadingDegrees: Double? = null,
        originHeadingToleranceDegrees: Int = RoutingEngineRequest.DEFAULT_HEADING_TOLERANCE_DEGREES,
    ): List<RoutingParsedRoute> {
        val engine = localRoutingEngine
            ?: throw NativeRoutingSelectionException(app.roadstr.service.routing.RoutingEngineUnavailableReason.DatasetMissing)
        val value = installed
            ?: throw NativeRoutingSelectionException(app.roadstr.service.routing.RoutingEngineUnavailableReason.AreaNotDownloaded)
        val avoidance = when {
            avoidHighwaysAndTolls -> app.roadstr.core.network.RoutingRouteAvoidance.HighwayAndTollFree
            avoidUnpavedRoads -> app.roadstr.core.network.RoutingRouteAvoidance.OffRoadAvoided
            else -> app.roadstr.core.network.RoutingRouteAvoidance.None
        }
        val outcome = engine.route(
            RoutingEngineRequest(
                origin = origin.toRoutingPoint(),
                destination = destination.toRoutingPoint(),
                via = via.map { it.toRoutingPoint() },
                mode = mode,
                languageCode = languageCode,
                avoidance = avoidance,
                requestAlternatives = false,
                localDataset = LocalRoutingDataset(
                    id = value.artifact.id,
                    version = value.artifact.version,
                    tileExtractPath = value.filePath,
                    buildId = value.artifact.build.compatibilityId,
                ),
                originHeadingDegrees = originHeadingDegrees,
                originHeadingToleranceDegrees = originHeadingToleranceDegrees,
            ),
        )
        return when (outcome) {
            is RoutingEngineOutcome.Success -> {
                lastProviderId = "local"
                lastEngineId = engine.id
                outcome.routes
            }
            is RoutingEngineOutcome.Unavailable -> throw NativeRoutingSelectionException(outcome.reason)
            is RoutingEngineOutcome.Failed -> throw app.roadstr.service.routing.NativeRoutingException(
                if (outcome.kind == RoutingEngineFailureKind.InvalidRequest) {
                    app.roadstr.service.routing.NativeRoutingFailureKind.InvalidConfiguration
                } else {
                    app.roadstr.service.routing.NativeRoutingFailureKind.InvalidResponse
                },
            )
        }
    }

    // One pacer for every Nominatim call, so the place search and the town lookup behind a
    // web query together stay under the service's one request per second.
    private val nominatimPacer = HostPacer(NativeDiscoveryService.NOMINATIM_SPACING_MILLIS)
    private val discovery = NativeDiscoveryService(
        transport,
        networkAvailable = networkAvailable,
        overpassMirrors = SearchProviderProtocol.overpassMirrors(overpassEndpoint),
        nominatimPacer = nominatimPacer,
    )
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
        val query = NativeRoutingQuery(
            origin = origin.toRoutingPoint(),
            destination = destination.toRoutingPoint(),
            configuration = routingConfiguration(),
            languageCode = languageCode,
            vehicle = mode.wireValue,
            requestAlternatives = false,
            originBearingDegrees = headingDegrees,
        )
        val local = localSelection(listOf(origin, destination), mode)
        var standard: List<RoutingParsedRoute> = emptyList()
        val preferred = when {
            local != null -> localRoutes(
                origin, destination, emptyList(), mode, languageCode,
                avoidHighwaysAndTolls = false,
                avoidUnpavedRoads = avoidUnpavedRoads,
                installed = local.installed,
            )
            // Offline routing on, but this trip may and does go online: the same answer routes() gives.
            offlineSettings().enabled -> onlineRoutes(
                origin, destination, emptyList(), mode, languageCode,
                avoidHighwaysAndTolls = false,
                avoidUnpavedRoads = avoidUnpavedRoads,
            )
            else -> {
                standard = routingService.getRerouteRoutes(
                    query = query,
                    speedKilometresPerHour = speedKilometresPerHour,
                    straightLineDistanceMeters = straightLineDistanceMeters,
                )
                val avoided = if (avoidUnpavedRoads && mode == NativeRouteTransportMode.Driving) {
                    runCatching {
                        routingService.getAvoidanceRoute(
                            NativeAvoidanceRoutingQuery(
                                origin = origin.toRoutingPoint(),
                                destination = destination.toRoutingPoint(),
                                mode = RoutingAvoidanceMode.OFF_ROAD,
                                languageCode = languageCode,
                            ),
                        )
                    }.getOrNull()
                } else {
                    null
                }
                if (avoided == null) standard else listOf(avoided)
            }
        }
        if (mode != NativeRouteTransportMode.Driving) return preferred
        val chosen = preferred.firstOrNull() ?: return preferred
        // Most answers go on ahead and need nothing more. One that starts by turning round is fine in
        // town and not on a motorway: only then is the road under the car looked up.
        if (!RerouteUTurnPolicy.reversesDirection(chosen, headingDegrees)) return preferred
        if (!uTurnForbidden(origin, speedKilometresPerHour)) return preferred
        val usable = RerouteUTurnPolicy.withoutReversals((preferred + standard).distinct(), headingDegrees)
        if (usable.isNotEmpty()) return usable
        // Ask again with the driving direction as a wide constraint: on to the next exit, then back.
        if (local == null) return routingService.getRerouteRoutesWithoutUTurn(query)
        if (headingDegrees == null) throw NativeRoutingException(NativeRoutingFailureKind.UTurnRejected)
        val directed = try {
            localRoutes(
                origin, destination, emptyList(), mode, languageCode,
                avoidHighwaysAndTolls = false,
                avoidUnpavedRoads = avoidUnpavedRoads,
                installed = local.installed,
                originHeadingDegrees = headingDegrees,
                originHeadingToleranceDegrees = RerouteUTurnPolicy.WIDE_BEARING_TOLERANCE_DEGREES,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Valhalla answers 171 when no edge goes the right way; that is not a missing area.
            emptyList()
        }
        return RerouteUTurnPolicy.withoutReversals(directed, headingDegrees).ifEmpty {
            throw NativeRoutingException(NativeRoutingFailureKind.UTurnRejected)
        }
    }

    private suspend fun uTurnForbidden(point: SearchResponsePoint, speedKilometresPerHour: Double): Boolean {
        val road = if (networkAvailable()) speedLimitResolver.forbidsUTurn(point) else null
        if (road != null) return RerouteUTurnPolicy.forbidsUTurn(road, speedKilometresPerHour, null)
        // OSM could not say: the speed stands in, against the country's rural limit (Italy: 90).
        val place = cityCountryIndex()?.nearest(point.latitude, point.longitude)
        val threshold = RerouteUTurnPolicy.fallbackSpeedKmh(place?.countryCode, place?.admin1Code)
        // Mostly the value the trip already holds for this stretch; a stuck lookup never holds the reroute up.
        val limit = if (speedKilometresPerHour > threshold) {
            withTimeoutOrNull(SPEED_LIMIT_LOOKUP_MILLIS) {
                try {
                    speedLimitResolver.resolve(point)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            null
        }
        return RerouteUTurnPolicy.forbidsUTurn(null, speedKilometresPerHour, limit, threshold)
    }

    @Volatile private var cityCountries: CityCountryIndex? = null

    /** The bundled GeoNames cities, read once; null when the asset is not in this build. */
    private suspend fun cityCountryIndex(): CityCountryIndex? {
        cityCountries?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                // AAPT expands .gz assets and exposes them without the suffix.
                context.assets.open("offline_catalog/geonames-cities15000.tsv").bufferedReader(Charsets.UTF_8)
                    .useLines { lines -> CityCountryIndex.fromTsv(lines) }
            }.getOrNull()?.also { cityCountries = it }
        }
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
        const val SPEED_LIMIT_LOOKUP_MILLIS = 3_000L
        val OSRM_CONFIGURATION: RoutingProviderConfiguration = RoutingProviderConfigProtocol.resolve(
            providerKey = "osrm",
            secureApiKey = null,
            legacyApiKey = "",
            graphHopperServer = "",
            deferCredentialReadForOsrm = true,
        )
    }
}

/**
 * Keeps ordinary addresses and POIs as the primary generic-search results while
 * still reserving room for operational stops. Nearby > Transit has its own
 * dedicated path and is intentionally not merged here.
 */
internal fun mergeGenericSearchResults(
    geographical: List<SearchResult>,
    operational: List<SearchResult>,
): List<SearchResult> {
    val uniquePlaces = geographical.distinctBy(::genericSearchResultKey)
    val knownKeys = uniquePlaces.mapTo(mutableSetOf(), ::genericSearchResultKey)
    val uniqueStops = operational
        .distinctBy(::genericSearchResultKey)
        .filter { knownKeys.add(genericSearchResultKey(it)) }
    return buildList(uniquePlaces.size + uniqueStops.size) {
        addAll(uniquePlaces.take(GENERIC_SEARCH_PLACE_SLOTS))
        addAll(uniqueStops.take(GENERIC_SEARCH_TRANSIT_SLOTS))
        addAll(uniquePlaces.drop(GENERIC_SEARCH_PLACE_SLOTS))
        addAll(uniqueStops.drop(GENERIC_SEARCH_TRANSIT_SLOTS))
    }
}

private fun genericSearchResultKey(value: SearchResult): Any =
    value.transitStop?.id
        ?: "${value.position.latitude}:${value.position.longitude}:${value.shortName.lowercase(Locale.ROOT)}"

private const val GENERIC_SEARCH_PLACE_SLOTS = 20
private const val GENERIC_SEARCH_TRANSIT_SLOTS = 5
