package app.roadstr.feature.history

import app.roadstr.core.geo.GeoMath
import app.roadstr.core.geo.GeoPoint
import app.roadstr.core.protocol.nostr.BoundedJsonParser
import app.roadstr.feature.map.NativeMapPoint
import java.math.BigDecimal
import java.util.Collections

/** One route the driver started: where it went and when. */
data class NativeRouteHistoryEntry(
    val label: String,
    val point: NativeMapPoint,
    val startedAtEpochMillis: Long,
)

/**
 * The recent-routes list behind the Activity button.
 *
 * Destinations are sensitive, so this type only shapes the list and its text
 * form; the host stores that text encrypted (Android Keystore, AES-GCM) and
 * nothing here touches disk, network or logs.
 */
object NativeRouteHistoryProtocol {
    const val MAX_ENTRIES = 50
    const val MAX_LABEL_CHARS = 200
    const val MAX_STORED_BYTES = 64 * 1024

    /** Starting again within this distance of an earlier destination is the same place. */
    const val SAME_PLACE_METRES = 50.0

    /** Newest first; an earlier trip to the same place is replaced, not repeated. */
    fun record(
        current: List<NativeRouteHistoryEntry>,
        entry: NativeRouteHistoryEntry,
    ): List<NativeRouteHistoryEntry> {
        val normalized = normalize(entry) ?: return current
        val others = current.filterNot { samePlace(it, normalized) }
        return Collections.unmodifiableList((listOf(normalized) + others).take(MAX_ENTRIES))
    }

    fun remove(
        current: List<NativeRouteHistoryEntry>,
        entry: NativeRouteHistoryEntry,
    ): List<NativeRouteHistoryEntry> = Collections.unmodifiableList(current.filterNot { it == entry })

    fun decode(raw: String?): List<NativeRouteHistoryEntry> {
        if (raw == null || raw.toByteArray(Charsets.UTF_8).size > MAX_STORED_BYTES) return emptyList()
        val rows = try {
            BoundedJsonParser(raw).parse() as? List<*>
        } catch (_: RuntimeException) {
            null
        } ?: return emptyList()
        val decoded = rows.asSequence()
            .mapNotNull { (it as? Map<*, *>)?.let(::fromMap) }
            .take(MAX_ENTRIES)
            .toList()
        return Collections.unmodifiableList(decoded)
    }

    fun encode(entries: List<NativeRouteHistoryEntry>): String = buildString {
        append('[')
        entries.mapNotNull(::normalize).take(MAX_ENTRIES).forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append("{\"label\":")
            appendJsonString(entry.label)
            append(",\"lat\":").append(decimal(entry.point.latitude))
            append(",\"lon\":").append(decimal(entry.point.longitude))
            append(",\"ts\":").append(entry.startedAtEpochMillis)
            append('}')
        }
        append(']')
    }

    private fun fromMap(value: Map<*, *>): NativeRouteHistoryEntry? {
        val label = value["label"] as? String ?: return null
        val latitude = (value["lat"] as? Number)?.toDouble() ?: return null
        val longitude = (value["lon"] as? Number)?.toDouble() ?: return null
        val timestamp = (value["ts"] as? Number)?.toLong() ?: return null
        return normalize(NativeRouteHistoryEntry(label, NativeMapPoint(latitude, longitude), timestamp))
    }

    private fun normalize(entry: NativeRouteHistoryEntry): NativeRouteHistoryEntry? {
        val label = entry.label.trim()
        val latitude = entry.point.latitude
        val longitude = entry.point.longitude
        if (label.isEmpty() || label.length > MAX_LABEL_CHARS) return null
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return null
        if (entry.startedAtEpochMillis < 0) return null
        return entry.copy(label = label)
    }

    private fun samePlace(a: NativeRouteHistoryEntry, b: NativeRouteHistoryEntry): Boolean =
        GeoMath.distanceMeters(
            GeoPoint(a.point.latitude, a.point.longitude),
            GeoPoint(b.point.latitude, b.point.longitude),
        ) < SAME_PLACE_METRES

    private fun decimal(value: Double): String = BigDecimal.valueOf(value).toPlainString()

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char < ' ' -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
        append('"')
    }
}
