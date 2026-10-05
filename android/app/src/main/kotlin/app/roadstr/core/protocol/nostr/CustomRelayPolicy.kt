package app.roadstr.core.protocol.nostr

import java.net.InetAddress
import java.net.URI

/**
 * Which relay addresses a person may type into settings.
 *
 * `wss://` is accepted for any host with a dotted name, including the default
 * relays (typing `wss://relay.damus.io` is not an error, it is simply already
 * there). Plain `ws://` is accepted only for a relay on the local network: a
 * loopback or private address, or a `.local`, `.lan` or `home.arpa` name. A
 * cleartext connection to a public host would expose the signed events and the
 * encrypted snapshots to every network on the way.
 */
object CustomRelayPolicy {
    private const val MAX_LENGTH = 200
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val IPV6 = Regex("^[0-9a-fA-F:.]+$")

    /** `wss://host[:port][/path]` or `ws://local-host[:port][/path]`, normalised; null if unacceptable. */
    fun normalise(input: String): String? {
        return try {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            if (
                host.isNullOrEmpty() ||
                !uri.userInfo.isNullOrEmpty() ||
                uri.rawQuery != null ||
                uri.rawFragment != null ||
                uri.port !in -1..65_535
            ) {
                return null
            }
            val acceptable = when (scheme) {
                "wss" -> '.' in host
                "ws" -> isLocalNetworkHost(host)
                else -> false
            }
            if (!acceptable) return null
            val path = if (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") "" else normaliseEscapes(uri.rawPath)
            buildString {
                append(scheme).append("://").append(host)
                if (uri.port >= 0) append(':').append(uri.port)
                append(path)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** True for a literal local address or a name that only a local network resolves. */
    fun isLocalNetworkHost(host: String): Boolean {
        val name = host.lowercase().removePrefix("[").removeSuffix("]")
        if (name == "localhost") return true
        if (LOCAL_SUFFIXES.any { name.endsWith(it) && name.length > it.length }) return true
        val literal = when {
            IPV4.matches(name) && name.split('.').all { it.toInt() in 0..255 } -> true
            ':' in name && IPV6.matches(name) -> true
            else -> false
        }
        if (!literal) return false
        // A literal needs no DNS lookup, so this cannot be steered by a resolver.
        return isLocalNetworkAddress(InetAddress.getByName(name))
    }

    /** Loopback, link-local, IPv4 private ranges and IPv6 unique-local addresses. */
    fun isLocalNetworkAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isMulticastAddress) return false
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
        val bytes = address.address
        // fc00::/7, the IPv6 counterpart of the private IPv4 ranges.
        return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    }

    private fun normaliseEscapes(rawPath: String): String {
        val out = StringBuilder(rawPath.length)
        var index = 0
        while (index < rawPath.length) {
            val c = rawPath[index]
            if (c == '%' && index + 2 < rawPath.length && rawPath.substring(index + 1, index + 3).all { it.isLetterOrDigit() }) {
                out.append('%').append(rawPath.substring(index + 1, index + 3).uppercase())
                index += 3
            } else {
                out.append(c)
                index += 1
            }
        }
        return out.toString()
    }
}
