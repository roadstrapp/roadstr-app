package app.roadstr.core.discovery.web

import java.net.URI
import java.util.Locale

/** A SearXNG instance the user chose; [cleartext] marks plain http to a local address. */
data class SearxngEndpoint(val base: URI, val cleartext: Boolean) {
    val host: String get() = base.host.removeSurrounding("[", "]")

    /** True for the addresses Android's network policy already lets cleartext reach. */
    val loopback: Boolean get() = SearxngEndpointPolicy.isLoopback(host)

    fun url(path: String): String = base.toString().trimEnd('/') + path

    // The address of a self-hosted server says something about the user's network.
    override fun toString(): String = "SearxngEndpoint(cleartext=$cleartext)"
}

enum class EndpointRejection {
    INVALID,
    NOT_HTTPS,
    CREDENTIALS,
    QUERY_OR_FRAGMENT,
    TOO_LONG,
    LOCAL_HTTP_NOT_CONFIRMED,
}

sealed interface EndpointCheck {
    data class Accepted(val endpoint: SearxngEndpoint) : EndpointCheck

    data class Rejected(val reason: EndpointRejection) : EndpointCheck
}

/**
 * Which addresses may be used as a SearXNG instance: https anywhere, or http only for
 * the device itself and, when the user confirms it is their own, a local network.
 * Credentials and query strings are refused, and the app only ever asks for `/config`
 * and `/search` below the address, so a pasted search URL is trimmed back to its base.
 */
object SearxngEndpointPolicy {
    const val MAX_CHARS = 200
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")
    private val localSuffixes = listOf(".local", ".lan", ".home.arpa", ".internal")

    fun check(text: String, ownInstanceConfirmed: Boolean): EndpointCheck {
        val trimmed = text.trim()
        if (trimmed.length > MAX_CHARS) return reject(EndpointRejection.TOO_LONG)
        val uri = parse(trimmed) ?: return reject(EndpointRejection.INVALID)
        val scheme = uri.scheme.lowercase(Locale.ROOT)
        val host = uri.host.removeSurrounding("[", "]").lowercase(Locale.ROOT)
        return when {
            scheme != "http" && scheme != "https" -> reject(EndpointRejection.INVALID)
            uri.userInfo != null -> reject(EndpointRejection.CREDENTIALS)
            uri.rawQuery != null || uri.rawFragment != null -> reject(EndpointRejection.QUERY_OR_FRAGMENT)
            scheme == "https" -> accept(uri, scheme, cleartext = false)
            isLoopback(host) -> accept(uri, scheme, cleartext = true)
            !isLocalNetwork(host) -> reject(EndpointRejection.NOT_HTTPS)
            !ownInstanceConfirmed -> reject(EndpointRejection.LOCAL_HTTP_NOT_CONFIRMED)
            else -> accept(uri, scheme, cleartext = true)
        }
    }

    fun isLoopback(host: String): Boolean = host.lowercase(Locale.ROOT).removeSurrounding("[", "]") in loopbackHosts

    /** A private IPv4 literal, a CGNAT/VPN one, or a host name that is only meaningful locally. */
    fun isLocalNetwork(host: String): Boolean {
        val name = host.lowercase(Locale.ROOT)
        if (localSuffixes.any { name.endsWith(it) }) return true
        val octets = ipv4(name) ?: return !name.contains('.') && name.isNotEmpty()
        val (first, second) = octets
        return first == 10 || first == 127 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254) ||
            (first == 100 && second in 64..127)
    }

    private fun parse(text: String): URI? {
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val uri = try {
            URI(text)
        } catch (_: Exception) {
            return null
        }
        return uri.takeIf { !it.scheme.isNullOrEmpty() && !it.host.isNullOrEmpty() }
    }

    private fun ipv4(name: String): Pair<Int, Int>? {
        val parts = name.split('.')
        if (parts.size != 4) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it !in 0..255 }) return null
        return numbers[0] to numbers[1]
    }

    private fun accept(uri: URI, scheme: String, cleartext: Boolean): EndpointCheck {
        val authority = uri.rawAuthority
        val path = uri.rawPath.orEmpty().trimEnd('/').removeSuffix("/search").trimEnd('/')
        val base = URI("$scheme://$authority$path")
        return EndpointCheck.Accepted(SearxngEndpoint(base, cleartext))
    }

    private fun reject(reason: EndpointRejection): EndpointCheck = EndpointCheck.Rejected(reason)
}
