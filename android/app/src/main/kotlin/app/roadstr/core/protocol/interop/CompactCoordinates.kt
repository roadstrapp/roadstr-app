package app.roadstr.core.protocol.interop

import kotlin.math.abs

/**
 * Coordinates of the compact format: fixed-point degrees x 10^7 in an int32, printed as a decimal
 * string with exactly seven decimals.
 *
 * Everything here is integer arithmetic. The jooray reference client goes through `Double` and
 * `String.format`, which is wrong twice: `(lat * 1e7).toLong()` truncates and lands one unit short
 * for about 6% of coordinates, and `"%.7f".format` follows the device locale (a comma on an Italian
 * phone). Both change the signed bytes, so neither is reproduced.
 */
object CompactCoordinates {
    const val SCALE: Long = 10_000_000L
    const val MAX_LATITUDE_E7: Long = 900_000_000L
    const val MAX_LONGITUDE_E7: Long = 1_800_000_000L

    private val LATITUDE_SHAPE = Regex("^-?[0-9]{1,2}\\.[0-9]{7}\$")
    private val LONGITUDE_SHAPE = Regex("^-?[0-9]{1,3}\\.[0-9]{7}\$")

    sealed interface Parsed {
        data class Value(val e7: Int) : Parsed

        /** Well formed but outside the planet. */
        data object OutOfRange : Parsed

        /** Not the canonical text of any int32 (wrong decimals, "-0.0000000", leading zeros...). */
        data object Malformed : Parsed
    }

    fun parseLatitude(text: String): Parsed = parse(text, LATITUDE_SHAPE, MAX_LATITUDE_E7)

    fun parseLongitude(text: String): Parsed = parse(text, LONGITUDE_SHAPE, MAX_LONGITUDE_E7)

    /** Locale-free `-?D+.DDDDDDD` text of [e7]; the only spelling the format accepts. */
    fun format(e7: Int): String {
        val magnitude = abs(e7.toLong())
        val sign = if (e7 < 0) "-" else ""
        return sign + (magnitude / SCALE) + "." + (magnitude % SCALE).toString().padStart(7, '0')
    }

    fun degrees(e7: Int): Double = e7 / SCALE.toDouble()

    fun inRange(latitudeE7: Int, longitudeE7: Int): Boolean =
        abs(latitudeE7.toLong()) <= MAX_LATITUDE_E7 && abs(longitudeE7.toLong()) <= MAX_LONGITUDE_E7

    /** Nearest representable value of a GPS reading, rounded (never truncated). Null if not on Earth. */
    fun latitudeE7(degrees: Double): Int? = quantize(degrees, MAX_LATITUDE_E7)

    fun longitudeE7(degrees: Double): Int? = quantize(degrees, MAX_LONGITUDE_E7)

    private fun quantize(degrees: Double, limit: Long): Int? {
        if (!degrees.isFinite()) return null
        val scaled = Math.round(degrees * SCALE)
        return if (abs(scaled) <= limit) scaled.toInt() else null
    }

    private fun parse(text: String, shape: Regex, limit: Long): Parsed {
        if (!shape.matches(text)) return Parsed.Malformed
        val negative = text.startsWith('-')
        val body = if (negative) text.substring(1) else text
        val dot = body.indexOf('.')
        val magnitude = body.substring(0, dot).toLong() * SCALE + body.substring(dot + 1).toLong()
        if (magnitude > limit) return Parsed.OutOfRange
        val value = (if (negative) -magnitude else magnitude).toInt()
        // Rejects "-0.0000000" and "05.1234567": values no formatter would ever produce.
        return if (format(value) == text) Parsed.Value(value) else Parsed.Malformed
    }
}
