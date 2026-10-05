package app.roadstr.core.discovery.web

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** What Roadstr learned by testing an instance. */
sealed interface SearxngCapability {
    /** JSON works; [allowlist] is the engine list to send, or null when none could be built. */
    data class Compatible(val info: SearxngInstanceInfo?, val allowlist: List<String>?) : SearxngCapability

    /** The instance answers 403: JSON output is switched off (the default setting). */
    data object JsonDisabled : SearxngCapability

    /** 429: the instance's bot limiter or rate limit said no. */
    data class RateLimited(val retryAfterSeconds: Long?) : SearxngCapability

    /** The address answers, but not like SearXNG (HTML, 404, wrong JSON). */
    data object NotSearxng : SearxngCapability

    /** Strict source policy and the instance's engines cannot be listed. */
    data object NoEngineList : SearxngCapability

    data object Unreachable : SearxngCapability
}

object SearxngCapabilityClassifier {
    /** Reads the answers of `/config` and of the probe search; either may be missing. */
    fun classify(
        probeStatus: Int?,
        probeBody: String?,
        probeRetryAfter: String?,
        configBody: String?,
        policy: SearchSourcePolicy,
        now: Instant = Instant.now(),
    ): SearxngCapability {
        if (probeStatus == null) return SearxngCapability.Unreachable
        return when (probeStatus) {
            HTTP_OK -> compatibleOrNot(probeBody, configBody, policy)
            HTTP_FORBIDDEN -> SearxngCapability.JsonDisabled
            HTTP_TOO_MANY -> SearxngCapability.RateLimited(RetryAfter.seconds(probeRetryAfter, now))
            HTTP_NOT_FOUND -> SearxngCapability.NotSearxng
            else -> SearxngCapability.Unreachable
        }
    }

    private fun compatibleOrNot(
        probeBody: String?,
        configBody: String?,
        policy: SearchSourcePolicy,
    ): SearxngCapability {
        if (probeBody == null || SearxngResponseParser.parseSearch(probeBody) == null) {
            return SearxngCapability.NotSearxng
        }
        val info = configBody?.let { SearxngResponseParser.parseConfig(it) }
        val allowlist = policy.allowlist(info)
        if (policy.strict && allowlist == null) return SearxngCapability.NoEngineList
        return SearxngCapability.Compatible(info, allowlist)
    }

    private const val HTTP_OK = 200
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_TOO_MANY = 429
}

/** `Retry-After` as a number of seconds, from either of its two forms. */
object RetryAfter {
    const val MAX_SECONDS = 3_600L

    fun seconds(value: String?, now: Instant): Long? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let { return it.coerceIn(0, MAX_SECONDS) }
        return try {
            val at = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(text))
            (at.epochSecond - now.epochSecond).coerceIn(0, MAX_SECONDS)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
