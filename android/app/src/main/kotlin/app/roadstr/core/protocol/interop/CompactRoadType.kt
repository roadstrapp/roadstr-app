package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.RoadCategoryWire

/**
 * The road-event types of the jooray/roadstr compact format (nips/roadstr.md), with the byte used
 * in the binary form and the client-side lifetime a report of that type is shown for.
 *
 * Values 12-254 are reserved by that format. They are NOT mapped to [OTHER]: turning an unknown
 * type into "other" would change the signed `t` tag and the ID with it.
 */
enum class CompactRoadType(
    val value: Int,
    val wireName: String,
    val effectiveTtlSeconds: Long,
) {
    POLICE(0, "police", 2 * HOUR),
    SPEED_CAMERA(1, "speed_camera", 30 * DAY),
    TRAFFIC_JAM(2, "traffic_jam", 1 * HOUR),
    ACCIDENT(3, "accident", 3 * HOUR),
    ROAD_CLOSURE(4, "road_closure", 7 * DAY),
    CONSTRUCTION(5, "construction", 7 * DAY),
    HAZARD(6, "hazard", 4 * HOUR),
    ROAD_CONDITION(7, "road_condition", 6 * HOUR),
    POTHOLE(8, "pothole", 7 * DAY),
    FOG(9, "fog", 3 * HOUR),
    ICE(10, "ice", 6 * HOUR),
    ANIMAL(11, "animal", 1 * HOUR),
    OTHER(255, "other", 2 * HOUR),
    ;

    /** Roadstr's own category with the same wire key; every compact type has exactly one. */
    val nativeCategory: RoadCategoryWire
        get() = RoadCategoryWire.entries.first { it.wireKey == wireName }

    companion object {
        fun fromValue(value: Int): CompactRoadType? = entries.firstOrNull { it.value == value }

        fun fromWireName(name: String): CompactRoadType? = entries.firstOrNull { it.wireName == name }

        /**
         * The compact type for a Roadstr category, or null when the format has no equivalent
         * (Roadstr's "police station" is a long-lived fixture, not an event). Never approximated.
         */
        fun fromNative(category: RoadCategoryWire): CompactRoadType? = fromWireName(category.wireKey)
    }
}

private const val HOUR = 3_600L
private const val DAY = 86_400L
