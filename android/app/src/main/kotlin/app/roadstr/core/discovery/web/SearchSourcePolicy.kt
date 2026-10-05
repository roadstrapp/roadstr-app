package app.roadstr.core.discovery.web

import java.util.Locale

/**
 * Which search engines Roadstr is willing to have a SearXNG instance use. By default it
 * never asks for a Google engine (or Startpage, which is Google behind a proxy) and
 * drops results that only came from one. This is a request-side promise: an instance
 * the user does not control may still query whatever it likes upstream, and Settings
 * says so rather than claiming otherwise.
 */
data class SearchSourcePolicy(
    val blockedPatterns: List<String> = DEFAULT_BLOCKED,
    val allowUnknownEngines: Boolean = true,
    /** Refuse an instance whose engine list cannot be read, so no allowlist can be sent. */
    val strict: Boolean = false,
) {
    fun isBlocked(engine: String): Boolean {
        val name = engine.lowercase(Locale.ROOT)
        return blockedPatterns.any { name.contains(it) }
    }

    /** The enabled general-purpose engines that pass the policy, or null without an engine list. */
    fun allowlist(info: SearxngInstanceInfo?): List<String>? {
        val engines = info?.engines?.takeIf { it.isNotEmpty() } ?: return null
        val allowed = engines
            .filter { it.enabled && GENERAL in it.categories && !isBlocked(it.name) }
            .map { it.name }
        return allowed.take(SearxngRequests.MAX_ENGINES).ifEmpty { null }
    }

    /** Drops results that only blocked engines produced. */
    fun filter(results: List<WebResult>): List<WebResult> = results.filter { result ->
        when {
            result.engines.isEmpty() -> allowUnknownEngines
            else -> result.engines.any { !isBlocked(it) }
        }
    }

    companion object {
        val DEFAULT_BLOCKED = listOf("google", "startpage")
        const val GENERAL = "general"
    }
}
