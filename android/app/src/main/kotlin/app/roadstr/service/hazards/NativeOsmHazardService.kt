package app.roadstr.service.hazards

import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.RefetchPolicy
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

enum class NativeOsmHazardKind { TrafficLight, Crosswalk, SpeedBump }

/** A point of interest read from OpenStreetMap, not a community report. */
data class NativeOsmHazard(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val kind: NativeOsmHazardKind,
)

/**
 * Traffic lights, pedestrian crossings and speed bumps around the driver, from
 * the public Overpass mirrors.
 *
 * Port of the Flutter TrafficLightService and CrossingHazardService: the same
 * 1.5 km radius, the same "refetch once half way to the edge, or after 15
 * minutes" rule (a parked car never refetches an unchanged neighbourhood), the
 * same 400-result cap against dense city centres, and the same back-off after
 * a failure. Each source keeps its own cache so toggling one overlay off does
 * not discard the other's data.
 */
class NativeOsmHazardService(
    transport: app.roadstr.service.network.NativeSearchHttpTransport,
    /** What each fetch did (counts and failure kinds), never coordinates. */
    private val diagnostics: (String) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
) {
    private val trafficLights = Source(
        label = "traffic lights",
        overpass = NativeOverpassClient(transport),
        query = { client, point ->
            // "out skel" is id + coordinates only: all a plain icon needs.
            "[out:json][timeout:8];" +
                "node[\"highway\"=\"traffic_signals\"]" +
                "(around:$RADIUS_METERS,${client.coord(point.latitude)},${client.coord(point.longitude)});" +
                "out skel;"
        },
        classify = { NativeOsmHazardKind.TrafficLight },
    )
    private val crossings = Source(
        label = "crossings",
        overpass = NativeOverpassClient(transport),
        query = { client, point ->
            // "out body": tags are needed to tell a crosswalk from a bump, and
            // only "body" returns coordinates and tags together for nodes.
            val around = "(around:$RADIUS_METERS,${client.coord(point.latitude)},${client.coord(point.longitude)})"
            "[out:json][timeout:8];" +
                "(node[\"highway\"=\"crossing\"]$around;node[\"traffic_calming\"]$around;);" +
                "out body;"
        },
        classify = { tags ->
            if (tags.containsKey("traffic_calming")) {
                NativeOsmHazardKind.SpeedBump
            } else {
                NativeOsmHazardKind.Crosswalk
            }
        },
    )

    suspend fun trafficLights(point: GeoPoint): List<NativeOsmHazard> = trafficLights.update(point)

    suspend fun crossingsAndBumps(point: GeoPoint): List<NativeOsmHazard> = crossings.update(point)

    fun reset() {
        trafficLights.reset()
        crossings.reset()
    }

    private inner class Source(
        private val label: String,
        private val overpass: NativeOverpassClient,
        private val query: (NativeOverpassClient, GeoPoint) -> String,
        private val classify: (Map<*, *>) -> NativeOsmHazardKind,
    ) {
        private var cached: List<NativeOsmHazard> = emptyList()
        private var lastQueryPosition: GeoPoint? = null
        private var lastSuccessAt: Instant? = null
        private var nextRetryAt: Instant? = null
        private var fetching = false

        @Synchronized
        fun reset() {
            cached = emptyList()
            lastQueryPosition = null
            lastSuccessAt = null
            nextRetryAt = null
            fetching = false
        }

        suspend fun update(point: GeoPoint): List<NativeOsmHazard> {
            if (!beginIfDue(point)) return snapshot()
            try {
                if (!fetchThroughMirrors(point)) {
                    synchronized(this) {
                        nextRetryAt = clock().plus(overpass.failureBackoff(RETRY_BASE))
                    }
                }
            } finally {
                synchronized(this) { fetching = false }
            }
            return snapshot()
        }

        /**
         * One mirror after another until one answers: a mirror that is slow or
         * refuses must not leave the map empty for a whole back-off period
         * while a healthy one sits next in line. Only a round in which every
         * mirror failed counts as a failure and starts the back-off.
         */
        private suspend fun fetchThroughMirrors(point: GeoPoint): Boolean {
            var lastError: Exception? = null
            repeat(overpass.mirrorCount) {
                try {
                    val elements = overpass.fetchElements(
                        query = query(overpass, point),
                        maxBytes = MAX_BYTES,
                        timeoutMillis = TIMEOUT_MILLIS,
                    )
                    val parsed = parse(elements)
                    diagnostics("$label: ${elements.size} elements, ${parsed.size} kept")
                    synchronized(this) {
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
                    diagnostics("$label: mirror failed (${(error as? NativeOverpassException)?.statusCode ?: error.javaClass.simpleName})")
                    overpass.rotate()
                }
            }
            overpass.noteFailure(lastError)
            return false
        }

        @Synchronized
        private fun snapshot(): List<NativeOsmHazard> = cached

        @Synchronized
        private fun beginIfDue(point: GeoPoint): Boolean {
            if (fetching) return false
            val now = clock()
            val retryAt = nextRetryAt
            if (retryAt != null && now.isBefore(retryAt)) return false
            val due = POLICY.isDue(lastQueryPosition, point, lastSuccessAt, now)
            if (due) fetching = true
            return due
        }

        private fun parse(elements: List<Map<String, Any?>>): List<NativeOsmHazard> {
            val result = ArrayList<NativeOsmHazard>(minOf(elements.size, MAX_RESULTS))
            for (element in elements) {
                if (result.size >= MAX_RESULTS) break
                val id = (element["id"] as? Number)?.toLong() ?: continue
                val latitude = (element["lat"] as? Number)?.toDouble() ?: continue
                val longitude = (element["lon"] as? Number)?.toDouble() ?: continue
                if (!latitude.isFinite() || !longitude.isFinite()) continue
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
                val tags = element["tags"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
                result += NativeOsmHazard(id, latitude, longitude, classify(tags))
            }
            return result
        }
    }

    private companion object {
        const val RADIUS_METERS = 1_500
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
