package app.roadstr.core.protocol.interop

/**
 * A kind 1315 report in the shared layout, normalised for the radio path.
 *
 * Keeps what a bridge needs to republish the event unchanged: the ID, key and signature of the
 * author are the ones on the wire, never the receiver's.
 */
data class ExternalRoadReport(
    val eventId: String,
    val pubkey: String,
    val signature: String,
    val latitudeE6: Int,
    val longitudeE6: Int,
    val type: CompactRoadType,
    val createdAt: Long,
    /** NIP-40 time: when relays may drop the event, and the latest the marker may be shown. */
    val relayExpiration: Long,
    val comment: String,
)

/**
 * Reads a kind 1315 event of the shared layout (`lat, lon, g, g, g, t, expiration[, maxspeed]`).
 * The caller has already verified ID and signature; whether the event fits in a frame is decided
 * by [CompactRoadCodec.encodeReport] alone.
 */
object ExternalRoadReportAdapter {
    private const val MAX_COMMENT = 500

    fun adaptReport(event: Map<String, Any?>): CompactOutcome<ExternalRoadReport> {
        val kind = (event["kind"] as? Number)?.toInt()
        if (kind != CompactRoadCodec.KIND_REPORT) return CompactOutcome.Failed(CompactError.WRONG_KIND)
        val id = event["id"] as? String ?: return CompactOutcome.Failed(CompactError.MALFORMED_EVENT)
        val pubkey = event["pubkey"] as? String ?: return CompactOutcome.Failed(CompactError.MALFORMED_EVENT)
        val signature = event["sig"] as? String ?: return CompactOutcome.Failed(CompactError.MALFORMED_EVENT)
        val createdAt = (event["created_at"] as? Number)?.toLong()
            ?: return CompactOutcome.Failed(CompactError.MALFORMED_EVENT)
        val tags = tagsOf(event["tags"]) ?: return CompactOutcome.Failed(CompactError.MALFORMED_EVENT)
        val type = tag(tags, "t")?.let { CompactRoadType.fromWireName(it) }
            ?: return CompactOutcome.Failed(CompactError.UNKNOWN_TYPE)
        val latitude = (CompactCoordinates.parseLatitude(tag(tags, "lat") ?: "")
            as? CompactCoordinates.Parsed.Value)?.e6
            ?: return CompactOutcome.Failed(CompactError.NON_CANONICAL)
        val longitude = (CompactCoordinates.parseLongitude(tag(tags, "lon") ?: "")
            as? CompactCoordinates.Parsed.Value)?.e6
            ?: return CompactOutcome.Failed(CompactError.NON_CANONICAL)
        val expiration = tag(tags, "expiration")?.toLongOrNull()
            ?: return CompactOutcome.Failed(CompactError.NON_CANONICAL)
        val content = event["content"] as? String ?: ""
        return CompactOutcome.Ok(
            ExternalRoadReport(
                eventId = id,
                pubkey = pubkey,
                signature = signature,
                latitudeE6 = latitude,
                longitudeE6 = longitude,
                type = type,
                createdAt = createdAt,
                relayExpiration = expiration,
                comment = content.take(MAX_COMMENT),
            ),
        )
    }

    private fun tag(tags: List<List<String>>, name: String): String? =
        tags.firstOrNull { it.size == 2 && it[0] == name }?.get(1)

    private fun tagsOf(value: Any?): List<List<String>>? {
        val rows = value as? List<*> ?: return null
        val result = ArrayList<List<String>>(rows.size)
        for (row in rows) {
            val cells = row as? List<*> ?: return null
            val strings = ArrayList<String>(cells.size)
            for (cell in cells) strings.add(cell as? String ?: return null)
            result.add(strings)
        }
        return result
    }
}
