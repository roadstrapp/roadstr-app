package app.roadstr.core.network

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** A stop identifier is meaningful only together with the provider that issued it. */
data class TransitStopReference(
    val id: TransitScopedId,
    val name: String,
    val position: TransitResponsePoint?,
    val parentId: TransitScopedId? = null,
    val timezone: String? = null,
)

data class TransitStopSearchResult(
    val stop: TransitStopReference,
    val modes: Set<TransitMode>,
    val countryCode: String?,
    val locality: String?,
    val importance: Double?,
)

data class TransitLine(
    val id: TransitScopedId,
    val shortName: String?,
    val longName: String?,
    val mode: TransitMode,
    val agencyId: TransitScopedId?,
    val agencyName: String?,
    val colorArgb: Long?,
    val textColorArgb: Long?,
)

enum class TransitServiceState {
    Scheduled,
    OnTime,
    Delayed,
    Cancelled,
}

data class TransitDeparture(
    val stop: TransitStopReference,
    val tripId: TransitScopedId,
    val line: TransitLine?,
    val direction: String?,
    val scheduledTime: Instant,
    val expectedTime: Instant,
    val scheduledPlatform: String?,
    val expectedPlatform: String?,
    val realtime: Boolean,
    val state: TransitServiceState,
    val alerts: List<TransitAlert>,
    val nextStops: List<TransitStopReference>,
) {
    val delaySeconds: Long?
        get() = if (realtime) expectedTime.epochSecond - scheduledTime.epochSecond else null
}

data class TransitStopBoard(
    val stop: TransitStopReference,
    val departures: List<TransitDeparture>,
    val updatedAt: Instant,
    val previousPageCursor: String?,
    val nextPageCursor: String?,
)

data class TransitTripDetails(
    val itinerary: TransitItinerary,
    val updatedAt: Instant,
)

/** Socket-free MOTIS requests and parsers for operational transit information. */
object TransitOperationalProtocol {
    const val MAX_SEARCH_RESULTS = 20
    const val MAX_DEPARTURES = 64
    const val MAX_LINES = 256
    private const val MAX_TEXT_CHARS = TransitProtocol.MAX_TEXT_CHARS
    private val userAgentHeaders = mapOf("User-Agent" to TransitProtocol.USER_AGENT)

    fun searchStopsRequest(
        endpoint: String,
        query: String,
        location: TransitRequestPoint?,
        languageCode: String?,
    ): TransitProviderRequest {
        val normalized = query.trim().take(200)
        require(normalized.length >= 2) { "Transit stop query is too short" }
        location?.let(::requirePoint)
        return get(
            endpoint,
            "/api/v1/geocode",
            buildList {
                add("text" to normalized)
                add("type" to "STOP")
                add("numResults" to MAX_SEARCH_RESULTS.toString())
                location?.let { add("place" to "${decimal(it.latitude)},${decimal(it.longitude)}") }
                language(languageCode)?.let { add("language" to it) }
            },
        )
    }

    fun nearbyStopsRequest(
        endpoint: String,
        location: TransitRequestPoint,
    ): TransitProviderRequest {
        requirePoint(location)
        return get(
            endpoint,
            "/api/v1/reverse-geocode",
            listOf(
                "place" to "${decimal(location.latitude)},${decimal(location.longitude)}",
                "type" to "STOP",
                "numResults" to MAX_SEARCH_RESULTS.toString(),
            ),
        )
    }

    fun departuresRequest(
        endpoint: String,
        stop: TransitStopReference,
        time: Instant,
        languageCode: String?,
    ): TransitProviderRequest = get(
        endpoint,
        "/api/v6/stoptimes",
        buildList {
            add("stopId" to stop.id.value)
            stop.position?.let { add("center" to "${decimal(it.latitude)},${decimal(it.longitude)}") }
            if (stop.position != null) add("radius" to "75")
            add("time" to time.toString())
            add("arriveBy" to "false")
            add("both" to "false")
            add("n" to "20")
            add("fetchStops" to "true")
            add("realtimeMode" to "REALTIME")
            add("withAlerts" to "true")
            language(languageCode)?.let { add("language" to it) }
        },
    )

    fun linesRequest(
        endpoint: String,
        stop: TransitStopReference,
        languageCode: String?,
    ): TransitProviderRequest = get(
        endpoint,
        "/api/v6/stop",
        buildList {
            add("stopId" to stop.id.value)
            stop.position?.let { add("center" to "${decimal(it.latitude)},${decimal(it.longitude)}") }
            if (stop.position != null) add("radius" to "75")
            language(languageCode)?.let { add("language" to it) }
        },
    )

    fun tripRequest(
        endpoint: String,
        tripId: TransitScopedId,
        languageCode: String?,
    ): TransitProviderRequest = get(
        endpoint,
        "/api/v6/trip",
        buildList {
            add("tripId" to tripId.value)
            add("withScheduledSkippedStops" to "false")
            add("detailedLegs" to "true")
            language(languageCode)?.let { add("language" to it) }
        },
    )

    fun refreshRequest(
        endpoint: String,
        itineraryId: TransitScopedId,
        languageCode: String?,
    ): TransitProviderRequest = get(
        endpoint,
        "/api/v6/refresh-itinerary",
        buildList {
            add("itineraryId" to itineraryId.value)
            add("requireDisplayNameMatch" to "true")
            add("detailedTransfers" to "true")
            add("detailedLegs" to "true")
            add("realtimeMode" to "REALTIME")
            language(languageCode)?.let { add("language" to it) }
        },
    )

    fun parseStopSearch(body: String, provider: TransitProvider): List<TransitStopSearchResult> =
        parseArray(body).take(MAX_SEARCH_RESULTS).mapNotNull { value ->
            val raw = value as? Map<*, *> ?: return@mapNotNull null
            if (text(raw["type"]) != "STOP") return@mapNotNull null
            val id = scoped(provider, raw["id"]) ?: return@mapNotNull null
            val name = text(raw["name"]) ?: return@mapNotNull null
            val position = point(raw) ?: return@mapNotNull null
            val areas = raw["areas"] as? List<*>
            val locality = areas.orEmpty().asSequence().mapNotNull { it as? Map<*, *> }
                .firstOrNull { it["default"] == true }
                ?.let { text(it["name"]) }
            TransitStopSearchResult(
                stop = TransitStopReference(
                    id = id,
                    name = name,
                    position = position,
                    timezone = text(raw["tz"]),
                ),
                modes = (raw["modes"] as? List<*>).orEmpty().map(TransitMode::fromWire).toSet(),
                countryCode = text(raw["country"]),
                locality = locality,
                importance = number(raw["importance"])?.takeIf { it in 0.0..1.0 },
            )
        }.distinctBy { it.stop.id }

    fun parseStopBoard(
        body: String,
        provider: TransitProvider,
        updatedAt: Instant,
    ): TransitStopBoard {
        val root = parseObject(body)
        val requestedStop = stopReference(root["place"], provider)
            ?: fail("Transit stop board has no valid stop")
        val rawDepartures = root["stopTimes"] as? List<*>
            ?: fail("Transit stop board has no departures")
        if (rawDepartures.size > MAX_DEPARTURES * 4) fail("Transit stop board is too large")
        val departures = rawDepartures.take(MAX_DEPARTURES).mapNotNull { value ->
            departure(value as? Map<*, *> ?: return@mapNotNull null, requestedStop, provider)
        }
        return TransitStopBoard(
            stop = requestedStop,
            departures = departures,
            updatedAt = updatedAt,
            previousPageCursor = text(root["previousPageCursor"]),
            nextPageCursor = text(root["nextPageCursor"]),
        )
    }

    fun parseLines(body: String, provider: TransitProvider): List<TransitLine> {
        val root = parseObject(body)
        val lines = root["routes"] as? List<*> ?: fail("Transit stop has no route list")
        if (lines.size > MAX_LINES * 4) fail("Transit route list is too large")
        return lines.take(MAX_LINES).mapNotNull { line(it as? Map<*, *>, provider) }.distinctBy { it.id }
    }

    private fun departure(
        raw: Map<*, *>,
        requestedStop: TransitStopReference,
        provider: TransitProvider,
    ): TransitDeparture? {
        val place = raw["place"] as? Map<*, *> ?: return null
        val stop = stopReference(place, provider) ?: requestedStop
        val scheduled = time(place["scheduledDeparture"]) ?: time(place["scheduledArrival"]) ?: return null
        val expected = time(place["departure"]) ?: time(place["arrival"]) ?: scheduled
        val tripId = scoped(provider, raw["tripId"]) ?: return null
        val realtime = raw["realTime"] == true
        val cancelled = raw["cancelled"] == true || raw["tripCancelled"] == true || place["cancelled"] == true
        val state = when {
            cancelled -> TransitServiceState.Cancelled
            !realtime -> TransitServiceState.Scheduled
            expected.isAfter(scheduled) -> TransitServiceState.Delayed
            else -> TransitServiceState.OnTime
        }
        return TransitDeparture(
            stop = stop,
            tripId = tripId,
            line = line(raw, provider),
            direction = text(raw["headsign"]) ?: stopReference(raw["tripTo"], provider)?.name,
            scheduledTime = scheduled,
            expectedTime = expected,
            scheduledPlatform = text(place["scheduledTrack"]),
            expectedPlatform = text(place["track"]),
            realtime = realtime,
            state = state,
            alerts = alerts(place["alerts"]),
            nextStops = (raw["nextStops"] as? List<*>).orEmpty()
                .take(TransitProtocol.MAX_LEGS_PER_ITINERARY)
                .mapNotNull { stopReference(it, provider) },
        )
    }

    private fun line(raw: Map<*, *>?, provider: TransitProvider): TransitLine? {
        raw ?: return null
        val id = scoped(provider, raw["routeId"]) ?: return null
        return TransitLine(
            id = id,
            shortName = text(raw["routeShortName"]) ?: text(raw["displayName"]),
            longName = text(raw["routeLongName"]),
            mode = TransitMode.fromWire(raw["mode"]),
            agencyId = scoped(provider, raw["agencyId"]),
            agencyName = text(raw["agencyName"]),
            colorArgb = color(raw["routeColor"]),
            textColorArgb = color(raw["routeTextColor"]),
        )
    }

    private fun stopReference(value: Any?, provider: TransitProvider): TransitStopReference? {
        val raw = value as? Map<*, *> ?: return null
        val id = scoped(provider, raw["stopId"] ?: raw["id"]) ?: return null
        val name = text(raw["name"]) ?: return null
        return TransitStopReference(
            id = id,
            name = name,
            position = point(raw),
            parentId = scoped(provider, raw["parentId"]),
            timezone = text(raw["tz"]),
        )
    }

    private fun alerts(value: Any?): List<TransitAlert> = (value as? List<*>).orEmpty()
        .take(TransitProtocol.MAX_LEGS_PER_ITINERARY)
        .mapNotNull { entry ->
            val raw = entry as? Map<*, *> ?: return@mapNotNull null
            TransitAlert(
                code = text(raw["code"]),
                header = text(raw["headerText"]) ?: return@mapNotNull null,
                description = text(raw["descriptionText"]),
                severity = text(raw["severityLevel"]),
                effect = text(raw["effect"]),
                url = text(raw["url"]),
            )
        }

    private fun get(
        endpoint: String,
        path: String,
        query: List<Pair<String, String>>,
    ): TransitProviderRequest {
        val source = validateEndpoint(endpoint)
        val base = "${source.scheme}://${source.rawAuthority}"
        val encoded = query.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return TransitProviderRequest("$base$path?$encoded", userAgentHeaders)
    }

    private fun parseArray(body: String): List<*> = try {
        BoundedJsonParser(body).parse() as? List<*> ?: fail("Unexpected transit response shape")
    } catch (error: TransitResponseException) {
        throw error
    } catch (_: Exception) {
        fail("Malformed transit response")
    }

    private fun parseObject(body: String): Map<*, *> = try {
        BoundedJsonParser(body).parse() as? Map<*, *> ?: fail("Unexpected transit response shape")
    } catch (error: TransitResponseException) {
        throw error
    } catch (_: Exception) {
        fail("Malformed transit response")
    }

    private fun validateEndpoint(value: String): URI {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid transit endpoint")
        val loopback = uri.host?.lowercase() in setOf("localhost", "127.0.0.1", "::1")
        require(uri.host != null && uri.rawUserInfo == null && (uri.scheme == "https" || uri.scheme == "http" && loopback)) {
            "Transit endpoint must use HTTPS or loopback HTTP"
        }
        return uri
    }

    private fun requirePoint(point: TransitRequestPoint) {
        require(point.latitude.isFinite() && point.latitude in -90.0..90.0)
        require(point.longitude.isFinite() && point.longitude in -180.0..180.0)
    }

    private fun point(raw: Map<*, *>): TransitResponsePoint? {
        val latitude = number(raw["lat"]) ?: return null
        val longitude = number(raw["lon"]) ?: return null
        return TransitResponsePoint(latitude, longitude).takeIf {
            latitude in -90.0..90.0 && longitude in -180.0..180.0
        }
    }

    private fun time(value: Any?): Instant? {
        val raw = value as? String ?: return null
        return runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
    }

    private fun number(value: Any?): Double? = (value as? Number)?.toDouble()?.takeIf(Double::isFinite)

    private fun text(value: Any?): String? = (value as? String)?.trim()
        ?.takeIf(String::isNotEmpty)?.take(MAX_TEXT_CHARS)

    private fun scoped(provider: TransitProvider, value: Any?): TransitScopedId? =
        text(value)?.let { TransitScopedId(provider.id, it) }

    private fun language(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)?.take(35)

    private fun decimal(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun color(value: Any?): Long? {
        val raw = text(value)?.removePrefix("#") ?: return null
        if (raw.length != 6) return null
        return raw.toLongOrNull(16)?.let { 0xFF00_0000L or it }
    }

    private fun encode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { signed ->
            val byte = signed.toInt() and 0xff
            if (byte.toChar().isLetterOrDigit() && byte < 128 || byte.toChar() in "-._~") {
                append(byte.toChar())
            } else {
                append('%')
                append(HEX[byte ushr 4])
                append(HEX[byte and 0x0f])
            }
        }
    }

    private fun fail(message: String): Nothing = throw TransitResponseException(message)

    private const val HEX = "0123456789ABCDEF"
}
