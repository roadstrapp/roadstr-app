package app.roadstr.roadtest

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.RoutingRequestProtocol
import app.roadstr.core.network.SearchProviderProtocol
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.service.network.NativeHttpMethod
import app.roadstr.service.network.NativeHttpRequest
import app.roadstr.service.network.NativeHttpRequestLimits
import app.roadstr.service.network.NativeTransitHttpTransport
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Conservative OSM maxspeed lookup, matching main's throttled policy.
 *
 * The limit comes first from Valhalla's `locate` on the FOSSGIS server the app already routes with,
 * and only then from Overpass. Overpass alone used to be the source, and when every public instance
 * was saturated or closed at once (overpass-api.de "at capacity", openstreetmap.fr whitelisted,
 * private.coffee unreachable over IPv4) the sign stayed on "–" for whole trips. Valhalla answers the
 * same question from the same OSM `maxspeed` tags, already converted to km/h, in one small request.
 */
internal class NativeRoadTestSpeedLimitResolver(
    private val transport: NativeTransitHttpTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var cached: Int? = null
    private var lastPoint: SearchResponsePoint? = null
    private var lastAt = 0L
    private var retryAt = 0L
    private var fetching = false
    private var misses = 0
    private var mirror = 0

    suspend fun resolve(point: SearchResponsePoint): Int? {
        val now = clock()
        val due = synchronized(lock) {
            val moved = lastPoint?.let { distance(point, it) >= 100.0 } ?: true
            val stale = now - lastAt > 4 * 60_000L
            if (fetching || now < retryAt || (!moved && !stale)) false else {
                fetching = true
                true
            }
        }
        if (!due) return synchronized(lock) { cached.takeIf { now - lastAt <= 5 * 60_000L } }
        return try {
            val value = fetch(point)
            synchronized(lock) {
                if (value != null) { cached = value; misses = 0 }
                else if (++misses >= 2) cached = null
                lastPoint = point
                lastAt = clock()
                retryAt = 0L
                cached
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            synchronized(lock) {
                mirror = (mirror + 1) % OVERPASS_MIRRORS.size
                retryAt = clock() + 15_000L
                cached.takeIf { clock() - lastAt <= 5 * 60_000L }
            }
        } finally {
            synchronized(lock) { fetching = false }
        }
    }

    /** A null answer means "no posted limit here"; a source that cannot answer throws instead. */
    private suspend fun fetch(point: SearchResponsePoint): Int? =
        try {
            valhalla(point)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            overpass(point)
        }

    private suspend fun valhalla(point: SearchResponsePoint): Int? {
        val body = String.format(
            Locale.ROOT,
            "{\"locations\":[{\"lat\":%.6f,\"lon\":%.6f,\"radius\":%d}],\"costing\":\"auto\",\"verbose\":true}",
            point.latitude,
            point.longitude,
            RADIUS_METERS,
        )
        val request = NativeHttpRequest(
            method = NativeHttpMethod.Post,
            uri = RoutingRequestProtocol.valhallaLocateEndpoint,
            headers = mapOf("Content-Type" to "application/json", "User-Agent" to "Roadstr/1.0"),
            body = body,
        )
        val response = transport.execute(request, limits(NetworkResponseLimit.SmallJson))
        if (response.statusCode != 200) throw IOException("Valhalla locate HTTP ${response.statusCode}")
        val root = BoundedJsonParser(response.bodyUtf8).parse() as? List<*>
            ?: throw IOException("Valhalla locate: unexpected body")
        val location = root.firstOrNull() as? Map<*, *> ?: return null
        val edges = location["edges"] as? List<*> ?: return null
        return pickLimit(edges.mapNotNull(::valhallaCandidate))
    }

    /** Service roads, parking aisles and footways are left out, as the Overpass query leaves them out. */
    private fun valhallaCandidate(raw: Any?): Candidate? {
        val edge = raw as? Map<*, *> ?: return null
        val distance = (edge["distance"] as? Number)?.toDouble() ?: return null
        val classification = ((edge["edge"] as? Map<*, *>)?.get("classification") as? Map<*, *>)
            ?.get("classification")?.toString()
        if (classification == null || classification == "service_other") return null
        // 0 is "no maxspeed tag" and 255 is Valhalla's "no limit" (maxspeed=none).
        val limit = ((edge["edge_info"] as? Map<*, *>)?.get("speed_limit") as? Number)?.toInt()
            ?.takeIf { it in 5..300 && it != VALHALLA_UNLIMITED }
        return Candidate(distance, limit)
    }

    private suspend fun overpass(point: SearchResponsePoint): Int? {
        val query = "[out:json][timeout:5];way[highway~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$\"](around:$RADIUS_METERS,${point.latitude},${point.longitude});out tags geom;"
        val endpoint = synchronized(lock) { OVERPASS_MIRRORS[mirror] }
        val composed = SearchProviderProtocol.overpass(endpoint, query)
        val request = NativeHttpRequest(NativeHttpMethod.Post, composed.uri, composed.headers, composed.body)
        val response = transport.execute(request, limits(NetworkResponseLimit.AreaQuery))
        // A saturated or closed instance answers 429/502/403: that must rotate the mirror, not read as
        // "this road has no limit" and keep asking the same refusing server for the whole trip.
        if (response.statusCode != 200) throw IOException("Overpass HTTP ${response.statusCode}")
        val root = BoundedJsonParser(response.bodyUtf8).parse() as? Map<*, *>
            ?: throw IOException("Overpass: unexpected body")
        val elements = root["elements"] as? List<*> ?: return null
        return pickLimit(elements.mapNotNull { overpassCandidate(it, point) })
    }

    private fun overpassCandidate(raw: Any?, point: SearchResponsePoint): Candidate? {
        val item = raw as? Map<*, *> ?: return null
        val tags = item["tags"] as? Map<*, *> ?: return null
        val geometry = (item["geometry"] as? List<*>)?.mapNotNull(::geometryPoint) ?: return null
        if (geometry.size < 2) return null
        val nearest = geometry.zipWithNext { a, b -> segmentDistance(point, a, b) }.min()
        return Candidate(nearest, parseTags(tags, point))
    }

    private fun geometryPoint(raw: Any?): SearchResponsePoint? {
        val node = raw as? Map<*, *> ?: return null
        val lat = (node["lat"] as? Number)?.toDouble() ?: return null
        val lon = (node["lon"] as? Number)?.toDouble() ?: return null
        return SearchResponsePoint(lat, lon)
    }

    /**
     * Several ways can describe one carriageway: only those tied with the nearest one count, and they
     * must agree. An untagged nearby street never borrows the limit of a more distant parallel road.
     */
    private fun pickLimit(candidates: List<Candidate>): Int? {
        val nearest = candidates.minOfOrNull { it.distance } ?: return null
        if (nearest > RADIUS_METERS) return null
        return candidates.asSequence()
            .filter { it.distance <= nearest + 4.0 }
            .mapNotNull { it.limitKmh }
            .toSet()
            .singleOrNull()
    }

    private fun parseTags(tags: Map<*, *>, point: SearchResponsePoint): Int? {
        val type = tags["maxspeed:type"]?.toString()?.lowercase().orEmpty()
        val mph = type.startsWith("us:") || type.startsWith("gb:") ||
            (point.latitude in 24.3..49.5 && point.longitude in -125.0..-66.0) ||
            (point.latitude in 49.8..59.0 && point.longitude in -8.5..2.0)
        val raw = tags["maxspeed"]?.toString()?.trim()?.lowercase() ?: return null
        if (raw in setOf("none", "unlimited", "walk", "living_street", "signals", "variable")) return null
        val mphValue = Regex("^(\\d+)\\s*mph$").matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull()
        if (mphValue != null) return (mphValue * 1.60934).toInt().coerceIn(5, 300)
        val value = Regex("^(\\d{1,3})(?:\\s*(?:km/h|kph))?$").matchEntire(raw)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (value !in 5..300) return null
        return if (mph) (value * 1.60934).toInt() else value
    }

    private fun limits(size: NetworkResponseLimit) =
        NativeHttpRequestLimits(NetworkTimeoutBudget.Standard.milliseconds, size.bytes)

    private fun segmentDistance(p: SearchResponsePoint, a: SearchResponsePoint, b: SearchResponsePoint): Double {
        val scale = 111320.0
        val c = cos(p.latitude * PI / 180.0)
        val dx = (b.longitude - a.longitude) * scale * c
        val dy = (b.latitude - a.latitude) * scale
        val px = (p.longitude - a.longitude) * scale * c
        val py = (p.latitude - a.latitude) * scale
        val len = dx * dx + dy * dy
        val t = if (len == 0.0) 0.0 else ((px * dx + py * dy) / len).coerceIn(0.0, 1.0)
        return sqrt((px - t * dx) * (px - t * dx) + (py - t * dy) * (py - t * dy))
    }

    private fun distance(a: SearchResponsePoint, b: SearchResponsePoint): Double = segmentDistance(a, b, b)
    private data class Candidate(val distance: Double, val limitKmh: Int?)

    private companion object {
        const val RADIUS_METERS = 60
        const val VALHALLA_UNLIMITED = 255

        /**
         * The live position goes out every 100 m of a trip, so the VK-run mail.ru instance is not
         * among them even though general area searches still list it.
         */
        val OVERPASS_MIRRORS = SearchProviderProtocol.overpassMirrors.filterNot { "mail.ru" in it }
    }
}
