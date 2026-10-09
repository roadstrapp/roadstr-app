package app.roadstr.feature.savedroute

import app.roadstr.core.network.RoutingParsedRoute
import app.roadstr.core.network.RoutingResponsePoint
import app.roadstr.core.network.RoutingResponseStep
import app.roadstr.core.network.RoutingRouteAvoidance
import app.roadstr.core.network.RoutingSpeedLimitEntry
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.math.BigDecimal
import java.util.Collections
import java.util.Locale
import java.util.UUID
import kotlin.math.round

enum class NativeSavedRouteState(val wireValue: String) {
    Calculated("calculated"),
    NeedsRecalculation("needs_recalculation");

    companion object {
        fun fromWire(value: String?): NativeSavedRouteState? = entries.firstOrNull {
            it.wireValue == value
        }
    }
}

data class NativeSavedRouteStop(
    val label: String,
    val point: RoutingResponsePoint,
)

data class NativeSavedRoutePreferences(
    val profile: String,
    val avoidance: RoutingRouteAvoidance = RoutingRouteAvoidance.None,
    val avoidUnpavedRoads: Boolean = false,
)

/** A complete route snapshot. Its geometry is always an encoded polyline at precision six. */
data class NativeSavedRoute(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val stops: List<NativeSavedRouteStop>,
    val preferences: NativeSavedRoutePreferences,
    val providerId: String,
    val engineId: String,
    val calculatedAtEpochMillis: Long?,
    val distanceM: Double?,
    val durationS: Double?,
    val geometryPolyline6: String?,
    val steps: List<RoutingResponseStep>,
    val speedLimits: List<RoutingSpeedLimitEntry>,
    val state: NativeSavedRouteState,
    val offlineRecalculable: Boolean,
) {
    fun parsedRoute(): RoutingParsedRoute? {
        if (state != NativeSavedRouteState.Calculated) return null
        val geometry = geometryPolyline6 ?: return null
        val distance = distanceM ?: return null
        val duration = durationS ?: return null
        val points = NativeSavedRouteProtocol.decodePolyline6(geometry) ?: return null
        if (points.size < 2) return null
        return RoutingParsedRoute(
            polyline = points,
            steps = steps.toList(),
            totalDistanceM = distance,
            totalDurationS = duration,
            speedLimits = speedLimits.toList(),
            avoidance = preferences.avoidance,
            fromAvoidanceRouter = preferences.avoidance != RoutingRouteAvoidance.None,
        )
    }
}

/** Versioned, bounded and fail-closed codec for the plaintext inside the encrypted store. */
object NativeSavedRouteProtocol {
    const val CURRENT_SCHEMA_VERSION = 2
    const val MAX_ROUTES = 25
    const val MAX_STOPS = 5
    const val MAX_NAME_CHARS = 160
    const val MAX_LABEL_CHARS = 500
    const val MAX_ID_CHARS = 80
    const val MAX_ENGINE_CHARS = 80
    const val MAX_STEPS = 60_000
    const val MAX_SPEED_LIMITS = 60_000
    const val MAX_GEOMETRY_CHARS = 2_000_000
    const val MAX_STORED_BYTES = 4 * 1024 * 1024

    private val idPattern = Regex("^[A-Za-z0-9._-]{1,$MAX_ID_CHARS}$")
    private val profilePattern = Regex("^[a-z0-9_-]{1,32}$")

    fun create(
        name: String,
        stops: List<NativeSavedRouteStop>,
        preferences: NativeSavedRoutePreferences,
        providerId: String,
        engineId: String,
        route: RoutingParsedRoute,
        nowEpochMillis: Long,
        id: String = UUID.randomUUID().toString(),
        createdAtEpochMillis: Long = nowEpochMillis,
    ): NativeSavedRoute {
        val value = NativeSavedRoute(
            id = id,
            name = name,
            createdAtEpochMillis = createdAtEpochMillis,
            modifiedAtEpochMillis = nowEpochMillis,
            stops = stops,
            preferences = preferences,
            providerId = providerId,
            engineId = engineId,
            calculatedAtEpochMillis = nowEpochMillis,
            distanceM = route.totalDistanceM,
            durationS = route.totalDurationS,
            geometryPolyline6 = encodePolyline6(route.polyline),
            steps = route.steps,
            speedLimits = route.speedLimits,
            state = NativeSavedRouteState.Calculated,
            offlineRecalculable = false,
        )
        return requireNotNull(normalize(value)) { "Saved route is invalid" }
    }

    fun upsert(current: List<NativeSavedRoute>, value: NativeSavedRoute): List<NativeSavedRoute> {
        val normalized = requireNotNull(normalize(value)) { "Saved route is invalid" }
        val next = ArrayList<NativeSavedRoute>(minOf(MAX_ROUTES, current.size + 1))
        next += normalized
        current.asSequence()
            .filterNot { it.id == normalized.id }
            .mapNotNull(::normalize)
            .sortedByDescending { it.modifiedAtEpochMillis }
            .take(MAX_ROUTES - 1)
            .forEach(next::add)
        return Collections.unmodifiableList(next)
    }

    fun remove(current: List<NativeSavedRoute>, id: String): List<NativeSavedRoute> =
        Collections.unmodifiableList(current.filterNot { it.id == id })

    fun encode(routes: List<NativeSavedRoute>): String = buildString {
        append("{\"schema\":").append(CURRENT_SCHEMA_VERSION).append(",\"routes\":[")
        routes.asSequence().mapNotNull(::normalize).take(MAX_ROUTES).forEachIndexed { index, route ->
            if (index > 0) append(',')
            appendRoute(route)
        }
        append("]}")
    }.also { encoded ->
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_STORED_BYTES) {
            "Saved routes exceed the storage limit"
        }
    }

    fun decode(raw: String?): List<NativeSavedRoute> {
        if (raw == null || raw.toByteArray(Charsets.UTF_8).size > MAX_STORED_BYTES) return emptyList()
        val root = try {
            BoundedJsonParser(raw).parse() as? Map<*, *>
        } catch (_: RuntimeException) {
            null
        } ?: return emptyList()
        val schema = (root["schema"] as? Number)?.toInt() ?: return emptyList()
        if (schema !in 1..CURRENT_SCHEMA_VERSION) return emptyList()
        val rows = root["routes"] as? List<*> ?: return emptyList()
        val routes = rows.asSequence()
            .mapNotNull { (it as? Map<*, *>)?.let { map -> fromMap(map, schema) } }
            .distinctBy(NativeSavedRoute::id)
            .take(MAX_ROUTES)
            .toList()
        return Collections.unmodifiableList(routes)
    }

    fun rename(route: NativeSavedRoute, name: String, nowEpochMillis: Long): NativeSavedRoute =
        requireNotNull(normalize(route.copy(name = name, modifiedAtEpochMillis = nowEpochMillis))) {
            "Saved route name is invalid"
        }

    fun markForRecalculation(route: NativeSavedRoute, nowEpochMillis: Long): NativeSavedRoute =
        requireNotNull(
            normalize(
                route.copy(
                    modifiedAtEpochMillis = nowEpochMillis,
                    calculatedAtEpochMillis = null,
                    distanceM = null,
                    durationS = null,
                    geometryPolyline6 = null,
                    steps = emptyList(),
                    speedLimits = emptyList(),
                    state = NativeSavedRouteState.NeedsRecalculation,
                    offlineRecalculable = false,
                ),
            ),
        )

    fun encodePolyline6(points: List<RoutingResponsePoint>): String {
        require(points.size in 2..250_000) { "Saved route geometry has an invalid point count" }
        var lastLatitude = 0L
        var lastLongitude = 0L
        return buildString {
            for (point in points) {
                require(validPoint(point)) { "Saved route geometry has an invalid coordinate" }
                val latitude = round(point.latitude * 1_000_000.0).toLong()
                val longitude = round(point.longitude * 1_000_000.0).toLong()
                appendSigned(latitude - lastLatitude)
                appendSigned(longitude - lastLongitude)
                lastLatitude = latitude
                lastLongitude = longitude
            }
        }.also {
            require(it.length <= MAX_GEOMETRY_CHARS) { "Saved route geometry is too large" }
        }
    }

    fun decodePolyline6(encoded: String): List<RoutingResponsePoint>? {
        if (encoded.isEmpty() || encoded.length > MAX_GEOMETRY_CHARS) return null
        val points = ArrayList<RoutingResponsePoint>()
        var index = 0
        var latitude = 0L
        var longitude = 0L
        while (index < encoded.length) {
            val first = decodeSigned(encoded, index) ?: return null
            index = first.second
            val second = decodeSigned(encoded, index) ?: return null
            index = second.second
            latitude += first.first
            longitude += second.first
            val point = RoutingResponsePoint(latitude / 1_000_000.0, longitude / 1_000_000.0)
            if (!validPoint(point) || points.size >= 250_000) return null
            points += point
        }
        return points.takeIf { it.size >= 2 }
    }

    private fun normalize(value: NativeSavedRoute): NativeSavedRoute? {
        val name = value.name.clean(MAX_NAME_CHARS) ?: return null
        val provider = value.providerId.clean(MAX_ENGINE_CHARS)?.lowercase(Locale.ROOT) ?: return null
        val engine = value.engineId.clean(MAX_ENGINE_CHARS)?.lowercase(Locale.ROOT) ?: return null
        if (!idPattern.matches(value.id) || !profilePattern.matches(value.preferences.profile)) return null
        if (value.createdAtEpochMillis < 0 || value.modifiedAtEpochMillis < value.createdAtEpochMillis) return null
        if (value.stops.size !in 2..MAX_STOPS) return null
        val stops = value.stops.map { stop ->
            NativeSavedRouteStop(stop.label.clean(MAX_LABEL_CHARS) ?: return null, stop.point)
                .takeIf { validPoint(it.point) } ?: return null
        }
        if (value.steps.size > MAX_STEPS || value.speedLimits.size > MAX_SPEED_LIMITS) return null
        if (value.steps.any { !validStep(it) } || value.speedLimits.any { !validSpeed(it) }) return null
        when (value.state) {
            NativeSavedRouteState.Calculated -> {
                if (value.calculatedAtEpochMillis == null || value.calculatedAtEpochMillis < 0) return null
                if (value.distanceM == null || !value.distanceM.isFinite() || value.distanceM <= 0) return null
                if (value.durationS == null || !value.durationS.isFinite() || value.durationS <= 0) return null
                val geometry = value.geometryPolyline6 ?: return null
                if (decodePolyline6(geometry) == null) return null
            }
            NativeSavedRouteState.NeedsRecalculation -> {
                if (value.geometryPolyline6 != null || value.distanceM != null || value.durationS != null) return null
                if (value.steps.isNotEmpty() || value.speedLimits.isNotEmpty()) return null
            }
        }
        return value.copy(
            name = name,
            stops = Collections.unmodifiableList(stops),
            providerId = provider,
            engineId = engine,
            steps = Collections.unmodifiableList(value.steps.toList()),
            speedLimits = Collections.unmodifiableList(value.speedLimits.toList()),
            offlineRecalculable = value.offlineRecalculable && value.state == NativeSavedRouteState.Calculated,
        )
    }

    private fun fromMap(map: Map<*, *>, schema: Int): NativeSavedRoute? {
        val state = NativeSavedRouteState.fromWire(map["state"] as? String)
            ?: if (map["geometry"] is String) NativeSavedRouteState.Calculated else NativeSavedRouteState.NeedsRecalculation
        val preferences = map["preferences"] as? Map<*, *> ?: return null
        val avoidance = (preferences["avoidance"] as? String)?.let { wire ->
            RoutingRouteAvoidance.entries.firstOrNull { it.name == wire }
        } ?: RoutingRouteAvoidance.None
        val rawStops = map["stops"] as? List<*> ?: return null
        val stops = rawStops.map { raw ->
            val stop = raw as? Map<*, *> ?: return null
            val label = stop["label"] as? String ?: return null
            val point = point(stop) ?: return null
            NativeSavedRouteStop(label, point)
        }
        val rawSteps = map["steps"] as? List<*> ?: emptyList<Any?>()
        val steps = rawSteps.map { raw ->
            val row = raw as? Map<*, *> ?: return null
            step(row) ?: return null
        }
        val rawSpeedLimits = map["speedLimits"] as? List<*> ?: emptyList<Any?>()
        val speedLimits = rawSpeedLimits.map { raw ->
            val row = raw as? Map<*, *> ?: return null
            val distance = (row["distance"] as? Number)?.toDouble() ?: return null
            val speed = (row["speed"] as? Number)?.toInt()
            RoutingSpeedLimitEntry(distance, speed)
        }
        val provider = map["provider"] as? String ?: return null
        return normalize(
            NativeSavedRoute(
                id = map["id"] as? String ?: return null,
                name = map["name"] as? String ?: return null,
                createdAtEpochMillis = (map["createdAt"] as? Number)?.toLong() ?: return null,
                modifiedAtEpochMillis = (map["modifiedAt"] as? Number)?.toLong() ?: return null,
                stops = stops,
                preferences = NativeSavedRoutePreferences(
                    profile = preferences["profile"] as? String ?: return null,
                    avoidance = avoidance,
                    avoidUnpavedRoads = preferences["avoidUnpaved"] as? Boolean ?: false,
                ),
                providerId = provider,
                engineId = (map["engine"] as? String)?.takeIf { schema >= 2 } ?: provider,
                calculatedAtEpochMillis = (map["calculatedAt"] as? Number)?.toLong(),
                distanceM = (map["distance"] as? Number)?.toDouble(),
                durationS = (map["duration"] as? Number)?.toDouble(),
                geometryPolyline6 = map["geometry"] as? String,
                steps = steps,
                speedLimits = speedLimits,
                state = state,
                offlineRecalculable = if (schema >= 2) map["offlineRecalculable"] as? Boolean ?: false else false,
            ),
        )
    }

    private fun point(map: Map<*, *>): RoutingResponsePoint? {
        val latitude = (map["lat"] as? Number)?.toDouble() ?: return null
        val longitude = (map["lon"] as? Number)?.toDouble() ?: return null
        return RoutingResponsePoint(latitude, longitude).takeIf(::validPoint)
    }

    private fun step(map: Map<*, *>): RoutingResponseStep? {
        val location = point(map) ?: return null
        return RoutingResponseStep(
            instruction = map["instruction"] as? String ?: return null,
            direction = map["direction"] as? String ?: return null,
            modifier = map["modifier"] as? String ?: "",
            distanceM = (map["distance"] as? Number)?.toDouble() ?: return null,
            location = location,
            exitNumber = (map["exitNumber"] as? Number)?.toInt(),
            roundaboutArmCount = (map["roundaboutArmCount"] as? Number)?.toInt(),
            exitLabel = map["exitLabel"] as? String,
            roadName = map["roadName"] as? String ?: "",
            roadRef = map["roadRef"] as? String ?: "",
        ).takeIf(::validStep)
    }

    private fun validPoint(point: RoutingResponsePoint): Boolean =
        point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
            point.longitude.isFinite() && point.longitude in -180.0..180.0

    private fun validStep(step: RoutingResponseStep): Boolean =
        step.instruction.length <= 2_000 && step.direction.length <= 80 && step.modifier.length <= 80 &&
            step.roadName.length <= 500 && step.roadRef.length <= 120 &&
            (step.exitLabel?.length ?: 0) <= 500 && step.distanceM.isFinite() && step.distanceM >= 0 &&
            validPoint(step.location) && (step.exitNumber == null || step.exitNumber in 0..100) &&
            (step.roundaboutArmCount == null || step.roundaboutArmCount in 0..100)

    private fun validSpeed(value: RoutingSpeedLimitEntry): Boolean =
        value.distFromStartM.isFinite() && value.distFromStartM >= 0 &&
            (value.speedKmh == null || value.speedKmh in 1..300)

    private fun String.clean(limit: Int): String? = trim().takeIf { it.isNotEmpty() && it.length <= limit }

    private fun StringBuilder.appendRoute(route: NativeSavedRoute) {
        append('{')
        field("id", route.id); append(','); field("name", route.name)
        append(",\"createdAt\":").append(route.createdAtEpochMillis)
        append(",\"modifiedAt\":").append(route.modifiedAtEpochMillis)
        append(",\"stops\":[")
        route.stops.forEachIndexed { index, stop ->
            if (index > 0) append(',')
            append('{'); field("label", stop.label)
            append(",\"lat\":").append(decimal(stop.point.latitude))
            append(",\"lon\":").append(decimal(stop.point.longitude)).append('}')
        }
        append("],\"preferences\":{"); field("profile", route.preferences.profile)
        append(','); field("avoidance", route.preferences.avoidance.name)
        append(",\"avoidUnpaved\":").append(route.preferences.avoidUnpavedRoads).append('}')
        append(','); field("provider", route.providerId); append(','); field("engine", route.engineId)
        append(",\"calculatedAt\":").append(route.calculatedAtEpochMillis ?: "null")
        append(",\"distance\":").append(route.distanceM?.let(::decimal) ?: "null")
        append(",\"duration\":").append(route.durationS?.let(::decimal) ?: "null")
        append(",\"geometry\":"); json(route.geometryPolyline6)
        append(",\"steps\":[")
        route.steps.forEachIndexed { index, step ->
            if (index > 0) append(',')
            appendStep(step)
        }
        append("],\"speedLimits\":[")
        route.speedLimits.forEachIndexed { index, speed ->
            if (index > 0) append(',')
            append("{\"distance\":").append(decimal(speed.distFromStartM)).append(",\"speed\":")
            append(speed.speedKmh ?: "null").append('}')
        }
        append("],"); field("state", route.state.wireValue)
        append(",\"offlineRecalculable\":").append(route.offlineRecalculable).append('}')
    }

    private fun StringBuilder.appendStep(step: RoutingResponseStep) {
        append('{'); field("instruction", step.instruction); append(','); field("direction", step.direction)
        append(','); field("modifier", step.modifier)
        append(",\"distance\":").append(decimal(step.distanceM))
        append(",\"lat\":").append(decimal(step.location.latitude))
        append(",\"lon\":").append(decimal(step.location.longitude))
        append(",\"exitNumber\":").append(step.exitNumber ?: "null")
        append(",\"roundaboutArmCount\":").append(step.roundaboutArmCount ?: "null")
        append(",\"exitLabel\":"); json(step.exitLabel)
        append(','); field("roadName", step.roadName); append(','); field("roadRef", step.roadRef); append('}')
    }

    private fun StringBuilder.field(name: String, value: String) {
        append('"').append(name).append("\":")
        json(value)
    }

    private fun StringBuilder.json(value: String?) {
        if (value == null) {
            append("null")
            return
        }
        append('"')
        for (char in value) when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char < ' ' -> append("\\u%04x".format(Locale.ROOT, char.code))
            else -> append(char)
        }
        append('"')
    }

    private fun decimal(value: Double): String = BigDecimal.valueOf(value).toPlainString()

    private fun StringBuilder.appendSigned(value: Long) {
        var encoded = if (value < 0) (value shl 1).inv() else value shl 1
        while (encoded >= 0x20) {
            append(((0x20 or (encoded and 0x1f).toInt()) + 63).toChar())
            encoded = encoded shr 5
        }
        append((encoded.toInt() + 63).toChar())
    }

    private fun decodeSigned(encoded: String, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var index = start
        while (index < encoded.length && shift <= 60) {
            val value = encoded[index++].code - 63
            if (value !in 0..63) return null
            result = result or ((value and 0x1f).toLong() shl shift)
            if (value < 0x20) {
                val signed = if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
                return signed to index
            }
            shift += 5
        }
        return null
    }
}

/** Small persistence boundary; the host supplies the encrypted value operations. */
class NativeSavedRoutesStore(
    private val readEncryptedValue: () -> String?,
    private val writeEncryptedValue: (String) -> Boolean,
    private val removeEncryptedValue: () -> Boolean,
) {
    fun load(): List<NativeSavedRoute> = NativeSavedRouteProtocol.decode(readEncryptedValue())

    fun save(routes: List<NativeSavedRoute>): Boolean {
        val bounded = routes.take(NativeSavedRouteProtocol.MAX_ROUTES)
        if (bounded.isEmpty()) return removeEncryptedValue()
        return runCatching { writeEncryptedValue(NativeSavedRouteProtocol.encode(bounded)) }.getOrDefault(false)
    }

    fun upsert(routes: List<NativeSavedRoute>, route: NativeSavedRoute): List<NativeSavedRoute>? {
        val next = NativeSavedRouteProtocol.upsert(routes, route)
        return next.takeIf(::save)
    }

    fun remove(routes: List<NativeSavedRoute>, id: String): List<NativeSavedRoute>? {
        val next = NativeSavedRouteProtocol.remove(routes, id)
        return next.takeIf(::save)
    }
}
