package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.RoadCategoryWire

/** A community road report (Nostr kind 1315) with its vote tally. */
data class NativeRoadEvent(
    val id: String,
    val pubkey: String,
    val category: RoadCategoryWire,
    val latitude: Double,
    val longitude: Double,
    val comment: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val speedLimit: Int? = null,
    val confirmations: Int = 0,
    val denials: Int = 0,
) {
    fun isExpired(nowSeconds: Long): Boolean =
        nowSeconds >= createdAt + category.ttlSeconds ||
            (expiresAt != null && nowSeconds >= expiresAt)
}

/** Strict reader for relay-supplied kind-1315 events. */
object NativeRoadEventCodec {
    private const val MAX_COMMENT = 500
    private const val MAX_FUTURE_SKEW_SECONDS = 300L

    fun categoryFromKey(key: String): RoadCategoryWire =
        RoadCategoryWire.entries.firstOrNull { it.wireKey == key } ?: RoadCategoryWire.OTHER

    /**
     * Returns null for anything that is not a well-formed, current report.
     * Mirrors RoadEvent.fromNostr: exactly one lat/lon/category tag, at most
     * one expiration and one maxspeed, coordinates in range, comment capped,
     * no timestamps far in the future (they would outlive their TTL), and no
     * report already past its client-side TTL.
     */
    fun parse(json: Map<String, Any?>, nowSeconds: Long): NativeRoadEvent? = runCatching {
        if (NativeNostrWire.integral(json["kind"]) != 1315L) return null
        val id = json["id"] as? String ?: return null
        val pubkey = json["pubkey"] as? String ?: return null
        if (!NativeNostrWire.isHex32(id) || !NativeNostrWire.isHex32(pubkey)) return null
        val createdAt = NativeNostrWire.integral(json["created_at"]) ?: return null
        val tags = NativeNostrWire.tags(json["tags"]) ?: return null

        val lat = ArrayList<String>(1)
        val lon = ArrayList<String>(1)
        val category = ArrayList<String>(1)
        val expiration = ArrayList<String>(1)
        val maxspeed = ArrayList<String>(1)
        for (tag in tags) {
            if (tag.size < 2) continue
            when (tag[0]) {
                "lat" -> lat += tag[1]
                "lon" -> lon += tag[1]
                "t" -> category += tag[1]
                "expiration" -> expiration += tag[1]
                "maxspeed" -> maxspeed += tag[1]
            }
        }
        if (lat.size != 1 || lon.size != 1 || category.size != 1 ||
            expiration.size > 1 || maxspeed.size > 1
        ) {
            return null
        }
        val speedLimit = maxspeed.firstOrNull()?.toIntOrNull()?.takeIf { it in 1..300 }
        val expiresAt = if (expiration.isEmpty()) {
            null
        } else {
            expiration.single().toLongOrNull() ?: return null
        }
        val latitude = lat.single().toDoubleOrNull() ?: return null
        val longitude = lon.single().toDoubleOrNull() ?: return null
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return null

        // Relays are untrusted: a multi-megabyte comment would bloat memory.
        var comment = json["content"] as? String ?: ""
        if (comment.length > MAX_COMMENT) comment = comment.substring(0, MAX_COMMENT) + "…"

        val event = NativeRoadEvent(
            id = id,
            pubkey = pubkey,
            category = categoryFromKey(category.single()),
            latitude = latitude,
            longitude = longitude,
            comment = comment,
            createdAt = createdAt,
            expiresAt = expiresAt,
            speedLimit = speedLimit,
        )
        if (event.createdAt > nowSeconds + MAX_FUTURE_SKEW_SECONDS) return null
        if (event.isExpired(nowSeconds)) return null
        event
    }.getOrNull()
}
