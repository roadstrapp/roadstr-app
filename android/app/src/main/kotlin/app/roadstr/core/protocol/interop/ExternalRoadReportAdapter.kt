package app.roadstr.core.protocol.interop

/** Which road-event dialect a kind 1315 event is written in, judged from its tags alone. */
enum class RoadEventLayout {
    /** Roadstr's own tags: `lat, lon, g, g, g, t, expiration[, maxspeed]`. */
    ROADSTR_NATIVE,

    /** The jooray/roadstr layout, byte for byte as the format rebuilds it (comment allowed). */
    COMPACT_CANONICAL,

    /** Anything else. Handled by the existing native reader exactly as before. */
    OTHER,
}

/**
 * A kind 1315 report in the jooray/roadstr format, normalised.
 *
 * Keeps what a bridge needs to republish the event unchanged: the ID, key and signature of the
 * author are the ones on the wire, never the receiver's.
 */
data class ExternalRoadReport(
    val eventId: String,
    val pubkey: String,
    val signature: String,
    val latitudeE7: Int,
    val longitudeE7: Int,
    val type: CompactRoadType,
    val createdAt: Long,
    /** NIP-40 time: when relays may drop the event. Not how long the report is shown. */
    val relayExpiration: Long,
    val comment: String,
    /** True when the event can travel in a 110-byte frame; false if it carries a comment. */
    val compactRepresentable: Boolean,
    /**
     * Coordinates were written with a decimal comma. The reference client formats them with the
     * device locale, and reports from Slovak and Czech phones were seen on public relays. The event
     * is genuine and readable, but not the canonical text, so it can never be rebuilt from a frame.
     */
    val decimalCommaVariant: Boolean = false,
)

data class ExternalRoadConfirmation(
    val eventId: String,
    val pubkey: String,
    val signature: String,
    val referencedEventId: String,
    val stillThere: Boolean,
    val createdAt: Long,
    val latitudeE7: Int,
    val longitudeE7: Int,
    val relayExpiration: Long,
    val comment: String,
    val compactRepresentable: Boolean,
    val decimalCommaVariant: Boolean = false,
)

/**
 * Turns relay events of the jooray/roadstr format into Roadstr's internal reasoning, without
 * touching how Roadstr reads its own reports.
 *
 * The caller has already verified ID and signature (the relay path does it before routing); the
 * adapter checks the structure: that the tags are exactly those the format prescribes. An event
 * that merely looks similar is [RoadEventLayout.OTHER] and keeps going through the native reader.
 */
object ExternalRoadReportAdapter {
    private const val MAX_COMMENT = 500

    fun layoutOf(event: Map<String, Any?>): RoadEventLayout {
        val names = tagNames(event["tags"]) ?: return RoadEventLayout.OTHER
        return when {
            names.take(NATIVE_PREFIX.size) == NATIVE_PREFIX -> RoadEventLayout.ROADSTR_NATIVE
            adaptReport(event) is CompactOutcome.Ok -> RoadEventLayout.COMPACT_CANONICAL
            else -> RoadEventLayout.OTHER
        }
    }

    fun adaptReport(event: Map<String, Any?>): CompactOutcome<ExternalRoadReport> {
        val common = commonFields(event, CompactRoadCodec.KIND_REPORT)
            ?: return CompactOutcome.Failed(CompactError.MALFORMED_EVENT)
        val type = CompactRoadType.fromWireName(common.tag("t") ?: "")
            ?: return CompactOutcome.Failed(CompactError.UNKNOWN_TYPE)
        val place = when (val located = locate(common)) {
            is CompactOutcome.Failed -> return located
            is CompactOutcome.Ok -> located.value
        }
        val expected = CompactRoadCodec.reportTags(place.latitudeE7, place.longitudeE7, type, common.createdAt)
        val shape = layoutMatch(common.tags, expected, place.decimalComma)
            ?: return CompactOutcome.Failed(CompactError.NON_CANONICAL)
        return CompactOutcome.Ok(
            ExternalRoadReport(
                eventId = common.id,
                pubkey = common.pubkey,
                signature = common.signature,
                latitudeE7 = place.latitudeE7,
                longitudeE7 = place.longitudeE7,
                type = type,
                createdAt = common.createdAt,
                relayExpiration = common.createdAt + CompactRoadCodec.RELAY_TTL_SECONDS,
                comment = common.content.take(MAX_COMMENT),
                compactRepresentable = shape.exact && common.content.isEmpty(),
                decimalCommaVariant = place.decimalComma,
            ),
        )
    }

    fun adaptConfirmation(event: Map<String, Any?>): CompactOutcome<ExternalRoadConfirmation> {
        val common = commonFields(event, CompactRoadCodec.KIND_CONFIRMATION)
            ?: return CompactOutcome.Failed(CompactError.MALFORMED_EVENT)
        val referenced = common.tag("e")?.takeIf { it.matches(HEX_64) }
            ?: return CompactOutcome.Failed(CompactError.NON_CANONICAL)
        val stillThere = when (common.tag("status")) {
            "still_there" -> true
            "no_longer_there" -> false
            else -> return CompactOutcome.Failed(CompactError.BAD_STATUS)
        }
        val place = when (val located = locate(common)) {
            is CompactOutcome.Failed -> return located
            is CompactOutcome.Ok -> located.value
        }
        val where = CompactReportLocation(referenced, place.latitudeE7, place.longitudeE7)
        val expected = CompactRoadCodec.confirmationDraft(common.pubkey, common.createdAt, where, stillThere).tags
        val shape = layoutMatch(common.tags, expected, place.decimalComma)
            ?: return CompactOutcome.Failed(CompactError.NON_CANONICAL)
        return CompactOutcome.Ok(
            ExternalRoadConfirmation(
                eventId = common.id,
                pubkey = common.pubkey,
                signature = common.signature,
                referencedEventId = referenced,
                stillThere = stillThere,
                createdAt = common.createdAt,
                latitudeE7 = place.latitudeE7,
                longitudeE7 = place.longitudeE7,
                relayExpiration = common.createdAt + CompactRoadCodec.RELAY_TTL_SECONDS,
                comment = common.content.take(MAX_COMMENT),
                compactRepresentable = shape.exact && common.content.isEmpty(),
                decimalCommaVariant = place.decimalComma,
            ),
        )
    }

    /** What the lat/lon tags say, and whether they needed the decimal-comma allowance. */
    private class Place(val latitudeE7: Int, val longitudeE7: Int, val decimalComma: Boolean)

    private class Shape(val exact: Boolean)

    private fun locate(common: Common): CompactOutcome<Place> {
        val latitudeText = common.tag("lat") ?: ""
        val longitudeText = common.tag("lon") ?: ""
        val comma = ',' in latitudeText || ',' in longitudeText
        val latitudeParsed = CompactCoordinates.parseLatitude(latitudeText.replace(',', '.'))
        val longitudeParsed = CompactCoordinates.parseLongitude(longitudeText.replace(',', '.'))
        val latitude = (latitudeParsed as? CompactCoordinates.Parsed.Value)?.e7
            ?: return CompactOutcome.Failed(coordinateError(latitudeParsed))
        val longitude = (longitudeParsed as? CompactCoordinates.Parsed.Value)?.e7
            ?: return CompactOutcome.Failed(coordinateError(longitudeParsed))
        return CompactOutcome.Ok(Place(latitude, longitude, comma))
    }

    /**
     * The tags must be exactly the canonical ones. `alt` may be absent (optional in the NIP). A
     * decimal comma is the one tolerated deviation, and only in the two coordinate tags: every
     * other tag, including the geohashes recomputed from the coordinates, must match as ever.
     */
    private fun layoutMatch(actual: List<List<String>>, expected: List<List<String>>, comma: Boolean): Shape? {
        val wanted = if (comma) expected.map(::commaCoordinates) else expected
        return when (actual) {
            wanted -> Shape(exact = !comma)
            wanted.dropLast(1) -> Shape(exact = false)
            else -> null
        }
    }

    private fun commaCoordinates(tag: List<String>): List<String> =
        if (tag.size == 2 && (tag[0] == "lat" || tag[0] == "lon")) listOf(tag[0], tag[1].replace('.', ',')) else tag

    /** Out of the planet is a range error; anything not in the canonical spelling is a layout error. */
    private fun coordinateError(parsed: CompactCoordinates.Parsed): CompactError =
        if (parsed == CompactCoordinates.Parsed.OutOfRange) CompactError.COORDINATE_RANGE else CompactError.NON_CANONICAL

    /** Position of the report an external confirmation was cast on, as that confirmation repeats it. */
    fun locationOf(report: ExternalRoadReport): CompactReportLocation =
        CompactReportLocation(report.eventId, report.latitudeE7, report.longitudeE7)

    private class Common(
        val id: String,
        val pubkey: String,
        val signature: String,
        val createdAt: Long,
        val content: String,
        val tags: List<List<String>>,
    ) {
        fun tag(name: String): String? =
            tags.filter { it.size >= 2 && it[0] == name }.singleOrNull()?.get(1)
    }

    private fun commonFields(event: Map<String, Any?>, kind: Int): Common? {
        val eventKind = event["kind"]
        val kindMatches = (eventKind as? Long) == kind.toLong() || (eventKind as? Int) == kind
        val id = event["id"] as? String
        val pubkey = event["pubkey"] as? String
        val signature = event["sig"] as? String
        val content = event["content"] as? String
        val createdAt = (event["created_at"] as? Long) ?: (event["created_at"] as? Int)?.toLong()
        val tags = tagLists(event["tags"])
        val ok = kindMatches && id != null && pubkey != null && signature != null && content != null &&
            createdAt != null && tags != null && id.matches(HEX_64) && pubkey.matches(HEX_64) &&
            signature.matches(HEX_128) && createdAt in 0..MAX_UINT32
        return if (ok) Common(id!!, pubkey!!, signature!!, createdAt!!, content!!, tags!!) else null
    }

    private fun tagLists(raw: Any?): List<List<String>>? {
        val outer = raw as? List<*> ?: return null
        return outer.map { tag ->
            val inner = tag as? List<*> ?: return null
            inner.map { it as? String ?: return null }
        }
    }

    private fun tagNames(raw: Any?): List<String>? = tagLists(raw)?.map { it.firstOrNull() ?: "" }

    private val NATIVE_PREFIX = listOf("lat", "lon", "g", "g", "g", "t")
    private val HEX_64 = Regex("^[0-9a-f]{64}\$")
    private val HEX_128 = Regex("^[0-9a-f]{128}\$")
    private const val MAX_UINT32 = 0xFFFF_FFFFL
}
