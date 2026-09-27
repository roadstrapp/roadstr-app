package app.roadstr.core.network

import java.net.URI

/** Per-attempt deadlines mirrored from Dart's NetworkTimeouts. */
enum class NetworkTimeoutBudget(
    val fixtureName: String,
    val milliseconds: Long,
) {
    Interactive("interactive", 4_000L),
    Standard("standard", 8_000L),
    Routing("routing", 12_000L),
    Transit("transit", 20_000L),
    Background("background", 25_000L),
    SocketHandshake("socket_handshake", 5_000L),
}

/** Response caps mirrored from Dart's NetworkLimits. */
enum class NetworkResponseLimit(
    val fixtureName: String,
    val bytes: Long,
) {
    SmallJson("small_json", 1L * 1024 * 1024),
    Route("route", 2L * 1024 * 1024),
    TransitPlan("transit_plan", 2L * 1024 * 1024),
    AreaQuery("area_query", 6L * 1024 * 1024),
    LargeAreaQuery("large_area_query", 10L * 1024 * 1024),
    BulkGeometry("bulk_geometry", 20L * 1024 * 1024),
}

/**
 * Engine-independent response accounting for the future native HTTP client.
 *
 * The network adapter must reject an oversized declared length before reading
 * and call [acceptChunk] before retaining every streamed chunk. Redirects stay
 * disabled so credentials and precise coordinates cannot cross origins.
 */
object BoundedHttpPolicy {
    const val followRedirects: Boolean = false

    fun acceptsContentLength(contentLength: Long?, maxBytes: Long): Boolean {
        require(maxBytes > 0) { "HTTP response budget must be positive" }
        return contentLength == null || contentLength in 0..maxBytes
    }
}

class BoundedHttpBodyBudget(
    val maxBytes: Long,
) {
    var receivedBytes: Long = 0
        private set

    var rejected: Boolean = false
        private set

    init {
        require(maxBytes > 0) { "HTTP response budget must be positive" }
    }

    /** Returns false before the chunk can exceed the configured body budget. */
    fun acceptChunk(chunkBytes: Int): Boolean {
        require(chunkBytes >= 0) { "HTTP chunk size must not be negative" }
        if (rejected || chunkBytes.toLong() > maxBytes - receivedBytes) {
            rejected = true
            return false
        }
        receivedBytes += chunkBytes
        return true
    }
}

enum class RoutingEndpointDecision(val fixtureName: String) {
    Accepted("accepted"),
    Invalid("invalid"),
    CleartextRejected("cleartext_rejected"),
}

object RoutingEndpointPolicy {
    val cleartextAllowedHosts: Set<String> = setOf(
        "localhost",
        "127.0.0.1",
        "10.0.2.2",
    )

    /**
     * Mirrors the shipped Dart validator: require a host and restrict explicit
     * HTTP to Android's loopback exceptions. Other schemes and user-info remain
     * recorded compatibility behavior until a separately approved tightening.
     */
    fun graphHopperDecision(server: String): RoutingEndpointDecision {
        val uri = try {
            URI(server)
        } catch (_: Exception) {
            return RoutingEndpointDecision.Invalid
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty()) {
            return RoutingEndpointDecision.Invalid
        }
        if (scheme == "http" && host !in cleartextAllowedHosts) {
            return RoutingEndpointDecision.CleartextRejected
        }
        return RoutingEndpointDecision.Accepted
    }
}
