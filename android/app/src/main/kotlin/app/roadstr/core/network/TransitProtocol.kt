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
    val body: String? = null,
)

/** Provider identity kept with every result so foreign identifiers never collide. */
data class TransitProvider(
    val id: String,
    val displayName: String,
    val attributionUrl: String?,
)

data class TransitScopedId(
    val providerId: String,
    val value: String,
)

enum class TransitAccessibility {
    Accessible,
    NotAccessible,
    Unknown,
}

enum class TransitRealtimeState {
    Scheduled,
    Partial,
    Realtime,
}

data class TransitAlert(
    val code: String?,
    val header: String,
    val description: String?,
    val severity: String?,
    val effect: String?,
    val url: String?,
)

data class TransitStop(
    val id: TransitScopedId?,
    val name: String?,
    val position: TransitResponsePoint?,
    val timezone: String?,
    val scheduledArrival: Instant?,
    val expectedArrival: Instant?,
    val actualArrival: Instant? = null,
    val scheduledDeparture: Instant?,
    val expectedDeparture: Instant?,
    val actualDeparture: Instant? = null,
    val scheduledPlatform: String?,
    val expectedPlatform: String?,
    val cancelled: Boolean,
    val alerts: List<TransitAlert>,
)

enum class TransitMode(
    val wireName: String,
    val isTransit: Boolean,
) {
    Walk("WALK", false),
    Bike("BIKE", false),
    Rental("RENTAL", false),
    Car("CAR", false),
    HeavyGoodsVehicle("HGV", false),
    CarParking("CAR_PARKING", false),
    CarDropoff("CAR_DROPOFF", false),
    RideSharing("RIDE_SHARING", true),
    Flex("FLEX", true),
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
    CableCar("CABLE_CAR", true),
    OnDemand("ODM", true),
    Other("OTHER", true),
    ;

    companion object {
        fun fromWire(value: Any?): TransitMode {
            val normalized = (value as? String)?.trim()?.uppercase() ?: return Other
            if (normalized == "AREAL_LIFT") return AerialLift
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
    val from: TransitStop? = null,
    val to: TransitStop? = null,
    val scheduledStartTime: Instant = startTime,
    val scheduledEndTime: Instant = endTime,
    val actualStartTime: Instant? = null,
    val actualEndTime: Instant? = null,
    val routeId: TransitScopedId? = null,
    val tripId: TransitScopedId? = null,
    val agencyId: TransitScopedId? = null,
    val displayName: String? = null,
    val intermediateStops: List<TransitStop> = emptyList(),
    val cancelled: Boolean = false,
    val scheduled: Boolean? = null,
    val accessibility: TransitAccessibility = TransitAccessibility.Unknown,
    val alerts: List<TransitAlert> = emptyList(),
    val sourceReference: String? = null,
) {
    val displayLine: String?
        get() = routeShortName ?: displayName ?: routeLongName

    val departureDelaySeconds: Long?
        get() = if (realTime) startTime.epochSecond - scheduledStartTime.epochSecond else null

    val arrivalDelaySeconds: Long?
        get() = if (realTime) endTime.epochSecond - scheduledEndTime.epochSecond else null
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
    val id: TransitScopedId? = null,
    val providerRank: Int = 0,
) {
    val transitLegs: List<TransitLeg>
        get() = legs.filter { it.mode.isTransit }

    val walkingDistanceMeters: Double
        get() = legs.asSequence()
            .filter { it.mode == TransitMode.Walk }
            .sumOf { it.distanceMeters ?: 0.0 }

    val walkingDurationSeconds: Long
        get() = legs.asSequence()
            .filter { it.mode == TransitMode.Walk }
            .sumOf(TransitLeg::durationSeconds)

    /** Time not spent moving, including timetable waits between legs. */
    val waitingDurationSeconds: Long
        get() = (durationSeconds - legs.sumOf(TransitLeg::durationSeconds)).coerceAtLeast(0)

    /** Null means at least one provider leg omitted its distance. */
    val distanceMeters: Double?
        get() = legs.map(TransitLeg::distanceMeters)
            .takeIf { values -> values.all { it != null } }
            ?.sumOf { requireNotNull(it) }

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

    val realtimeState: TransitRealtimeState
        get() = when {
            transitLegs.isEmpty() || transitLegs.none(TransitLeg::realTime) -> TransitRealtimeState.Scheduled
            transitLegs.all(TransitLeg::realTime) -> TransitRealtimeState.Realtime
            else -> TransitRealtimeState.Partial
        }

    val alerts: List<TransitAlert>
        get() = legs.flatMap(TransitLeg::alerts).distinct()

    val cancelled: Boolean
        get() = transitLegs.any(TransitLeg::cancelled)
}

sealed interface TransitPlanResponse

data class TransitParsedPlan(
    val itineraries: List<TransitItinerary>,
    val provider: TransitProvider = TransitProtocol.MOTIS_PROVIDER,
    val searchedAt: Instant? = null,
    val origin: TransitRequestPoint? = null,
    val destination: TransitRequestPoint? = null,
    val requestedDeparture: Instant? = null,
) : TransitPlanResponse

data object TransitParsedUnavailable : TransitPlanResponse

class TransitResponseException(
    val responseMessage: String,
) : RuntimeException(responseMessage)

/** Socket-free request and response oracle for Transitous public-transport plans. */
object TransitProtocol {
    const val DEFAULT_ENDPOINT = "https://api.transitous.org/api/v6/plan"
    const val REQUESTED_ITINERARIES = 3
    const val MAX_ACCESS_WALK_SECONDS = 1_800
    const val MAX_RESPONSE_ITINERARIES = 16
    const val MAX_LEGS_PER_ITINERARY = 128
    const val MAX_GEOMETRY_POINTS = 250_000
    const val MAX_ENCODED_GEOMETRY_CHARS = 1_000_000
    const val MAX_TEXT_CHARS = 1_000

    val MOTIS_PROVIDER = TransitProvider(
        id = "motis",
        displayName = "MOTIS",
        attributionUrl = null,
    )
    val TRANSITOUS_PROVIDER = TransitProvider(
        id = "transitous",
        displayName = "Transitous",
        attributionUrl = "https://transitous.org/sources/",
    )
    const val USER_AGENT = "Roadstr/1.0.1 (+https://github.com/roadstrapp/roadstr-app)"
    private val userAgentHeaders = mapOf("User-Agent" to USER_AGENT)
    private val instantSeconds = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        .withZone(ZoneOffset.UTC)

    fun planRequest(
        from: TransitRequestPoint,
        to: TransitRequestPoint,
        departure: Instant,
        endpoint: String = DEFAULT_ENDPOINT,
        languageCode: String? = null,
    ): TransitProviderRequest {
        requireValidPoint(from)
        requireValidPoint(to)
        val parsedEndpoint = validateEndpoint(endpoint)
        val query = buildList {
            add(
            "fromPlace" to "${dartDouble(from.latitude)},${dartDouble(from.longitude)}",
            )
            add(
            "toPlace" to "${dartDouble(to.latitude)},${dartDouble(to.longitude)}",
            )
            add(
            "time" to dartUtcIso8601(departure),
            )
            add(
            "numItineraries" to REQUESTED_ITINERARIES.toString(),
            )
            add(
            "maxPreTransitTime" to MAX_ACCESS_WALK_SECONDS.toString(),
            )
            add(
            "maxPostTransitTime" to MAX_ACCESS_WALK_SECONDS.toString(),
            )
            add("realtimeMode" to "REALTIME")
            add("detailedTransfers" to "true")
            languageCode?.trim()?.takeIf(String::isNotEmpty)?.take(35)?.let {
                add("language" to it)
            }
        }.joinToString("&") { (name, value) -> "$name=${encodeQueryComponent(value)}" }
        val fragment = parsedEndpoint.rawFragment?.let { "#$it" } ?: ""
        val authority = parsedEndpoint.rawAuthority
        val path = parsedEndpoint.rawPath.orEmpty()
        val uri = "${parsedEndpoint.scheme}://$authority$path?$query$fragment"
        return TransitProviderRequest(uri = uri, headers = userAgentHeaders)
    }

    fun parsePlan(
        body: String,
        provider: TransitProvider = MOTIS_PROVIDER,
        searchedAt: Instant? = null,
        origin: TransitRequestPoint? = null,
        destination: TransitRequestPoint? = null,
        requestedDeparture: Instant? = null,
    ): TransitPlanResponse = wrapErrors {
        val decoded = BoundedJsonParser(body).parse() as? Map<*, *>
            ?: fail("Unexpected transit response shape")
        val rawItineraries = decoded["itineraries"] as? List<*>
            ?: return@wrapErrors TransitParsedUnavailable
        if (rawItineraries.size > MAX_RESPONSE_ITINERARIES) {
            fail("Transit response has too many itineraries")
        }

        val parsedItineraries = rawItineraries.mapIndexedNotNull { index, raw ->
            (raw as? Map<*, *>)?.let { parseItinerary(it, provider, index) }
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
        if (itineraries.isEmpty()) {
            TransitParsedUnavailable
        } else {
            TransitParsedPlan(
                itineraries = itineraries,
                provider = provider,
                searchedAt = searchedAt,
                origin = origin,
                destination = destination,
                requestedDeparture = requestedDeparture,
            )
        }
    }

    /** Parses the bare itinerary returned by MOTIS trip/refresh endpoints. */
    fun parseItinerary(
        body: String,
        provider: TransitProvider = MOTIS_PROVIDER,
    ): TransitItinerary = wrapErrors {
        val decoded = BoundedJsonParser(body).parse() as? Map<*, *>
            ?: fail("Unexpected transit itinerary shape")
        parseItinerary(decoded, provider, providerRank = 0)
            ?: fail("Transit itinerary is incomplete")
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

    private fun parseItinerary(
        raw: Map<*, *>,
        provider: TransitProvider,
        providerRank: Int,
    ): TransitItinerary? {
        val start = parseTime(raw["startTime"]) ?: return null
        val end = parseTime(raw["endTime"]) ?: return null
        val rawLegs = raw["legs"] as? List<*> ?: return null
        if (rawLegs.size > MAX_LEGS_PER_ITINERARY) fail("Transit itinerary has too many legs")
        val legs = rawLegs.mapNotNull { value ->
            (value as? Map<*, *>)?.let { parseLeg(it, provider) }
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
            id = scopedId(provider, raw["id"]),
            providerRank = providerRank,
        )
    }

    private fun parseLeg(raw: Map<*, *>, provider: TransitProvider): TransitLeg? {
        val start = parseTime(raw["startTime"]) ?: return null
        val end = parseTime(raw["endTime"]) ?: return null
        val from = parseStop(raw["from"], provider)
        val to = parseStop(raw["to"], provider)
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
            fromName = from?.name,
            toName = to?.name,
            routeShortName = text(raw["routeShortName"]),
            routeLongName = text(raw["routeLongName"]),
            agencyName = text(raw["agencyName"]),
            headsign = text(raw["headsign"]),
            routeColorArgb = color(raw["routeColor"]),
            routeTextColorArgb = color(raw["routeTextColor"]),
            realTime = raw["realTime"] == true,
            geometry = geometry,
            from = from,
            to = to,
            scheduledStartTime = parseTime(raw["scheduledStartTime"]) ?: start,
            scheduledEndTime = parseTime(raw["scheduledEndTime"]) ?: end,
            routeId = scopedId(provider, raw["routeId"]),
            tripId = scopedId(provider, raw["tripId"]),
            agencyId = scopedId(provider, raw["agencyId"]),
            displayName = text(raw["displayName"]),
            intermediateStops = parseStops(raw["intermediateStops"], provider),
            cancelled = raw["cancelled"] == true,
            scheduled = raw["scheduled"] as? Boolean,
            accessibility = accessibility(raw["wheelchairAccessible"]),
            alerts = parseAlerts(raw["alerts"]),
            sourceReference = text(raw["source"]),
        )
    }

    private fun parseStops(value: Any?, provider: TransitProvider): List<TransitStop> {
        val values = value as? List<*> ?: return emptyList()
        return values.take(MAX_LEGS_PER_ITINERARY).mapNotNull { parseStop(it, provider) }
    }

    private fun parseStop(value: Any?, provider: TransitProvider): TransitStop? {
        val raw = value as? Map<*, *> ?: return null
        val rawName = text(raw["name"])
        val name = rawName?.takeUnless { it == "START" || it == "END" }
        val latitude = (raw["lat"] as? Number)?.toDouble()?.takeIf(Double::isFinite)
        val longitude = (raw["lon"] as? Number)?.toDouble()?.takeIf(Double::isFinite)
        val position = if (latitude != null && longitude != null) {
            TransitResponsePoint(latitude, longitude).takeIf(::isValidPoint)
        } else {
            null
        }
        return TransitStop(
            id = scopedId(provider, raw["stopId"]),
            name = name,
            position = position,
            timezone = text(raw["tz"]),
            scheduledArrival = parseTime(raw["scheduledArrival"]),
            expectedArrival = parseTime(raw["arrival"]),
            scheduledDeparture = parseTime(raw["scheduledDeparture"]),
            expectedDeparture = parseTime(raw["departure"]),
            scheduledPlatform = text(raw["scheduledTrack"]),
            expectedPlatform = text(raw["track"]),
            cancelled = raw["cancelled"] == true,
            alerts = parseAlerts(raw["alerts"]),
        )
    }

    private fun parseAlerts(value: Any?): List<TransitAlert> {
        val values = value as? List<*> ?: return emptyList()
        return values.take(MAX_LEGS_PER_ITINERARY).mapNotNull { entry ->
            val raw = entry as? Map<*, *> ?: return@mapNotNull null
            val header = text(raw["headerText"]) ?: return@mapNotNull null
            TransitAlert(
                code = text(raw["code"]),
                header = header,
                description = text(raw["descriptionText"]),
                severity = text(raw["severityLevel"]),
                effect = text(raw["effect"]),
                url = text(raw["url"]),
            )
        }
    }

    private fun scopedId(provider: TransitProvider, value: Any?): TransitScopedId? =
        text(value)?.let { TransitScopedId(provider.id, it) }

    private fun accessibility(value: Any?): TransitAccessibility = when ((value as? String)?.uppercase()) {
        "ACCESSIBLE" -> TransitAccessibility.Accessible
        "NOT_ACCESSIBLE" -> TransitAccessibility.NotAccessible
        else -> TransitAccessibility.Unknown
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
