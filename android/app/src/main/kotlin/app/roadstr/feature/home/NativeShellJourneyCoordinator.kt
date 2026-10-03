package app.roadstr.feature.home

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import app.roadstr.feature.route.NativeRoutePlanningCandidate
import app.roadstr.feature.route.NativeRoutePlanningSession
import app.roadstr.feature.route.NativeRoutePlanningSnapshot
import app.roadstr.feature.route.NativeRouteTransportMode
import app.roadstr.feature.search.NativeSearchNearbyCategory
import app.roadstr.feature.search.NativeSearchResultPresentation
import app.roadstr.feature.search.NativeSearchSession
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
    ): List<RoutingParsedRoute>
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
    private var selectedDestination: SelectedDestination? = null

    fun openSearch(nearbyEnabled: Boolean): Boolean {
        cancelSearchWork()
        selectedDestination = null
        return searchSession.show(nextSearchRevision(), nearbyEnabled = nearbyEnabled)
    }

    fun updateSearchQuery(query: String): Boolean {
        cancelSearchWork()
        selectedDestination = null
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

    fun calculateRoute(
        snapshot: NativeRoutePlanningSnapshot,
        gpsPoint: SearchResponsePoint?,
        myLocationLabel: String,
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
                )
                if (routes.isEmpty()) return@launch failRoute(revision)
                val accepted = routeSession.submitAlternatives(
                    revision = revision,
                    values = routes.map(::NativeRoutePlanningCandidate),
                    destinationLabel = snapshot.stops.lastOrNull()?.query,
                )
                if (!accepted) failRoute(revision)
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
        return searchSession.hide(nextSearchRevision())
    }

    fun cancelRoute(): Boolean {
        cancelRouteWork()
        selectedDestination = null
        return routeSession.hide(nextRouteRevision())
    }

    fun close() {
        cancelSearchWork()
        cancelRouteWork()
        selectedDestination = null
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

    private fun normalizedLanguageCode(): String = languageCode
        .trim()
        .lowercase(Locale.ROOT)
        .take(8)
        .ifEmpty { "en" }

    private data class SelectedDestination(
        val label: String,
        val point: SearchResponsePoint,
    )
}
