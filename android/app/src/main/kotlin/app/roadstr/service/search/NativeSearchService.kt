package app.roadstr.service.search

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NominatimReverseDetail
import app.roadstr.core.network.SearchProviderProtocol
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResponseProtocol
import app.roadstr.core.network.SearchResult
import app.roadstr.core.search.SearchOrchestrationDecision
import app.roadstr.core.search.SearchOrchestrationProtocol
import app.roadstr.core.search.SearchProviderBatch
import app.roadstr.core.search.SearchProviderKind
import app.roadstr.core.search.SearchRankingProtocol
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeSearchHttpTransport
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class NativeSearchPhase {
    TYPE_AHEAD,
    SETTLED,
}

data class NativeSearchQuery(
    val query: String,
    val near: SearchResponsePoint? = null,
    val languageCode: String = "en",
    val phase: NativeSearchPhase = NativeSearchPhase.SETTLED,
)

/**
 * Headless native place-search slice for Nominatim, Photon and nearby Overpass.
 *
 * Enabled providers execute as children of the caller's coroutine. A single
 * provider failure becomes an empty completion without cancelling its peers;
 * caller cancellation is rethrown and reaches every in-flight OkHttp call.
 * Startup, UI request generations and search-history persistence remain owned
 * by later migration layers.
 */
class NativeSearchService(
    private val transport: NativeSearchHttpTransport,
    private val overpassMirrors: List<String> = SearchProviderProtocol.overpassMirrors,
) {
    constructor() : this(NativeBoundedHttpClient())

    private val preferredOverpassMirror = AtomicInteger(0)

    init {
        require(overpassMirrors.isNotEmpty()) { "At least one Overpass mirror is required" }
    }

    suspend fun search(
        query: NativeSearchQuery,
        onPartial: ((List<SearchResult>) -> Unit)? = null,
    ): List<SearchResult> {
        val plan = SearchRankingProtocol.executionPlan(
            query = query.query,
            settled = query.phase == NativeSearchPhase.SETTLED,
            hasNear = query.near != null,
        ) ?: return emptyList()
        val orchestration = SearchOrchestrationProtocol(plan, query.near)

        return coroutineScope {
            val completions = Channel<ProviderCompletion>(Channel.UNLIMITED)

            fun start(
                batch: SearchProviderBatch,
                provider: SearchProviderKind,
                providerQuery: String,
            ) {
                launch {
                    val results = guardedProviderCall {
                        when (provider) {
                            SearchProviderKind.NOMINATIM -> searchNominatim(
                                providerQuery,
                                query.near,
                            )

                            SearchProviderKind.PHOTON -> searchPhoton(
                                providerQuery,
                                query.near,
                                query.languageCode,
                            )

                            SearchProviderKind.POI -> searchPoiResults(
                                providerQuery,
                                requireNotNull(query.near),
                            )
                        }
                    }
                    completions.send(ProviderCompletion(batch, provider, results))
                }
            }

            for (provider in orchestration.expectedInitialProviders) {
                start(SearchProviderBatch.INITIAL, provider, plan.query)
            }

            while (true) {
                val completion = completions.receive()
                val decision = orchestration.accept(
                    batch = completion.batch,
                    provider = completion.provider,
                    results = completion.results,
                )
                publishPartial(decision, onPartial)
                decision.retryQuery?.let { retryQuery ->
                    start(SearchProviderBatch.RETRY, SearchProviderKind.NOMINATIM, retryQuery)
                    start(SearchProviderBatch.RETRY, SearchProviderKind.PHOTON, retryQuery)
                }
                decision.finalResults?.let { return@coroutineScope it }
            }
            @Suppress("UNREACHABLE_CODE")
            emptyList()
        }
    }

    /** Bounded reverse lookup used by a map tap and the Wikipedia context. */
    suspend fun reverseGeocode(
        point: SearchResponsePoint,
    ): NominatimReverseDetail? {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0)
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0)
        val response = transport.execute(
            SearchProviderProtocol.nominatimReverse(point.latitude, point.longitude),
            NOMINATIM_LIMITS,
        )
        if (response.statusCode != HTTP_OK) return null
        return SearchResponseProtocol.parseNominatimReverse(response.bodyUtf8)
    }

    private suspend fun searchNominatim(
        query: String,
        near: SearchResponsePoint?,
    ): List<SearchResult> {
        val request = SearchProviderProtocol.nominatimSearch(
            query = query,
            latitude = near?.latitude,
            longitude = near?.longitude,
        ) ?: return emptyList()
        val response = transport.execute(request, NOMINATIM_LIMITS)
        if (response.statusCode != HTTP_OK) return emptyList()
        return SearchResponseProtocol.parseNominatimSearch(response.bodyUtf8)
    }

    private suspend fun searchPhoton(
        query: String,
        near: SearchResponsePoint?,
        languageCode: String,
    ): List<SearchResult> {
        val request = SearchProviderProtocol.photonSearch(
            query = query,
            latitude = near?.latitude,
            longitude = near?.longitude,
            languageCode = languageCode,
        ) ?: return emptyList()
        val response = transport.execute(request, PHOTON_LIMITS)
        if (response.statusCode != HTTP_OK) return emptyList()
        return SearchResponseProtocol.parsePhoton(response.bodyUtf8)
    }

    internal suspend fun searchPoiResults(
        query: String,
        center: SearchResponsePoint,
    ): List<SearchResult> {
        val filters = NativePoiSearchProtocol.categoryFilters(query) ?: return emptyList()
        val overpassQuery = NativePoiSearchProtocol.overpassQuery(filters, center)
        val firstMirror = Math.floorMod(
            preferredOverpassMirror.get(),
            overpassMirrors.size,
        )
        for (offset in overpassMirrors.indices) {
            val index = (firstMirror + offset) % overpassMirrors.size
            val request = SearchProviderProtocol.overpass(
                mirror = overpassMirrors[index],
                query = overpassQuery,
            )
            try {
                val response = transport.execute(request, OVERPASS_LIMITS)
                if (response.statusCode != HTTP_OK) {
                    preferredOverpassMirror.compareAndSet(index, (index + 1) % overpassMirrors.size)
                    continue
                }
                val results = SearchResponseProtocol.parseOverpassElements(response.bodyUtf8)
                    .mapNotNull { element ->
                        SearchResponseProtocol.overpassElementToResult(element, center)
                    }
                    .sortedBy { result -> result.distanceM ?: 0.0 }
                preferredOverpassMirror.set(index)
                return results
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                preferredOverpassMirror.compareAndSet(index, (index + 1) % overpassMirrors.size)
            }
        }
        return emptyList()
    }

    private suspend fun guardedProviderCall(
        request: suspend () -> List<SearchResult>,
    ): List<SearchResult> = try {
        request()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        emptyList()
    }

    private fun publishPartial(
        decision: SearchOrchestrationDecision,
        callback: ((List<SearchResult>) -> Unit)?,
    ) {
        val partial = decision.partialResults ?: return
        if (callback == null) return
        try {
            callback(partial)
        } catch (_: Exception) {
            // A stale rendering callback must not turn provider success into a
            // failed search. UI generation ownership is deliberately external.
        }
    }

    private data class ProviderCompletion(
        val batch: SearchProviderBatch,
        val provider: SearchProviderKind,
        val results: List<SearchResult>,
    )

    companion object {
        const val NOMINATIM_TIMEOUT_MILLIS = 5_000L
        const val PHOTON_TIMEOUT_MILLIS = 4_000L
        const val OVERPASS_TIMEOUT_MILLIS = 5_000L

        private const val HTTP_OK = 200

        val NOMINATIM_LIMITS = NativeHttpRequestLimits(
            timeoutMillis = NOMINATIM_TIMEOUT_MILLIS,
            maxResponseBytes = NetworkResponseLimit.Route.bytes,
        )
        val PHOTON_LIMITS = NativeHttpRequestLimits(
            timeoutMillis = PHOTON_TIMEOUT_MILLIS,
            maxResponseBytes = NetworkResponseLimit.Route.bytes,
        )
        val OVERPASS_LIMITS = NativeHttpRequestLimits(
            timeoutMillis = OVERPASS_TIMEOUT_MILLIS,
            maxResponseBytes = NetworkResponseLimit.AreaQuery.bytes,
        )
    }
}
