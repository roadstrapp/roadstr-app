package app.roadstr.feature.home

import app.roadstr.core.discovery.DiscoveryOutcome
import app.roadstr.core.discovery.DiscoveryRequest
import app.roadstr.core.discovery.LocationConstraint
import app.roadstr.core.discovery.NaturalPlaceQuery
import app.roadstr.core.discovery.NaturalQueryParser
import app.roadstr.core.discovery.QueryInterpreter
import app.roadstr.core.discovery.RankedPlace
import app.roadstr.core.discovery.RoadstrPlace
import app.roadstr.core.discovery.SearchArea
import app.roadstr.core.discovery.resolve.MatchClass
import app.roadstr.core.discovery.resolve.PageMatcher
import app.roadstr.core.discovery.resolve.PagePlace
import app.roadstr.core.discovery.resolve.PlaceEntityResolver
import app.roadstr.core.discovery.resolve.PlaceLookup
import app.roadstr.core.discovery.resolve.ResolveContext
import app.roadstr.core.discovery.resolve.ResolvedWebResult
import app.roadstr.core.discovery.resolve.reachMeters
import app.roadstr.core.discovery.structured.StructuredPlace
import app.roadstr.core.discovery.structured.WebPageMessage
import app.roadstr.core.discovery.web.ConnectionTest
import app.roadstr.core.discovery.web.EndpointCheck
import app.roadstr.core.discovery.web.SearxngEndpointPolicy
import app.roadstr.core.discovery.web.WebDiscoveryMode
import app.roadstr.core.discovery.web.WebDiscoveryOutcome
import app.roadstr.core.discovery.web.WebDiscoverySettings
import app.roadstr.core.discovery.web.WebQueryBuilder
import app.roadstr.core.discovery.web.WebSearchContext
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.time.OpenState
import app.roadstr.feature.search.NativeSearchWeb
import app.roadstr.feature.search.NativeWebPlaceLink
import app.roadstr.feature.search.NativeWebProblem
import app.roadstr.feature.search.toWebProblem
import app.roadstr.feature.search.NativeWebResultPresentation
import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.feature.discovery.DiscoveryPresentation
import app.roadstr.feature.search.NativeSearchNotice
import java.time.LocalDateTime
import app.roadstr.core.network.NominatimReverseDetail
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.feature.navigation.NativeNavigationRerouteRequest
import app.roadstr.feature.place.NativePlaceArticleInput
import app.roadstr.feature.route.NativeRoutePlanningCandidate
import app.roadstr.feature.route.NativeRoutePlanningSession
import app.roadstr.feature.route.NativeRoutePlanningSnapshot
import app.roadstr.feature.route.NativeRoutePlanningFailure
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.route.NativeRouteWeatherPresentation
import app.roadstr.feature.savedroute.NativeSavedRoute
import app.roadstr.feature.savedroute.NativeSavedRoutePreferences
import app.roadstr.feature.savedroute.NativeSavedRouteProtocol
import app.roadstr.feature.savedroute.NativeSavedRouteStop
import app.roadstr.feature.search.NativeSearchNearbyCategory
import app.roadstr.feature.search.NativeSearchResultPresentation
import app.roadstr.feature.search.NativeSearchFavorite
import app.roadstr.feature.search.NativeSearchSession
import app.roadstr.core.search.SearchHistoryEntry
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import app.roadstr.service.routing.NativeRoutingSelectionException
import app.roadstr.service.routing.RoutingEngineUnavailableReason

/**
 * Provider-neutral network boundary consumed by the shared Compose shell.
 *
 * Concrete endpoint selection stays in the standalone application composition
 * root. This interface deliberately exposes only bounded, parsed values.
 */
interface NativeShellJourneyGateway {
    /** Actual online engine after configuration fallback, never a credential or endpoint. */
    fun routingProviderId(): String = "unknown"

    /** Engine that produced the latest accepted route; local routing reports valhalla-local. */
    fun routingEngineId(): String = routingProviderId()

    suspend fun search(
        query: String,
        near: SearchResponsePoint?,
        languageCode: String,
        onPartial: (List<SearchResult>) -> Unit,
    ): List<SearchResult>

    suspend fun routes(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        via: List<SearchResponsePoint>,
        mode: NativeRouteTransportMode,
        languageCode: String,
        avoidHighwaysAndTolls: Boolean = false,
        avoidUnpavedRoads: Boolean = false,
    ): List<RoutingParsedRoute>

    suspend fun reroute(
        origin: SearchResponsePoint,
        destination: SearchResponsePoint,
        mode: NativeRouteTransportMode,
        languageCode: String,
        speedKilometresPerHour: Double,
        headingDegrees: Double?,
        straightLineDistanceMeters: Double,
        avoidUnpavedRoads: Boolean = false,
    ): List<RoutingParsedRoute> = routes(
        origin = origin,
        destination = destination,
        via = emptyList(),
        mode = mode,
        languageCode = languageCode,
        avoidUnpavedRoads = avoidUnpavedRoads,
    )

    suspend fun reverseGeocode(
        point: SearchResponsePoint,
        languageCode: String,
    ): NominatimReverseDetail? = null

    /** Whether [server] answers like a GraphHopper instance; false without a routing backend. */
    suspend fun probeRoutingServer(server: String, apiKey: String?): Boolean = false

    /** Wikipedia preview resolved from a place label; precise coordinates are not disclosed. */
    suspend fun wikipediaArticle(
        query: String,
        languageCode: String,
    ): NativePlaceArticleInput? = null

    suspend fun weather(destination: SearchResponsePoint): NativeRouteWeatherPresentation? = null

    /** Explicit posted limit of the geometrically nearest road, when known. */
    suspend fun speedLimit(point: SearchResponsePoint): Int? = null

    /** Natural-language place search over open data; the default leaves it to the classic search. */
    suspend fun discover(request: DiscoveryRequest): DiscoveryOutcome = DiscoveryOutcome.NotApplicable

    /** Web results for the typed words; the default has no web provider. */
    suspend fun webSearch(context: WebSearchContext): WebDiscoveryOutcome = WebDiscoveryOutcome.Disabled

    /** Places a name might refer to near a town, for tying a web result to the map; none by default. */
    suspend fun lookupPlaces(
        name: String,
        locality: String?,
        near: GeoPoint,
        languageCode: String,
    ): List<RoadstrPlace> = emptyList()

    /** The "test connection" button of the web search settings. */
    suspend fun testWebSearch(): ConnectionTest = ConnectionTest.NotConfigured

    suspend fun loadSearchHistory(): List<SearchHistoryEntry> = emptyList()

    suspend fun saveSearchHistory(entry: SearchHistoryEntry): List<SearchHistoryEntry> = emptyList()

    suspend fun clearSearchHistory() = Unit
}

/**
 * Lifecycle-scoped orchestration for destination search and route preview.
 *
 * It owns no Android context, endpoint, credential, location source or
 * persistence. Cancelling or replacing work fences every late result through
 * the existing session revisions.
 */
class NativeShellJourneyCoordinator(
    private val gateway: NativeShellJourneyGateway,
    private val scope: CoroutineScope,
    private val searchSession: NativeSearchSession,
    private val routeSession: NativeRoutePlanningSession,
    private val languageCode: String = Locale.getDefault().language,
    /** The saved places, read each time the search opens so the list is never stale. */
    private val favorites: () -> List<NativeSearchFavorite> = { emptyList() },
    /** Understands "vegan near me" style queries; anything it does not understand stays classic. */
    private val interpreter: QueryInterpreter = NaturalQueryParser(),
    /** Where the current trip ends, for "near my destination". */
    private val destination: () -> SearchResponsePoint? = { null },
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    /** Told which places the latest search found, or none, so the map can pin them. */
    private val onDiscovery: (revision: Long, places: List<RankedPlace>) -> Unit = { _, _ -> },
    /** What the user chose about web search; read each time, so a change applies at once. */
    private val webSettings: () -> WebDiscoverySettings = { WebDiscoverySettings() },
    /** The part of the route still to drive, for "along my route"; empty when there is no route. */
    private val routeAhead: () -> List<GeoPoint> = { emptyList() },
) {
    private var searchRevision = searchSession.state.value.revision.coerceAtLeast(0L)
    private var routeRevision = routeSession.state.value.revision.coerceAtLeast(0L)
    private var searchJob: Job? = null
    private var webJob: Job? = null
    private var lastPlaces: List<RankedPlace> = emptyList()
    private var lastArea: SearchArea? = null
    private var lastParsed: NaturalPlaceQuery? = null
    private var lastNear: SearchResponsePoint? = null
    private var webPlaces: Map<String, RoadstrPlace> = emptyMap()
    private val resolver = PlaceEntityResolver(
        PlaceLookup { name, locality, near, language -> gateway.lookupPlaces(name, locality, near, language) },
    )
    private var pendingWeb: PendingWeb? = null
    private var routeJob: Job? = null
    private var rerouteJob: Job? = null
    private var selectedDestination: SelectedDestination? = null
    private var navigationDestination: SearchResponsePoint? = null
    private var navigationDestinationRevision = NO_REVISION
    private var resolvedRouteStops: List<NativeSavedRouteStop> = emptyList()

    fun openSearch(nearbyEnabled: Boolean): Boolean {
        cancelSearchWork()
        onDiscovery(searchSession.state.value.revision, emptyList())
        selectedDestination = null
        clearNavigationDestination()
        val revision = nextSearchRevision()
        val opened = searchSession.show(
            revision,
            favorites = runCatching(favorites).getOrDefault(emptyList()),
            nearbyEnabled = nearbyEnabled,
        )
        if (opened) {
            scope.launch {
                val history = runCatching { gateway.loadSearchHistory() }.getOrDefault(emptyList())
                searchSession.replaceHistory(revision, history)
            }
        }
        return opened
    }

    fun updateSearchQuery(query: String): Boolean {
        cancelSearchWork()
        selectedDestination = null
        clearNavigationDestination()
        return searchSession.updateQuery(nextSearchRevision(), query)
    }

    fun submitSearch(query: String, near: SearchResponsePoint?): Boolean {
        cancelSearchWork()
        val revision = nextSearchRevision()
        if (!searchSession.beginQuery(revision, query)) return false
        if (searchSession.state.value.query.isEmpty()) return true
        launchSearch(revision, searchSession.state.value.query, near)
        return true
    }

    fun submitNearby(
        category: NativeSearchNearbyCategory,
        near: SearchResponsePoint?,
    ): Boolean {
        if (near == null) return false
        cancelSearchWork()
        val revision = nextSearchRevision()
        if (!searchSession.beginNearby(revision, category)) return false
        launchSearch(revision, category.wireQuery, near)
        return true
    }

    fun selectDestination(
        label: String,
        point: SearchResponsePoint,
        gpsPoint: SearchResponsePoint?,
        myLocationLabel: String,
    ): Boolean {
        cancelSearchWork()
        selectedDestination = SelectedDestination(label, point)
        scope.launch {
            runCatching {
                gateway.saveSearchHistory(
                    SearchHistoryEntry(
                        label = label,
                        latitude = point.latitude,
                        longitude = point.longitude,
                    ),
                )
            }
        }
        searchSession.hide(nextSearchRevision())
        return routeSession.showPlanner(
            revision = nextRouteRevision(),
            originQuery = if (gpsPoint == null) "" else myLocationLabel,
            destinationQuery = label,
            hasGps = gpsPoint != null,
        )
    }

    fun selectDestination(
        result: NativeSearchResultPresentation,
        gpsPoint: SearchResponsePoint?,
        myLocationLabel: String,
    ): Boolean = selectDestination(
        label = result.title,
        point = result.position,
        gpsPoint = gpsPoint,
        myLocationLabel = myLocationLabel,
    )

    /**
     * Information-sheet shortcut used by “Navigate here”. It seeds the same
     * planner state as the manual flow and immediately advances to route
     * calculation, so the redundant from/to sheet never flashes on screen.
     */
    fun selectDestinationAndCalculate(
        label: String,
        point: SearchResponsePoint,
        gpsPoint: SearchResponsePoint?,
        myLocationLabel: String,
        avoidUnpavedRoads: Boolean = false,
    ): Boolean {
        if (!selectDestination(label, point, gpsPoint, myLocationLabel)) return false
        return calculateRoute(
            routeSession.state.value,
            gpsPoint,
            myLocationLabel,
            avoidUnpavedRoads,
        )
    }

    fun calculateRoute(
        snapshot: NativeRoutePlanningSnapshot,
        gpsPoint: SearchResponsePoint?,
        myLocationLabel: String,
        avoidUnpavedRoads: Boolean = false,
    ): Boolean {
        cancelRouteWork()
        val revision = nextRouteRevision()
        if (!routeSession.beginRouteRequest(revision)) return false
        if (snapshot.mode == NativeRouteTransportMode.Transit) {
            routeSession.failRouteRequest(revision)
            return false
        }

        routeJob = scope.launch {
            try {
                val savedStops = resolvedRouteStops.takeIf { pointsMatchSnapshot(it, snapshot) }
                val origin = savedStops?.firstOrNull()?.point?.toSearchPoint()
                    ?: resolvePoint(snapshot.originQuery, gpsPoint, myLocationLabel, gpsPoint)
                    ?: return@launch failRoute(revision)
                val stops = ArrayList<SearchResponsePoint>(snapshot.stops.size)
                for ((index, stop) in snapshot.stops.withIndex()) {
                    val point = savedStops?.getOrNull(index + 1)?.point?.toSearchPoint()
                        ?: selectedDestination
                        ?.takeIf { it.label == stop.query }
                        ?.point
                        ?: resolvePoint(stop.query, null, myLocationLabel, gpsPoint)
                        ?: return@launch failRoute(revision)
                    stops += point
                }
                val destination = stops.lastOrNull() ?: return@launch failRoute(revision)
                val routes = gateway.routes(
                    origin = origin,
                    destination = destination,
                    via = stops.dropLast(1),
                    mode = snapshot.mode,
                    languageCode = normalizedLanguageCode(),
                    avoidHighwaysAndTolls = snapshot.avoidanceEnabled,
                    avoidUnpavedRoads = avoidUnpavedRoads,
                )
                if (routes.isEmpty()) return@launch failRoute(revision)
                val accepted = routeSession.submitAlternatives(
                    revision = revision,
                    values = routes.map(::NativeRoutePlanningCandidate),
                    destinationLabel = snapshot.stops.lastOrNull()?.query,
                )
                if (!accepted) {
                    failRoute(revision)
                } else {
                    resolvedRouteStops = listOf(
                        NativeSavedRouteStop(snapshot.originQuery, origin.toRoutingPoint()),
                    ) + snapshot.stops.zip(stops) { stop, point ->
                        NativeSavedRouteStop(stop.query, point.toRoutingPoint())
                    }
                    navigationDestination = destination
                    navigationDestinationRevision = revision
                    val weather = runCatching { gateway.weather(destination) }.getOrNull()
                    routeSession.updateWeather(revision, weather)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: NativeRoutingSelectionException) {
                failRoute(
                    revision,
                    when (failure.reason) {
                        RoutingEngineUnavailableReason.AreaNotDownloaded,
                        RoutingEngineUnavailableReason.DatasetMissing,
                        -> NativeRoutePlanningFailure.AreaNotDownloaded
                        RoutingEngineUnavailableReason.DatasetInvalid -> NativeRoutePlanningFailure.DatasetInvalid
                        RoutingEngineUnavailableReason.ModeUnsupported,
                        RoutingEngineUnavailableReason.NetworkUnavailable,
                        -> NativeRoutePlanningFailure.Generic
                    },
                )
            } catch (_: Exception) {
                failRoute(revision)
            }
        }
        return true
    }

    fun openSavedRoute(route: NativeSavedRoute): Boolean {
        val parsed = route.parsedRoute() ?: return false
        cancelRouteWork()
        val revision = nextRouteRevision()
        val accepted = routeSession.showCalculatedRoute(
            revision = revision,
            route = parsed,
            destinationLabel = route.stops.last().label,
            mode = NativeRouteTransportMode.fromWire(route.preferences.profile),
        )
        if (!accepted) return false
        resolvedRouteStops = route.stops.toList()
        navigationDestination = route.stops.last().point.toSearchPoint()
        navigationDestinationRevision = revision
        return true
    }

    fun editSavedRoute(route: NativeSavedRoute, hasGps: Boolean): Boolean {
        cancelRouteWork()
        clearNavigationDestination()
        val accepted = routeSession.showPlanner(
            revision = nextRouteRevision(),
            originQuery = route.stops.first().label,
            stopQueries = route.stops.drop(1).map(NativeSavedRouteStop::label),
            hasGps = hasGps,
            mode = NativeRouteTransportMode.fromWire(route.preferences.profile),
            avoidanceEnabled = route.preferences.avoidance != app.roadstr.core.network.RoutingRouteAvoidance.None,
        )
        if (accepted) resolvedRouteStops = route.stops.toList()
        return accepted
    }

    fun buildSavedRoute(
        name: String,
        providerId: String,
        engineId: String,
        avoidUnpavedRoads: Boolean,
        nowEpochMillis: Long,
        existing: NativeSavedRoute? = null,
    ): NativeSavedRoute? {
        val snapshot = routeSession.state.value
        val route = routeSession.selectedRoute(snapshot.revision) ?: return null
        if (!pointsMatchSnapshot(resolvedRouteStops, snapshot)) return null
        return runCatching {
            NativeSavedRouteProtocol.create(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                name = name,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: nowEpochMillis,
                nowEpochMillis = nowEpochMillis,
                stops = resolvedRouteStops,
                preferences = NativeSavedRoutePreferences(
                    profile = snapshot.mode.wireValue,
                    avoidance = route.avoidance,
                    avoidUnpavedRoads = avoidUnpavedRoads,
                ),
                providerId = if (route.fromAvoidanceRouter) "valhalla+osrm" else providerId,
                engineId = if (route.fromAvoidanceRouter) "valhalla-online" else engineId,
                route = route,
            )
        }.getOrNull()
    }

    fun dismissSearch(): Boolean {
        cancelSearchWork()
        selectedDestination = null
        clearNavigationDestination()
        return searchSession.hide(nextSearchRevision())
    }

    /** Closes search for the information-first place sheet without touching an active trip. */
    fun closeSearchForPlace(): Boolean {
        cancelSearchWork()
        selectedDestination = null
        return searchSession.hide(nextSearchRevision())
    }

    fun clearSearchHistory(): Boolean {
        val cleared = searchSession.clearHistory(searchSession.state.value.revision)
        scope.launch { runCatching { gateway.clearSearchHistory() } }
        return cleared
    }

    fun cancelRoute(): Boolean {
        cancelRouteWork()
        selectedDestination = null
        clearNavigationDestination()
        return routeSession.hide(nextRouteRevision())
    }

    fun navigationDestination(revision: Long): SearchResponsePoint? {
        val current = routeSession.state.value
        if (
            revision != navigationDestinationRevision ||
            current.revision != revision ||
            current.status != app.roadstr.feature.route.NativeRoutePlanningStatus.Preview
        ) {
            return null
        }
        return navigationDestination
    }

    fun reroute(
        request: NativeNavigationRerouteRequest,
        avoidUnpavedRoads: Boolean = false,
        onSuccess: (Long, RoutingParsedRoute) -> Unit,
        onFailure: (Long) -> Unit,
    ): Boolean {
        if (rerouteJob?.isActive == true) return false
        rerouteJob = scope.launch {
            try {
                val routes = gateway.reroute(
                    origin = SearchResponsePoint(
                        request.origin.latitude,
                        request.origin.longitude,
                    ),
                    destination = SearchResponsePoint(
                        request.destination.latitude,
                        request.destination.longitude,
                    ),
                    mode = request.mode,
                    languageCode = normalizedLanguageCode(),
                    speedKilometresPerHour = request.speedKilometresPerHour,
                    headingDegrees = request.headingDegrees,
                    straightLineDistanceMeters = request.straightLineDistanceMeters,
                    avoidUnpavedRoads = avoidUnpavedRoads,
                )
                val route = routes.firstOrNull()
                if (route == null) {
                    onFailure(request.sequence)
                } else {
                    onSuccess(request.sequence, route)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onFailure(request.sequence)
            }
        }
        return true
    }

    fun cancelReroute() {
        rerouteJob?.cancel()
        rerouteJob = null
    }

    fun close() {
        cancelSearchWork()
        cancelRouteWork()
        cancelReroute()
        selectedDestination = null
        clearNavigationDestination()
    }

    private fun launchSearch(
        revision: Long,
        query: String,
        near: SearchResponsePoint?,
    ) {
        onDiscovery(revision, emptyList())
        lastPlaces = emptyList()
        lastArea = null
        lastParsed = null
        lastNear = near
        webPlaces = emptyMap()
        searchJob = scope.launch {
            try {
                val parsed = interpreter.interpret(query, normalizedLanguageCode())
                lastParsed = parsed
                val emptyNotice = when (val attempt = discover(revision, parsed, near)) {
                    DiscoveryAttempt.Shown -> {
                        offerWeb(revision, parsed, near, sparse = lastNoticeWasSparse)
                        return@launch
                    }
                    is DiscoveryAttempt.Fallback -> attempt.notice
                }
                classicSearch(revision, parsed.classicQuery, near, emptyNotice)
                offerWeb(revision, parsed, near, sparse = emptyNotice == NativeSearchNotice.FewTagged)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                searchSession.submitResults(revision, emptyList())
            }
        }
    }

    private sealed interface DiscoveryAttempt {
        data object Shown : DiscoveryAttempt

        /** Discovery did not answer; [notice] explains an empty answer if the classic search is empty too. */
        data class Fallback(val notice: NativeSearchNotice?) : DiscoveryAttempt
    }

    private suspend fun discover(
        revision: Long,
        parsed: NaturalPlaceQuery,
        near: SearchResponsePoint?,
    ): DiscoveryAttempt {
        val request = DiscoveryRequest(
            query = parsed,
            device = near?.let { GeoPoint(it.latitude, it.longitude) },
            mapCenter = null,
            destination = destination()?.let { GeoPoint(it.latitude, it.longitude) },
            languageCode = normalizedLanguageCode(),
            now = clock(),
            route = routeAhead(),
        )
        val outcome = try {
            gateway.discover(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DiscoveryOutcome.NotApplicable
        }
        return when (outcome) {
            is DiscoveryOutcome.Found -> show(revision, outcome)
            is DiscoveryOutcome.Empty -> DiscoveryAttempt.Fallback(DiscoveryPresentation.notice(outcome.notices))
            DiscoveryOutcome.NotApplicable -> DiscoveryAttempt.Fallback(null)
        }
    }

    private var lastNoticeWasSparse = false

    private fun show(revision: Long, found: DiscoveryOutcome.Found): DiscoveryAttempt {
        val language = normalizedLanguageCode()
        val results = found.places.map { DiscoveryPresentation.searchResult(it, language) }
        val notice = DiscoveryPresentation.notice(found.notices)
        lastNoticeWasSparse = notice == NativeSearchNotice.FewTagged
        if (!searchSession.submitResults(revision, results, notice)) return DiscoveryAttempt.Shown
        lastPlaces = found.places
        lastArea = found.area
        onDiscovery(revision, found.places)
        return DiscoveryAttempt.Shown
    }

    private suspend fun classicSearch(
        revision: Long,
        query: String,
        near: SearchResponsePoint?,
        emptyNotice: NativeSearchNotice?,
    ) {
        val results = gateway.search(
            query = query,
            near = near,
            languageCode = normalizedLanguageCode(),
            onPartial = { partial -> searchSession.submitPartial(revision, partial) },
        )
        searchSession.submitResults(revision, results, if (results.isEmpty()) emptyNotice else null)
    }

    private class PendingWeb(
        val revision: Long,
        val parsed: NaturalPlaceQuery,
        val near: SearchResponsePoint?,
        val host: String,
        /** The town name is added to the words, so the question has to say so. */
        val addsTown: Boolean,
    )

    /**
     * After the places are shown, the web part of the list: nothing when web search is off or
     * has no instance, a row offering a search otherwise, and the search itself (or the
     * question before it) when the leftover words or the sparse tags suggest the web may know more.
     */
    private fun offerWeb(
        revision: Long,
        parsed: NaturalPlaceQuery,
        near: SearchResponsePoint?,
        sparse: Boolean,
    ) {
        val settings = webSettings()
        if (settings.mode == WebDiscoveryMode.OFF || parsed.rawText.isBlank()) return
        val host = (SearxngEndpointPolicy.check(settings.endpointText, settings.ownInstanceConfirmed) as? EndpointCheck.Accepted)
            ?.endpoint?.host ?: return
        val addsTown = WebQueryBuilder.localityPoint(
            WebSearchContext(
                parsed = parsed,
                device = near?.let { GeoPoint(it.latitude, it.longitude) },
                destination = destination()?.let { GeoPoint(it.latitude, it.longitude) },
                languageCode = normalizedLanguageCode(),
            ),
        ) != null
        val pending = PendingWeb(revision, parsed, near, host, addsTown)
        pendingWeb = pending
        val text = WebQueryBuilder.build(parsed, null)
        val automatic = parsed.webHint || sparse
        when {
            automatic && settings.mode == WebDiscoveryMode.ON -> runWeb(revision)
            automatic -> searchSession.updateWeb(revision, NativeSearchWeb.Consent(text, host, addsTown))
            else -> searchSession.updateWeb(revision, NativeSearchWeb.Offer(text))
        }
    }

    /** The user tapped the offer: search now, or ask first when web search is set to "ask". */
    fun searchWeb(): Boolean {
        val pending = pendingWeb ?: return false
        if (webSettings().mode == WebDiscoveryMode.ASK) {
            val text = WebQueryBuilder.build(pending.parsed, null)
            val consent = NativeSearchWeb.Consent(text, pending.host, pending.addsTown)
            return searchSession.updateWeb(pending.revision, consent)
        }
        return runWeb(pending.revision)
    }

    fun confirmWeb(): Boolean = pendingWeb?.let { runWeb(it.revision) } ?: false

    fun declineWeb(): Boolean {
        val pending = pendingWeb ?: return false
        val text = WebQueryBuilder.build(pending.parsed, null)
        return searchSession.updateWeb(pending.revision, NativeSearchWeb.Offer(text))
    }

    private fun runWeb(revision: Long): Boolean {
        val pending = pendingWeb?.takeIf { it.revision == revision } ?: return false
        if (!searchSession.updateWeb(revision, NativeSearchWeb.Loading)) return false
        webJob?.cancel()
        webJob = scope.launch {
            val context = WebSearchContext(
                parsed = pending.parsed,
                device = pending.near?.let { GeoPoint(it.latitude, it.longitude) },
                destination = destination()?.let { GeoPoint(it.latitude, it.longitude) },
                languageCode = normalizedLanguageCode(),
            )
            val outcome = try {
                gateway.webSearch(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                WebDiscoveryOutcome.Failed
            }
            val shown = if (outcome is WebDiscoveryOutcome.Results) {
                linked(pending, outcome)
            } else {
                LinkedResults(webState(outcome), emptyList())
            }
            if (searchSession.updateWeb(revision, shown.state)) pinNewMatches(revision, shown.fresh)
        }
        return true
    }

    /** The place the user was shown for a web result, from the latest search; null once it is gone. */
    fun webPlace(id: String): RoadstrPlace? = webPlaces[id]

    /** Where "in the area" is judged from, or null when there is neither a search area nor a position. */
    private fun resolveContext(parsed: NaturalPlaceQuery, near: SearchResponsePoint?, locality: String?): ResolveContext? {
        val area = lastArea
        val centre = area?.center ?: near?.let { GeoPoint(it.latitude, it.longitude) } ?: return null
        val named = (parsed.location as? LocationConstraint.NamedPlace)?.text
        return ResolveContext(
            center = centre,
            radiusMeters = area?.reachMeters() ?: 0.0,
            locality = named ?: locality,
            languageCode = normalizedLanguageCode(),
            categories = parsed.categories.toSet(),
        )
    }

    /**
     * What a page the user is reading says about a place, judged against the current search:
     * null without a search to judge by, or when the page puts the place outside its area.
     */
    fun pagePlace(page: WebPageMessage, structured: List<StructuredPlace>): PagePlace? {
        val parsed = lastParsed ?: return null
        val context = resolveContext(parsed, lastNear, null) ?: return null
        return PageMatcher.match(page, structured, lastPlaces.map { it.place }, context)
    }

    /** The web part of the list, and the places that were found only through it. */
    private class LinkedResults(val state: NativeSearchWeb, val fresh: List<RankedPlace>)

    /**
     * The results with each one tied to a place when the evidence allows it. Resolution is an
     * extra: any failure leaves the plain list, and nothing is linked without a centre to judge
     * "in the area" against.
     */
    private suspend fun linked(pending: PendingWeb, found: WebDiscoveryOutcome.Results): LinkedResults {
        val plain = LinkedResults(webState(found), emptyList())
        val context = resolveContext(pending.parsed, pending.near, found.locality) ?: return plain
        val rows = try {
            resolver.resolve(found.results, lastPlaces.map { it.place }, context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return plain
        }
        webPlaces = rows.mapNotNull { it.match?.place }.associateBy { it.id }
        val fresh = rows.mapNotNull { row -> row.match?.takeIf { it.isNew && it.matchClass == MatchClass.LINKED } }
            .map { match -> RankedPlace(match.place.copy(confidence = match.confidence), match.confidence, OpenState.UNKNOWN) }
        return LinkedResults(NativeSearchWeb.Results(found.host, rows.map(::presentation)), fresh)
    }

    private fun presentation(row: ResolvedWebResult): NativeWebResultPresentation {
        val result = row.result
        val match = row.match
        val link = when {
            match == null -> NativeWebPlaceLink.None
            row.matchClass == MatchClass.LINKED -> NativeWebPlaceLink.Linked(match.place.id, match.place.name)
            row.matchClass == MatchClass.CANDIDATE -> NativeWebPlaceLink.Candidate(match.place.id, match.place.name)
            else -> NativeWebPlaceLink.None
        }
        return NativeWebResultPresentation(result.title, result.host, result.snippet, result.url.toString(), link)
    }

    /** A place found only through a web result gets a pin once it is confidently linked, never before. */
    private fun pinNewMatches(revision: Long, matches: List<RankedPlace>) {
        val fresh = matches.filter { added -> lastPlaces.none { it.place.id == added.place.id } }
        if (fresh.isEmpty()) return
        lastPlaces = lastPlaces + fresh
        onDiscovery(revision, lastPlaces)
    }

    private fun webState(outcome: WebDiscoveryOutcome): NativeSearchWeb = when (outcome) {
        is WebDiscoveryOutcome.Results -> NativeSearchWeb.Results(
            outcome.host,
            outcome.results.map {
                NativeWebResultPresentation(it.title, it.host, it.snippet, it.url.toString())
            },
        )
        WebDiscoveryOutcome.Disabled -> NativeSearchWeb.Hidden
        is WebDiscoveryOutcome.Rejected -> NativeSearchWeb.Unavailable(NativeWebProblem.Rejected)
        is WebDiscoveryOutcome.RateLimited -> NativeSearchWeb.Unavailable(NativeWebProblem.RateLimited)
        WebDiscoveryOutcome.Failed -> NativeSearchWeb.Unavailable(NativeWebProblem.Unreachable)
        is WebDiscoveryOutcome.Incompatible -> NativeSearchWeb.Unavailable(
            outcome.capability.toWebProblem() ?: NativeWebProblem.Unreachable,
        )
    }

    private suspend fun resolvePoint(
        query: String,
        gpsPoint: SearchResponsePoint?,
        myLocationLabel: String,
        near: SearchResponsePoint?,
    ): SearchResponsePoint? {
        if (gpsPoint != null && query.trim() == myLocationLabel.trim()) return gpsPoint
        return gateway.search(
            query = query,
            near = near,
            languageCode = normalizedLanguageCode(),
            onPartial = {},
        ).firstOrNull()?.position
    }

    private fun pointsMatchSnapshot(
        points: List<NativeSavedRouteStop>,
        snapshot: NativeRoutePlanningSnapshot,
    ): Boolean = points.size == snapshot.stops.size + 1 &&
        points.firstOrNull()?.label == snapshot.originQuery &&
        points.drop(1).map(NativeSavedRouteStop::label) == snapshot.stops.map { it.query }

    private fun app.roadstr.core.network.RoutingResponsePoint.toSearchPoint() =
        SearchResponsePoint(latitude, longitude)

    private fun SearchResponsePoint.toRoutingPoint() =
        app.roadstr.core.network.RoutingResponsePoint(latitude, longitude)

    private fun failRoute(
        revision: Long,
        failure: NativeRoutePlanningFailure = NativeRoutePlanningFailure.Generic,
    ) {
        routeSession.failRouteRequest(revision, failure)
    }

    private fun nextSearchRevision(): Long = maxOf(
        searchRevision,
        searchSession.state.value.revision,
    ).plus(1L).also { searchRevision = it }

    private fun nextRouteRevision(): Long = maxOf(
        routeRevision,
        routeSession.state.value.revision,
    ).plus(1L).also { routeRevision = it }

    private fun cancelSearchWork() {
        searchJob?.cancel()
        searchJob = null
        webJob?.cancel()
        webJob = null
        pendingWeb = null
    }

    private fun cancelRouteWork() {
        routeJob?.cancel()
        routeJob = null
    }

    private fun clearNavigationDestination() {
        navigationDestination = null
        navigationDestinationRevision = NO_REVISION
        resolvedRouteStops = emptyList()
    }

    private fun normalizedLanguageCode(): String = languageCode
        .trim()
        .lowercase(Locale.ROOT)
        .take(8)
        .ifEmpty { "en" }

    private data class SelectedDestination(
        val label: String,
        val point: SearchResponsePoint,
    )

    private companion object {
        const val NO_REVISION = -1L
    }
}
