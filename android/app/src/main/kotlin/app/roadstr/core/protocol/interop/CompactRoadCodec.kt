package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents

/** Why a compact frame or a Nostr event was refused. Stable names: the test vectors use them. */
enum class CompactError {
    BAD_LENGTH,
    BAD_VERSION,
    RESERVED_BITS,
    UNKNOWN_TYPE,
    COORDINATE_RANGE,
    BAD_STATUS,
    REFERENCE_MISMATCH,
    SIGNATURE_INVALID,
    WRONG_KIND,
    MALFORMED_EVENT,
    NON_EMPTY_CONTENT,
    NON_CANONICAL,
    TIMESTAMP_RANGE,
}

sealed interface CompactOutcome<out T> {
    data class Ok<T>(val value: T) : CompactOutcome<T>

    data class Failed(val error: CompactError) : CompactOutcome<Nothing>
}

enum class CompactTolerance {
    STRICT,

    /**
     * Also accept a frame whose coordinates are one unit short of what the sender signed, which is
     * what the jooray reference encoder produces for ~12% of reports (it truncates `lat * 1e7`).
     * Costs at most eight extra signature checks, and every accepted result is still a fully
     * verified signature over the exact event, so no forgery becomes possible.
     */
    RECOVER_REFERENCE_TRUNCATION,
}

/** The report a confirmation points at: its coordinates are what the confirmation's tags repeat. */
class CompactReportLocation(val eventId: String, val latitudeE7: Int, val longitudeE7: Int)

sealed interface CompactDecoded {
    /** The reconstructed, signature-verified Nostr event in relay wire form. */
    val event: Map<String, Any?>

    /** The unsigned `ephemeral key` flag: a hint from the sender, never evidence. */
    val ephemeralHint: Boolean
}

data class CompactReport(
    override val event: Map<String, Any?>,
    override val ephemeralHint: Boolean,
    val latitudeE7: Int,
    val longitudeE7: Int,
    val type: CompactRoadType,
    val recoveredFromTruncation: Boolean = false,
) : CompactDecoded

data class CompactConfirmation(
    override val event: Map<String, Any?>,
    override val ephemeralHint: Boolean,
    val referencedEventId: String,
    val stillThere: Boolean,
) : CompactDecoded

enum class CompactFrameKind { REPORT, CONFIRMATION }

/**
 * Nostr kind 1315/1316 road events <-> the 110/134-byte frame meant for MeshCore channel datagrams.
 *
 * The frame carries only what cannot be derived (key, time, position, type, signature). Everything
 * else (geohashes, expiry, `alt`, tag order) is rebuilt by fixed rules, and the signature is checked
 * against the rebuilt ID. So the property that matters is
 *
 *     event -> frame -> event  has the same ID, pubkey and signature, or the encoding is refused.
 *
 * Nothing here can be tuned to "close enough": a different tag order, a decimal more or a comma
 * instead of a dot is a different ID and an invalid signature. See
 * docs/interoperability/MESHCORE_NOSTR.md for the format and where its two source documents disagree.
 */
object CompactRoadCodec {
    const val KIND_REPORT = 1315
    const val KIND_CONFIRMATION = 1316
    const val REPORT_SIZE = 110
    const val CONFIRMATION_SIZE = 134

    /** Relay-side garbage collection time (NIP-40), fixed for every event of the format. */
    const val RELAY_TTL_SECONDS = 1_209_600L

    private const val FLAG_CONFIRMATION = 0x08
    private const val FLAG_EPHEMERAL = 0x04
    private const val FLAG_RESERVED = 0x03
    private const val MAX_UINT32 = 0xFFFF_FFFFL
    private const val STATUS_STILL_THERE = "still_there"
    private const val STATUS_NO_LONGER_THERE = "no_longer_there"

    // ── canonical event construction ────────────────────────────────────────────────────────────

    fun reportTags(
        latitudeE7: Int,
        longitudeE7: Int,
        type: CompactRoadType,
        createdAt: Long,
    ): List<List<String>> = buildList {
        add(listOf("t", type.wireName))
        addAll(geohashTags(latitudeE7, longitudeE7))
        add(listOf("lat", CompactCoordinates.format(latitudeE7)))
        add(listOf("lon", CompactCoordinates.format(longitudeE7)))
        add(listOf("expiration", (createdAt + RELAY_TTL_SECONDS).toString()))
        add(listOf("alt", "Roadstr: ${type.wireName} report"))
    }

    fun confirmationTags(
        referencedEventId: String,
        latitudeE7: Int,
        longitudeE7: Int,
        stillThere: Boolean,
        createdAt: Long,
    ): List<List<String>> = buildList {
        add(listOf("e", referencedEventId))
        addAll(geohashTags(latitudeE7, longitudeE7))
        add(listOf("status", if (stillThere) STATUS_STILL_THERE else STATUS_NO_LONGER_THERE))
        add(listOf("lat", CompactCoordinates.format(latitudeE7)))
        add(listOf("lon", CompactCoordinates.format(longitudeE7)))
        add(listOf("expiration", (createdAt + RELAY_TTL_SECONDS).toString()))
        add(listOf("alt", if (stillThere) "Roadstr: event confirmed" else "Roadstr: event denied"))
    }

    /**
     * An unsigned report in the compact layout, for a signer to sign. With the default empty
     * [comment] it is always frame-encodable; with one it is still a valid event for relays (the
     * NIP lets `content` carry a plain-text comment) but cannot travel in a frame.
     */
    fun reportDraft(
        pubkey: String,
        createdAt: Long,
        latitudeE7: Int,
        longitudeE7: Int,
        type: CompactRoadType,
        comment: String = "",
    ): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = KIND_REPORT,
        tags = reportTags(latitudeE7, longitudeE7, type, createdAt),
        content = comment,
    )

    fun confirmationDraft(
        pubkey: String,
        createdAt: Long,
        location: CompactReportLocation,
        stillThere: Boolean,
    ): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = KIND_CONFIRMATION,
        tags = confirmationTags(location.eventId, location.latitudeE7, location.longitudeE7, stillThere, createdAt),
        content = "",
    )

    private fun geohashTags(latitudeE7: Int, longitudeE7: Int): List<List<String>> {
        val latitude = CompactCoordinates.degrees(latitudeE7)
        val longitude = CompactCoordinates.degrees(longitudeE7)
        return (4..6).map { precision ->
            listOf("g", RoadstrNostrEvents.geohash(latitude, longitude, precision))
        }
    }

    // ── frame inspection (no cryptography) ──────────────────────────────────────────────────────

    /** Report or confirmation by length and class bit; null when it is neither. */
    fun kindOf(data: ByteArray): CompactFrameKind? {
        if (data.isEmpty()) return null
        val confirmation = (data[0].toInt() and FLAG_CONFIRMATION) != 0
        return when {
            data.size == REPORT_SIZE && !confirmation -> CompactFrameKind.REPORT
            data.size == CONFIRMATION_SIZE && confirmation -> CompactFrameKind.CONFIRMATION
            else -> null
        }
    }

    /** The report ID a confirmation frame points at, so the caller can look the report up. */
    fun referencedEventId(data: ByteArray): String? =
        if (kindOf(data) == CompactFrameKind.CONFIRMATION) hex(data, 37, 69) else null

    // ── frame -> event ──────────────────────────────────────────────────────────────────────────

    fun decodeReport(
        data: ByteArray,
        tolerance: CompactTolerance = CompactTolerance.STRICT,
    ): CompactOutcome<CompactReport> {
        val failure = checkFrame(data, CompactFrameKind.REPORT)
        if (failure != null) return CompactOutcome.Failed(failure)
        val type = CompactRoadType.fromValue(data[45].toInt() and 0xFF)
            ?: return CompactOutcome.Failed(CompactError.UNKNOWN_TYPE)
        val latitude = int32(data, 37)
        val longitude = int32(data, 41)
        if (!CompactCoordinates.inRange(latitude, longitude)) {
            return CompactOutcome.Failed(CompactError.COORDINATE_RANGE)
        }
        val pubkey = hex(data, 1, 33)
        val createdAt = uint32(data, 33)
        val signature = hex(data, 46, 110)
        val ephemeral = (data[0].toInt() and FLAG_EPHEMERAL) != 0

        val exact = rebuildReport(pubkey, createdAt, latitude, longitude, type, signature)
        if (exact != null) return CompactOutcome.Ok(CompactReport(exact, ephemeral, latitude, longitude, type))
        if (tolerance == CompactTolerance.STRICT) return CompactOutcome.Failed(CompactError.SIGNATURE_INVALID)
        return recoverTruncatedReport(pubkey, createdAt, latitude, longitude, type, signature, ephemeral)
    }

    /**
     * [location] must be the report named by [referencedEventId] (look it up first): the frame has
     * no coordinates, and the signature only verifies against the report's own.
     */
    fun decodeConfirmation(
        data: ByteArray,
        location: CompactReportLocation,
    ): CompactOutcome<CompactConfirmation> {
        val failure = checkFrame(data, CompactFrameKind.CONFIRMATION)
        if (failure != null) return CompactOutcome.Failed(failure)
        val referenced = hex(data, 37, 69)
        if (referenced != location.eventId) return CompactOutcome.Failed(CompactError.REFERENCE_MISMATCH)
        val status = data[69].toInt() and 0xFF
        if (status > 1) return CompactOutcome.Failed(CompactError.BAD_STATUS)
        if (!CompactCoordinates.inRange(location.latitudeE7, location.longitudeE7)) {
            return CompactOutcome.Failed(CompactError.COORDINATE_RANGE)
        }
        val draft = confirmationDraft(hex(data, 1, 33), uint32(data, 33), location, stillThere = status == 1)
        val event = verified(draft, hex(data, 70, 134))
            ?: return CompactOutcome.Failed(CompactError.SIGNATURE_INVALID)
        val ephemeral = (data[0].toInt() and FLAG_EPHEMERAL) != 0
        return CompactOutcome.Ok(CompactConfirmation(event, ephemeral, referenced, status == 1))
    }

    private fun checkFrame(data: ByteArray, expected: CompactFrameKind): CompactError? {
        if (kindOf(data) != expected) return CompactError.BAD_LENGTH
        val flags = data[0].toInt() and 0xFF
        if ((flags ushr 4) != 0) return CompactError.BAD_VERSION
        if ((flags and FLAG_RESERVED) != 0) return CompactError.RESERVED_BITS
        return null
    }

    private fun rebuildReport(
        pubkey: String,
        createdAt: Long,
        latitudeE7: Int,
        longitudeE7: Int,
        type: CompactRoadType,
        signature: String,
    ): Map<String, Any?>? =
        verified(reportDraft(pubkey, createdAt, latitudeE7, longitudeE7, type), signature)

    private fun recoverTruncatedReport(
        pubkey: String,
        createdAt: Long,
        latitudeE7: Int,
        longitudeE7: Int,
        type: CompactRoadType,
        signature: String,
        ephemeral: Boolean,
    ): CompactOutcome<CompactReport> {
        for (latitude in truncationCandidates(latitudeE7)) {
            for (longitude in truncationCandidates(longitudeE7)) {
                val skip = latitude == latitudeE7 && longitude == longitudeE7
                if (skip || !CompactCoordinates.inRange(latitude, longitude)) continue
                val event = rebuildReport(pubkey, createdAt, latitude, longitude, type, signature) ?: continue
                val report = CompactReport(event, ephemeral, latitude, longitude, type, recoveredFromTruncation = true)
                return CompactOutcome.Ok(report)
            }
        }
        return CompactOutcome.Failed(CompactError.SIGNATURE_INVALID)
    }

    /** Truncation moves a value one unit towards zero, so the signed one is one unit further out. */
    private fun truncationCandidates(truncated: Int): List<Int> = when {
        truncated > 0 -> listOf(truncated, truncated + 1)
        truncated < 0 -> listOf(truncated, truncated - 1)
        else -> listOf(0, 1, -1)
    }

    private fun verified(draft: NostrEventDraft, signature: String): Map<String, Any?>? {
        val id = draft.id()
        return if (NostrSchnorr.verifyHash(draft.pubkey, id, signature)) draft.toWireMap(signature) else null
    }

    // ── event -> frame ──────────────────────────────────────────────────────────────────────────

    /** Encodes a signed kind 1315 or 1316 event, or says exactly why it cannot be done. */
    fun encode(event: Map<String, Any?>, ephemeralHint: Boolean = false): CompactOutcome<ByteArray> =
        when (integral(event["kind"])) {
            KIND_REPORT.toLong() -> encodeReport(event, ephemeralHint)
            KIND_CONFIRMATION.toLong() -> encodeConfirmation(event, null, ephemeralHint)
            else -> CompactOutcome.Failed(CompactError.WRONG_KIND)
        }

    fun encodeReport(event: Map<String, Any?>, ephemeralHint: Boolean = false): CompactOutcome<ByteArray> =
        attempt {
            val header = parseHeader(event, KIND_REPORT)
            val type = CompactRoadType.fromWireName(header.tag("t") ?: "") ?: refuse(CompactError.UNKNOWN_TYPE)
            val latitude = coordinate(CompactCoordinates.parseLatitude(header.tag("lat") ?: ""))
            val longitude = coordinate(CompactCoordinates.parseLongitude(header.tag("lon") ?: ""))
            requireRoundTrip(reportDraft(header.pubkey, header.createdAt, latitude, longitude, type), header)

            val frame = ByteArray(REPORT_SIZE)
            frame[0] = flags(confirmation = false, ephemeralHint).toByte()
            copyHex(header.pubkey, frame, 1)
            putUint32(frame, 33, header.createdAt)
            putInt32(frame, 37, latitude)
            putInt32(frame, 41, longitude)
            frame[45] = type.value.toByte()
            copyHex(header.signature, frame, 46)
            frame
        }

    /**
     * [location] is the referenced report when the caller has it; with it, a confirmation that
     * repeats different coordinates than its report is refused instead of being sent to fail
     * verification at every receiver.
     */
    fun encodeConfirmation(
        event: Map<String, Any?>,
        location: CompactReportLocation?,
        ephemeralHint: Boolean = false,
    ): CompactOutcome<ByteArray> = attempt {
        val header = parseHeader(event, KIND_CONFIRMATION)
        val referenced = header.tag("e")?.takeIf { isLowerHex(it, 64) } ?: refuse(CompactError.NON_CANONICAL)
        val stillThere = when (header.tag("status")) {
            STATUS_STILL_THERE -> true
            STATUS_NO_LONGER_THERE -> false
            else -> refuse(CompactError.NON_CANONICAL)
        }
        val latitude = coordinate(CompactCoordinates.parseLatitude(header.tag("lat") ?: ""))
        val longitude = coordinate(CompactCoordinates.parseLongitude(header.tag("lon") ?: ""))
        if (location != null && (location.latitudeE7 != latitude || location.longitudeE7 != longitude)) {
            refuse(CompactError.REFERENCE_MISMATCH)
        }
        val place = CompactReportLocation(referenced, latitude, longitude)
        requireRoundTrip(confirmationDraft(header.pubkey, header.createdAt, place, stillThere), header)

        val frame = ByteArray(CONFIRMATION_SIZE)
        frame[0] = flags(confirmation = true, ephemeralHint).toByte()
        copyHex(header.pubkey, frame, 1)
        putUint32(frame, 33, header.createdAt)
        copyHex(referenced, frame, 37)
        frame[69] = if (stillThere) 1 else 0
        copyHex(header.signature, frame, 70)
        frame
    }

    /** What the event claims to be must be exactly what the frame would rebuild, signature included. */
    private fun requireRoundTrip(rebuilt: NostrEventDraft, claimed: Header) {
        if (rebuilt.tags != claimed.tags) refuse(CompactError.NON_CANONICAL)
        if (rebuilt.id() != claimed.id) refuse(CompactError.SIGNATURE_INVALID)
        if (!NostrSchnorr.verifyHash(claimed.pubkey, claimed.id, claimed.signature)) {
            refuse(CompactError.SIGNATURE_INVALID)
        }
    }

    private fun coordinate(parsed: CompactCoordinates.Parsed): Int = when (parsed) {
        is CompactCoordinates.Parsed.Value -> parsed.e7
        CompactCoordinates.Parsed.OutOfRange -> refuse(CompactError.COORDINATE_RANGE)
        CompactCoordinates.Parsed.Malformed -> refuse(CompactError.NON_CANONICAL)
    }

    /** Refusals travel as a stackless exception so the encoders stay a flat list of checks. */
    private class Refusal(val error: CompactError) : RuntimeException(error.name, null, false, false)

    private fun refuse(error: CompactError): Nothing = throw Refusal(error)

    private inline fun <T> attempt(block: () -> T): CompactOutcome<T> = try {
        CompactOutcome.Ok(block())
    } catch (refusal: Refusal) {
        CompactOutcome.Failed(refusal.error)
    }

    private class Header(
        val id: String,
        val pubkey: String,
        val signature: String,
        val createdAt: Long,
        val tags: List<List<String>>,
    ) {
        /** The value of the single tag named [name]; null if absent or repeated. */
        fun tag(name: String): String? =
            tags.filter { it.size >= 2 && it[0] == name }.singleOrNull()?.get(1)
    }

    private fun parseHeader(event: Map<String, Any?>, kind: Int): Header {
        if (integral(event["kind"]) != kind.toLong()) refuse(CompactError.WRONG_KIND)
        val id = event["id"] as? String
        val pubkey = event["pubkey"] as? String
        val signature = event["sig"] as? String
        val content = event["content"] as? String
        val tags = tagList(event["tags"])
        val createdAt = integral(event["created_at"])
        val shapeOk = id != null && pubkey != null && signature != null && content != null &&
            tags != null && createdAt != null &&
            isLowerHex(id, 64) && isLowerHex(pubkey, 64) && isLowerHex(signature, 128)
        if (!shapeOk) refuse(CompactError.MALFORMED_EVENT)
        if (createdAt!! !in 0..MAX_UINT32) refuse(CompactError.TIMESTAMP_RANGE)
        if (content!!.isNotEmpty()) refuse(CompactError.NON_EMPTY_CONTENT)
        return Header(id!!, pubkey!!, signature!!, createdAt, tags!!)
    }

    private fun tagList(raw: Any?): List<List<String>>? {
        val outer = raw as? List<*> ?: return null
        return outer.map { tag ->
            val inner = tag as? List<*> ?: return null
            inner.map { it as? String ?: return null }
        }
    }

    // ── byte helpers ────────────────────────────────────────────────────────────────────────────

    private fun flags(confirmation: Boolean, ephemeral: Boolean): Int =
        (if (confirmation) FLAG_CONFIRMATION else 0) or (if (ephemeral) FLAG_EPHEMERAL else 0)

    private fun integral(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        else -> null
    }

    private fun isLowerHex(value: String, length: Int): Boolean =
        value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun hex(data: ByteArray, from: Int, to: Int): String {
        val out = StringBuilder((to - from) * 2)
        for (index in from until to) {
            val byte = data[index].toInt() and 0xFF
            out.append(HEX[byte ushr 4]).append(HEX[byte and 0x0F])
        }
        return out.toString()
    }

    private fun copyHex(hexString: String, into: ByteArray, offset: Int) {
        for (index in 0 until hexString.length / 2) {
            val high = Character.digit(hexString[index * 2], 16)
            val low = Character.digit(hexString[index * 2 + 1], 16)
            into[offset + index] = ((high shl 4) or low).toByte()
        }
    }

    private fun uint32(data: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 4) value = (value shl 8) or (data[offset + index].toLong() and 0xFF)
        return value
    }

    private fun int32(data: ByteArray, offset: Int): Int = uint32(data, offset).toInt()

    private fun putUint32(into: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 4) into[offset + index] = (value ushr (24 - 8 * index)).toByte()
    }

    private fun putInt32(into: ByteArray, offset: Int, value: Int) = putUint32(into, offset, value.toLong() and MAX_UINT32)

    private const val HEX = "0123456789abcdef"
}
