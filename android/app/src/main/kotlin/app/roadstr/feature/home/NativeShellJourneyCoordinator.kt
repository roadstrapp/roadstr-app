package app.roadstr.feature.home

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.NominatimReverseDetail
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.feature.navigation.NativeNavigationRerouteRequest
import app.roadstr.feature.place.NativePlaceArticleInput
import app.roadstr.feature.route.NativeRoutePlanningCandidate
import app.roadstr.feature.route.NativeRoutePlanningSession
import app.roadstr.feature.route.NativeRoutePlanningSnapshot
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.route.NativeRouteWeatherPresentation
import app.roadstr.feature.search.NativeSearchNearbyCategory
import app.roadstr.feature.search.NativeSearchResultPresentation
import app.roadstr.feature.search.NativeSearchSession
import app.roadstr.core.search.SearchHistoryEntry
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Provider-neutral network boundary consumed by the shared Compose shell.
 *
 * Concrete endpoint selection stays in the standalone application composition
 * root. This interface deliberately exposes only bounded, parsed values.
 */
interface NativeShellJourneyGateway {
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

    /** Wikipedia preview resolved from a place label; precise coordinates are not disclosed. */
    suspend fun wikipediaArticle(
        query: String,
        languageCode: String,
    ): NativePlaceArticleInput? = null

    suspend fun weather(destination: SearchResponsePoint): NativeRouteWeatherPresentation? = null

    /** Explicit posted limit of the geometrically nearest road, when known. */
    suspend fun speedLimit(point: SearchResponsePoint): Int? = null

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
) {
    private var searchRevision = searchSession.state.value.revision.coerceAtLeast(0L)
    private var routeRevision = routeSession.state.value.revision.coerceAtLeast(0L)
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var rerouteJob: Job? = null
    private var selectedDestination: SelectedDestination? = null
    private var navigationDestination: SearchResponsePoint? = null
    private var navigationDestinationRevision = NO_REVISION

    fun openSearch(nearbyEnabled: Boolean): Boolean {
        cancelSearchWork()
        selectedDestination = null
        clearNavigationDestination()
        val revision = nextSearchRevision()
        val opened = searchSession.show(revision, nearbyEnabled = nearbyEnabled)
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
                val origin = resolvePoint(snapshot.originQuery, gpsPoint, myLocationLabel, gpsPoint)
                    ?: return@launch failRoute(revision)
                val stops = ArrayList<SearchResponsePoint>(snapshot.stops.size)
                for (stop in snapshot.stops) {
                    val point = selectedDestination
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
                    navigationDestination = destination
                    navigationDestinationRevision = revision
                    val weather = runCatching { gateway.weather(destination) }.getOrNull()
                    routeSession.updateWeather(revision, weather)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failRoute(revision)
            }
        }
        return true
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
        searchJob = scope.launch {
            try {
                val results = gateway.search(
                    query = query,
                    near = near,
                    languageCode = normalizedLanguageCode(),
                    onPartial = { partial -> searchSession.submitPartial(revision, partial) },
                )
                searchSession.submitResults(revision, results)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                searchSession.submitResults(revision, emptyList())
            }
        }
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

    private fun failRoute(revision: Long) {
        routeSession.failRouteRequest(revision)
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
    }

    private fun cancelRouteWork() {
        routeJob?.cancel()
        routeJob = null
    }

    private fun clearNavigationDestination() {
        navigationDestination = null
        navigationDestinationRevision = NO_REVISION
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
