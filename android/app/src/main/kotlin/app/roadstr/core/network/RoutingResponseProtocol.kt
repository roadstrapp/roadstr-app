package app.roadstr.core.network

import app.roadstr.core.navigation.NavigationPhrases
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class RoutingResponsePoint(
    val latitude: Double,
    val longitude: Double,
)

data class RoutingResponseStep(
    val instruction: String,
    val direction: String,
    val modifier: String = "",
    val distanceM: Double,
    val location: RoutingResponsePoint,
    val exitNumber: Int? = null,
    val roundaboutArmCount: Int? = null,
    val exitLabel: String? = null,
    val roadName: String = "",
    val roadRef: String = "",
) {
    val isUrbanStreet: Boolean
        get() = roadName.isNotEmpty() && roadRef.isEmpty()
}

data class RoutingSpeedLimitEntry(
    val distFromStartM: Double,
    val speedKmh: Int?,
)

enum class RoutingRouteAvoidance {
    None,
    HighwayAndTollFree,
    MinimizedHighwaysAndTolls,
    OffRoadAvoided,
}

data class RoutingParsedRoute(
    val polyline: List<RoutingResponsePoint>,
    val steps: List<RoutingResponseStep>,
    val totalDistanceM: Double,
    val totalDurationS: Double,
    val speedLimits: List<RoutingSpeedLimitEntry> = emptyList(),
    val avoidance: RoutingRouteAvoidance = RoutingRouteAvoidance.None,
    val fromAvoidanceRouter: Boolean = false,
)

data class ValhallaParsedResponse(
    val route: RoutingParsedRoute,
    val summary: Map<String, Any?>,
)

data class OsrmRetimeLeg(
    val distanceM: Double?,
    val durationS: Double?,
)

class RoutingResponseException(
    val responseMessage: String,
) : RuntimeException(responseMessage)

/** Exact socket-free normalization of Roadstr's routing-provider responses. */
object RoutingResponseProtocol {
    const val MAX_ROUNDABOUT_ARMS = 20
    const val MAX_ROUTE_POINTS = 250_000
    const val MAX_ROUTE_STEPS = 60_000

    fun parseOpenRouteService(
        body: String,
        fallbackOrigin: RoutingResponsePoint,
    ): RoutingParsedRoute = wrapErrors {
        val data = parseObject(body)
        val features = data["features"] as? List<*>
        if (features.isNullOrEmpty()) {
            fail("OpenRouteService response missing features")
        }
        val feature = features.first() as Map<*, *>
        val properties = feature["properties"] as Map<*, *>
        val summary = properties["summary"] as? Map<*, *>
        val segments = properties["segments"] as? List<*>
        val geometry = feature["geometry"] as Map<*, *>
        val rawCoordinates = geometry["coordinates"] as List<*>
        checkPointCount(rawCoordinates.size)
        val coordinates = rawCoordinates.map(::coordinate)

        val steps = mutableListOf<RoutingResponseStep>()
        if (!segments.isNullOrEmpty()) {
            val segment = segments.first() as Map<*, *>
            for (rawStep in segment["steps"] as? List<*> ?: emptyList<Any?>()) {
                val step = rawStep as Map<*, *>
                val instruction = step["instruction"] as? String ?: ""
                val distance = (step["distance"] as? Number)?.toDouble() ?: 0.0
                val type = intValue(step["type"]) ?: 6
                val maneuver = orsManeuver(type)
                val waypoints = step["way_points"] as? List<*>
                val location = if (!waypoints.isNullOrEmpty()) {
                    coordinates[(waypoints.first() as Number).toInt().coerceIn(0, coordinates.lastIndex)]
                } else {
                    coordinates.firstOrNull() ?: fallbackOrigin
                }
                steps += RoutingResponseStep(
                    instruction = instruction,
                    direction = maneuver.first,
                    modifier = maneuver.second,
                    distanceM = distance,
                    location = location,
                    exitNumber = if (maneuver.first == "roundabout") {
                        (step["exit_number"] as? Number)?.toInt()
                            ?: parseExitNumber(instruction)
                    } else {
                        null
                    },
                )
            }
        }

        validate(
            RoutingParsedRoute(
                polyline = coordinates,
                steps = steps,
                totalDistanceM = (summary?.get("distance") as? Number)?.toDouble() ?: 0.0,
                totalDurationS = (summary?.get("duration") as? Number)?.toDouble() ?: 0.0,
            ),
        )
    }

    fun parseGraphHopper(
        body: String,
        fallbackOrigin: RoutingResponsePoint,
    ): RoutingParsedRoute = wrapErrors {
        val data = parseObject(body)
        val paths = data["paths"] as? List<*>
        if (paths.isNullOrEmpty()) fail("GraphHopper response missing paths")
        val path = paths.first() as Map<*, *>
        val points = path["points"] as? Map<*, *>
        val coordinates = mutableListOf<RoutingResponsePoint>()
        if (points?.get("coordinates") != null) {
            val rawCoordinates = points["coordinates"] as List<*>
            checkPointCount(rawCoordinates.size)
            coordinates += rawCoordinates.map(::coordinate)
        }

        val steps = mutableListOf<RoutingResponseStep>()
        for (rawInstruction in path["instructions"] as? List<*> ?: emptyList<Any?>()) {
            val instruction = rawInstruction as Map<*, *>
            val text = instruction["text"] as? String ?: ""
            val distance = (instruction["distance"] as? Number)?.toDouble() ?: 0.0
            val sign = intValue(instruction["sign"]) ?: 0
            val maneuver = graphHopperManeuver(sign)
            val index = ((instruction["interval"] as? List<*>)?.firstOrNull() as? Number)
                ?.toInt() ?: 0
            val location = coordinates.getOrNull(index)
                ?: coordinates.firstOrNull()
                ?: fallbackOrigin
            steps += RoutingResponseStep(
                instruction = text,
                direction = maneuver.first,
                modifier = maneuver.second,
                distanceM = distance,
                location = location,
                exitNumber = if (maneuver.first == "roundabout") {
                    (instruction["exit_number"] as? Number)?.toInt()
                        ?: parseExitNumber(text)
                } else {
                    null
                },
            )
        }

        val speedLimits = mutableListOf<RoutingSpeedLimitEntry>()
        try {
            val details = path["details"] as? Map<*, *>
            val intervals = details?.get("max_speed") as? List<*>
            if (intervals != null && coordinates.isNotEmpty()) {
                val cumulative = mutableListOf(0.0)
                for (index in 1 until coordinates.size) {
                    cumulative += cumulative.last() +
                        roundedVincentyDistance(coordinates[index - 1], coordinates[index])
                }
                for (rawInterval in intervals) {
                    val interval = rawInterval as List<*>
                    val fromIndex = (interval[0] as Number).toInt()
                        .coerceIn(0, coordinates.lastIndex)
                    val value = interval[2]
                    speedLimits += RoutingSpeedLimitEntry(
                        distFromStartM = cumulative[fromIndex],
                        speedKmh = if (value is Number && value.toDouble() > 0) {
                            value.toInt()
                        } else {
                            null
                        },
                    )
                }
            }
        } catch (_: Exception) {}

        validate(
            RoutingParsedRoute(
                polyline = coordinates,
                steps = steps,
                totalDistanceM = (path["distance"] as? Number)?.toDouble() ?: 0.0,
                totalDurationS = (path["time"] as? Number)?.toDouble()?.div(1000.0) ?: 0.0,
                speedLimits = speedLimits,
            ),
        )
    }

    fun parseOsrmRoutes(
        body: String,
        languageCode: String = "en",
    ): List<RoutingParsedRoute> = wrapErrors {
        val data = parseObject(body)
        if (data["code"] != "Ok") fail("OSRM returned error code: ${data["code"]}")
        val routes = data["routes"] as? List<*>
        if (routes.isNullOrEmpty()) fail("OSRM response missing routes")
        routes.map { parseOsrmRoute(it as Map<*, *>, languageCode) }
    }

    fun parseValhalla(body: String): ValhallaParsedResponse = wrapErrors {
        val data = parseObject(body)
        val trip = data["trip"] as? Map<*, *>
        if (trip == null || (trip["status"] as? Number)?.toDouble() != 0.0) {
            fail("Valhalla returned no route")
        }
        val rawSummary = trip["summary"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val summary = linkedMapOf<String, Any?>()
        for ((key, value) in rawSummary) if (key is String) summary[key] = value
        val legs = trip["legs"] as? List<*>
        if (legs.isNullOrEmpty()) fail("Valhalla response missing legs")

        val coordinates = mutableListOf<RoutingResponsePoint>()
        val steps = mutableListOf<RoutingResponseStep>()
        for (rawLeg in legs) {
            val leg = rawLeg as Map<*, *>
            val legCoordinates = decodeValhallaPolyline(leg["shape"] as? String ?: "")
            if (legCoordinates.isEmpty()) fail("Valhalla response missing shape")
            val sharesEndpoint = coordinates.isNotEmpty() && coordinates.last() == legCoordinates.first()
            val coordinateOffset = if (sharesEndpoint) coordinates.lastIndex else coordinates.size
            if (sharesEndpoint) {
                coordinates += legCoordinates.drop(1)
            } else {
                coordinates += legCoordinates
            }
            checkPointCount(coordinates.size)

            for (rawManeuver in leg["maneuvers"] as? List<*> ?: emptyList<Any?>()) {
                val maneuver = rawManeuver as Map<*, *>
                val localIndex = (maneuver["begin_shape_index"] as? Number)?.toInt() ?: 0
                val pointIndex = (coordinateOffset + localIndex).coerceIn(0, coordinates.lastIndex)
                val type = (maneuver["type"] as? Number)?.toInt() ?: 0
                val mapped = valhallaManeuver(type)
                steps += RoutingResponseStep(
                    instruction = (maneuver["instruction"] as? String)?.trim() ?: "",
                    direction = mapped.first,
                    modifier = mapped.second,
                    distanceM = ((maneuver["length"] as? Number)?.toDouble() ?: 0.0) * 1000,
                    location = coordinates[pointIndex],
                    exitNumber = (maneuver["roundabout_exit_count"] as? Number)?.toInt(),
                    exitLabel = valhallaExitLabel(maneuver),
                )
            }
        }

        ValhallaParsedResponse(
            route = validate(
                RoutingParsedRoute(
                    polyline = coordinates,
                    steps = steps,
                    totalDistanceM = ((summary["length"] as? Number)?.toDouble() ?: 0.0) * 1000,
                    totalDurationS = (summary["time"] as? Number)?.toDouble() ?: 0.0,
                    fromAvoidanceRouter = true,
                ),
            ),
            summary = summary,
        )
    }

    fun parseOsrmRetimeLegs(body: String): List<OsrmRetimeLeg>? {
        return try {
            val data = parseObject(body)
            if (data["code"] != "Ok") return null
            val routes = data["routes"] as? List<*> ?: return null
            if (routes.isEmpty()) return null
            val route = routes.first() as Map<*, *>
            val legs = route["legs"] as? List<*> ?: return null
            legs.map { rawLeg ->
                val leg = rawLeg as Map<*, *>
                OsrmRetimeLeg(
                    distanceM = (leg["distance"] as? Number)?.toDouble(),
                    durationS = (leg["duration"] as? Number)?.toDouble(),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun validate(input: RoutingParsedRoute): RoutingParsedRoute {
        val cleanedSteps = sanitiseDecorations(coalescePassiveNameChanges(input.steps))
        val route = if (cleanedSteps !== input.steps) input.copy(steps = cleanedSteps) else input
        if (
            route.polyline.size < 2 ||
            route.steps.isEmpty() ||
            route.steps.size > MAX_ROUTE_STEPS ||
            !route.totalDistanceM.isFinite() ||
            route.totalDistanceM <= 0 ||
            route.totalDistanceM > 50_000_000 ||
            !route.totalDurationS.isFinite() ||
            route.totalDurationS < 0 ||
            route.totalDurationS > 366 * 86_400
        ) {
            fail("Malformed or incomplete route")
        }
        for (point in route.polyline) {
            if (
                !point.latitude.isFinite() ||
                !point.longitude.isFinite() ||
                point.latitude !in -90.0..90.0 ||
                point.longitude !in -180.0..180.0
            ) {
                fail("Route contains invalid coordinates")
            }
        }
        for (step in route.steps) {
            if (
                !step.distanceM.isFinite() ||
                step.distanceM < 0 ||
                step.instruction.length > 1000 ||
                step.direction.length > 100 ||
                step.modifier.length > 100 ||
                !step.location.latitude.isFinite() ||
                !step.location.longitude.isFinite() ||
                step.location.latitude !in -90.0..90.0 ||
                step.location.longitude !in -180.0..180.0
            ) {
                fail("Route contains an invalid maneuver")
            }
        }
        var previousDistance = -1.0
        for (entry in route.speedLimits) {
            if (
                !entry.distFromStartM.isFinite() ||
                entry.distFromStartM < previousDistance ||
                entry.distFromStartM > route.totalDistanceM ||
                (entry.speedKmh != null && entry.speedKmh !in 1..500)
            ) {
                fail("Route contains invalid speed limits")
            }
            previousDistance = entry.distFromStartM
        }
        return route
    }

    fun coalescePassiveNameChanges(steps: List<RoutingResponseStep>): List<RoutingResponseStep> {
        if (steps.size < 2) return steps
        var changed = false
        val output = mutableListOf<RoutingResponseStep>()
        for (step in steps) {
            val passiveRename = step.direction == "new name" &&
                (step.modifier.isEmpty() || step.modifier == "straight")
            if (!passiveRename || output.isEmpty()) {
                output += step
                continue
            }
            changed = true
            val previous = output.removeAt(output.lastIndex)
            output += previous.copy(distanceM = previous.distanceM + step.distanceM)
        }
        return if (changed) output else steps
    }

    fun sanitiseDecorations(steps: List<RoutingResponseStep>): List<RoutingResponseStep> {
        var changed = false
        val output = mutableListOf<RoutingResponseStep>()
        for (step in steps) {
            val badNumber = step.exitNumber != null && step.exitNumber !in 1..MAX_ROUNDABOUT_ARMS
            val badArmCount = step.roundaboutArmCount != null &&
                (
                    step.roundaboutArmCount !in 3..MAX_ROUNDABOUT_ARMS ||
                        (!badNumber && step.exitNumber != null && step.roundaboutArmCount < step.exitNumber)
                )
            val badLabel = step.exitLabel != null && step.exitLabel.length > 32
            if (!badNumber && !badArmCount && !badLabel) {
                output += step
                continue
            }
            changed = true
            output += RoutingResponseStep(
                instruction = step.instruction,
                direction = step.direction,
                modifier = step.modifier,
                distanceM = step.distanceM,
                location = step.location,
                exitNumber = if (badNumber) null else step.exitNumber,
                roundaboutArmCount = if (badArmCount) null else step.roundaboutArmCount,
                exitLabel = if (badLabel) null else step.exitLabel,
            )
        }
        return if (changed) output else steps
    }

    fun decodeValhallaPolyline(encoded: String): List<RoutingResponsePoint> {
        val points = mutableListOf<RoutingResponsePoint>()
        var index = 0
        var latitude = 0
        var longitude = 0

        fun readDelta(): Int {
            var result = 0
            var shift = 0
            var byte: Int
            do {
                if (index >= encoded.length || shift > 30) fail("Malformed Valhalla shape")
                byte = encoded[index++].code - 63
                if (byte !in 0..63) fail("Malformed Valhalla shape")
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            return if (result and 1 != 0) (result shr 1).inv() else result shr 1
        }

        while (index < encoded.length) {
            latitude += readDelta()
            longitude += readDelta()
            points += RoutingResponsePoint(latitude / 1e6, longitude / 1e6)
            checkPointCount(points.size)
        }
        return points
    }

    fun parseExitNumber(instruction: String): Int? {
        val numeric = Regex(
            """\b(1[0-2]|[1-9])(?:°|º|ª|st|nd|rd|th)""",
            RegexOption.IGNORE_CASE,
        ).find(instruction)
        if (numeric != null) return numeric.groupValues[1].toIntOrNull()
        val ordinals = linkedMapOf(
            "first" to 1,
            "prima" to 1,
            "première" to 1,
            "primera" to 1,
            "primeira" to 1,
            "second" to 2,
            "seconda" to 2,
            "deuxième" to 2,
            "segunda" to 2,
            "third" to 3,
            "terza" to 3,
            "troisième" to 3,
            "tercera" to 3,
            "terceira" to 3,
            "fourth" to 4,
            "quarta" to 4,
            "quatrième" to 4,
            "cuarta" to 4,
            "fifth" to 5,
            "quinta" to 5,
            "cinquième" to 5,
            "sixth" to 6,
            "sesta" to 6,
            "sixième" to 6,
            "sexta" to 6,
        )
        val lower = instruction.lowercase(Locale.ROOT)
        return ordinals.entries.firstOrNull { lower.contains(it.key) }?.value
    }

    private fun parseOsrmRoute(route: Map<*, *>, languageCode: String): RoutingParsedRoute {
        val legs = route["legs"] as? List<*>
        if (legs.isNullOrEmpty()) fail("OSRM response missing legs")
        val leg = legs.first() as Map<*, *>
        val geometry = route["geometry"] as Map<*, *>
        val rawCoordinates = geometry["coordinates"] as List<*>
        checkPointCount(rawCoordinates.size)
        val coordinates = rawCoordinates.map(::coordinate)

        val steps = mutableListOf<RoutingResponseStep>()
        for (rawStep in leg["steps"] as? List<*> ?: emptyList<Any?>()) {
            val step = rawStep as Map<*, *>
            val maneuver = step["maneuver"] as Map<*, *>
            val providerDirection = maneuver["type"] as? String ?: "straight"
            val providerModifier = maneuver["modifier"] as? String ?: ""
            val correctedModifier = correctedModifier(step, providerDirection, providerModifier)
            val resolvedDirection = if (
                providerDirection == "continue" &&
                correctedModifier != providerModifier &&
                correctedModifier != "straight"
            ) {
                "turn"
            } else {
                providerDirection
            }
            steps += RoutingResponseStep(
                instruction = buildInstruction(step, languageCode),
                direction = resolvedDirection,
                modifier = correctedModifier,
                distanceM = (step["distance"] as Number).toDouble(),
                location = coordinate(maneuver["location"]),
                exitNumber = (maneuver["exit"] as? Number)?.toInt(),
                exitLabel = (step["exits"] as? String)?.trim(),
                roadName = (step["name"] as? String ?: "").trim(),
                roadRef = (step["ref"] as? String ?: "").trim(),
            )
        }
        return validate(
            RoutingParsedRoute(
                polyline = coordinates,
                steps = steps,
                totalDistanceM = (route["distance"] as Number).toDouble(),
                totalDurationS = (route["duration"] as Number).toDouble(),
            ),
        )
    }

    private fun buildInstruction(step: Map<*, *>, languageCode: String): String {
        val maneuver = step["maneuver"] as Map<*, *>
        val type = maneuver["type"] as? String ?: ""
        val providerModifier = maneuver["modifier"] as? String ?: ""
        val modifier = correctedModifier(step, type, providerModifier)
        val name = (step["name"] as? String ?: "").trim()
        val ref = (step["ref"] as? String ?: "").trim()
        val refFirst = ref.split(Regex("[;,]")).first().trim()
        val roadName = refFirst.ifEmpty { name }
        fun phrase(key: String): String = NavigationPhrases.phrase(languageCode, key)
        val road = if (roadName.isEmpty()) "" else phrase("on") + roadName
        fun withRoad(key: String): String = phrase(key) + road
        fun turnFor(value: String): String? = when (value) {
            "left" -> withRoad("turnLeft")
            "right" -> withRoad("turnRight")
            "slight left" -> withRoad("keepLeft")
            "slight right" -> withRoad("keepRight")
            "sharp left" -> withRoad("sharpLeft")
            "sharp right" -> withRoad("sharpRight")
            "uturn" -> withRoad("uturn")
            else -> null
        }
        return when (type) {
            "depart" -> withRoad("depart")
            "arrive" -> phrase("arrive")
            "turn" -> turnFor(modifier) ?: withRoad("continueStraight")
            "new name" -> turnFor(modifier) ?: withRoad("continueOn")
            "continue" -> turnFor(modifier) ?: withRoad("continueStraight")
            "merge" -> withRoad("merge")
            "on ramp" -> when (modifier) {
                "left" -> withRoad("rampLeft")
                "right" -> withRoad("rampRight")
                else -> withRoad("takeRamp")
            }

            "off ramp" -> {
                val exits = (step["exits"] as? String)?.trim()
                val label = exits?.takeIf(String::isNotEmpty) ?: refFirst.takeIf(String::isNotEmpty)
                if (label != null) {
                    phrase("exitLabelled").replace("{label}", label) + road
                } else {
                    withRoad("exitPlain")
                }
            }

            "fork" -> withRoad(if (modifier.contains("left")) "forkLeft" else "forkRight")
            "end of road" -> withRoad(if (modifier.contains("left")) "endLeft" else "endRight")
            "roundabout", "rotary" -> {
                val exit = (maneuver["exit"] as? Number)?.toInt() ?: 1
                val key = if (type == "rotary") "rotary" else "roundabout"
                phrase(key).replace("{n}", exit.toString()) + road
            }

            else -> withRoad("continueStraight")
        }
    }

    private fun correctedModifier(
        step: Map<*, *>,
        type: String,
        providerModifier: String,
    ): String {
        if (type == "roundabout" || type == "rotary") return providerModifier
        val intersections = step["intersections"] as? List<*>
        val first = intersections?.firstOrNull { it is Map<*, *> } as? Map<*, *>
        val bearings = (first?.get("bearings") as? List<*>)
            ?.filterIsInstance<Number>()
            ?.map(Number::toDouble)
        val inIndex = (first?.get("in") as? Number)?.toInt()
        val outIndex = (first?.get("out") as? Number)?.toInt()
        if (
            bearings == null ||
            inIndex == null ||
            outIndex == null ||
            inIndex !in bearings.indices ||
            outIndex !in bearings.indices
        ) {
            return providerModifier
        }
        val inboundTravelBearing = (bearings[inIndex] + 180.0) % 360.0
        var delta = floorMod(bearings[outIndex] - inboundTravelBearing, 360.0)
        if (delta > 180) delta -= 360
        val magnitude = abs(delta)
        if (magnitude < 18 || magnitude > 160) return providerModifier
        return if (delta < 0) {
            if (magnitude > 110) "sharp left" else "left"
        } else {
            if (magnitude > 110) "sharp right" else "right"
        }
    }

    private fun valhallaManeuver(type: Int): Pair<String, String> = when (type) {
        1 -> "depart" to ""
        2 -> "depart" to "right"
        3 -> "depart" to "left"
        4 -> "arrive" to ""
        5 -> "arrive" to "right"
        6 -> "arrive" to "left"
        7 -> "new name" to "straight"
        8, 22 -> "continue" to "straight"
        9 -> "turn" to "slight right"
        10 -> "turn" to "right"
        11 -> "turn" to "sharp right"
        12 -> "turn" to "uturn right"
        13 -> "turn" to "uturn left"
        14 -> "turn" to "sharp left"
        15 -> "turn" to "left"
        16 -> "turn" to "slight left"
        17 -> "on ramp" to "straight"
        18 -> "on ramp" to "right"
        19 -> "on ramp" to "left"
        20 -> "off ramp" to "right"
        21 -> "off ramp" to "left"
        23 -> "fork" to "right"
        24 -> "fork" to "left"
        25 -> "merge" to ""
        26, 27 -> "roundabout" to ""
        28, 29 -> "ferry" to "straight"
        else -> "continue" to "straight"
    }

    private fun orsManeuver(type: Int): Pair<String, String> = when (type) {
        0 -> "turn" to "left"
        1 -> "turn" to "right"
        2 -> "turn" to "sharp left"
        3 -> "turn" to "sharp right"
        4 -> "turn" to "slight left"
        5 -> "turn" to "slight right"
        6, 8 -> "continue" to "straight"
        7 -> "roundabout" to ""
        9 -> "turn" to "uturn left"
        10 -> "arrive" to ""
        11 -> "depart" to ""
        12 -> "fork" to "left"
        13 -> "fork" to "right"
        else -> "continue" to "straight"
    }

    private fun graphHopperManeuver(sign: Int): Pair<String, String> = when (sign) {
        -8 -> "turn" to "uturn left"
        -7 -> "fork" to "left"
        -3 -> "turn" to "sharp left"
        -2 -> "turn" to "left"
        -1 -> "turn" to "slight left"
        0, 5 -> "continue" to "straight"
        1 -> "turn" to "slight right"
        2 -> "turn" to "right"
        3 -> "turn" to "sharp right"
        4 -> "arrive" to ""
        6 -> "roundabout" to ""
        7 -> "fork" to "right"
        8 -> "turn" to "uturn right"
        else -> "continue" to "straight"
    }

    private fun valhallaExitLabel(maneuver: Map<*, *>): String? {
        val sign = maneuver["sign"] as? Map<*, *>
        val elements = sign?.get("exit_number_elements") as? List<*>
        val first = elements?.firstOrNull() as? Map<*, *> ?: return null
        return first["text"]?.toString()?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun coordinate(raw: Any?): RoutingResponsePoint {
        val coordinate = raw as List<*>
        return RoutingResponsePoint(
            latitude = (coordinate[1] as Number).toDouble(),
            longitude = (coordinate[0] as Number).toDouble(),
        )
    }

    private fun intValue(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        null -> null
        else -> value.toString().toIntOrNull()
    }

    private fun parseObject(body: String): Map<String, Any?> {
        val decoded = BoundedJsonParser(body).parse() as? Map<*, *>
            ?: fail("Malformed routing response")
        val result = linkedMapOf<String, Any?>()
        for ((key, value) in decoded) {
            if (key !is String) fail("Malformed routing response")
            result[key] = value
        }
        return result
    }

    private fun checkPointCount(count: Int) {
        if (count > MAX_ROUTE_POINTS) fail("Route contains too many points")
    }

    private inline fun <T> wrapErrors(block: () -> T): T = try {
        block()
    } catch (error: RoutingResponseException) {
        throw error
    } catch (error: Exception) {
        throw RoutingResponseException(error.toString())
    }

    private fun fail(message: String): Nothing = throw RoutingResponseException(message)

    private fun floorMod(value: Double, divisor: Double): Double =
        ((value % divisor) + divisor) % divisor

    /** Mirrors latlong2's default rounded WGS-84 Vincenty distance. */
    private fun roundedVincentyDistance(
        first: RoutingResponsePoint,
        second: RoutingResponsePoint,
    ): Double {
        val equatorRadius = 6_378_137.0
        val polarRadius = 6_356_752.314245
        val flattening = 1 / 298.257223563
        val latitude1 = Math.toRadians(first.latitude)
        val latitude2 = Math.toRadians(second.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val u1 = atan((1 - flattening) * tan(latitude1))
        val u2 = atan((1 - flattening) * tan(latitude2))
        val sinU1 = sin(u1)
        val cosU1 = cos(u1)
        val sinU2 = sin(u2)
        val cosU2 = cos(u2)
        var lambda = longitudeDelta
        var iterations = 200
        var sinSigma: Double
        var cosSigma: Double
        var sigma: Double
        var sinAlpha: Double
        var cosSqAlpha: Double
        var cos2SigmaM: Double
        do {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt(
                (cosU2 * sinLambda) * (cosU2 * sinLambda) +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) *
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda),
            )
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha
            if (cos2SigmaM.isNaN()) cos2SigmaM = 0.0
            val c = flattening / 16 * cosSqAlpha * (4 + flattening * (4 - 3 * cosSqAlpha))
            val previousLambda = lambda
            lambda = longitudeDelta +
                (1 - c) * flattening * sinAlpha *
                (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
            if (abs(lambda - previousLambda) <= 1e-12) break
        } while (--iterations > 0)
        if (iterations == 0) error("Distance calculation failed to converge")
        val uSquared = cosSqAlpha *
            (equatorRadius * equatorRadius - polarRadius * polarRadius) /
            (polarRadius * polarRadius)
        val a = 1 + uSquared / 16_384 *
            (4096 + uSquared * (-768 + uSquared * (320 - 175 * uSquared)))
        val b = uSquared / 1024 *
            (256 + uSquared * (-128 + uSquared * (74 - 47 * uSquared)))
        val deltaSigma = b * sinSigma *
            (
                cos2SigmaM + b / 4 *
                    (
                        cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                            b / 6 * cos2SigmaM *
                            (-3 + 4 * sinSigma * sinSigma) *
                            (-3 + 4 * cos2SigmaM * cos2SigmaM)
                    )
            )
        val distance = polarRadius * a * (sigma - deltaSigma)
        return floor(distance + 0.5)
    }
}
