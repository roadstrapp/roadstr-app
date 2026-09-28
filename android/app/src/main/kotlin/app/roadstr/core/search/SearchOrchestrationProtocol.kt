package app.roadstr.core.search

import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.network.SearchResult
import java.util.Collections

enum class SearchProviderKind {
    NOMINATIM,
    PHOTON,
    POI,
}

enum class SearchProviderBatch {
    INITIAL,
    RETRY,
}

data class SearchOrchestrationDecision(
    val partialResults: List<SearchResult>? = null,
    val retryQuery: String? = null,
    val finalResults: List<SearchResult>? = null,
)

/** Socket-free state machine for out-of-order place-search provider results. */
class SearchOrchestrationProtocol(
    val plan: SearchExecutionPlan,
    val near: SearchResponsePoint?,
) {
    private val expectedInitial = linkedSetOf<SearchProviderKind>().apply {
        if (plan.useNominatim) add(SearchProviderKind.NOMINATIM)
        if (plan.usePhoton) add(SearchProviderKind.PHOTON)
        if (plan.usePoi) add(SearchProviderKind.POI)
    }
    private val initial = linkedMapOf<SearchProviderKind, List<SearchResult>>()
    private val retry = linkedMapOf<SearchProviderKind, List<SearchResult>>()
    private var partialEmitted = false
    private var retryStarted = false
    private var completed = false
    private var retryQuery: String? = null

    val isCompleted: Boolean
        get() = completed

    val expectedInitialProviders: Set<SearchProviderKind>
        get() = Collections.unmodifiableSet(LinkedHashSet(expectedInitial))

    fun accept(
        batch: SearchProviderBatch,
        provider: SearchProviderKind,
        results: List<SearchResult>,
    ): SearchOrchestrationDecision = when (batch) {
        SearchProviderBatch.INITIAL -> acceptInitial(provider, results)
        SearchProviderBatch.RETRY -> acceptRetry(provider, results)
    }

    private fun acceptInitial(
        provider: SearchProviderKind,
        results: List<SearchResult>,
    ): SearchOrchestrationDecision {
        if (
            completed ||
            retryStarted ||
            provider !in expectedInitial ||
            provider in initial
        ) {
            return NONE
        }
        initial[provider] = results.toList()

        var partial: List<SearchResult>? = null
        if (!partialEmitted && results.isNotEmpty()) {
            partialEmitted = true
            partial = SearchRankingProtocol.rankResults(plan.query, results, near)
        }
        if (initial.size != expectedInitial.size) {
            return SearchOrchestrationDecision(partialResults = partial)
        }

        val geocoded = SearchRankingProtocol.rankGeocoders(
            query = plan.query,
            nominatim = initial[SearchProviderKind.NOMINATIM].orEmpty(),
            photon = initial[SearchProviderKind.PHOTON].orEmpty(),
            near = near,
        )
        val poi = initial[SearchProviderKind.POI].orEmpty()
        val relaxed = SearchRankingProtocol.relaxedRetryQuery(plan, geocoded, poi)
        if (relaxed != null) {
            retryStarted = true
            retryQuery = relaxed
            return SearchOrchestrationDecision(
                partialResults = partial,
                retryQuery = relaxed,
            )
        }

        completed = true
        return SearchOrchestrationDecision(
            partialResults = partial,
            finalResults = SearchRankingProtocol.mergePoiFirst(poi, geocoded),
        )
    }

    private fun acceptRetry(
        provider: SearchProviderKind,
        results: List<SearchResult>,
    ): SearchOrchestrationDecision {
        if (
            completed ||
            !retryStarted ||
            provider == SearchProviderKind.POI ||
            provider in retry
        ) {
            return NONE
        }
        retry[provider] = results.toList()
        if (retry.size != 2) return NONE

        val geocoded = SearchRankingProtocol.rankGeocoders(
            query = requireNotNull(retryQuery),
            nominatim = retry[SearchProviderKind.NOMINATIM].orEmpty(),
            photon = retry[SearchProviderKind.PHOTON].orEmpty(),
            near = near,
        )
        completed = true
        return SearchOrchestrationDecision(
            finalResults = SearchRankingProtocol.mergePoiFirst(
                initial[SearchProviderKind.POI].orEmpty(),
                geocoded,
            ),
        )
    }

    private companion object {
        val NONE = SearchOrchestrationDecision()
    }
}
