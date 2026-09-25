package app.roadstr.core.network

import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeoutException

sealed class NetworkFailure(
    override val message: String,
    val host: String? = null,
) : RuntimeException(message) {
    override fun toString(): String =
        "${this::class.simpleName}: $message${host?.let { " ($it)" } ?: ""}"
}

class TransientFailure(
    message: String,
    host: String? = null,
    val retryAfter: Duration? = null,
) : NetworkFailure(message, host)

class PermanentFailure(message: String, host: String? = null) : NetworkFailure(message, host)

/** Marker used by response adapters when a body cannot be decoded. */
class MalformedResponseException(message: String) : RuntimeException(message)

object NetworkFailureClassifier {
    private val maxHonouredRetryAfter: Duration = Duration.ofSeconds(30)

    fun classify(error: Throwable, host: String? = null): NetworkFailure = when (error) {
        is NetworkFailure -> error
        is SocketTimeoutException, is TimeoutException ->
            TransientFailure("request timed out", host)
        is SocketException -> TransientFailure("connection failed", host)
        is ProtocolException -> PermanentFailure(error.message ?: "HTTP protocol error", host)
        is MalformedResponseException, is NumberFormatException ->
            PermanentFailure("malformed response", host)
        else -> PermanentFailure("unexpected error: ${error::class.simpleName ?: "Throwable"}", host)
    }

    fun classifyStatus(
        status: Int,
        host: String? = null,
        retryAfterHeader: String? = null,
        now: Instant = Instant.now(),
    ): NetworkFailure? {
        if (status in 200..299) return null
        if (status == 429 || status >= 500) {
            return TransientFailure(
                message = "HTTP $status",
                host = host,
                retryAfter = parseRetryAfter(retryAfterHeader, now),
            )
        }
        return PermanentFailure("HTTP $status", host)
    }

    internal fun parseRetryAfter(header: String?, now: Instant): Duration? {
        if (header == null) return null
        val delay = header.trim().toLongOrNull()?.let(Duration::ofSeconds) ?: run {
            try {
                Duration.between(now, Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(header)))
            } catch (_: DateTimeParseException) {
                null
            }
        }
        if (delay == null || delay.isZero || delay.isNegative || delay > maxHonouredRetryAfter) {
            return null
        }
        return delay
    }
}

data class RetryPolicy(
    val attempts: Int = 3,
    val baseDelay: Duration = Duration.ofMillis(400),
    val maxDelay: Duration = Duration.ofSeconds(4),
) {
    init {
        require(attempts >= 1) { "attempts must include at least the initial request" }
        require(!baseDelay.isNegative) { "baseDelay cannot be negative" }
        require(!maxDelay.isNegative) { "maxDelay cannot be negative" }
    }

    fun delayBefore(attempt: Int, retryAfter: Duration? = null): Duration {
        val exponent = (attempt - 2).coerceIn(0, 16)
        val backoff = baseDelay.multipliedBy(1L shl exponent)
        val chosen = if (retryAfter != null && retryAfter > backoff) retryAfter else backoff
        return if (chosen > maxDelay) maxDelay else chosen
    }

    companion object {
        val None = RetryPolicy(attempts = 1)
        val Interactive = RetryPolicy()
    }
}
