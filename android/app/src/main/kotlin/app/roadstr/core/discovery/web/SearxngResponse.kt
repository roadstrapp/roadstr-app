package app.roadstr.core.discovery.web

import app.roadstr.core.discovery.PlaceTagPolicy
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.net.URI
import java.util.Locale

/** A web search result: data to show and open, never a place until it has been resolved. */
data class WebResult(
    val title: String,
    val url: URI,
    val host: String,
    val snippet: String,
    val engines: List<String>,
    val rank: Int,
) {
    override fun toString(): String = "WebResult(rank=$rank)"
}

data class SearxngResults(val results: List<WebResult>, val unresponsiveEngines: List<String>)

data class SearxngEngineInfo(val name: String, val categories: List<String>, val enabled: Boolean)

/** What `/config` says about an instance. */
data class SearxngInstanceInfo(
    val version: String?,
    val engines: List<SearxngEngineInfo>,
    val categories: List<String>,
    val limiterEnabled: Boolean?,
    val publicInstance: Boolean?,
)

object SearxngResponseParser {
    const val MAX_RESULTS = 20
    private const val MAX_TITLE = 160
    private const val MAX_SNIPPET = 300
    private val tags = Regex("<[^>]*>")
    private val spaces = Regex("\\s+")
    private val engineName = Regex("^[A-Za-z0-9][A-Za-z0-9 ._+-]{0,39}$")

    fun parseSearch(body: String, maxResults: Int = MAX_RESULTS): SearxngResults? {
        val root = parse(body) as? Map<*, *> ?: return null
        val rows = root["results"] as? List<*> ?: return null
        val seen = HashSet<String>()
        val results = ArrayList<WebResult>()
        for (row in rows) {
            if (results.size >= maxResults) break
            val result = (row as? Map<*, *>)?.let { result(it, results.size + 1) } ?: continue
            if (seen.add(result.url.toString())) results += result
        }
        // Each entry is [engine, error]; only the engine name is kept.
        val unresponsive = (root["unresponsive_engines"] as? List<*>).orEmpty().mapNotNull {
            engineOf(it) ?: engineOf((it as? List<*>)?.firstOrNull())
        }
        return SearxngResults(results, unresponsive)
    }

    fun parseConfig(body: String): SearxngInstanceInfo? {
        val root = parse(body) as? Map<*, *> ?: return null
        val engines = (root["engines"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::engine) }
        val limiter = (root["limiter"] as? Map<*, *>)?.get("enabled") as? Boolean
        return SearxngInstanceInfo(
            version = (root["version"] as? String)?.let { PlaceTagPolicy.clamp(it) },
            engines = engines.take(MAX_ENGINES_READ),
            categories = (root["categories"] as? List<*>).orEmpty().filterIsInstance<String>().take(MAX_CATEGORIES),
            limiterEnabled = limiter,
            publicInstance = root["public_instance"] as? Boolean,
        )
    }

    private fun result(row: Map<*, *>, rank: Int): WebResult? {
        val uri = normalizeUrl(row["url"] as? String ?: return null) ?: return null
        val title = text(row["title"], MAX_TITLE).ifEmpty { uri.host }
        val engines = engines(row)
        return WebResult(
            title = title,
            url = uri,
            host = uri.host.lowercase(Locale.ROOT),
            snippet = text(row["content"], MAX_SNIPPET),
            engines = engines,
            rank = rank,
        )
    }

    /** `http` or `https`, with a host, no credentials and no fragment; a port is kept. */
    fun normalizeUrl(value: String): URI? {
        if (value.length > MAX_URL) return null
        val uri = try {
            URI(value.trim())
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val acceptable = (scheme == "http" || scheme == "https") &&
            !uri.host.isNullOrEmpty() && uri.userInfo == null
        if (!acceptable) return null
        return URI(scheme, null, uri.host, uri.port, uri.path, uri.query, null)
    }

    private fun engines(row: Map<*, *>): List<String> {
        val many = (row["engines"] as? List<*>).orEmpty().mapNotNull(::engineOf)
        val one = (row["engine"] as? String)?.let(::engineOf)
        return (many + listOfNotNull(one)).distinct()
    }

    private fun engineOf(value: Any?): String? =
        (value as? String)?.takeIf { engineName.matches(it) }?.lowercase(Locale.ROOT)

    private fun engine(row: Map<*, *>): SearxngEngineInfo? {
        val name = engineOf(row["name"]) ?: return null
        val categories = (row["categories"] as? List<*>).orEmpty().filterIsInstance<String>().take(MAX_CATEGORIES)
        return SearxngEngineInfo(name, categories, row["enabled"] as? Boolean ?: false)
    }

    private fun text(value: Any?, max: Int): String {
        val raw = value as? String ?: return ""
        val plain = unescape(raw.replace(tags, " ")).replace(spaces, " ").trim()
        return PlaceTagPolicy.clamp(plain.take(max)).orEmpty()
    }

    private fun unescape(value: String): String = value
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&")

    private fun parse(body: String): Any? = try {
        BoundedJsonParser(body).parse()
    } catch (_: RuntimeException) {
        null
    }

    private const val MAX_URL = 500
    private const val MAX_ENGINES_READ = 300
    private const val MAX_CATEGORIES = 40
}
