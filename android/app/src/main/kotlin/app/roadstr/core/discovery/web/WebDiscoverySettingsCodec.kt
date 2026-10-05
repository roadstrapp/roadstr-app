package app.roadstr.core.discovery.web

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.util.Locale

/**
 * How the web search settings are stored: one small JSON object, which the host keeps
 * encrypted as a whole. Anything unreadable, oversized or unknown decodes to the defaults,
 * which means web search off and no instance, so a damaged value can never switch it on.
 */
object WebDiscoverySettingsCodec {
    const val MAX_STORED_BYTES = 2_048

    fun encode(settings: WebDiscoverySettings): String = buildString {
        append("{\"mode\":\"").append(settings.mode.name.lowercase(Locale.ROOT)).append('"')
        append(",\"endpoint\":")
        appendJsonString(settings.endpointText.trim().take(SearxngEndpointPolicy.MAX_CHARS))
        append(",\"own\":").append(settings.ownInstanceConfirmed)
        append(",\"strict\":").append(settings.strictSources)
        append(",\"safe\":").append(settings.safeSearch.value)
        append('}')
    }

    fun decode(raw: String?): WebDiscoverySettings {
        if (raw == null || raw.toByteArray(Charsets.UTF_8).size > MAX_STORED_BYTES) return WebDiscoverySettings()
        val map = try {
            BoundedJsonParser(raw).parse() as? Map<*, *>
        } catch (_: RuntimeException) {
            null
        } ?: return WebDiscoverySettings()
        val endpoint = (map["endpoint"] as? String)?.trim().orEmpty()
        return WebDiscoverySettings(
            mode = WebDiscoveryMode.entries.firstOrNull { it.name.equals(map["mode"] as? String, ignoreCase = true) }
                ?: WebDiscoveryMode.OFF,
            endpointText = endpoint.takeIf { it.length <= SearxngEndpointPolicy.MAX_CHARS }.orEmpty(),
            ownInstanceConfirmed = map["own"] == true,
            strictSources = map["strict"] == true,
            safeSearch = SafeSearch.entries.firstOrNull { it.value == (map["safe"] as? Number)?.toInt() }
                ?: SafeSearch.MODERATE,
        )
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char < ' ' -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
        append('"')
    }
}
