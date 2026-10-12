package app.roadstr.core.network

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.security.MessageDigest
import java.time.Instant

class EnturGraphQlException(message: String) : IllegalArgumentException(message)

object EnturTransitProtocol {
    const val ENDPOINT = "https://api.entur.io/journey-planner/v3/graphql"
    const val CLIENT_NAME = "roadstrapp-roadstr"
    val PROVIDER = TransitProvider("entur", "Entur", "https://developer.entur.org/")

    private val TRIP_QUERY = """
        query RoadstrTrip(${ '$' }from: Location!, ${ '$' }to: Location!, ${ '$' }dateTime: DateTime!, ${ '$' }numTripPatterns: Int!) {
          trip(from: ${ '$' }from, to: ${ '$' }to, dateTime: ${ '$' }dateTime, numTripPatterns: ${ '$' }numTripPatterns) {
            tripPatterns {
              duration expectedStartTime expectedEndTime
              legs {
                mode distance duration aimedStartTime expectedStartTime aimedEndTime expectedEndTime
                fromPlace { name latitude longitude quay { id publicCode stopPlace { id } } }
                toPlace { name latitude longitude quay { id publicCode stopPlace { id } } }
                fromEstimatedCall { realtime destinationDisplay { frontText } }
                line { id publicCode name transportMode presentation { colour textColour } }
                serviceJourney { id }
                pointsOnLink { points }
                intermediateEstimatedCalls {
                  aimedArrivalTime expectedArrivalTime aimedDepartureTime expectedDepartureTime cancellation realtime
                  quay { id name latitude longitude publicCode stopPlace { id } }
                }
                situations { situationNumber severity summary { value } description { value } }
              }
            }
          }
        }
    """.trimIndent()

    fun tripRequest(
        query: app.roadstr.service.transit.NativeTransitQuery,
        endpoint: String = ENDPOINT,
    ): TransitProviderRequest {
        val body = buildString {
            append("{\"query\":\"").append(jsonEscape(TRIP_QUERY)).append("\",\"variables\":{")
            append("\"from\":{\"coordinates\":{")
            append("\"latitude\":").append(query.from.latitude)
            append(",\"longitude\":").append(query.from.longitude).append("}},")
            append("\"to\":{\"coordinates\":{")
            append("\"latitude\":").append(query.to.latitude)
            append(",\"longitude\":").append(query.to.longitude).append("}},")
            append("\"dateTime\":\"").append(query.departure.toString()).append("\",")
            append("\"numTripPatterns\":3}}")
        }
        return TransitProviderRequest(
            uri = endpoint,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "ET-Client-Name" to CLIENT_NAME,
            ),
            body = body,
        )
    }

    fun parsePlan(
        body: String,
        query: app.roadstr.service.transit.NativeTransitQuery,
        searchedAt: Instant,
    ): TransitPlanResponse {
        val root = parseMap(body)
        val errors = root["errors"] as? List<*>
        val data = root["data"] as? Map<*, *>
        val patterns = ((data?.get("trip") as? Map<*, *>)?.get("tripPatterns") as? List<*>)
        if (patterns == null) {
            if (!errors.isNullOrEmpty()) throw EnturGraphQlException("Entur GraphQL request failed")
            return TransitParsedUnavailable
        }
        val itineraries = patterns.take(8).mapNotNull { raw ->
            runCatching { parseItinerary(raw as? Map<*, *> ?: return@mapNotNull null) }.getOrNull()
        }
        if (itineraries.isEmpty()) return TransitParsedUnavailable
        return TransitParsedPlan(
            itineraries = itineraries,
            provider = PROVIDER,
            searchedAt = searchedAt,
            origin = query.from,
            destination = query.to,
            requestedDeparture = query.departure,
        )
    }

    private fun parseItinerary(map: Map<*, *>): TransitItinerary {
        val legs = (map["legs"] as? List<*>)?.take(128)?.mapNotNull { raw ->
            (raw as? Map<*, *>)?.let(::parseLeg)
        }.orEmpty()
        require(legs.isNotEmpty())
        val start = instant(map["expectedStartTime"]) ?: legs.first().startTime
        val end = instant(map["expectedEndTime"]) ?: legs.last().endTime
        val duration = number(map["duration"])?.toLong() ?: (end.epochSecond - start.epochSecond)
        val identity = legs.mapNotNull { it.tripId?.value }.joinToString("|") + "|$start|$end"
        return TransitItinerary(
            durationSeconds = duration.coerceAtLeast(0),
            startTime = start,
            endTime = end,
            transfers = (legs.count { it.mode.isTransit } - 1).coerceAtLeast(0),
            legs = legs,
            id = TransitScopedId(PROVIDER.id, "journey-${sha256(identity).take(24)}"),
        )
    }

    private fun parseLeg(map: Map<*, *>): TransitLeg {
        val aimedStart = instant(map["aimedStartTime"])
        val expectedStart = instant(map["expectedStartTime"])
        val aimedEnd = instant(map["aimedEndTime"])
        val expectedEnd = instant(map["expectedEndTime"])
        val start = expectedStart ?: aimedStart ?: error("Missing Entur leg start")
        val end = expectedEnd ?: aimedEnd ?: error("Missing Entur leg end")
        val fromPlace = map["fromPlace"] as? Map<*, *>
        val toPlace = map["toPlace"] as? Map<*, *>
        val line = map["line"] as? Map<*, *>
        val serviceJourney = map["serviceJourney"] as? Map<*, *>
        val estimatedCall = map["fromEstimatedCall"] as? Map<*, *>
        val situations = parseAlerts(map["situations"] as? List<*>)
        val mode = mode(map["mode"] ?: line?.get("transportMode"))
        val presentation = line?.get("presentation") as? Map<*, *>
        return TransitLeg(
            mode = mode,
            distanceMeters = number(map["distance"]),
            durationSeconds = (number(map["duration"])?.toLong() ?: (end.epochSecond - start.epochSecond))
                .coerceAtLeast(0),
            startTime = start,
            endTime = end,
            fromName = fromPlace?.get("name") as? String,
            toName = toPlace?.get("name") as? String,
            routeShortName = line?.get("publicCode") as? String,
            routeLongName = line?.get("name") as? String,
            agencyName = null,
            headsign = ((estimatedCall?.get("destinationDisplay") as? Map<*, *>)?.get("frontText") as? String),
            routeColorArgb = color(presentation?.get("colour") as? String),
            routeTextColorArgb = color(presentation?.get("textColour") as? String),
            realTime = estimatedCall?.get("realtime") as? Boolean ?: (expectedStart != aimedStart || expectedEnd != aimedEnd),
            geometry = decodePolyline(((map["pointsOnLink"] as? Map<*, *>)?.get("points") as? String)),
            from = parsePlace(fromPlace, aimedDeparture = aimedStart, expectedDeparture = expectedStart, alerts = situations),
            to = parsePlace(toPlace, aimedArrival = aimedEnd, expectedArrival = expectedEnd, alerts = situations),
            scheduledStartTime = aimedStart ?: start,
            scheduledEndTime = aimedEnd ?: end,
            routeId = scoped(line?.get("id")),
            tripId = scoped(serviceJourney?.get("id")),
            displayName = line?.get("name") as? String,
            intermediateStops = (map["intermediateEstimatedCalls"] as? List<*>)
                ?.take(1_024)
                ?.mapNotNull { (it as? Map<*, *>)?.let(::parseIntermediateCall) }
                .orEmpty(),
            alerts = situations,
            sourceReference = serviceJourney?.get("id") as? String,
        )
    }

    private fun parseIntermediateCall(map: Map<*, *>): TransitStop {
        val quay = map["quay"] as? Map<*, *>
        return parsePlace(
            quay,
            aimedArrival = instant(map["aimedArrivalTime"]),
            expectedArrival = instant(map["expectedArrivalTime"]),
            aimedDeparture = instant(map["aimedDepartureTime"]),
            expectedDeparture = instant(map["expectedDepartureTime"]),
            cancelled = map["cancellation"] as? Boolean ?: false,
        ) ?: error("Invalid Entur intermediate call")
    }

    private fun parsePlace(
        place: Map<*, *>?,
        aimedArrival: Instant? = null,
        expectedArrival: Instant? = null,
        aimedDeparture: Instant? = null,
        expectedDeparture: Instant? = null,
        cancelled: Boolean = false,
        alerts: List<TransitAlert> = emptyList(),
    ): TransitStop? {
        place ?: return null
        val quay = (place["quay"] as? Map<*, *>) ?: place
        val stopPlace = quay["stopPlace"] as? Map<*, *>
        val identifier = stopPlace?.get("id") ?: quay["id"]
        val latitude = number(place["latitude"] ?: quay["latitude"])
        val longitude = number(place["longitude"] ?: quay["longitude"])
        return TransitStop(
            id = scoped(identifier),
            name = (place["name"] ?: quay["name"]) as? String,
            position = if (latitude != null && longitude != null) TransitResponsePoint(latitude, longitude) else null,
            timezone = "Europe/Oslo",
            scheduledArrival = aimedArrival,
            expectedArrival = expectedArrival,
            scheduledDeparture = aimedDeparture,
            expectedDeparture = expectedDeparture,
            scheduledPlatform = quay["publicCode"] as? String,
            expectedPlatform = quay["publicCode"] as? String,
            cancelled = cancelled,
            alerts = alerts,
        )
    }

    private fun parseAlerts(values: List<*>?): List<TransitAlert> = values.orEmpty().take(128).mapNotNull { raw ->
        val row = raw as? Map<*, *> ?: return@mapNotNull null
        val summary = (row["summary"] as? List<*>)?.firstOrNull() as? Map<*, *>
        val description = (row["description"] as? List<*>)?.firstOrNull() as? Map<*, *>
        val header = summary?.get("value") as? String ?: return@mapNotNull null
        TransitAlert(
            code = row["situationNumber"] as? String,
            header = header,
            description = description?.get("value") as? String,
            severity = row["severity"] as? String,
            effect = null,
            url = null,
        )
    }

    private fun parseMap(body: String): Map<*, *> = try {
        BoundedJsonParser(body).parse() as? Map<*, *> ?: throw EnturGraphQlException("Malformed Entur response")
    } catch (_: RuntimeException) {
        throw EnturGraphQlException("Malformed Entur response")
    }

    private fun instant(value: Any?): Instant? = (value as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() }
    private fun number(value: Any?): Double? = (value as? Number)?.toDouble()
    private fun scoped(value: Any?): TransitScopedId? = (value as? String)?.takeIf(String::isNotBlank)?.let {
        TransitScopedId(PROVIDER.id, it)
    }

    private fun mode(value: Any?): TransitMode = when ((value as? String)?.uppercase()) {
        "FOOT", "WALK" -> TransitMode.Walk
        "BICYCLE", "BIKE" -> TransitMode.Bike
        "RAIL" -> TransitMode.Rail
        "METRO", "SUBWAY" -> TransitMode.Metro
        "TRAM" -> TransitMode.Tram
        "BUS" -> TransitMode.Bus
        "COACH" -> TransitMode.Coach
        "WATER", "FERRY" -> TransitMode.Ferry
        "AIR" -> TransitMode.Airplane
        "FUNICULAR" -> TransitMode.Funicular
        "CABLEWAY", "AERIAL_LIFT" -> TransitMode.AerialLift
        else -> TransitMode.Other
    }

    private fun color(value: String?): Long? {
        val text = value?.removePrefix("#")?.takeIf { it.length == 6 || it.length == 8 } ?: return null
        return text.toLongOrNull(16)?.let { if (text.length == 6) 0xFF000000L or it else it }
    }

    /** Entur pointsOnLink uses the standard encoded-polyline representation. */
    private fun decodePolyline(value: String?): List<TransitResponsePoint> {
        if (value.isNullOrEmpty() || value.length > 1_000_000) return emptyList()
        val result = ArrayList<TransitResponsePoint>()
        var index = 0
        var latitude = 0
        var longitude = 0
        while (index < value.length && result.size < 250_000) {
            val lat = decodeDelta(value, index) ?: return emptyList()
            index = lat.second
            val lon = decodeDelta(value, index) ?: return emptyList()
            index = lon.second
            latitude += lat.first
            longitude += lon.first
            result += TransitResponsePoint(latitude / 100_000.0, longitude / 100_000.0)
        }
        return result
    }

    private fun decodeDelta(value: String, start: Int): Pair<Int, Int>? {
        var index = start
        var shift = 0
        var result = 0
        while (index < value.length && shift <= 30) {
            val chunk = value[index++].code - 63
            if (chunk !in 0..63) return null
            result = result or ((chunk and 0x1F) shl shift)
            if (chunk < 0x20) return (if (result and 1 != 0) (result shr 1).inv() else result shr 1) to index
            shift += 5
        }
        return null
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun jsonEscape(value: String): String = buildString(value.length + 32) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
    }
}
