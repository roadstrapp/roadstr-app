package app.roadstr.service.discovery

import app.roadstr.core.discovery.TextNormalizer
import app.roadstr.core.discovery.web.ConnectionTest
import app.roadstr.core.discovery.web.EndpointCheck
import app.roadstr.core.discovery.web.RetryAfter
import app.roadstr.core.discovery.web.SearchSourcePolicy
import app.roadstr.core.discovery.web.SearxngCapability
import app.roadstr.core.discovery.web.SearxngCapabilityClassifier
import app.roadstr.core.discovery.web.SearxngEndpoint
import app.roadstr.core.discovery.web.SearxngEndpointPolicy
import app.roadstr.core.discovery.web.SearxngRequests
import app.roadstr.core.discovery.web.SearxngResponseParser
import app.roadstr.core.discovery.web.WebDiscoveryOutcome
import app.roadstr.core.discovery.web.WebDiscoveryProvider
import app.roadstr.core.discovery.web.WebDiscoveryRequest
import app.roadstr.core.discovery.web.WebDiscoverySettings
import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.SearchProviderRequest
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeHttpResponse
import app.roadstr.service.network.NativeSearchHttpTransport
import java.time.Instant
import java.util.concurrent.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Asks the SearXNG instance the user chose for web results. Everything about it is
 * defensive: off unless enabled, one request at a time, no redirects, 1 MiB and 8 s
 * per answer, a pause after a 429 or three failures in a row, and results cached
 * in memory for ten minutes. A failure never reaches the caller as an exception.
 */
class NativeSearxngProvider(
    private val settings: () -> WebDiscoverySettings,
    /** OkHttp: https, and plain http to the device itself. */
    private val secure: NativeSearchHttpTransport,
    /** Raw sockets for plain http to a local network, which Android's policy cannot allow by address. */
    private val local: NativeSearchHttpTransport,
    private val now: () -> Long = System::currentTimeMillis,
) : WebDiscoveryProvider {
    private val gate = Mutex()
    private val cache = TtlCache<String, WebDiscoveryOutcome.Results>(CACHE_ENTRIES, CACHE_TTL_MILLIS, now)
    private var capability: Pair<String, SearxngCapability>? = null
    private var capabilityAt = 0L
    private var failures = 0
    private var pausedUntil = 0L

    override suspend fun discover(request: WebDiscoveryRequest): WebDiscoveryOutcome {
        val current = settings()
        if (current.mode == app.roadstr.core.discovery.web.WebDiscoveryMode.OFF) {
            return WebDiscoveryOutcome.Disabled
        }
        val endpoint = when (val check = SearxngEndpointPolicy.check(current.endpointText, current.ownInstanceConfirmed)) {
            is EndpointCheck.Accepted -> check.endpoint
            is EndpointCheck.Rejected ->
                return if (current.endpointText.isBlank()) {
                    WebDiscoveryOutcome.Disabled
                } else {
                    WebDiscoveryOutcome.Rejected(check.reason)
                }
        }
        return gate.withLock { serve(request, current, endpoint) }
    }

    override suspend fun testConnection(): ConnectionTest {
        val current = settings()
        if (current.endpointText.isBlank()) return ConnectionTest.NotConfigured
        val check = SearxngEndpointPolicy.check(current.endpointText, current.ownInstanceConfirmed)
        val endpoint = (check as? EndpointCheck.Accepted)?.endpoint
            ?: return ConnectionTest.Rejected((check as EndpointCheck.Rejected).reason)
        val tested = gate.withLock { probe(endpoint, current, forceRefresh = true) }
        return ConnectionTest.Tested(tested, endpoint.host)
    }

    private suspend fun serve(
        request: WebDiscoveryRequest,
        current: WebDiscoverySettings,
        endpoint: SearxngEndpoint,
    ): WebDiscoveryOutcome {
        pause()?.let { return it }
        val key = cacheKey(request, current, endpoint)
        cache.get(key)?.let { return it }
        val ready = when (val found = probe(endpoint, current, forceRefresh = false)) {
            is SearxngCapability.Compatible -> found
            is SearxngCapability.RateLimited -> return rateLimited(found.retryAfterSeconds)
            SearxngCapability.Unreachable -> return failed()
            else -> return WebDiscoveryOutcome.Incompatible(found)
        }
        val search = SearxngRequests.search(
            endpoint, request.text, request.languageCode, current.safeSearch, ready.allowlist.orEmpty(),
        ) ?: return WebDiscoveryOutcome.Disabled
        return fetch(search, endpoint, current, key)
    }

    private suspend fun fetch(
        search: SearchProviderRequest,
        endpoint: SearxngEndpoint,
        current: WebDiscoverySettings,
        key: String,
    ): WebDiscoveryOutcome {
        val response = send(search, endpoint) ?: return failed()
        return when (response.statusCode) {
            HTTP_OK -> accept(response, endpoint, current, key)
            HTTP_FORBIDDEN -> {
                capability = null
                WebDiscoveryOutcome.Incompatible(SearxngCapability.JsonDisabled)
            }
            HTTP_TOO_MANY -> rateLimited(retryAfter(response))
            else -> failed()
        }
    }

    private fun accept(
        response: NativeHttpResponse,
        endpoint: SearxngEndpoint,
        current: WebDiscoverySettings,
        key: String,
    ): WebDiscoveryOutcome {
        val parsed = SearxngResponseParser.parseSearch(response.bodyUtf8)
            ?: return WebDiscoveryOutcome.Incompatible(SearxngCapability.NotSearxng)
        failures = 0
        val policy = SearchSourcePolicy(strict = current.strictSources)
        val results = WebDiscoveryOutcome.Results(policy.filter(parsed.results), endpoint.host, parsed.unresponsiveEngines)
        cache.put(key, results)
        return results
    }

    /** `/config` (optional) then a harmless search; the answer is remembered for half an hour. */
    private suspend fun probe(
        endpoint: SearxngEndpoint,
        current: WebDiscoverySettings,
        forceRefresh: Boolean,
    ): SearxngCapability {
        val tag = endpoint.base.toString() + "|" + current.strictSources
        val known = capability
        val fresh = now() - capabilityAt < CAPABILITY_TTL_MILLIS
        if (!forceRefresh && known != null && known.first == tag && fresh) return known.second
        val config = send(SearxngRequests.config(endpoint), endpoint)
        val probe = send(SearxngRequests.probe(endpoint), endpoint)
        val result = SearxngCapabilityClassifier.classify(
            probeStatus = probe?.statusCode,
            probeBody = probe?.bodyUtf8,
            probeRetryAfter = probe?.let(::header),
            configBody = config?.takeIf { it.statusCode == HTTP_OK }?.bodyUtf8,
            policy = SearchSourcePolicy(strict = current.strictSources),
            now = Instant.ofEpochMilli(now()),
        )
        if (result is SearxngCapability.Compatible) {
            capability = tag to result
            capabilityAt = now()
        }
        return result
    }

    private suspend fun send(request: SearchProviderRequest, endpoint: SearxngEndpoint): NativeHttpResponse? {
        val transport = if (endpoint.cleartext && !viaOkHttp(endpoint)) local else secure
        return try {
            transport.execute(request, LIMITS)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            null
        }
    }

    private fun viaOkHttp(endpoint: SearxngEndpoint): Boolean = endpoint.host.lowercase() in OKHTTP_CLEARTEXT_HOSTS

    private fun pause(): WebDiscoveryOutcome? {
        val left = pausedUntil - now()
        if (left <= 0) return null
        return WebDiscoveryOutcome.RateLimited((left + MILLIS - 1) / MILLIS)
    }

    private fun failed(): WebDiscoveryOutcome {
        failures += 1
        if (failures >= FAILURES_BEFORE_PAUSE) {
            pausedUntil = now() + FAILURE_PAUSE_MILLIS
            failures = 0
        }
        return WebDiscoveryOutcome.Failed
    }

    private fun rateLimited(seconds: Long?): WebDiscoveryOutcome {
        val wait = seconds ?: DEFAULT_RATE_LIMIT_SECONDS
        pausedUntil = now() + wait * MILLIS
        return WebDiscoveryOutcome.RateLimited(wait)
    }

    private fun retryAfter(response: NativeHttpResponse): Long? =
        RetryAfter.seconds(header(response), Instant.ofEpochMilli(now()))

    private fun header(response: NativeHttpResponse): String? =
        response.headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }?.value

    private fun cacheKey(request: WebDiscoveryRequest, current: WebDiscoverySettings, endpoint: SearxngEndpoint): String =
        listOf(
            TextNormalizer.normalize(request.text),
            request.languageCode,
            current.safeSearch.name,
            current.strictSources.toString(),
            endpoint.base.toString(),
        ).joinToString("|")

    companion object {
        const val CACHE_ENTRIES = 16
        const val CACHE_TTL_MILLIS = 10L * 60 * 1000
        const val CAPABILITY_TTL_MILLIS = 30L * 60 * 1000
        const val FAILURES_BEFORE_PAUSE = 3
        const val FAILURE_PAUSE_MILLIS = 5L * 60 * 1000
        const val DEFAULT_RATE_LIMIT_SECONDS = 60L
        private const val MILLIS = 1_000L
        private const val HTTP_OK = 200
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY = 429
        private val OKHTTP_CLEARTEXT_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

        val LIMITS = NativeHttpRequestLimits(
            timeoutMillis = 8_000L,
            maxResponseBytes = NetworkResponseLimit.SmallJson.bytes,
        )
    }
}
