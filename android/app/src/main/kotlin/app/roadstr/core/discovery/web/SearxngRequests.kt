package app.roadstr.core.discovery.web

import app.roadstr.core.discovery.AreaGeocoding
import app.roadstr.core.discovery.UrlEncoding
import app.roadstr.core.network.SearchProviderHttpMethod
import app.roadstr.core.network.SearchProviderRequest
import java.util.Locale

enum class SafeSearch(val value: Int) {
    OFF(0),
    MODERATE(1),
    STRICT(2),
}

/**
 * The two requests Roadstr makes to a SearXNG instance. They carry the typed text, the
 * interface language and the safe-search level, and nothing else: no coordinates, no
 * cookies, no referrer. The headers are the ordinary ones a browser sends, because an
 * instance with its bot limiter on answers 429 to a client without them (it wants
 * `text/html` in Accept, a non-empty Accept-Language and a User-Agent that is not a
 * known library such as `okhttp`).
 */
object SearxngRequests {
    const val MAX_QUERY_CHARS = 200
    const val MAX_ENGINES = 30
    const val USER_AGENT = AreaGeocoding.USER_AGENT
    private val engineName = Regex("^[a-z0-9][a-z0-9 ._+-]{0,39}$")
    private val language = Regex("^[a-z]{2,3}(-[A-Za-z]{2,4})?$")
    private val control = Regex("[\\u0000-\\u001f]")

    fun config(endpoint: SearxngEndpoint, languageCode: String = "en"): SearchProviderRequest =
        SearchProviderRequest(
            method = SearchProviderHttpMethod.Get,
            uri = endpoint.url("/config"),
            headers = headers(languageCode),
        )

    fun search(
        endpoint: SearxngEndpoint,
        text: String,
        languageCode: String,
        safeSearch: SafeSearch,
        engines: List<String> = emptyList(),
    ): SearchProviderRequest? {
        val query = text.replace(control, " ").trim().take(MAX_QUERY_CHARS)
        if (query.isEmpty()) return null
        val lang = languageCode.lowercase(Locale.ROOT).takeIf { language.matches(it) } ?: "en"
        val chosen = engines.filter { engineName.matches(it) }.distinct().take(MAX_ENGINES)
        val engineParam = if (chosen.isEmpty()) {
            "&categories=general"
        } else {
            "&engines=" + chosen.joinToString(",") { UrlEncoding.encode(it) }
        }
        return SearchProviderRequest(
            method = SearchProviderHttpMethod.Get,
            uri = endpoint.url("/search") + "?q=${UrlEncoding.encode(query)}&format=json" +
                "$engineParam&language=$lang&safesearch=${safeSearch.value}&pageno=1",
            headers = headers(lang),
        )
    }

    /** A harmless search used to learn whether the instance answers JSON. */
    fun probe(endpoint: SearxngEndpoint): SearchProviderRequest =
        requireNotNull(search(endpoint, "roadstr", "en", SafeSearch.MODERATE))

    private fun headers(languageCode: String): Map<String, String> {
        val lang = languageCode.lowercase(Locale.ROOT).takeIf { language.matches(it) } ?: "en"
        return linkedMapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/html;q=0.9, */*;q=0.8",
            "Accept-Language" to lang,
        )
    }
}
