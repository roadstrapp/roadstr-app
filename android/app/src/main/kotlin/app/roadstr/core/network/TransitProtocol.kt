package app.roadstr.core.network

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

data class TransitRequestPoint(
    val latitude: Double,
    val longitude: Double,
)

data class TransitProviderRequest(
    val uri: String,
    val headers: Map<String, String>,
)

enum class TransitMode(
    val wireName: String,
    val isTransit: Boolean,
) {
    Walk("WALK", false),
    Bike("BIKE", false),
    Car("CAR", false),
    Tram("TRAM", true),
    Subway("SUBWAY", true),
    Metro("METRO", true),
    Suburban("SUBURBAN", true),
    RegionalRail("REGIONAL_RAIL", true),
    RegionalFastRail("REGIONAL_FAST_RAIL", true),
    LongDistance("LONG_DISTANCE", true),
    HighspeedRail("HIGHSPEED_RAIL", true),
    NightRail("NIGHT_RAIL", true),
    Rail("RAIL", true),
    Bus("BUS", true),
    Coach("COACH", true),
    Ferry("FERRY", true),
    Airplane("AIRPLANE", true),
    Funicular("FUNICULAR", true),
    AerialLift("AERIAL_LIFT", true),
    OnDemand("ODM", true),
    Other("OTHER", true),
    ;

    companion object {
        fun fromWire(value: Any?): TransitMode {
            val normalized = (value as? String)?.trim()?.uppercase() ?: return Other
            return entries.firstOrNull { it.wireName == normalized } ?: Other
        }
    }
}

data class TransitResponsePoint(
    val latitude: Double,
    val longitude: Double,
)

data class TransitLeg(
    val mode: TransitMode,
    val distanceMeters: Double?,
    val durationSeconds: Long,
    val startTime: Instant,
    val endTime: Instant,
    val fromName: String?,
    val toName: String?,
    val routeShortName: String?,
    val routeLongName: String?,
    val agencyName: String?,
    val headsign: String?,
    val routeColorArgb: Long?,
    val routeTextColorArgb: Long?,
    val realTime: Boolean,
    val geometry: List<TransitResponsePoint>,
) {
    val displayLine: String?
        get() = routeShortName ?: routeLongName
}

data class TransitBoarding(
    val name: String,
    val time: Instant,
)

data class TransitItinerary(
    val durationSeconds: Long,
    val startTime: Instant,
    val endTime: Instant,
    val transfers: Int,
    val legs: List<TransitLeg>,
) {
    val transitLegs: List<TransitLeg>
        get() = legs.filter { it.mode.isTransit }

    val walkingDistanceMeters: Double
        get() = legs.asSequence()
            .filter { it.mode == TransitMode.Walk }
            .sumOf { it.distanceMeters ?: 0.0 }

    val boarding: TransitBoarding?
        get() = legs.firstOrNull { it.mode.isTransit }?.let { leg ->
            leg.fromName?.let { TransitBoarding(it, leg.startTime) }
        }

    val accessWalkSeconds: Long
        get() = legs.takeWhile { !it.mode.isTransit }.sumOf(TransitLeg::durationSeconds)

    val isWalkOnly: Boolean
        get() = transitLegs.isEmpty()

    val isFullyRealTime: Boolean
        get() = transitLegs.let { legs -> legs.isNotEmpty() && legs.all(TransitLeg::realTime) }
}

sealed interface TransitPlanResponse

data class TransitParsedPlan(val itineraries: List<TransitItinerary>) : TransitPlanResponse

data object TransitParsedUnavailable : TransitPlanResponse

class TransitResponseException(
    val responseMessage: String,
) : RuntimeException(responseMessage)

/** Socket-free request and response oracle for Transitous public-transport plans. */
object TransitProtocol {
    const val DEFAULT_ENDPOINT = "https://api.transitous.org/api/v1/plan"
    const val REQUESTED_ITINERARIES = 3
    const val MAX_ACCESS_WALK_SECONDS = 1_800
    const val MAX_RESPONSE_ITINERARIES = 16
    const val MAX_LEGS_PER_ITINERARY = 128
    const val MAX_GEOMETRY_POINTS = 250_000
    const val MAX_ENCODED_GEOMETRY_CHARS = 1_000_000
    const val MAX_TEXT_CHARS = 1_000

    private val userAgentHeaders = mapOf("User-Agent" to "Roadstr/1.0 (navigation app)")
    private val instantSeconds = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        .withZone(ZoneOffset.UTC)

    fun planRequest(
        from: TransitRequestPoint,
        to: TransitRequestPoint,
        departure: Instant,
        endpoint: String = DEFAULT_ENDPOINT,
    ): TransitProviderRequest {
        requireValidPoint(from)
        requireValidPoint(to)
        val parsedEndpoint = validateEndpoint(endpoint)
        val query = listOf(
            "fromPlace" to "${dartDouble(from.latitude)},${dartDouble(from.longitude)}",
            "toPlace" to "${dartDouble(to.latitude)},${dartDouble(to.longitude)}",
            "time" to dartUtcIso8601(departure),
            "numItineraries" to REQUESTED_ITINERARIES.toString(),
            "maxPreTransitTime" to MAX_ACCESS_WALK_SECONDS.toString(),
            "maxPostTransitTime" to MAX_ACCESS_WALK_SECONDS.toString(),
        ).joinToString("&") { (name, value) -> "$name=${encodeQueryComponent(value)}" }
        val fragment = parsedEndpoint.rawFragment?.let { "#$it" } ?: ""
        val authority = parsedEndpoint.rawAuthority
        val path = parsedEndpoint.rawPath.orEmpty()
        val uri = "${parsedEndpoint.scheme}://$authority$path?$query$fragment"
        return TransitProviderRequest(uri = uri, headers = userAgentHeaders)
    }

    fun parsePlan(body: String): TransitPlanResponse = wrapErrors {
        val decoded = BoundedJsonParser(body).parse() as? Map<*, *>
            ?: fail("Unexpected transit response shape")
        val rawItineraries = decoded["itineraries"] as? List<*>
            ?: return@wrapErrors TransitParsedUnavailable
        if (rawItineraries.size > MAX_RESPONSE_ITINERARIES) {
            fail("Transit response has too many itineraries")
        }

        val parsedItineraries = rawItineraries.mapNotNull { raw ->
            (raw as? Map<*, *>)?.let(::parseItinerary)
        }
        var pointCount = 0L
        parsedItineraries.forEach { itinerary ->
            itinerary.legs.forEach { leg ->
                pointCount += leg.geometry.size.toLong()
                if (pointCount > MAX_GEOMETRY_POINTS.toLong()) {
                    fail("Transit plan has too many geometry points")
                }
            }
        }
        val itineraries = parsedItineraries.filterNot(TransitItinerary::isWalkOnly)
            .sortedBy(TransitItinerary::durationSeconds)
        if (itineraries.isEmpty()) TransitParsedUnavailable else TransitParsedPlan(itineraries)
    }

    /** Google encoded-polyline decoder with the same partial-on-truncation behavior as Dart. */
    fun decodePolyline(encoded: String, precision: Int): List<TransitResponsePoint> {
        if (encoded.isEmpty() || precision !in 0..15) return emptyList()
        if (encoded.length > MAX_ENCODED_GEOMETRY_CHARS) {
            fail("Transit geometry is too large")
        }
        var factor = 1.0
        repeat(precision) { factor *= 10.0 }
        val points = mutableListOf<TransitResponsePoint>()
        var index = 0
        var latitude = 0L
        var longitude = 0L
        while (index < encoded.length) {
            val latitudeDelta = decodeSignedValue(encoded, index) ?: return points
            latitude = safeAdd(latitude, latitudeDelta.first) ?: return points
            index = latitudeDelta.second
            val longitudeDelta = decodeSignedValue(encoded, index) ?: return points
            longitude = safeAdd(longitude, longitudeDelta.first) ?: return points
            index = longitudeDelta.second
            if (points.size >= MAX_GEOMETRY_POINTS) fail("Transit geometry has too many points")
            points += TransitResponsePoint(latitude / factor, longitude / factor)
        }
        return points
    }

    private fun parseItinerary(raw: Map<*, *>): TransitItinerary? {
        val start = parseTime(raw["startTime"]) ?: return null
        val end = parseTime(raw["endTime"]) ?: return null
        val rawLegs = raw["legs"] as? List<*> ?: return null
        if (rawLegs.size > MAX_LEGS_PER_ITINERARY) fail("Transit itinerary has too many legs")
        val legs = rawLegs.mapNotNull { value ->
            (value as? Map<*, *>)?.let(::parseLeg)
        }
        if (legs.isEmpty()) return null
        return TransitItinerary(
            durationSeconds = asLong(raw["duration"]) ?: 0,
            startTime = start,
            endTime = end,
            transfers = (asLong(raw["transfers"]) ?: 0)
                .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                .toInt(),
            legs = legs,
        )
    }

    private fun parseLeg(raw: Map<*, *>): TransitLeg? {
        val start = parseTime(raw["startTime"]) ?: return null
        val end = parseTime(raw["endTime"]) ?: return null
        val geometry = (raw["legGeometry"] as? Map<*, *>)?.let { value ->
            val encoded = value["points"] as? String ?: return@let emptyList()
            val precision = (asLong(value["precision"]) ?: 5).toInt()
            decodePolyline(encoded, precision).takeIf { points ->
                points.all(::isValidPoint)
            } ?: emptyList()
        } ?: emptyList()
        return TransitLeg(
            mode = TransitMode.fromWire(raw["mode"]),
            durationSeconds = asLong(raw["duration"]) ?: 0,
            startTime = start,
            endTime = end,
            distanceMeters = (raw["distance"] as? Number)?.toDouble()?.takeIf(Double::isFinite),
            fromName = placeName(raw["from"]),
            toName = placeName(raw["to"]),
            routeShortName = text(raw["routeShortName"]),
            routeLongName = text(raw["routeLongName"]),
            agencyName = text(raw["agencyName"]),
            headsign = text(raw["headsign"]),
            routeColorArgb = color(raw["routeColor"]),
            routeTextColorArgb = color(raw["routeTextColor"]),
            realTime = raw["realTime"] == true,
            geometry = geometry,
        )
    }

    private fun parseTime(value: Any?): Instant? {
        val text = value as? String ?: return null
        return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(text) }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant()
            }.getOrNull()
    }

    private fun placeName(value: Any?): String? {
        val name = text((value as? Map<*, *>)?.get("name")) ?: return null
        return name.takeUnless { it == "START" || it == "END" }
    }

    private fun text(value: Any?): String? {
        val trimmed = (value as? String)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return trimmed.take(MAX_TEXT_CHARS)
    }

    private fun color(value: Any?): Long? {
        val raw = value as? String ?: return null
        val hex = if (raw.startsWith('#')) raw.drop(1) else raw
        if (hex.length != 6) return null
        val parsed = hex.toLongOrNull(16) ?: return null
        return 0xFF00_0000L or parsed
    }

    private fun asLong(value: Any?): Long? {
        val number = (value as? Number)?.toDouble()?.takeIf(Double::isFinite) ?: return null
        if (number < Long.MIN_VALUE.toDouble() || number > Long.MAX_VALUE.toDouble()) return null
        return if (number >= 0) floor(number + 0.5).toLong() else ceil(number - 0.5).toLong()
    }

    private fun decodeSignedValue(encoded: String, start: Int): Pair<Long, Int>? {
        var index = start
        var shift = 0
        var result = 0L
        while (true) {
            if (index >= encoded.length) return null
            val chunk = encoded[index++].code - 63
            if (chunk !in 0..63) return null
            result = result or ((chunk and 0x1F).toLong() shl shift)
            if (chunk < 0x20) break
            shift += 5
            if (shift > 30) return null
        }
        val value = if ((result and 1L) != 0L) -(result shr 1) - 1 else result shr 1
        return value to index
    }

    private fun safeAdd(left: Long, right: Long): Long? = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }

    private fun validateEndpoint(value: String): URI {
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid transit endpoint")
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        require(!host.isNullOrEmpty() && uri.rawUserInfo == null) { "Invalid transit endpoint" }
        require(
            scheme == "https" ||
                (scheme == "http" && host in RoutingEndpointPolicy.cleartextAllowedHosts),
        ) { "Transit endpoint must use HTTPS or loopback HTTP" }
        return uri
    }

    private fun requireValidPoint(point: TransitRequestPoint) {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
            "Transit latitude is outside the WGS84 range"
        }
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
            "Transit longitude is outside the WGS84 range"
        }
    }

    private fun isValidPoint(point: TransitResponsePoint): Boolean =
        point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
            point.longitude.isFinite() && point.longitude in -180.0..180.0

    private fun dartDouble(value: Double): String {
        if (value == 0.0) return if (value.toRawBits() < 0) "-0.0" else "0.0"
        val decimal = BigDecimal(value.toString()).stripTrailingZeros()
        val absolute = decimal.abs()
        if (absolute >= BigDecimal("0.000001") && absolute < BigDecimal("1e21")) {
            val plain = decimal.toPlainString()
            return if (decimal.scale() <= 0) "$plain.0" else plain
        }
        return decimal.toString().lowercase()
    }

    private fun dartUtcIso8601(value: Instant): String {
        val milliseconds = value.nano / 1_000_000
        val microseconds = (value.nano / 1_000) % 1_000
        return buildString {
            append(instantSeconds.format(value))
            append('.')
            append(milliseconds.toString().padStart(3, '0'))
            if (microseconds != 0) append(microseconds.toString().padStart(3, '0'))
            append('Z')
        }
    }

    private fun encodeQueryComponent(value: String): String = buildString {
        for (signedByte in value.toByteArray(Charsets.UTF_8)) {
            val byte = signedByte.toInt() and 0xFF
            if (
                byte in 'a'.code..'z'.code ||
                byte in 'A'.code..'Z'.code ||
                byte in '0'.code..'9'.code ||
                byte.toChar() in "-._~"
            ) {
                append(byte.toChar())
            } else {
                append('%')
                append(HEX[byte ushr 4])
                append(HEX[byte and 0x0F])
            }
        }
    }

    private inline fun <T> wrapErrors(block: () -> T): T = try {
        block()
    } catch (error: TransitResponseException) {
        throw error
    } catch (_: Exception) {
        throw TransitResponseException("Malformed transit response")
    }

    private fun fail(message: String): Nothing = throw TransitResponseException(message)

    private const val HEX = "0123456789ABCDEF"
}
