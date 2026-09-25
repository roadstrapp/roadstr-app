package app.roadstr.core.protocol.nostr

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import kotlin.math.abs

/** Deterministic NIP-01 event input. Signing is intentionally a separate gate. */
class NostrEventDraft(
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    tags: List<List<String>>,
    val content: String,
) {
    val tags: List<List<String>> = Collections.unmodifiableList(
        tags.map { tag -> Collections.unmodifiableList(ArrayList(tag)) },
    )

    fun canonicalJson(): String = NostrJson.encode(
        listOf(0, pubkey, createdAt, kind, tags, content),
    )

    fun id(): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson().toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    /** Ordered exactly like nostr_tools Event.toJson(), including an empty Amber signature. */
    fun toWireMap(signature: String = ""): Map<String, Any?> = linkedMapOf(
        "id" to id(),
        "pubkey" to pubkey,
        "created_at" to createdAt,
        "kind" to kind,
        "tags" to tags,
        "content" to content,
        "sig" to signature,
    )
}

enum class RoadCategoryWire(
    val dartName: String,
    val wireKey: String,
    val ttlSeconds: Int,
) {
    POLICE("police", "police", 4 * 3600),
    POLICE_STATION("policeStation", "police_station", 30 * 86400),
    SPEED_CAMERA("speedCamera", "speed_camera", 30 * 86400),
    TRAFFIC_JAM("trafficJam", "traffic_jam", 1 * 3600),
    ACCIDENT("accident", "accident", 4 * 3600),
    ROAD_CLOSURE("roadClosure", "road_closure", 24 * 3600),
    CONSTRUCTION("construction", "construction", 15 * 86400),
    HAZARD("hazard", "hazard", 4 * 3600),
    ROAD_CONDITION("roadCondition", "road_condition", 15 * 86400),
    POTHOLE("pothole", "pothole", 7 * 86400),
    FOG("fog", "fog", 4 * 3600),
    ICE("ice", "ice", 4 * 3600),
    ANIMAL("animal", "animal", 1 * 3600),
    OTHER("other", "other", 4 * 3600),
}

/** Exact Roadstr kind 1315-1318 and profile-visibility layouts. */
object RoadstrNostrEvents {
    fun report(
        pubkey: String,
        createdAt: Long,
        latitude: Double,
        longitude: Double,
        category: String,
        expiresAt: Long,
        content: String,
        speedLimit: Int? = null,
    ): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = 1315,
        tags = buildList {
            add(listOf("lat", formatCoordinate(latitude)))
            add(listOf("lon", formatCoordinate(longitude)))
            add(listOf("g", geohash(latitude, longitude, 4)))
            add(listOf("g", geohash(latitude, longitude, 5)))
            add(listOf("g", geohash(latitude, longitude, 6)))
            add(listOf("t", category))
            add(listOf("expiration", expiresAt.toString()))
            if (speedLimit != null) add(listOf("maxspeed", speedLimit.toString()))
        },
        content = content,
    )

    fun vote(
        pubkey: String,
        createdAt: Long,
        eventId: String,
        stillThere: Boolean,
    ): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = 1316,
        tags = listOf(
            listOf("e", eventId),
            listOf("status", if (stillThere) "still_there" else "no_longer_there"),
        ),
        content = "",
    )

    fun update(
        ownerPubkey: String,
        createdAt: Long,
        eventId: String,
        speedLimit: Int,
        latitude: Double,
        longitude: Double,
        content: String,
        requestId: String? = null,
    ): NostrEventDraft {
        requireSpeedLimit(speedLimit)
        return NostrEventDraft(
            pubkey = ownerPubkey,
            createdAt = createdAt,
            kind = 1317,
            tags = buildList {
                add(listOf("e", eventId))
                add(listOf("p", ownerPubkey))
                add(listOf("g", geohash(latitude, longitude, 4)))
                add(listOf("g", geohash(latitude, longitude, 5)))
                add(listOf("g", geohash(latitude, longitude, 6)))
                add(listOf("maxspeed", speedLimit.toString()))
                if (requestId != null) add(listOf("request", requestId))
            },
            content = content,
        )
    }

    fun editRequest(
        requesterPubkey: String,
        ownerPubkey: String,
        createdAt: Long,
        eventId: String,
        speedLimit: Int,
        latitude: Double,
        longitude: Double,
    ): NostrEventDraft {
        requireSpeedLimit(speedLimit)
        return NostrEventDraft(
            pubkey = requesterPubkey,
            createdAt = createdAt,
            kind = 1318,
            tags = listOf(
                listOf("e", eventId),
                listOf("p", ownerPubkey),
                listOf("g", geohash(latitude, longitude, 4)),
                listOf("g", geohash(latitude, longitude, 5)),
                listOf("g", geohash(latitude, longitude, 6)),
                listOf("maxspeed", speedLimit.toString()),
            ),
            content = "",
        )
    }

    fun profileVisibility(
        pubkey: String,
        createdAt: Long,
        isPublic: Boolean,
    ): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = 30078,
        tags = listOf(
            listOf("d", "roadstr-profile-visibility"),
            listOf("client", "roadstr"),
        ),
        content = NostrJson.encode(linkedMapOf("public" to isPublic)),
    )

    fun geohash(latitude: Double, longitude: Double, precision: Int): String {
        val alphabet = "0123456789bcdefghjkmnpqrstuvwxyz"
        var minLatitude = -90.0
        var maxLatitude = 90.0
        var minLongitude = -180.0
        var maxLongitude = 180.0
        var isLongitude = true
        var bits = 0
        var count = 0
        val result = StringBuilder()
        while (result.length < precision) {
            if (isLongitude) {
                val middle = (minLongitude + maxLongitude) / 2
                if (longitude >= middle) {
                    bits = (bits shl 1) or 1
                    minLongitude = middle
                } else {
                    bits = bits shl 1
                    maxLongitude = middle
                }
            } else {
                val middle = (minLatitude + maxLatitude) / 2
                if (latitude >= middle) {
                    bits = (bits shl 1) or 1
                    minLatitude = middle
                } else {
                    bits = bits shl 1
                    maxLatitude = middle
                }
            }
            isLongitude = !isLongitude
            count++
            if (count == 5) {
                result.append(alphabet[bits])
                bits = 0
                count = 0
            }
        }
        return result.toString()
    }

    private fun formatCoordinate(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val magnitude = BigDecimal.valueOf(abs(value))
            .setScale(6, RoundingMode.HALF_UP)
            .toPlainString()
        val negative = value < 0.0 || value.toRawBits() < 0
        return if (negative) "-$magnitude" else magnitude
    }

    private fun requireSpeedLimit(value: Int) {
        require(value in 5..300) { "Invalid speed limit" }
    }
}

/** NIP-01 frames whose list/map order is locked by the shared wire fixture. */
object NostrRelayWire {
    fun publish(event: Map<String, Any?>): List<Any?> = listOf("EVENT", event)

    fun areaRequest(
        subscriptionId: String,
        geohashes: List<String>,
        now: Long,
    ): List<Any?> = listOf(
        "REQ",
        subscriptionId,
        linkedMapOf(
            "kinds" to listOf(1315, 1317, 1318),
            "#g" to geohashes.toList(),
            "since" to now - 30 * 86400,
            "limit" to 500,
        ),
    )

    fun confirmationRequest(
        subscriptionId: String,
        eventIds: List<String>,
        now: Long,
    ): List<Any?> = listOf(
        "REQ",
        subscriptionId,
        linkedMapOf(
            "kinds" to listOf(1316),
            "#e" to eventIds.toList(),
            "since" to now - 30 * 86400,
            "limit" to 1000,
        ),
    )

    fun close(subscriptionId: String): List<Any?> = listOf("CLOSE", subscriptionId)

    fun encode(message: List<Any?>): String = NostrJson.encode(message)
}

/** Minimal deterministic JSON writer: no floating-point values enter NIP-01 events. */
internal object NostrJson {
    fun encode(value: Any?): String = buildString { appendValue(value) }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean -> append(if (value) "true" else "false")
            is Byte, is Short, is Int, is Long -> append(value.toString())
            is List<*> -> {
                append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    appendValue(item)
                }
                append(']')
            }

            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { index, entry ->
                    require(entry.key is String) { "JSON object keys must be strings" }
                    if (index > 0) append(',')
                    appendString(entry.key as String)
                    append(':')
                    appendValue(entry.value)
                }
                append('}')
            }

            else -> throw IllegalArgumentException(
                "Unsupported deterministic JSON value: ${value::class.java.name}",
            )
        }
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\u000c' -> append("\\f")
                '\r' -> append("\\r")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
    }
}
