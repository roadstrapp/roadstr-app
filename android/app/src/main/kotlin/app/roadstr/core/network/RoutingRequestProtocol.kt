package app.roadstr.core.network

import java.math.BigDecimal
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

enum class RoutingRequestHttpMethod(val fixtureName: String) {
    Get("get"),
    Post("post"),
}

enum class ValhallaCostingPolicy {
    HardHighwayAndTollExclusion,
    SoftHighwayAndTollAvoidance,
    AvoidTracks,
}

data class RoutingRequestPoint(
    val latitude: Double,
    val longitude: Double,
)

data class RoutingProviderRequest(
    val method: RoutingRequestHttpMethod,
    val uri: String,
    val headers: Map<String, String>,
    val body: String? = null,
) {
    // The URI carries the provider key and the user's coordinates; headers
    // may carry an Authorization value.
    override fun toString(): String = "RoutingProviderRequest(method=$method)"
}

/** Exact, socket-free request composition for Roadstr's routing providers. */
object RoutingRequestProtocol {
    const val osrmDrivingEndpoint =
        "https://routing.openstreetmap.de/routed-car/route/v1/driving"
    const val osrmWalkingEndpoint =
        "https://routing.openstreetmap.de/routed-foot/route/v1/foot"
    const val osrmCyclingEndpoint =
        "https://routing.openstreetmap.de/routed-bike/route/v1/bike"
    const val openRouteServiceBase = "https://api.openrouteservice.org/v2/directions/"
    const val graphHopperPublicEndpoint = "https://graphhopper.com/api/1/route"
    const val valhallaEndpoint = "https://valhalla1.openstreetmap.de/route"

    const val maxIntermediateWaypoints = 4
    const val rerouteBearingToleranceDegrees = 45

    private val userAgentHeaders = mapOf("User-Agent" to "Roadstr/1.0")

    fun osrmEndpoint(vehicle: String): String = when (vehicle) {
        "walking" -> osrmWalkingEndpoint
        "cycling" -> osrmCyclingEndpoint
        else -> osrmDrivingEndpoint
    }

    fun openRouteServiceProfile(vehicle: String): String = when (vehicle) {
        "walking" -> "foot-walking"
        "cycling" -> "cycling-regular"
        else -> "driving-car"
    }

    fun graphHopperVehicle(vehicle: String): String = when (vehicle) {
        "walking" -> "foot"
        "cycling" -> "bike"
        else -> "car"
    }

    fun graphHopperEndpoint(configured: String?): String =
        configured?.trimDart()?.takeIf(String::isNotEmpty) ?: graphHopperPublicEndpoint

    fun openRouteService(
        origin: RoutingRequestPoint,
        destination: RoutingRequestPoint,
        apiKey: String,
        languageCode: String,
        vehicle: String,
    ): RoutingProviderRequest {
        val body = buildString {
            append("{\"coordinates\":[[")
            append(dartDouble(origin.longitude))
            append(',')
            append(dartDouble(origin.latitude))
            append("],[")
            append(dartDouble(destination.longitude))
            append(',')
            append(dartDouble(destination.latitude))
            append("]],\"language\":\"")
            append(openRouteServiceLanguage(languageCode))
            append("\",\"instructions\":true}")
        }
        return RoutingProviderRequest(
            method = RoutingRequestHttpMethod.Post,
            uri = openRouteServiceBase + openRouteServiceProfile(vehicle),
            headers = linkedMapOf(
                "Authorization" to apiKey,
                "Content-Type" to "application/json",
                "User-Agent" to "Roadstr/1.0",
            ),
            body = body,
        )
    }

    fun graphHopperRoute(
        origin: RoutingRequestPoint,
        destination: RoutingRequestPoint,
        server: String,
        languageCode: String,
        vehicle: String,
        apiKey: String? = null,
    ): RoutingProviderRequest {
        val parts = mutableListOf(
            "point=${dartDouble(origin.latitude)},${dartDouble(origin.longitude)}",
            "point=${dartDouble(destination.latitude)},${dartDouble(destination.longitude)}",
            "vehicle=${graphHopperVehicle(vehicle)}",
            "locale=${encodeQueryComponent(languageCode)}",
            "instructions=true",
            "points_encoded=false",
            "details=max_speed",
        )
        if (apiKey != null && server == graphHopperPublicEndpoint) {
            parts += "key=${encodeQueryComponent(apiKey)}"
        }
        return RoutingProviderRequest(
            method = RoutingRequestHttpMethod.Get,
            uri = replaceQuery(server, parts.joinToString("&")),
            headers = userAgentHeaders,
        )
    }

    fun graphHopperProbe(server: String, apiKey: String? = null): RoutingProviderRequest {
        val parts = mutableListOf(
            "point=0.0,0.0",
            "point=0.1,0.1",
            "vehicle=car",
            "locale=it",
            "instructions=false",
            "points_encoded=false",
        )
        if (!apiKey.isNullOrEmpty() && server == graphHopperPublicEndpoint) {
            parts += "key=${encodeQueryComponent(apiKey)}"
        }
        return RoutingProviderRequest(
            method = RoutingRequestHttpMethod.Get,
            uri = replaceQuery(server, parts.joinToString("&")),
            headers = userAgentHeaders,
        )
    }

    fun osrmRoute(
        origin: RoutingRequestPoint,
        destination: RoutingRequestPoint,
        vehicle: String,
        via: List<RoutingRequestPoint> = emptyList(),
        requestAlternatives: Boolean = false,
        originBearingDegrees: Double? = null,
        endpoint: String? = null,
    ): RoutingProviderRequest {
        val stops = via.take(maxIntermediateWaypoints).map { point ->
            "${dartDouble(point.longitude)},${dartDouble(point.latitude)}"
        }
        val alternatives = if (requestAlternatives && stops.isEmpty()) {
            "&alternatives=3"
        } else {
            ""
        }
        val bearings = if (originBearingDegrees == null) {
            ""
        } else {
            val normalized = floorMod(dartRound(originBearingDegrees), 360L)
            "&bearings=$normalized,$rerouteBearingToleranceDegrees;"
        }
        val middle = if (stops.isEmpty()) "" else stops.joinToString(";") + ";"
        val uri = buildString {
            append(endpoint ?: osrmEndpoint(vehicle))
            append('/')
            append(dartDouble(origin.longitude))
            append(',')
            append(dartDouble(origin.latitude))
            append(';')
            append(middle)
            append(dartDouble(destination.longitude))
            append(',')
            append(dartDouble(destination.latitude))
            append("?overview=full&geometries=geojson&steps=true")
            append(alternatives)
            append(bearings)
        }
        return RoutingProviderRequest(
            method = RoutingRequestHttpMethod.Get,
            uri = uri,
            headers = userAgentHeaders,
        )
    }

    fun valhalla(
        origin: RoutingRequestPoint,
        destination: RoutingRequestPoint,
        languageCode: String,
        costingPolicy: ValhallaCostingPolicy,
        endpoint: String? = null,
    ): RoutingProviderRequest {
        val options = when (costingPolicy) {
            ValhallaCostingPolicy.HardHighwayAndTollExclusion ->
                "\"exclude_highways\":true,\"exclude_tolls\":true"

            ValhallaCostingPolicy.SoftHighwayAndTollAvoidance ->
                "\"use_highways\":0,\"use_tolls\":0,\"toll_booth_penalty\":900"

            ValhallaCostingPolicy.AvoidTracks -> "\"use_tracks\":0"
        }
        val payload = buildString {
            append("{\"locations\":[{\"lat\":")
            append(dartDouble(origin.latitude))
            append(",\"lon\":")
            append(dartDouble(origin.longitude))
            append("},{\"lat\":")
            append(dartDouble(destination.latitude))
            append(",\"lon\":")
            append(dartDouble(destination.longitude))
            append("}],\"costing\":\"auto\",\"costing_options\":{\"auto\":{")
            append(options)
            append("}},\"units\":\"kilometers\",\"language\":\"")
            append(valhallaLanguage(languageCode))
            append("\"}")
        }
        return RoutingProviderRequest(
            method = RoutingRequestHttpMethod.Get,
            uri = replaceQuery(endpoint ?: valhallaEndpoint, "json=${encodeQueryComponent(payload)}"),
            headers = userAgentHeaders,
        )
    }

    fun osrmRetime(waypoints: String, endpoint: String? = null): RoutingProviderRequest =
        RoutingProviderRequest(
            method = RoutingRequestHttpMethod.Get,
            uri = "${endpoint ?: osrmDrivingEndpoint}/$waypoints" +
                "?overview=false&steps=false",
            headers = userAgentHeaders,
        )

    fun openRouteServiceLanguage(languageCode: String): String = mapOf(
        "cs" to "cs",
        "de" to "de",
        "en" to "en",
        "es" to "es",
        "fr" to "fr",
        "el" to "gr",
        "hu" to "hu",
        "it" to "it",
        "ja" to "ja",
        "nl" to "nl",
        "pl" to "pl",
        "pt" to "pt",
        "ro" to "ro",
        "ru" to "ru",
        "tr" to "tr",
        "uk" to "ua",
        "zh" to "zh",
    )[languageCode.lowercase(Locale.ROOT)] ?: "en"

    fun valhallaLanguage(languageCode: String): String = mapOf(
        "bg" to "bg-BG",
        "cs" to "cs-CZ",
        "da" to "da-DK",
        "de" to "de-DE",
        "el" to "el-GR",
        "en" to "en-US",
        "es" to "es-ES",
        "et" to "et-EE",
        "fi" to "fi-FI",
        "fr" to "fr-FR",
        "hu" to "hu-HU",
        "it" to "it-IT",
        "ja" to "ja-JP",
        "nl" to "nl-NL",
        "pl" to "pl-PL",
        "pt" to "pt-BR",
        "ro" to "ro-RO",
        "ru" to "ru-RU",
        "sk" to "sk-SK",
        "sl" to "sl-SI",
        "sv" to "sv-SE",
        "zh" to "zh-CN",
    )[languageCode.lowercase(Locale.ROOT)] ?: "en-US"

    private fun replaceQuery(uri: String, query: String): String {
        val fragmentIndex = uri.indexOf('#')
        val fragment = if (fragmentIndex >= 0) uri.substring(fragmentIndex) else ""
        val withoutFragment = if (fragmentIndex >= 0) uri.substring(0, fragmentIndex) else uri
        val queryIndex = withoutFragment.indexOf('?')
        val base = if (queryIndex >= 0) withoutFragment.substring(0, queryIndex) else withoutFragment
        return "$base?$query$fragment"
    }

    private fun encodeQueryComponent(value: String): String = buildString {
        for (signedByte in value.toByteArray(Charsets.UTF_8)) {
            val byte = signedByte.toInt() and 0xff
            when {
                isAlphaNumeric(byte) || byte.toChar() in "-._~" -> append(byte.toChar())
                byte == 0x20 -> append('+')
                else -> {
                    append('%')
                    append(HEX[byte ushr 4])
                    append(HEX[byte and 0x0f])
                }
            }
        }
    }

    private fun isAlphaNumeric(byte: Int): Boolean =
        byte in 'a'.code..'z'.code ||
            byte in 'A'.code..'Z'.code ||
            byte in '0'.code..'9'.code

    /** Mirrors Dart double.toString's fixed/exponential thresholds. */
    private fun dartDouble(value: Double): String {
        if (!value.isFinite()) return value.toString()
        if (value == 0.0) return if (value.toRawBits() < 0) "-0.0" else "0.0"
        val decimal = BigDecimal(value.toString()).stripTrailingZeros()
        val absolute = decimal.abs()
        if (absolute >= BigDecimal("0.000001") && absolute < BigDecimal("1e21")) {
            val plain = decimal.toPlainString()
            return if (decimal.scale() <= 0) "$plain.0" else plain
        }
        return decimal.toString().lowercase(Locale.ROOT)
    }

    private fun dartRound(value: Double): Long =
        if (value < 0) ceil(value - 0.5).toLong() else floor(value + 0.5).toLong()

    private fun floorMod(value: Long, divisor: Long): Long =
        ((value % divisor) + divisor) % divisor

    private fun String.trimDart(): String {
        var start = 0
        var end = length
        while (start < end && this[start].isDartWhitespace()) start++
        while (end > start && this[end - 1].isDartWhitespace()) end--
        return substring(start, end)
    }

    private fun Char.isDartWhitespace(): Boolean =
        this in '\u0009'..'\u000d' ||
            this == '\u0020' ||
            this == '\u0085' ||
            this == '\u00a0' ||
            this == '\u1680' ||
            this in '\u2000'..'\u200a' ||
            this == '\u2028' ||
            this == '\u2029' ||
            this == '\u202f' ||
            this == '\u205f' ||
            this == '\u3000' ||
            this == '\ufeff'

    private const val HEX = "0123456789ABCDEF"
}
