package app.roadstr.core.search

import app.roadstr.core.protocol.nostr.BoundedJsonParser
import java.math.BigDecimal
import java.util.Collections
import kotlin.math.abs

data class SearchHistoryEntry(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Socket- and storage-free mirror of Flutter's encrypted search-history value
 * contract. The Android adapter will own the eventual persistence transaction.
 */
object SearchHistoryProtocol {
    const val STORAGE_KEY = "searchHistory"
    const val MAX_LABEL_LENGTH = 300
    const val MAX_LOADED_ITEMS = 100
    const val MAX_STORED_ITEMS = 5
    const val DUPLICATE_COORDINATE_DELTA = 0.0001

    fun decodeStored(raw: Any?): List<SearchHistoryEntry> {
        if (raw !is List<*>) return emptyList()
        val decoded = ArrayList<SearchHistoryEntry>()
        for (value in raw) {
            if (decoded.size == MAX_LOADED_ITEMS) break
            if (value !is String) continue
            val entry = try {
                val parsed = BoundedJsonParser(value).parse() as? Map<*, *>
                parsed?.let(::entryFromMap)
            } catch (_: Exception) {
                null
            }
            if (entry != null) decoded += entry
        }
        return Collections.unmodifiableList(decoded)
    }

    fun prepend(
        item: SearchHistoryEntry,
        current: Iterable<SearchHistoryEntry>,
    ): List<SearchHistoryEntry> {
        val updated = ArrayList<SearchHistoryEntry>(MAX_STORED_ITEMS)
        updated += item
        for (existing in current) {
            val samePosition =
                abs(existing.latitude - item.latitude) <= DUPLICATE_COORDINATE_DELTA &&
                    abs(existing.longitude - item.longitude) <= DUPLICATE_COORDINATE_DELTA
            if (!samePosition) updated += existing
            if (updated.size == MAX_STORED_ITEMS) break
        }
        return Collections.unmodifiableList(updated)
    }

    fun encodeStored(history: Iterable<SearchHistoryEntry>): List<String> {
        val encoded = ArrayList<String>(MAX_STORED_ITEMS)
        for (item in history) {
            if (encoded.size == MAX_STORED_ITEMS) break
            encoded += encodeEntry(item)
        }
        return Collections.unmodifiableList(encoded)
    }

    private fun entryFromMap(value: Map<*, *>): SearchHistoryEntry? {
        val label = value["label"] as? String ?: return null
        val latitude = (value["lat"] as? Number)?.toDouble() ?: return null
        val longitude = (value["lon"] as? Number)?.toDouble() ?: return null
        if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return null
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return null
        return SearchHistoryEntry(label, latitude, longitude)
    }

    fun encodeEntry(item: SearchHistoryEntry): String = buildString {
        append("{\"label\":")
        appendJsonString(item.label)
        append(",\"lat\":")
        append(dartDoubleString(item.latitude))
        append(",\"lon\":")
        append(dartDoubleString(item.longitude))
        append('}')
    }

    /** Mirrors Dart's signed-zero and decimal/exponent spelling thresholds. */
    private fun dartDoubleString(value: Double): String {
        require(value.isFinite()) { "History coordinates must be finite" }
        if (value == 0.0) {
            return if (java.lang.Double.doubleToRawLongBits(value) < 0) "-0.0" else "0.0"
        }
        val decimal = BigDecimal.valueOf(value).stripTrailingZeros()
        val exponent = decimal.precision() - decimal.scale() - 1
        if (exponent in -6..20) {
            val plain = decimal.toPlainString()
            return if ('.' in plain) plain else "$plain.0"
        }

        val digits = decimal.unscaledValue().abs().toString()
        val mantissa = if (digits.length == 1) {
            digits
        } else {
            "${digits[0]}.${digits.substring(1)}"
        }
        val sign = if (decimal.signum() < 0) "-" else ""
        val exponentSign = if (exponent >= 0) "+" else ""
        return "$sign$mantissa" + "e$exponentSign$exponent"
    }

    /** Dart JSON escapes lone UTF-16 surrogates instead of emitting them. */
    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\u000c' -> append("\\f")
                '\r' -> append("\\r")
                else -> when {
                    character.code < 0x20 -> appendUnicodeEscape(character)
                    Character.isHighSurrogate(character) &&
                        index + 1 < value.length &&
                        Character.isLowSurrogate(value[index + 1]) -> {
                        append(character)
                        index++
                        append(value[index])
                    }

                    Character.isSurrogate(character) -> appendUnicodeEscape(character)
                    else -> append(character)
                }
            }
            index++
        }
        append('"')
    }

    private fun StringBuilder.appendUnicodeEscape(character: Char) {
        append("\\u")
        append(character.code.toString(16).padStart(4, '0'))
    }
}
