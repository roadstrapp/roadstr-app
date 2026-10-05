package app.roadstr.core.discovery.structured

import app.roadstr.core.discovery.PlaceTagPolicy
import app.roadstr.core.discovery.UrlEncoding
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.net.URI

/** The one message the bundled page extension sends: the page, its JSON-LD scripts and a few meta tags. */
data class WebPageMessage(val url: URI, val jsonLd: List<String>, val meta: Map<String, String>) {
    // The address of the page is what the user is reading.
    override fun toString(): String = "WebPageMessage(blocks=${jsonLd.size})"
}

/**
 * Strict reading of the extension's message. The page cannot call any Roadstr API: all it can do
 * is shape this one small message, and anything that does not fit is dropped whole.
 */
object WebPageMessageSchema {
    const val VERSION = 1
    const val MAX_BYTES = 64 * 1024
    const val MAX_META_VALUE_CHARS = 160

    /** The only meta tags read, all of them about where a place is. */
    val metaKeys: Set<String> = setOf(
        "og:title", "og:site_name", "og:url", "og:type", "og:latitude", "og:longitude",
        "og:street-address", "og:locality", "og:postal-code", "og:country-name", "og:phone_number",
        "place:location:latitude", "place:location:longitude",
    )

    fun parse(raw: String): WebPageMessage? {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        val root = try {
            BoundedJsonParser(raw).parse() as? Map<*, *>
        } catch (_: RuntimeException) {
            null
        } ?: return null
        if ((root["v"] as? Number)?.toInt() != VERSION) return null
        val url = (root["url"] as? String)?.let { UrlEncoding.safeHttps(it, MAX_URL_CHARS) } ?: return null
        val blocks = (root["jsonLd"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            .filter { it.length <= JsonLdPlaceParser.MAX_BLOCK_CHARS }
            .take(JsonLdPlaceParser.MAX_BLOCKS)
        return WebPageMessage(url, blocks, meta(root["og"]))
    }

    private fun meta(value: Any?): Map<String, String> {
        val map = value as? Map<*, *> ?: return emptyMap()
        val kept = LinkedHashMap<String, String>()
        for (key in metaKeys) {
            val text = (map[key] as? String)?.let { PlaceTagPolicy.clamp(it) }?.take(MAX_META_VALUE_CHARS)
            if (text != null) kept[key] = text
        }
        return kept
    }

    private const val MAX_URL_CHARS = 2_048
}
