package app.roadstr.service.transit

import app.roadstr.core.network.NetworkFailure
import app.roadstr.core.network.NetworkFailureClassifier
import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.PermanentFailure
import app.roadstr.core.network.RetryPolicy
import app.roadstr.core.network.TransientFailure
import app.roadstr.core.network.TransitItinerary
import app.roadstr.core.network.TransitParsedPlan
import app.roadstr.core.network.TransitParsedUnavailable
import app.roadstr.core.network.TransitProvider
import app.roadstr.core.network.TransitProtocol
import app.roadstr.core.network.TransitRequestPoint
import app.roadstr.core.network.TransitResponseException
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpMethod
import app.roadstr.service.network.NativeHttpRequest
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeTransitHttpTransport
import java.time.Instant
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

interface NativeTransitProvider {
    val descriptor: NativeTransitProviderDescriptor

    suspend fun plan(query: NativeTransitQuery): NativeTransitResult
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
) : NativeTransitProvider {
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
            ),
            coverage = NativeTransitCoverage.Worldwide,
            priority = 100,
        )

        val REQUEST_LIMITS = NativeHttpRequestLimits(
            timeoutMillis = NetworkTimeoutBudget.Transit.milliseconds,
            maxResponseBytes = NetworkResponseLimit.TransitPlan.bytes,
        )
    }
}
