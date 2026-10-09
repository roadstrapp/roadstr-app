package app.roadstr.service.hazards

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.RefetchPolicy
import app.roadstr.service.network.NativeSearchHttpTransport
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/** A speed-camera position read from OpenStreetMap, not a community report. */
data class NativeOsmSpeedCamera(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    /** OSM's optional limit, normalised to km/h. */
    val speedLimitKmh: Int? = null,
)

/**
 * Fetches the OSM baseline for speed cameras through Overpass.
 *
 * This is deliberately separate from [NativeOsmHazardService]: camera data is
 * visible at a wider zoom and has a different response shape/size from the
 * denser crossing and traffic-light overlays. The cache and mirror policy are
 * bounded so a moving device cannot turn a public mirror into a polling target.
 */
class NativeOsmSpeedCameraService(
    transport: NativeSearchHttpTransport,
    /** Counts and failure kinds only; never coordinates or query text. */
    private val diagnostics: (String) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
) {
    private val overpass = NativeOverpassClient(transport)
    private val lock = Any()
    private var cached: List<NativeOsmSpeedCamera> = emptyList()
    private var lastQueryPosition: GeoPoint? = null
    private var lastSuccessAt: Instant? = null
    private var nextRetryAt: Instant? = null
    private var fetching = false

    fun cameras(): List<NativeOsmSpeedCamera> {
        // This small non-suspending snapshot helper keeps marker composition cheap.
        return synchronized(lock) { cached }
    }

    suspend fun update(point: GeoPoint): List<NativeOsmSpeedCamera> {
        if (!beginIfDue(point)) return cameras()
        try {
            if (!fetchThroughMirrors(point)) {
                synchronized(lock) {
                    nextRetryAt = clock().plus(overpass.failureBackoff(RETRY_BASE))
                }
            }
        } finally {
            synchronized(lock) { fetching = false }
        }
        return cameras()
    }

    fun reset() = synchronized(lock) {
        cached = emptyList()
        lastQueryPosition = null
        lastSuccessAt = null
        nextRetryAt = null
        fetching = false
    }

    private suspend fun fetchThroughMirrors(point: GeoPoint): Boolean {
        var lastError: Exception? = null
        repeat(overpass.mirrorCount) {
            try {
                val elements = overpass.fetchElements(
                    query = query(point),
                    maxBytes = MAX_BYTES,
                    timeoutMillis = TIMEOUT_MILLIS,
                )
                val parsed = parse(elements)
                diagnostics("speed cameras: ${elements.size} elements, ${parsed.size} kept")
                synchronized(lock) {
                    cached = parsed
                    lastQueryPosition = point
                    lastSuccessAt = clock()
                    nextRetryAt = null
                }
                overpass.noteSuccess()
                return true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                lastError = error
                diagnostics(
                    "speed cameras: mirror failed (" +
                        "${(error as? NativeOverpassException)?.statusCode ?: error.javaClass.simpleName})",
                )
                overpass.rotate()
            }
        }
        overpass.noteFailure(lastError)
        return false
    }

    @Synchronized
    private fun beginIfDue(point: GeoPoint): Boolean {
        if (fetching) return false
        val now = clock()
        if (nextRetryAt?.let { now.isBefore(it) } == true) return false
        if (!POLICY.isDue(lastQueryPosition, point, lastSuccessAt, now)) return false
        fetching = true
        return true
    }

    private fun query(point: GeoPoint): String {
        val lat = overpass.coord(point.latitude)
        val lon = overpass.coord(point.longitude)
        val around = "(around:$RADIUS_METERS,$lat,$lon)"
        return "[out:json][timeout:8];" +
            "(node[\"highway\"=\"speed_camera\"]$around;" +
            "node[\"enforcement\"=\"maxspeed\"]$around;);out body;"
    }

    private fun parse(elements: List<Map<String, Any?>>): List<NativeOsmSpeedCamera> {
        val result = ArrayList<NativeOsmSpeedCamera>(minOf(elements.size, MAX_RESULTS))
        val ids = HashSet<Long>()
        for (element in elements) {
            if (result.size >= MAX_RESULTS) break
            val id = (element["id"] as? Number)?.toLong() ?: continue
            if (!ids.add(id)) continue
            val latitude = (element["lat"] as? Number)?.toDouble() ?: continue
            val longitude = (element["lon"] as? Number)?.toDouble() ?: continue
            if (!latitude.isFinite() || !longitude.isFinite()) continue
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
            val tags = element["tags"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val rawLimit = listOf("maxspeed", "maxspeed:forward", "maxspeed:backward")
                .asSequence()
                .mapNotNull { tags[it]?.toString() }
                .firstOrNull()
            result += NativeOsmSpeedCamera(
                id = id,
                latitude = latitude,
                longitude = longitude,
                speedLimitKmh = parseMaxspeed(rawLimit),
            )
        }
        return result
    }

    private fun parseMaxspeed(raw: String?): Int? {
        val value = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
        val mph = Regex("^(\\d{1,3})\\s*mph$").matchEntire(value)
        if (mph != null) {
            return (mph.groupValues[1].toInt() * 1.60934).roundToInt().takeIf { it in 5..300 }
        }
        val kmh = Regex("^(\\d{1,3})(?:\\s*(?:km/h|kph))?$").matchEntire(value)
            ?: return null
        return kmh.groupValues[1].toInt().takeIf { it in 5..300 }
    }

    private companion object {
        const val RADIUS_METERS = 3_000
        const val MAX_RESULTS = 400
        const val MAX_BYTES = 2L * 1024 * 1024
        const val TIMEOUT_MILLIS = 8_000L
        val RETRY_BASE: Duration = Duration.ofMillis(15_000)
        val POLICY = RefetchPolicy(
            minimumMovementMeters = RADIUS_METERS / 2.0,
            maximumAge = Duration.ofMinutes(15),
        )
    }
}
