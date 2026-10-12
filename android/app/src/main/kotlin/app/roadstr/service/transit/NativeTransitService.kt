package app.roadstr.service.transit

import app.roadstr.core.network.NetworkFailure
import app.roadstr.core.network.NetworkFailureClassifier
import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.PermanentFailure
import app.roadstr.core.network.RetryPolicy
import app.roadstr.core.network.TransientFailure
import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitDeparture
import app.roadstr.core.network.TransitLine
import app.roadstr.core.network.TransitOperationalProtocol
import app.roadstr.core.network.TransitParsedPlan
import app.roadstr.core.network.TransitParsedUnavailable
import app.roadstr.core.network.TransitProvider
import app.roadstr.core.network.TransitProtocol
import app.roadstr.core.network.TransitRequestPoint
import app.roadstr.core.network.TransitResponseException
import app.roadstr.core.network.TransitScopedId
import app.roadstr.core.network.TransitStopBoard
import app.roadstr.core.network.TransitStopReference
import app.roadstr.core.network.TransitStopSearchResult
import app.roadstr.core.network.TransitTripDetails
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpMethod
import app.roadstr.service.network.NativeHttpRequest
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeTransitHttpTransport
import java.time.Instant
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

sealed interface NativeTransitResult

data class NativeTransitPlan(val plan: TransitParsedPlan) : NativeTransitResult {
    val itineraries: List<TransitItinerary>
        get() = plan.itineraries
}

data object NativeTransitUnavailable : NativeTransitResult

data class NativeTransitFailure(
    val message: String,
    val transient: Boolean,
) : NativeTransitResult

sealed interface NativeTransitDataResult<out T>

data class NativeTransitData<T>(val value: T) : NativeTransitDataResult<T>

data object NativeTransitDataUnavailable : NativeTransitDataResult<Nothing>

data object NativeTransitDataUnsupported : NativeTransitDataResult<Nothing>

data class NativeTransitDataFailure(
    val message: String,
    val transient: Boolean,
) : NativeTransitDataResult<Nothing>

enum class NativeTransitCapability {
    DoorToDoorRouting,
    MultipleAlternatives,
    RealtimePredictions,
    Cancellations,
    ServiceAlerts,
    Platforms,
    IntermediateStops,
    Accessibility,
    RefreshableItinerary,
    StopSearch,
    NearbyStops,
    Departures,
    StopLines,
    TripDetails,
    Replanning,
}

sealed interface NativeTransitCoverage {
    data object Worldwide : NativeTransitCoverage

    data class BoundingBox(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
    ) : NativeTransitCoverage {
        init {
            require(south in -90.0..90.0 && north in -90.0..90.0 && south <= north)
            require(west in -180.0..180.0 && east in -180.0..180.0 && west <= east)
        }
    }
}

data class NativeTransitProviderDescriptor(
    val provider: TransitProvider,
    val capabilities: Set<NativeTransitCapability>,
    val coverage: NativeTransitCoverage,
    val priority: Int,
)

data class NativeTransitQuery(
    val from: TransitRequestPoint,
    val to: TransitRequestPoint,
    val departure: Instant,
    val languageCode: String?,
)

data class NativeTransitStopSearchQuery(
    val query: String,
    val location: TransitRequestPoint?,
    val languageCode: String?,
)

data class NativeTransitDeparturesQuery(
    val stop: TransitStopReference,
    val time: Instant,
    val languageCode: String?,
)

data class NativeTransitStopQuery(
    val stop: TransitStopReference,
    val languageCode: String?,
)

data class NativeTransitReferenceQuery(
    val reference: TransitScopedId,
    val languageCode: String?,
)

interface NativeTransitProvider {
    val descriptor: NativeTransitProviderDescriptor

    suspend fun plan(query: NativeTransitQuery): NativeTransitResult

    suspend fun searchStops(query: NativeTransitStopSearchQuery): NativeTransitDataResult<List<TransitStopSearchResult>> =
        NativeTransitDataUnsupported

    suspend fun nearbyStops(
        location: TransitRequestPoint,
        languageCode: String?,
    ): NativeTransitDataResult<List<TransitStopSearchResult>> = NativeTransitDataUnsupported

    suspend fun departures(query: NativeTransitDeparturesQuery): NativeTransitDataResult<TransitStopBoard> =
        NativeTransitDataUnsupported

    suspend fun lines(query: NativeTransitStopQuery): NativeTransitDataResult<List<TransitLine>> =
        NativeTransitDataUnsupported

    suspend fun trip(query: NativeTransitReferenceQuery): NativeTransitDataResult<TransitTripDetails> =
        NativeTransitDataUnsupported

    suspend fun refreshItinerary(query: NativeTransitReferenceQuery): NativeTransitDataResult<TransitItinerary> =
        NativeTransitDataUnsupported

    suspend fun replan(query: NativeTransitQuery): NativeTransitResult = plan(query)

    suspend fun serviceAlerts(query: NativeTransitDeparturesQuery): NativeTransitDataResult<List<app.roadstr.core.network.TransitAlert>> =
        when (val board = departures(query)) {
            is NativeTransitData -> NativeTransitData(board.value.departures.flatMap(TransitDeparture::alerts).distinct())
            is NativeTransitDataFailure -> board
            NativeTransitDataUnavailable -> NativeTransitDataUnavailable
            NativeTransitDataUnsupported -> NativeTransitDataUnsupported
        }
}

/** Socket-free sentinel used when policy forbids every installed/online provider. */
object NativeUnavailableTransitProvider : NativeTransitProvider {
    override val descriptor = NativeTransitProviderDescriptor(
        provider = TransitProvider("unavailable", "Unavailable", null),
        capabilities = setOf(NativeTransitCapability.DoorToDoorRouting),
        coverage = NativeTransitCoverage.Worldwide,
        priority = Int.MIN_VALUE,
    )

    override suspend fun plan(query: NativeTransitQuery): NativeTransitResult = NativeTransitUnavailable
}

/** Chooses one capable provider and only falls back after a transient failure. */
class NativeTransitProviderRouter(
    providers: List<NativeTransitProvider>,
) {
    private val providers = providers.sortedByDescending { it.descriptor.priority }

    init {
        require(this.providers.isNotEmpty()) { "At least one transit provider is required" }
        require(this.providers.map { it.descriptor.provider.id }.distinct().size == this.providers.size) {
            "Transit provider IDs must be unique"
        }
    }

    suspend fun plan(query: NativeTransitQuery): NativeTransitResult {
        val candidates = providers.filter { it.covers(query.from) && it.covers(query.to) }
        if (candidates.isEmpty()) return NativeTransitUnavailable
        var lastTransient: NativeTransitFailure? = null
        for (provider in candidates) {
            when (val result = provider.plan(query)) {
                is NativeTransitFailure -> {
                    if (!result.transient) return result
                    lastTransient = result
                }
                else -> return result
            }
        }
        return lastTransient ?: NativeTransitUnavailable
    }

    suspend fun searchStops(
        query: NativeTransitStopSearchQuery,
    ): NativeTransitDataResult<List<TransitStopSearchResult>> = routeData(
        capability = NativeTransitCapability.StopSearch,
        point = query.location,
    ) { searchStops(query) }

    suspend fun nearbyStops(
        location: TransitRequestPoint,
        languageCode: String?,
    ): NativeTransitDataResult<List<TransitStopSearchResult>> = routeData(
        capability = NativeTransitCapability.NearbyStops,
        point = location,
    ) { nearbyStops(location, languageCode) }

    suspend fun departures(query: NativeTransitDeparturesQuery): NativeTransitDataResult<TransitStopBoard> =
        routeScoped(NativeTransitCapability.Departures, query.stop.id.providerId) { departures(query) }

    suspend fun lines(query: NativeTransitStopQuery): NativeTransitDataResult<List<TransitLine>> =
        routeScoped(NativeTransitCapability.StopLines, query.stop.id.providerId) { lines(query) }

    suspend fun trip(query: NativeTransitReferenceQuery): NativeTransitDataResult<TransitTripDetails> =
        routeScoped(NativeTransitCapability.TripDetails, query.reference.providerId) { trip(query) }

    suspend fun refreshItinerary(query: NativeTransitReferenceQuery): NativeTransitDataResult<TransitItinerary> =
        routeScoped(NativeTransitCapability.RefreshableItinerary, query.reference.providerId) {
            refreshItinerary(query)
        }

    /** Replanning stays inside the original provider so legs are never spliced across datasets. */
    suspend fun replan(query: NativeTransitQuery, providerId: String): NativeTransitResult {
        val provider = providers.firstOrNull {
            it.descriptor.provider.id == providerId &&
                NativeTransitCapability.Replanning in it.descriptor.capabilities &&
                it.covers(query.from) && it.covers(query.to)
        } ?: return NativeTransitUnavailable
        return provider.replan(query)
    }

    private suspend fun <T> routeScoped(
        capability: NativeTransitCapability,
        providerId: String,
        call: suspend NativeTransitProvider.() -> NativeTransitDataResult<T>,
    ): NativeTransitDataResult<T> {
        val provider = providers.firstOrNull {
            it.descriptor.provider.id == providerId && capability in it.descriptor.capabilities
        } ?: return NativeTransitDataUnsupported
        return provider.call()
    }

    private suspend fun <T> routeData(
        capability: NativeTransitCapability,
        point: TransitRequestPoint?,
        call: suspend NativeTransitProvider.() -> NativeTransitDataResult<T>,
    ): NativeTransitDataResult<T> {
        val candidates = providers.filter {
            capability in it.descriptor.capabilities && (point == null || it.covers(point))
        }
        if (candidates.isEmpty()) return NativeTransitDataUnsupported
        var lastTransient: NativeTransitDataFailure? = null
        for (provider in candidates) {
            when (val result = provider.call()) {
                is NativeTransitDataFailure -> {
                    if (!result.transient) return result
                    lastTransient = result
                }
                NativeTransitDataUnsupported -> Unit
                else -> return result
            }
        }
        return lastTransient ?: NativeTransitDataUnavailable
    }

    private fun NativeTransitProvider.covers(point: TransitRequestPoint): Boolean = when (
        val coverage = descriptor.coverage
    ) {
        NativeTransitCoverage.Worldwide -> true
        is NativeTransitCoverage.BoundingBox ->
            point.latitude in coverage.south..coverage.north &&
                point.longitude in coverage.west..coverage.east
    }
}

/**
 * Bounded, cancellable Transitous vertical slice.
 *
 * Precise endpoints remain only in the request value. Failures never retain a
 * URI, body or cause, and only typed transient failures consume the shipped
 * three-attempt interactive retry budget. The journey coordinator owns the
 * request lifecycle and drops stale work when the user leaves the flow.
 */
class NativeTransitService internal constructor(
    private val transport: NativeTransitHttpTransport,
    private val endpoint: String,
    private val sleep: suspend (Long) -> Unit,
    override val descriptor: NativeTransitProviderDescriptor = TRANSITOUS_DESCRIPTOR,
    private val clock: () -> Instant = Instant::now,
) : NativeTransitProvider {
    private data class CachedOperationalResponse(val body: String, val storedAt: Instant)
    private val cacheLock = Any()
    private val operationalCache = object : LinkedHashMap<String, CachedOperationalResponse>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedOperationalResponse>?): Boolean =
            size > MAX_OPERATIONAL_CACHE_ENTRIES
    }
    constructor(transport: NativeTransitHttpTransport) : this(
        transport = transport,
        endpoint = TransitProtocol.DEFAULT_ENDPOINT,
        sleep = { delay(it) },
    )

    constructor() : this(NativeBoundedHttpClient())

    suspend fun plan(
        from: TransitRequestPoint,
        to: TransitRequestPoint,
        departure: Instant = Instant.now(),
        languageCode: String? = null,
    ): NativeTransitResult = plan(NativeTransitQuery(from, to, departure, languageCode))

    override suspend fun plan(query: NativeTransitQuery): NativeTransitResult {
        val request = try {
            TransitProtocol.planRequest(
                from = query.from,
                to = query.to,
                departure = query.departure,
                endpoint = endpoint,
                languageCode = query.languageCode,
            )
        } catch (_: IllegalArgumentException) {
            return NativeTransitFailure("invalid transit request", transient = false)
        }
        val nativeRequest = NativeHttpRequest(
            method = NativeHttpMethod.Get,
            uri = request.uri,
            headers = request.headers,
        )
        val retryPolicy = RetryPolicy.Interactive
        for (attempt in 1..retryPolicy.attempts) {
            try {
                val response = transport.execute(nativeRequest, REQUEST_LIMITS)
                val statusFailure = NetworkFailureClassifier.classifyStatus(
                    status = response.statusCode,
                    retryAfterHeader = response.headers["retry-after"],
                )
                if (statusFailure != null) throw statusFailure
                return when (
                    val parsed = TransitProtocol.parsePlan(
                        response.bodyUtf8,
                        provider = descriptor.provider,
                        searchedAt = Instant.now(),
                        origin = query.from,
                        destination = query.to,
                        requestedDeparture = query.departure,
                    )
                ) {
                    is TransitParsedPlan -> NativeTransitPlan(parsed)
                    TransitParsedUnavailable -> NativeTransitUnavailable
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = classify(error)
                if (failure is TransientFailure && attempt < retryPolicy.attempts) {
                    val wait = retryPolicy.delayBefore(attempt + 1, failure.retryAfter)
                    sleep(wait.toMillis())
                    continue
                }
                return NativeTransitFailure(
                    message = failure.message,
                    transient = failure is TransientFailure,
                )
            }
        }
        error("Transit retry loop terminated without a result")
    }

    override suspend fun searchStops(
        query: NativeTransitStopSearchQuery,
    ): NativeTransitDataResult<List<TransitStopSearchResult>> {
        val request = runCatching {
            TransitOperationalProtocol.searchStopsRequest(endpoint, query.query, query.location, query.languageCode)
        }.getOrElse { return NativeTransitDataFailure("invalid transit stop request", transient = false) }
        return executeOperational(request, SEARCH_CACHE_MILLIS) { body ->
            TransitOperationalProtocol.parseStopSearch(body, descriptor.provider)
        }
    }

    override suspend fun nearbyStops(
        location: TransitRequestPoint,
        languageCode: String?,
    ): NativeTransitDataResult<List<TransitStopSearchResult>> {
        val request = runCatching {
            TransitOperationalProtocol.nearbyStopsRequest(endpoint, location)
        }.getOrElse { return NativeTransitDataFailure("invalid nearby stop request", transient = false) }
        return executeOperational(request, NEARBY_CACHE_MILLIS) { body ->
            TransitOperationalProtocol.parseStopSearch(body, descriptor.provider)
        }
    }

    override suspend fun departures(query: NativeTransitDeparturesQuery): NativeTransitDataResult<TransitStopBoard> {
        if (query.stop.id.providerId != descriptor.provider.id) return NativeTransitDataUnsupported
        val request = TransitOperationalProtocol.departuresRequest(endpoint, query.stop, query.time, query.languageCode)
        return executeOperational(request, BOARD_CACHE_MILLIS) { body ->
            TransitOperationalProtocol.parseStopBoard(body, descriptor.provider, clock())
        }
    }

    override suspend fun lines(query: NativeTransitStopQuery): NativeTransitDataResult<List<TransitLine>> {
        if (query.stop.id.providerId != descriptor.provider.id) return NativeTransitDataUnsupported
        val request = TransitOperationalProtocol.linesRequest(endpoint, query.stop, query.languageCode)
        return executeOperational(request, LINES_CACHE_MILLIS) { body ->
            TransitOperationalProtocol.parseLines(body, descriptor.provider)
        }
    }

    override suspend fun trip(query: NativeTransitReferenceQuery): NativeTransitDataResult<TransitTripDetails> {
        if (query.reference.providerId != descriptor.provider.id) return NativeTransitDataUnsupported
        val request = TransitOperationalProtocol.tripRequest(endpoint, query.reference, query.languageCode)
        return executeOperational(request) { body ->
            TransitTripDetails(TransitProtocol.parseItinerary(body, descriptor.provider), clock())
        }
    }

    override suspend fun refreshItinerary(
        query: NativeTransitReferenceQuery,
    ): NativeTransitDataResult<TransitItinerary> {
        if (query.reference.providerId != descriptor.provider.id) return NativeTransitDataUnsupported
        val request = TransitOperationalProtocol.refreshRequest(endpoint, query.reference, query.languageCode)
        return executeOperational(request) { body -> TransitProtocol.parseItinerary(body, descriptor.provider) }
    }

    private suspend fun <T> executeOperational(
        providerRequest: app.roadstr.core.network.TransitProviderRequest,
        cacheTtlMillis: Long = 0,
        parse: (String) -> T,
    ): NativeTransitDataResult<T> {
        if (cacheTtlMillis > 0) {
            val cached = synchronized(cacheLock) {
                operationalCache[providerRequest.uri]?.takeIf {
                    Duration.between(it.storedAt, clock()).toMillis() in 0 until cacheTtlMillis
                }
            }
            if (cached != null) {
                return runCatching { NativeTransitData(parse(cached.body)) }
                    .getOrElse { NativeTransitDataFailure("malformed transit response", transient = false) }
            }
        }
        val request = NativeHttpRequest(
            method = NativeHttpMethod.Get,
            uri = providerRequest.uri,
            headers = providerRequest.headers,
        )
        val retryPolicy = RetryPolicy.Interactive
        for (attempt in 1..retryPolicy.attempts) {
            try {
                val response = transport.execute(request, REQUEST_LIMITS)
                if (response.statusCode == 404) return NativeTransitDataUnavailable
                val statusFailure = NetworkFailureClassifier.classifyStatus(
                    status = response.statusCode,
                    retryAfterHeader = response.headers["retry-after"],
                )
                if (statusFailure != null) throw statusFailure
                val parsed = parse(response.bodyUtf8)
                if (cacheTtlMillis > 0) synchronized(cacheLock) {
                    operationalCache[providerRequest.uri] = CachedOperationalResponse(response.bodyUtf8, clock())
                }
                return NativeTransitData(parsed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = classify(error)
                if (failure is TransientFailure && attempt < retryPolicy.attempts) {
                    sleep(retryPolicy.delayBefore(attempt + 1, failure.retryAfter).toMillis())
                    continue
                }
                return NativeTransitDataFailure(failure.message, failure is TransientFailure)
            }
        }
        error("Transit operational retry loop terminated without a result")
    }

    private fun classify(error: Throwable): NetworkFailure = when (error) {
        is NetworkFailure -> error
        is TransitResponseException -> PermanentFailure("malformed transit response")
        is NativeHttpException -> when (error.kind) {
            NativeHttpFailureKind.Timeout -> TransientFailure("request timed out")
            NativeHttpFailureKind.Transport -> TransientFailure("connection failed")
            NativeHttpFailureKind.InvalidRequest -> PermanentFailure("invalid transit request")
            NativeHttpFailureKind.ResponseTooLarge -> PermanentFailure("transit response is too large")
        }

        else -> NetworkFailureClassifier.classify(error)
    }

    companion object {
        val TRANSITOUS_DESCRIPTOR = NativeTransitProviderDescriptor(
            provider = TransitProtocol.TRANSITOUS_PROVIDER,
            capabilities = setOf(
                NativeTransitCapability.DoorToDoorRouting,
                NativeTransitCapability.MultipleAlternatives,
                NativeTransitCapability.RealtimePredictions,
                NativeTransitCapability.Cancellations,
                NativeTransitCapability.ServiceAlerts,
                NativeTransitCapability.Platforms,
                NativeTransitCapability.IntermediateStops,
                NativeTransitCapability.Accessibility,
                NativeTransitCapability.RefreshableItinerary,
                NativeTransitCapability.StopSearch,
                NativeTransitCapability.NearbyStops,
                NativeTransitCapability.Departures,
                NativeTransitCapability.StopLines,
                NativeTransitCapability.TripDetails,
                NativeTransitCapability.Replanning,
            ),
            coverage = NativeTransitCoverage.Worldwide,
            priority = 100,
        )

        val REQUEST_LIMITS = NativeHttpRequestLimits(
            timeoutMillis = NetworkTimeoutBudget.Transit.milliseconds,
            maxResponseBytes = NetworkResponseLimit.TransitPlan.bytes,
        )
        private const val MAX_OPERATIONAL_CACHE_ENTRIES = 64
        private const val SEARCH_CACHE_MILLIS = 30_000L
        private const val NEARBY_CACHE_MILLIS = 20_000L
        private const val BOARD_CACHE_MILLIS = 15_000L
        private const val LINES_CACHE_MILLIS = 60_000L
    }
}
