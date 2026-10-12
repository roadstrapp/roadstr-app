package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.RoadCategoryWire
import kotlin.math.abs

/** What identifies an incident independently of the event that reported it. */
data class IncidentKey(
    val pubkey: String,
    val category: RoadCategoryWire,
    val createdAt: Long,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Logical equivalence: two different signed events that are one report, e.g. the native Roadstr
 * event and the compatible kind 1315 Roadstr publishes next to it.
 *
 * The rule is deliberately narrow. Two events are the same incident only if the SAME KEY signed
 * both, within a few minutes, for the same category and the same spot. Different authors are never
 * merged, however close: two drivers reporting the same police car are two voices, and merging them
 * would let anyone suppress or inflate a report by posting next to it.
 *
 * Coordinates are compared with a tolerance of one micro-degree (~11 cm) because the native event
 * prints six decimals and the compatible one seven; the same GPS reading rounded two ways differs
 * by at most that.
 */
object RoadEventEquivalence {
    const val MAX_TIME_DELTA_SECONDS = 300L
    const val COORDINATE_TOLERANCE_DEGREES = 0.0000011

    fun sameIncident(a: IncidentKey, b: IncidentKey): Boolean =
        a.pubkey == b.pubkey &&
            a.category == b.category &&
            abs(a.createdAt - b.createdAt) <= MAX_TIME_DELTA_SECONDS &&
            abs(a.latitude - b.latitude) <= COORDINATE_TOLERANCE_DEGREES &&
            abs(a.longitude - b.longitude) <= COORDINATE_TOLERANCE_DEGREES
}
