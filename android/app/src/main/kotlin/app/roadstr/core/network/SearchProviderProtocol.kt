package app.roadstr.core.network

import java.math.BigDecimal
import java.math.RoundingMode

enum class SearchProviderHttpMethod(val fixtureName: String) {
    Get("get"),
    Post("post"),
}

data class SearchProviderRequest(
    val method: SearchProviderHttpMethod,
    val uri: String,
    val headers: Map<String, String>,
    val body: String? = null,
)

/**
 * Socket-free request composition for Nominatim, Photon and Overpass.
 *
 * These values mirror the production-used Dart boundary byte for byte. The
 * future native adapter may execute a request only after applying the separate
 * bounded-response and deadline policy.
 */
object SearchProviderProtocol {
    const val nominatimSearchEndpoint = "https://nominatim.openstreetmap.org/search"
    const val nominatimReverseEndpoint = "https://nominatim.openstreetmap.org/reverse"
    const val photonEndpoint = "https://photon.komoot.io/api/"
    const val photonMaxQueryLength = 200

    val photonSupportedLanguages: Set<String> = setOf("en", "de", "fr")

    /** Switzerland-only overpass.osm.ch must not be added to this list. */
    val overpassMirrors: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.openstreetmap.fr/api/interpreter",
    )

    private val roadstrHeaders = mapOf("User-Agent" to "Roadstr/1.0")
    private val overpassHeaders = mapOf(
        "Content-Type" to "application/x-www-form-urlencoded",
        "User-Agent" to "Roadstr/1.0 (navigation app)",
    )

    fun nominatimSearch(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): SearchProviderRequest? {
        require((latitude == null) == (longitude == null)) {
            "Nominatim bias requires both coordinates"
        }
        val trimmed = query.trimDart()
        if (trimmed.isEmpty()) return null
        val viewbox = if (latitude == null) {
            ""
        } else {
            "&viewbox=${dartDouble(longitude!! - 0.25)},${dartDouble(latitude + 0.25)}," +
                "${dartDouble(longitude + 0.25)},${dartDouble(latitude - 0.25)}&bounded=0"
        }
        return SearchProviderRequest(
            method = SearchProviderHttpMethod.Get,
            uri = "$nominatimSearchEndpoint?q=${encodeComponent(trimmed)}" +
                "&format=json&limit=6&addressdetails=1&extratags=1" +
                "&polygon_geojson=0$viewbox",
            headers = roadstrHeaders,
        )
    }

    fun nominatimReverse(latitude: Double, longitude: Double): SearchProviderRequest =
        SearchProviderRequest(
            method = SearchProviderHttpMethod.Get,
            uri = "$nominatimReverseEndpoint?lat=${dartDouble(latitude)}" +
                "&lon=${dartDouble(longitude)}" +
                "&format=json&addressdetails=1&extratags=1",
            headers = roadstrHeaders,
        )

    fun photonSearch(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
        languageCode: String = "en",
        limit: Int = 8,
    ): SearchProviderRequest? {
        require((latitude == null) == (longitude == null)) {
            "Photon bias requires both coordinates"
        }
        val trimmed = query.trimDart()
        if (trimmed.isEmpty() || trimmed.length > photonMaxQueryLength) return null
        val bias = if (latitude == null) {
            ""
        } else {
            "&lat=${fixedTwo(latitude)}&lon=${fixedTwo(longitude!!)}" +
                "&location_bias_scale=0.3&zoom=12"
        }
        val language = if (languageCode in photonSupportedLanguages) {
            "&lang=$languageCode"
        } else {
            ""
        }
        return SearchProviderRequest(
            method = SearchProviderHttpMethod.Get,
            uri = "$photonEndpoint?q=${encodeQueryComponent(trimmed)}" +
                "&limit=$limit$bias$language",
            headers = roadstrHeaders,
        )
    }

    fun overpass(mirror: String, query: String): SearchProviderRequest =
        SearchProviderRequest(
            method = SearchProviderHttpMethod.Post,
            uri = mirror,
            headers = overpassHeaders,
            body = "data=${encodeQueryComponent(query)}",
        )

    private fun encodeComponent(value: String): String = percentEncode(
        value = value,
        spaceToPlus = false,
        allowed = { byte ->
            isAlphaNumeric(byte) || byte.toChar() in "-_.!~*'()"
        },
    )

    private fun encodeQueryComponent(value: String): String = percentEncode(
        value = value,
        spaceToPlus = true,
        allowed = { byte -> isAlphaNumeric(byte) || byte.toChar() in "-._~" },
    )

    private fun percentEncode(
        value: String,
        spaceToPlus: Boolean,
        allowed: (Int) -> Boolean,
    ): String = buildString {
        for (signedByte in value.toByteArray(Charsets.UTF_8)) {
            val byte = signedByte.toInt() and 0xff
            when {
                allowed(byte) -> append(byte.toChar())
                spaceToPlus && byte == 0x20 -> append('+')
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
        return decimal.toString().lowercase()
    }

    private fun fixedTwo(value: Double): String {
        if (!value.isFinite() || kotlin.math.abs(value) >= 1e21) {
            return dartDouble(value)
        }
        val negativeZero = value == 0.0 && value.toRawBits() < 0
        val rounded = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString()
        return if (negativeZero) "-$rounded" else rounded
    }

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
