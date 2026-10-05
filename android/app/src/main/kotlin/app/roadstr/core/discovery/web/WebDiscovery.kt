package app.roadstr.core.discovery.web

enum class WebDiscoveryMode { OFF, ASK, ON }

/** What the user chose about web search; everything defaults to the most private choice. */
data class WebDiscoverySettings(
    val mode: WebDiscoveryMode = WebDiscoveryMode.OFF,
    val endpointText: String = "",
    /** The user confirmed that a plain-http local-network address is their own instance. */
    val ownInstanceConfirmed: Boolean = false,
    /** Refuse an instance whose engines cannot be listed (so Google can be kept out of the request). */
    val strictSources: Boolean = false,
    val safeSearch: SafeSearch = SafeSearch.MODERATE,
) {
    // The address of a self-hosted server says something about the user's network.
    override fun toString(): String = "WebDiscoverySettings(mode=$mode)"
}

data class WebDiscoveryRequest(val text: String, val languageCode: String) {
    override fun toString(): String = "WebDiscoveryRequest"
}

sealed interface WebDiscoveryOutcome {
    data class Results(
        val results: List<WebResult>,
        val host: String,
        val unresponsiveEngines: List<String>,
    ) : WebDiscoveryOutcome

    /** Web search is off, or no instance is configured. */
    data object Disabled : WebDiscoveryOutcome

    data class Rejected(val reason: EndpointRejection) : WebDiscoveryOutcome

    /** The instance cannot serve Roadstr: JSON is off, it is not SearXNG, or no engine list. */
    data class Incompatible(val capability: SearxngCapability) : WebDiscoveryOutcome

    data class RateLimited(val retryAfterSeconds: Long?) : WebDiscoveryOutcome

    data object Failed : WebDiscoveryOutcome
}

/** The result of the "test connection" button. */
sealed interface ConnectionTest {
    data class Rejected(val reason: EndpointRejection) : ConnectionTest

    data class Tested(val capability: SearxngCapability, val host: String) : ConnectionTest

    data object NotConfigured : ConnectionTest
}

interface WebDiscoveryProvider {
    suspend fun discover(request: WebDiscoveryRequest): WebDiscoveryOutcome

    suspend fun testConnection(): ConnectionTest
}
