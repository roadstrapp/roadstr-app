package app.roadstr.service.hazards

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.network.RefetchPolicy
import app.roadstr.feature.map.NativeMapPoint
import app.roadstr.service.network.NativeSearchHttpTransport
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NativeZtlZone(val name: String?, val polygon: List<GeoPoint>) {
    val minLatitude: Double = polygon.minOfOrNull(GeoPoint::latitude) ?: 0.0
    val maxLatitude: Double = polygon.maxOfOrNull(GeoPoint::latitude) ?: 0.0
    val minLongitude: Double = polygon.minOfOrNull(GeoPoint::longitude) ?: 0.0
    val maxLongitude: Double = polygon.maxOfOrNull(GeoPoint::longitude) ?: 0.0
}
data class NativeZtlWay(val name: String?, val points: List<GeoPoint>) {
    val minLatitude: Double = points.minOfOrNull(GeoPoint::latitude) ?: 0.0
    val maxLatitude: Double = points.maxOfOrNull(GeoPoint::latitude) ?: 0.0
    val minLongitude: Double = points.minOfOrNull(GeoPoint::longitude) ?: 0.0
    val maxLongitude: Double = points.maxOfOrNull(GeoPoint::longitude) ?: 0.0
}

data class NativeZtlSnapshot(
    val revision: Long = 0,
    val zones: List<NativeZtlZone> = emptyList(),
    val restrictedWays: List<NativeZtlWay> = emptyList(),
) {
    fun isInside(point: GeoPoint): Boolean =
        zones.any { contains(point, it) } ||
            restrictedWays.any { near(point, it, WAY_PROXIMITY_METERS) }

    fun nameAt(point: GeoPoint): String? =
        zones.firstOrNull { contains(point, it) }?.name
            ?: restrictedWays.firstOrNull { near(point, it, WAY_PROXIMITY_METERS) }?.name

    fun nearestRestrictedWay(point: GeoPoint, withinMeters: Double = NEARBY_PROXIMITY_METERS): NativeZtlWay? =
        restrictedWays.firstOrNull { near(point, it, withinMeters) }

    fun classify(points: List<NativeMapPoint>): List<Boolean> {
        if (restrictedWays.isEmpty()) return List(points.size) { false }
        return points.map { point ->
            val geo = GeoPoint(point.latitude, point.longitude)
            restrictedWays.any { near(geo, it, WAY_PROXIMITY_METERS) }
        }
    }

    companion object {
        const val WAY_PROXIMITY_METERS = 12.0
        const val NEARBY_PROXIMITY_METERS = 60.0

        private fun near(point: GeoPoint, way: NativeZtlWay, withinMeters: Double): Boolean {
            if (way.points.size < 2) return false
            val latPadding = withinMeters / GeoMath.metresPerDegree
            val lonScale = abs(cos(point.latitude * Math.PI / 180.0)).coerceAtLeast(0.1)
            val lonPadding = withinMeters / (GeoMath.metresPerDegree * lonScale)
            if (point.latitude < way.minLatitude - latPadding || point.latitude > way.maxLatitude + latPadding ||
                point.longitude < way.minLongitude - lonPadding || point.longitude > way.maxLongitude + lonPadding
            ) return false
            return GeoMath.distanceToPolylineMeters(point, way.points) <= withinMeters
        }

        private fun contains(point: GeoPoint, zone: NativeZtlZone): Boolean {
            if (point.latitude !in zone.minLatitude..zone.maxLatitude ||
                point.longitude !in zone.minLongitude..zone.maxLongitude
            ) return false
            return GeoMath.pointInPolygon(point, zone.polygon)
        }

        fun officialAcronym(point: GeoPoint): String? = when {
            point.latitude in 36.8..42.2 && point.longitude in -9.6..-6.1 -> "ZAC"
            point.latitude in 41.2..51.2 && point.longitude in -5.3..9.7 -> "ZTL"
            point.latitude in 35.4..47.2 && point.longitude in 6.5..18.8 -> "ZTL"
            else -> null
        }
    }
}

/** OSM/Overpass restricted-road and limited-traffic-zone cache for the Kotlin runtime. */
class NativeZtlService(
    transport: NativeSearchHttpTransport,
    private val diagnostics: (String) -> Unit = {},
    private val clock: () -> Instant = Instant::now,
) {
    private val overpass = NativeOverpassClient(transport)
    private val lock = Any()
    private val _state = MutableStateFlow(NativeZtlSnapshot())
    private var lastQueryPosition: GeoPoint? = null
    private var lastSuccessAt: Instant? = null
    private var nextRetryAt: Instant? = null
    private var fetching = false

    val state: StateFlow<NativeZtlSnapshot> = _state.asStateFlow()

    suspend fun update(point: GeoPoint): NativeZtlSnapshot {
        if (!beginIfDue(point)) return state.value
        try {
            if (!fetchThroughMirrors(point)) {
                synchronized(lock) { nextRetryAt = clock().plus(overpass.failureBackoff(RETRY_BASE)) }
            }
        } finally {
            synchronized(lock) { fetching = false }
        }
        return state.value
    }

    private suspend fun fetchThroughMirrors(point: GeoPoint): Boolean {
        var lastError: Exception? = null
        repeat(overpass.mirrorCount) {
            try {
                val elements = overpass.fetchElements(query(point), MAX_BYTES, TIMEOUT_MILLIS)
                val parsed = parse(elements, state.value.revision + 1)
                diagnostics(
                    "ZTL: ${elements.size} elements, ${parsed.zones.size} zones, " +
                        "${parsed.restrictedWays.size} restricted ways",
                )
                synchronized(lock) {
                    _state.value = parsed
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
                    "ZTL: mirror failed (" +
                        "${(error as? NativeOverpassException)?.statusCode ?: error.javaClass.simpleName})",
                )
                overpass.rotate()
            }
        }
        overpass.noteFailure(lastError)
        return false
    }

    private fun beginIfDue(point: GeoPoint): Boolean = synchronized(lock) {
        if (fetching) return false
        val now = clock()
        if (nextRetryAt?.let(now::isBefore) == true) return false
        if (!POLICY.isDue(lastQueryPosition, point, lastSuccessAt, now)) return false
        fetching = true
        true
    }

    private fun query(point: GeoPoint): String {
        val lat = overpass.coord(point.latitude)
        val lon = overpass.coord(point.longitude)
        return """
            [out:json][timeout:20];
            (
              way[highway~"^(living_street|residential|unclassified|service|tertiary|secondary|primary)$"]
                 [~"^(access|motor_vehicle|vehicle|motorcar)$"~"^(no|destination|permit|delivery)$"]
                 (around:2000,$lat,$lon);
              way[highway=pedestrian]
                 [~"^(access|motor_vehicle|vehicle|motorcar)$"~"^(no|destination|permit|delivery)$"]
                 (around:2000,$lat,$lon);
              way[highway~"^(living_street|residential|unclassified|service|tertiary|secondary|primary|pedestrian)$"]
                 [~"^(access|motor_vehicle|vehicle|motorcar):conditional$"~"^(no|destination|permit|delivery)"]
                 (around:2000,$lat,$lon);
              way(around:3000,$lat,$lon)["zone:traffic"~"(restricted|limited)",i];
              relation(around:3000,$lat,$lon)["zone:traffic"~"(restricted|limited)",i];
              relation(around:3000,$lat,$lon)["boundary"="traffic_zone"];
              relation(around:3000,$lat,$lon)["boundary"~"^(restricted_area|limited_traffic_zone|low_emission_zone)$"];
              relation(around:3000,$lat,$lon)["name"~"ZTL",i]["access"!="yes"];
              way(around:3000,$lat,$lon)["name"~"ZTL",i]["area"="yes"];
            );
            out geom;
        """.trimIndent()
    }

    internal fun parseForTest(elements: List<Map<String, Any?>>, revision: Long = 1): NativeZtlSnapshot =
        parse(elements, revision)

    private fun parse(elements: List<Map<String, Any?>>, revision: Long): NativeZtlSnapshot {
        val zones = ArrayList<NativeZtlZone>()
        val ways = ArrayList<NativeZtlWay>()
        var retainedPoints = 0
        for (element in elements) {
            if (retainedPoints >= MAX_RETAINED_POINTS) break
            val tags = element["tags"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val name = (tags["name"] as? String)?.take(MAX_NAME_CHARS)
            when (element["type"] as? String) {
                "way" -> {
                    val points = geometry(element["geometry"] as? List<*>)
                    if (points.size < 2 || retainedPoints + points.size > MAX_RETAINED_POINTS) continue
                    if (tags["area"] == "yes" && points.size >= 3 && zones.size < MAX_ZONES) {
                        zones += NativeZtlZone(name, points)
                        retainedPoints += points.size
                    } else if (tags.containsKey("highway") && ways.size < MAX_WAYS) {
                        ways += NativeZtlWay(name, points)
                        retainedPoints += points.size
                    }
                }
                "relation" -> if (zones.size < MAX_ZONES) {
                    val outer = ArrayList<GeoPoint>()
                    val members = element["members"] as? List<*> ?: emptyList<Any?>()
                    for (raw in members) {
                        val member = raw as? Map<*, *> ?: continue
                        if (member["role"] != "outer") continue
                        val part = geometry(member["geometry"] as? List<*>)
                        if (part.isEmpty()) continue
                        if (outer.lastOrNull() == part.first()) outer.addAll(part.drop(1)) else outer.addAll(part)
                        if (outer.size + retainedPoints > MAX_RETAINED_POINTS) break
                    }
                    if (outer.size >= 3 && outer.size + retainedPoints <= MAX_RETAINED_POINTS) {
                        zones += NativeZtlZone(name, outer)
                        retainedPoints += outer.size
                    }
                }
            }
        }
        return NativeZtlSnapshot(revision, zones, ways)
    }

    private fun geometry(raw: List<*>?): List<GeoPoint> = raw.orEmpty().mapNotNull { value ->
        val point = value as? Map<*, *> ?: return@mapNotNull null
        val latitude = (point["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
        val longitude = (point["lon"] as? Number)?.toDouble() ?: return@mapNotNull null
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) null else GeoPoint(latitude, longitude)
    }

    private companion object {
        const val MAX_ZONES = 256
        const val MAX_WAYS = 2_000
        const val MAX_RETAINED_POINTS = 60_000
        const val MAX_NAME_CHARS = 160
        const val MAX_BYTES = 20L * 1024 * 1024
        const val TIMEOUT_MILLIS = 25_000L
        val RETRY_BASE: Duration = Duration.ofSeconds(15)
        val POLICY = RefetchPolicy(
            minimumMovementMeters = 2_000.0,
            maximumAge = Duration.ofMinutes(30),
        )
    }
}
