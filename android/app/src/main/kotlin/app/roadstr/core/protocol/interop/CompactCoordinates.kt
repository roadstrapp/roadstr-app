package app.roadstr.core.protocol.interop

import kotlin.math.abs

/**
 * Coordinates of the shared road-event format: degrees x 10^6 in an int32, printed as
 * `-?D+.DDDDDD` — always six decimals, always a dot, never a locale.
 *
 * Six decimals is ~11 cm, below GPS accuracy, and is what the 117-byte report frame reproduces
 * exactly. Reading `lat * 1e6` from a Double truncates; the tag text is the source of truth in
 * both directions.
 */
object CompactCoordinates {
    const val SCALE: Long = 1_000_000L
    const val MAX_LATITUDE_E6: Long = 90_000_000L
    const val MAX_LONGITUDE_E6: Long = 180_000_000L

    private val LATITUDE_SHAPE = Regex("^-?[0-9]{1,2}\\.[0-9]{6}$")
    private val LONGITUDE_SHAPE = Regex("^-?[0-9]{1,3}\\.[0-9]{6}$")

    sealed interface Parsed {
        data class Value(val e6: Int) : Parsed

        data object OutOfRange : Parsed

        data object Malformed : Parsed
    }

    fun parseLatitude(text: String): Parsed = parse(text, LATITUDE_SHAPE, MAX_LATITUDE_E6)

    fun parseLongitude(text: String): Parsed = parse(text, LONGITUDE_SHAPE, MAX_LONGITUDE_E6)

    fun format(e6: Int): String {
        val magnitude = abs(e6.toLong())
        val sign = if (e6 < 0) "-" else ""
        return sign + (magnitude / SCALE) + "." + (magnitude % SCALE).toString().padStart(6, '0')
    }

    fun degrees(e6: Int): Double = e6 / SCALE.toDouble()

    fun inRange(latitudeE6: Int, longitudeE6: Int): Boolean =
        abs(latitudeE6.toLong()) <= MAX_LATITUDE_E6 && abs(longitudeE6.toLong()) <= MAX_LONGITUDE_E6

    /** Nearest representable value of a GPS reading, rounded (never truncated). */
    fun latitudeE6(degrees: Double): Int? = quantize(degrees, MAX_LATITUDE_E6)

    fun longitudeE6(degrees: Double): Int? = quantize(degrees, MAX_LONGITUDE_E6)

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
        return if (format(value) == text) Parsed.Value(value) else Parsed.Malformed
    }
}
