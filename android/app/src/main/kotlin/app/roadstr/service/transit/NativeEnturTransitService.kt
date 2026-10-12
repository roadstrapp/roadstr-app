package app.roadstr.service.transit

import app.roadstr.core.network.EnturGraphQlException
import app.roadstr.core.network.EnturTransitProtocol
import app.roadstr.core.network.NetworkFailure
import app.roadstr.core.network.NetworkFailureClassifier
import app.roadstr.core.network.PermanentFailure
import app.roadstr.core.network.RetryPolicy
import app.roadstr.core.network.TransientFailure
import app.roadstr.core.network.TransitParsedPlan
import app.roadstr.core.network.TransitParsedUnavailable
import app.roadstr.service.network.NativeBoundedHttpClient
import app.roadstr.service.network.NativeHttpException
import app.roadstr.service.network.NativeHttpFailureKind
import app.roadstr.service.network.NativeHttpMethod
import app.roadstr.service.network.NativeHttpRequest
import app.roadstr.service.network.NativeTransitHttpTransport
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Real Entur Journey Planner adapter, scoped to Norway and kept coherent per itinerary. */
class NativeEnturTransitService internal constructor(
    private val transport: NativeTransitHttpTransport,
    private val endpoint: String,
    private val sleep: suspend (Long) -> Unit,
    override val descriptor: NativeTransitProviderDescriptor = ENTUR_DESCRIPTOR,
) : NativeTransitProvider {
    constructor(transport: NativeTransitHttpTransport) : this(
        transport = transport,
        endpoint = EnturTransitProtocol.ENDPOINT,
        sleep = { delay(it) },
    )

    constructor() : this(NativeBoundedHttpClient())

    override suspend fun plan(query: NativeTransitQuery): NativeTransitResult {
        val providerRequest = try {
            EnturTransitProtocol.tripRequest(query, endpoint)
        } catch (_: IllegalArgumentException) {
            return NativeTransitFailure("invalid transit request", transient = false)
        }
        val request = NativeHttpRequest(
            method = NativeHttpMethod.Post,
            uri = providerRequest.uri,
            headers = providerRequest.headers,
            body = providerRequest.body,
        )
        val retryPolicy = RetryPolicy.Interactive
        for (attempt in 1..retryPolicy.attempts) {
            try {
                val response = transport.execute(request, NativeTransitService.REQUEST_LIMITS)
                val statusFailure = NetworkFailureClassifier.classifyStatus(
                    status = response.statusCode,
                    retryAfterHeader = response.headers["retry-after"],
                )
                if (statusFailure != null) throw statusFailure
                return when (val parsed = EnturTransitProtocol.parsePlan(response.bodyUtf8, query, Instant.now())) {
                    is TransitParsedPlan -> NativeTransitPlan(parsed)
                    TransitParsedUnavailable -> NativeTransitUnavailable
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = classify(error)
                if (failure is TransientFailure && attempt < retryPolicy.attempts) {
                    sleep(retryPolicy.delayBefore(attempt + 1, failure.retryAfter).toMillis())
                    continue
                }
                return NativeTransitFailure(failure.message, failure is TransientFailure)
            }
        }
        error("Entur retry loop terminated without a result")
    }

    override suspend fun replan(query: NativeTransitQuery): NativeTransitResult = plan(query)

    private fun classify(error: Throwable): NetworkFailure = when (error) {
        is NetworkFailure -> error
        is EnturGraphQlException -> PermanentFailure("malformed Entur response")
        is NativeHttpException -> when (error.kind) {
            NativeHttpFailureKind.Timeout -> TransientFailure("request timed out")
            NativeHttpFailureKind.Transport -> TransientFailure("connection failed")
            NativeHttpFailureKind.InvalidRequest -> PermanentFailure("invalid transit request")
            NativeHttpFailureKind.ResponseTooLarge -> PermanentFailure("transit response is too large")
        }
        else -> NetworkFailureClassifier.classify(error)
    }

    companion object {
        val ENTUR_DESCRIPTOR = NativeTransitProviderDescriptor(
            provider = EnturTransitProtocol.PROVIDER,
            capabilities = setOf(
                NativeTransitCapability.DoorToDoorRouting,
                NativeTransitCapability.MultipleAlternatives,
                NativeTransitCapability.RealtimePredictions,
                NativeTransitCapability.Cancellations,
                NativeTransitCapability.ServiceAlerts,
                NativeTransitCapability.Platforms,
                NativeTransitCapability.IntermediateStops,
                NativeTransitCapability.Replanning,
            ),
            coverage = NativeTransitCoverage.BoundingBox(
                south = 57.5,
                west = 4.0,
                north = 71.5,
                east = 32.0,
            ),
            priority = 200,
        )
    }
}
