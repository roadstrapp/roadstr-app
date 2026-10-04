package app.roadstr.roadtest

import app.roadstr.core.network.NetworkResponseLimit
import app.roadstr.core.network.NetworkTimeoutBudget
import app.roadstr.core.network.SearchProviderProtocol
import app.roadstr.core.network.SearchResponsePoint
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.service.network.NativeBoundedHttpClient
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** Conservative OSM maxspeed lookup, matching main's throttled policy. */
internal class NativeRoadTestSpeedLimitResolver(private val transport: NativeBoundedHttpClient) {
    private val lock = Any()
    private var cached: Int? = null
    private var lastPoint: SearchResponsePoint? = null
    private var lastAt = 0L
    private var retryAt = 0L
    private var fetching = false
    private var misses = 0
    private var mirror = 0

    suspend fun resolve(point: SearchResponsePoint): Int? {
        val now = System.currentTimeMillis()
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
                lastAt = System.currentTimeMillis()
                retryAt = 0L
                fetching = false
                cached
            }
        } catch (_: Exception) {
            synchronized(lock) {
                mirror = (mirror + 1) % SearchProviderProtocol.overpassMirrors.size
                retryAt = System.currentTimeMillis() + 15_000L
                fetching = false
                cached
            }
        }
    }

    private suspend fun fetch(point: SearchResponsePoint): Int? {
        val query = "[out:json][timeout:5];way[highway~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$\"](around:60,${point.latitude},${point.longitude});out tags geom;"
        val endpoint = synchronized(lock) { SearchProviderProtocol.overpassMirrors[mirror] }
        val response = transport.execute(
            SearchProviderProtocol.overpass(endpoint, query),
            NetworkTimeoutBudget.Standard,
            NetworkResponseLimit.AreaQuery,
        )
        if (response.statusCode != 200) return null
        val root = BoundedJsonParser(response.bodyUtf8).parse() as? Map<*, *> ?: return null
        val elements = root["elements"] as? List<*> ?: return null
        val candidates = elements.mapNotNull { raw ->
            val item = raw as? Map<*, *> ?: return@mapNotNull null
            val tags = item["tags"] as? Map<*, *> ?: return@mapNotNull null
            val geometry = item["geometry"] as? List<*> ?: return@mapNotNull null
            if (geometry.size < 2) return@mapNotNull null
            var nearest = Double.POSITIVE_INFINITY
            for (i in 0 until geometry.lastIndex) {
                val a = geometry[i] as? Map<*, *> ?: continue
                val b = geometry[i + 1] as? Map<*, *> ?: continue
                val first = SearchResponsePoint((a["lat"] as? Number)?.toDouble() ?: continue, (a["lon"] as? Number)?.toDouble() ?: continue)
                val second = SearchResponsePoint((b["lat"] as? Number)?.toDouble() ?: continue, (b["lon"] as? Number)?.toDouble() ?: continue)
                nearest = minOf(nearest, segmentDistance(point, first, second))
            }
            if (nearest.isFinite()) Candidate(nearest, tags) else null
        }.sortedBy { it.distance }
        val nearest = candidates.firstOrNull()?.distance ?: return null
        val values = candidates.asSequence().takeWhile { it.distance <= nearest + 4.0 }
            .mapNotNull { parseTags(it.tags, point) }.toSet()
        return values.singleOrNull()
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
    private data class Candidate(val distance: Double, val tags: Map<*, *>)
}
