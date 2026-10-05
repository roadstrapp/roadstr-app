package app.roadstr.core.web

import app.roadstr.core.discovery.web.SearxngEndpointPolicy
import java.net.URI
import java.util.Locale

/** Something outside the browser that a page may ask for; always confirmed, never started by the page. */
sealed interface ExternalAction {
    data class Dial(val number: String) : ExternalAction

    data class Mail(val address: String) : ExternalAction

    data class UseAsDestination(val latitude: Double, val longitude: Double) : ExternalAction
}

enum class BlockReason {
    EMPTY,
    TOO_LONG,
    MALFORMED,
    CREDENTIALS,
    UNSAFE_SCHEME,
    LOCAL_HOST,
}

sealed interface NavigationDecision {
    /** Load [url] in the browser. */
    data class Allow(val url: URI) : NavigationDecision

    /** The address was plain http: try [https] instead, and never fall back silently. */
    data class Upgrade(val https: URI) : NavigationDecision

    /** Ask the user, then hand [action] to the system. */
    data class Confirm(val action: ExternalAction) : NavigationDecision

    data class Block(val reason: BlockReason) : NavigationDecision
}

/**
 * The one place that decides what the in-app browser may open. A page can only ask: a web
 * address is loaded (https, or http upgraded), a phone, mail or map link is confirmed first
 * and handed to the system, and everything else (`intent:`, `javascript:`, `data:`, `file:`,
 * `content:`, `market:`, custom schemes) is refused. Pages here come from the public web, so
 * the device's own network and local names are never reachable, from a result, a link or a redirect.
 */
object WebNavigationPolicy {
    const val MAX_URL_CHARS = 2_048
    const val MAX_REDIRECTS = 10
    private const val MIN_PHONE_DIGITS = 3
    private const val MAX_PHONE_CHARS = 32
    private val phoneChars = Regex("[+0-9 ()\\-./]+")
    private val queryCoordinates = Regex("^(-?\\d{1,3}(?:\\.\\d+)?),(-?\\d{1,3}(?:\\.\\d+)?)")
    private val mailbox = Regex("[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9.\\-]{1,190}\\.[A-Za-z]{2,24}")

    fun decide(raw: String): NavigationDecision {
        val text = raw.trim()
        if (text.isEmpty()) return block(BlockReason.EMPTY)
        if (text.length > MAX_URL_CHARS) return block(BlockReason.TOO_LONG)
        if (text.any { it.isWhitespace() || it.isISOControl() }) return block(BlockReason.MALFORMED)
        val colon = text.indexOf(':')
        if (colon <= 0) return block(BlockReason.MALFORMED)
        return when (text.substring(0, colon).lowercase(Locale.ROOT)) {
            "https" -> web(text, upgrade = false)
            "http" -> web(text, upgrade = true)
            "tel" -> dial(text.substring(colon + 1))
            "mailto" -> mail(text.substring(colon + 1))
            "geo" -> geo(text.substring(colon + 1))
            else -> block(BlockReason.UNSAFE_SCHEME)
        }
    }

    private fun web(text: String, upgrade: Boolean): NavigationDecision {
        val uri = try {
            URI(text)
        } catch (_: Exception) {
            return block(BlockReason.MALFORMED)
        }
        val host = uri.host?.removeSurrounding("[", "]")?.lowercase(Locale.ROOT)
        if (host.isNullOrEmpty()) return block(BlockReason.MALFORMED)
        if (uri.userInfo != null) return block(BlockReason.CREDENTIALS)
        if (isLocalHost(host)) return block(BlockReason.LOCAL_HOST)
        if (!upgrade) return NavigationDecision.Allow(uri)
        val https = try {
            URI("https", uri.userInfo, uri.host, if (uri.port == HTTP_PORT) -1 else uri.port, uri.path, uri.query, uri.fragment)
        } catch (_: Exception) {
            return block(BlockReason.MALFORMED)
        }
        return NavigationDecision.Upgrade(https)
    }

    /** The device itself, its network, an IPv6 literal or a name that only means something locally. */
    fun isLocalHost(host: String): Boolean {
        val name = host.lowercase(Locale.ROOT).trimEnd('.')
        if (name.contains(':')) return true
        if (name == "localhost" || name.endsWith(".localhost") || name == "0.0.0.0") return true
        return SearxngEndpointPolicy.isLoopback(name) || SearxngEndpointPolicy.isLocalNetwork(name)
    }

    private fun dial(rest: String): NavigationDecision {
        val number = rest.substringBefore('?').substringBefore(';').trim()
        if (number.isEmpty() || number.length > MAX_PHONE_CHARS || !phoneChars.matches(number)) {
            return block(BlockReason.MALFORMED)
        }
        if (number.count(Char::isDigit) < MIN_PHONE_DIGITS) return block(BlockReason.MALFORMED)
        return NavigationDecision.Confirm(ExternalAction.Dial(number))
    }

    /** Only the address is kept: a page does not get to write the subject, the body or other recipients. */
    private fun mail(rest: String): NavigationDecision {
        val address = rest.substringBefore('?').trim()
        if (!mailbox.matches(address)) return block(BlockReason.MALFORMED)
        return NavigationDecision.Confirm(ExternalAction.Mail(address))
    }

    private fun geo(rest: String): NavigationDecision {
        val parts = rest.substringBefore('?').substringBefore(';').split(',')
        if (parts.size !in 2..3) return block(BlockReason.MALFORMED)
        var latitude = parts[0].trim().toDoubleOrNull()
        var longitude = parts[1].trim().toDoubleOrNull()
        // `geo:0,0?q=lat,lon(label)` is the usual way to name a point by the query alone.
        if (latitude == 0.0 && longitude == 0.0) {
            val named = queryPoint(rest.substringAfter('?', ""))
            latitude = named?.first
            longitude = named?.second
        }
        val valid = latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
        if (!valid) return block(BlockReason.MALFORMED)
        return NavigationDecision.Confirm(ExternalAction.UseAsDestination(latitude!!, longitude!!))
    }

    private fun queryPoint(query: String): Pair<Double, Double>? {
        val q = query.split('&').firstOrNull { it.startsWith("q=") }?.removePrefix("q=") ?: return null
        val match = queryCoordinates.find(q) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        return latitude to longitude
    }

    private fun block(reason: BlockReason) = NavigationDecision.Block(reason)

    private const val HTTP_PORT = 80
}

/** Counts the hops of one navigation so that a redirect loop ends instead of spinning. */
class RedirectGuard(private val limit: Int = WebNavigationPolicy.MAX_REDIRECTS) {
    private var hops = 0

    /** True while another hop is still allowed. */
    fun allowNext(): Boolean = ++hops <= limit

    fun reset() {
        hops = 0
    }
}
