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

data class NativeTransitPlan(val itineraries: List<TransitItinerary>) : NativeTransitResult

data object NativeTransitUnavailable : NativeTransitResult

data class NativeTransitFailure(
    val message: String,
    val transient: Boolean,
) : NativeTransitResult

/**
 * Bounded, cancellable Transitous vertical slice.
 *
 * Precise endpoints remain only in the request value. Failures never retain a
 * URI, body or cause, and only typed transient failures consume the shipped
 * three-attempt interactive retry budget. App/UI ownership remains detached.
 */
class NativeTransitService internal constructor(
    private val transport: NativeTransitHttpTransport,
    private val endpoint: String,
    private val sleep: suspend (Long) -> Unit,
) {
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
    ): NativeTransitResult {
        val request = try {
            TransitProtocol.planRequest(
                from = from,
                to = to,
                departure = departure,
                endpoint = endpoint,
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
                return when (val parsed = TransitProtocol.parsePlan(response.bodyUtf8)) {
                    is TransitParsedPlan -> NativeTransitPlan(parsed.itineraries)
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
        val REQUEST_LIMITS = NativeHttpRequestLimits(
            timeoutMillis = NetworkTimeoutBudget.Transit.milliseconds,
            maxResponseBytes = NetworkResponseLimit.TransitPlan.bytes,
        )
    }
}
