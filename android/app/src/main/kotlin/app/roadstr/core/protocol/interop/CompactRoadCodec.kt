package app.roadstr.core.protocol.interop

import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrSchnorr
import app.roadstr.core.protocol.nostr.RoadstrNostrEvents
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Why a compact frame or a Nostr event was refused. Stable names: the test vectors use them. */
enum class CompactError {
    BAD_LENGTH,
    BAD_VERSION,
    RESERVED_BITS,
    UNKNOWN_TYPE,
    COORDINATE_RANGE,
    BAD_STATUS,
    SIGNATURE_INVALID,
    WRONG_KIND,
    MALFORMED_EVENT,
    NON_EMPTY_CONTENT,
    NON_CANONICAL,
    TIMESTAMP_RANGE,
    COMMENT_TOO_LONG,
    BAD_SPEED_LIMIT,
}

sealed interface CompactOutcome<out T> {
    data class Ok<T>(val value: T) : CompactOutcome<T>

    data class Failed(val error: CompactError) : CompactOutcome<Nothing>
}

sealed interface CompactDecoded {
    /** The reconstructed, signature-verified Nostr event in relay wire form. */
    val event: Map<String, Any?>

    /** The unsigned `ephemeral key` flag: a hint from the sender, never evidence. */
    val ephemeralHint: Boolean
}

data class CompactReport(
    override val event: Map<String, Any?>,
    override val ephemeralHint: Boolean,
    val latitudeE6: Int,
    val longitudeE6: Int,
    val type: CompactRoadType,
) : CompactDecoded

data class CompactConfirmation(
    override val event: Map<String, Any?>,
    override val ephemeralHint: Boolean,
    val referencedEventId: String,
    val stillThere: Boolean,
) : CompactDecoded

/**
 * Nostr kind 1315/1316 road events <-> the frame from nips/road-events.md (jooray/roadbeacon).
 *
 * Report: `lat, lon, g, g, g, t, expiration[, maxspeed]`, six decimals, no `alt`; 117 bytes plus
 * the UTF-8 comment. An encoder writes at most 46 comment bytes (163, what a BLE companion link and
 * MeshCore Open carry); a decoder accepts up to 48 (165, the radio's own limit).
 * Vote: `e, status` only; always 134 bytes and self-contained — no report lookup to decode.
 *
 *     event -> frame -> event  has the same ID, pubkey and signature, or the encoding is refused.
 */
object CompactRoadCodec {
    const val KIND_REPORT = 1315
    const val KIND_CONFIRMATION = 1316

    const val REPORT_BASE_SIZE = 117

    /** The longest comment a frame can carry: what a decoder accepts. */
    const val MAX_COMMENT_BYTES = 48
    const val REPORT_MAX_SIZE = REPORT_BASE_SIZE + MAX_COMMENT_BYTES

    /** The longest comment this encoder writes, so every frame fits a 163-byte transport. */
    const val MAX_ENCODED_COMMENT_BYTES = 46
    const val REPORT_MAX_ENCODED_SIZE = REPORT_BASE_SIZE + MAX_ENCODED_COMMENT_BYTES
    const val CONFIRMATION_SIZE = 134

    /** Relay-side garbage collection floor (NIP-40). */
    const val RELAY_TTL_SECONDS = 1_209_600L

    private const val FLAG_CONFIRMATION = 0x08
    private const val FLAG_EPHEMERAL = 0x04
    private const val FLAG_RESERVED = 0x03
    private const val VERSION_MASK = 0xF0
    private const val VERSION_1 = 0x00
    private const val MAX_UINT32 = 0xFFFF_FFFFL

    fun expirationFor(createdAt: Long, type: CompactRoadType): Long =
        createdAt + maxOf(RELAY_TTL_SECONDS, type.effectiveTtlSeconds)

    // ── canonical event construction ────────────────────────────────────────────────────────────

    fun reportTags(
        latitudeE6: Int,
        longitudeE6: Int,
        type: CompactRoadType,
        expiration: Long,
        speedLimitKmh: Int? = null,
    ): List<List<String>> = buildList {
        add(listOf("lat", CompactCoordinates.format(latitudeE6)))
        add(listOf("lon", CompactCoordinates.format(longitudeE6)))
        addAll(geohashTags(latitudeE6, longitudeE6))
        add(listOf("t", type.wireName))
        add(listOf("expiration", expiration.toString()))
        if (speedLimitKmh != null) add(listOf("maxspeed", speedLimitKmh.toString()))
    }

    fun confirmationTags(eventId: String, stillThere: Boolean): List<List<String>> = listOf(
        listOf("e", eventId),
        listOf("status", if (stillThere) "still_there" else "no_longer_there"),
    )

    fun reportDraft(
        pubkey: String,
        createdAt: Long,
        latitudeE6: Int,
        longitudeE6: Int,
        type: CompactRoadType,
        comment: String = "",
        speedLimitKmh: Int? = null,
    ): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = KIND_REPORT,
        tags = reportTags(
            latitudeE6,
            longitudeE6,
            type,
            expirationFor(createdAt, type),
            speedLimitKmh,
        ),
        content = comment,
    )

    fun confirmationDraft(
        pubkey: String,
        createdAt: Long,
        eventId: String,
        stillThere: Boolean,
    ): NostrEventDraft = NostrEventDraft(
        pubkey = pubkey,
        createdAt = createdAt,
        kind = KIND_CONFIRMATION,
        tags = confirmationTags(eventId, stillThere),
        content = "",
    )

    private fun geohashTags(latitudeE6: Int, longitudeE6: Int): List<List<String>> {
        val latitude = CompactCoordinates.degrees(latitudeE6)
        val longitude = CompactCoordinates.degrees(longitudeE6)
        return (4..6).map { precision ->
            listOf("g", RoadstrNostrEvents.geohash(latitude, longitude, precision))
        }
    }

    // ── frame -> event ──────────────────────────────────────────────────────────────────────────

    /** A report or a vote, as the class bit of the flags byte says. */
    fun decode(data: ByteArray): CompactOutcome<CompactDecoded> {
        if (data.isEmpty()) return CompactOutcome.Failed(CompactError.BAD_LENGTH)
        return if ((data[0].toInt() and FLAG_CONFIRMATION) != 0) decodeConfirmation(data) else decodeReport(data)
    }

    fun decodeReport(data: ByteArray): CompactOutcome<CompactReport> {
        if (data.size !in REPORT_BASE_SIZE..REPORT_MAX_SIZE) {
            return CompactOutcome.Failed(CompactError.BAD_LENGTH)
        }
        val flags = data[0].toInt() and 0xFF
        checkFlags(flags, confirmation = false)?.let { return CompactOutcome.Failed(it) }

        val contentLength = data[52].toInt() and 0xFF
        if (data.size != REPORT_BASE_SIZE + contentLength) {
            return CompactOutcome.Failed(CompactError.BAD_LENGTH)
        }

        val pubkey = hex(data, 1, 33)
        val createdAt = uint32(data, 33)
        val latitude = int32(data, 37)
        val longitude = int32(data, 41)
        val type = CompactRoadType.fromValue(data[45].toInt() and 0xFF)
            ?: return CompactOutcome.Failed(CompactError.UNKNOWN_TYPE)
        if (!CompactCoordinates.inRange(latitude, longitude)) {
            return CompactOutcome.Failed(CompactError.COORDINATE_RANGE)
        }
        val expiration = uint32(data, 46)
        val speedLimit = ((data[50].toInt() and 0xFF) shl 8) or (data[51].toInt() and 0xFF)
        if (speedLimit > 300) return CompactOutcome.Failed(CompactError.BAD_SPEED_LIMIT)
        if (speedLimit in 1..4) return CompactOutcome.Failed(CompactError.BAD_SPEED_LIMIT)

        val content = decodeUtf8(data.copyOfRange(53, 53 + contentLength))
            ?.takeIf(::isPrintable)
            ?: return CompactOutcome.Failed(CompactError.NON_CANONICAL)
        val sigOffset = 53 + contentLength
        if (sigOffset + 64 != data.size) return CompactOutcome.Failed(CompactError.BAD_LENGTH)
        val signature = hex(data, sigOffset, sigOffset + 64)

        val tags = reportTags(
            latitude,
            longitude,
            type,
            expiration,
            if (speedLimit == 0) null else speedLimit,
        )
        val draft = NostrEventDraft(pubkey, createdAt, KIND_REPORT, tags, content)
        if (!verify(draft, signature)) return CompactOutcome.Failed(CompactError.SIGNATURE_INVALID)
        return CompactOutcome.Ok(
            CompactReport(
                event = draft.toWireMap(signature),
                ephemeralHint = (flags and FLAG_EPHEMERAL) != 0,
                latitudeE6 = latitude,
                longitudeE6 = longitude,
                type = type,
            ),
        )
    }

    fun decodeConfirmation(data: ByteArray): CompactOutcome<CompactConfirmation> {
        if (data.size != CONFIRMATION_SIZE) return CompactOutcome.Failed(CompactError.BAD_LENGTH)
        val flags = data[0].toInt() and 0xFF
        checkFlags(flags, confirmation = true)?.let { return CompactOutcome.Failed(it) }

        val pubkey = hex(data, 1, 33)
        val createdAt = uint32(data, 33)
        val eventId = hex(data, 37, 69)
        val statusByte = data[69].toInt() and 0xFF
        if (statusByte !in 0..1) return CompactOutcome.Failed(CompactError.BAD_STATUS)
        val stillThere = statusByte == 1
        val signature = hex(data, 70, 134)

        val draft = NostrEventDraft(
            pubkey,
            createdAt,
            KIND_CONFIRMATION,
            confirmationTags(eventId, stillThere),
            "",
        )
        if (!verify(draft, signature)) return CompactOutcome.Failed(CompactError.SIGNATURE_INVALID)
        return CompactOutcome.Ok(
            CompactConfirmation(
                event = draft.toWireMap(signature),
                ephemeralHint = (flags and FLAG_EPHEMERAL) != 0,
                referencedEventId = eventId,
                stillThere = stillThere,
            ),
        )
    }

    // ── event -> frame ──────────────────────────────────────────────────────────────────────────

    fun encodeReport(event: Map<String, Any?>): CompactOutcome<ByteArray> {
        val kind = (event["kind"] as? Number)?.toInt() ?: return failed(CompactError.WRONG_KIND)
        if (kind != KIND_REPORT) return failed(CompactError.WRONG_KIND)
        val pubkey = event["pubkey"] as? String ?: return failed(CompactError.MALFORMED_EVENT)
        val signature = event["sig"] as? String ?: return failed(CompactError.MALFORMED_EVENT)
        val createdAt = (event["created_at"] as? Number)?.toLong()
            ?: return failed(CompactError.MALFORMED_EVENT)
        if (createdAt !in 0..MAX_UINT32) return failed(CompactError.TIMESTAMP_RANGE)
        if (!isHex(pubkey, 64) || !isHex(signature, 128)) {
            return failed(CompactError.MALFORMED_EVENT)
        }
        val content = event["content"] as? String ?: ""
        val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
        if (contentBytes.size > MAX_ENCODED_COMMENT_BYTES) return failed(CompactError.COMMENT_TOO_LONG)
        if (!isPrintable(content)) return failed(CompactError.NON_CANONICAL)
        val tags = stringTags(event["tags"]) ?: return failed(CompactError.MALFORMED_EVENT)

        val type = tags.firstOrNull { it.size == 2 && it[0] == "t" }
            ?.let { CompactRoadType.fromWireName(it[1]) }
            ?: return failed(CompactError.UNKNOWN_TYPE)
        val latitude = tagValue(tags, "lat")
            ?.let { CompactCoordinates.parseLatitude(it) as? CompactCoordinates.Parsed.Value }
            ?.e6 ?: return failed(CompactError.NON_CANONICAL)
        val longitude = tagValue(tags, "lon")
            ?.let { CompactCoordinates.parseLongitude(it) as? CompactCoordinates.Parsed.Value }
            ?.e6 ?: return failed(CompactError.NON_CANONICAL)
        if (!CompactCoordinates.inRange(latitude, longitude)) {
            return failed(CompactError.COORDINATE_RANGE)
        }
        val expirationText = tagValue(tags, "expiration")
            ?: return failed(CompactError.NON_CANONICAL)
        if (!CANONICAL_INTEGER.matches(expirationText)) return failed(CompactError.NON_CANONICAL)
        val expiration = expirationText.toLongOrNull()?.takeIf { it in 0..MAX_UINT32 }
            ?: return failed(CompactError.TIMESTAMP_RANGE)
        val speedTag = tagValue(tags, "maxspeed")
        val speedLimit = speedTag?.let {
            val value = it.toIntOrNull() ?: return failed(CompactError.BAD_SPEED_LIMIT)
            if (value !in 5..300 || value.toString() != it) {
                return failed(CompactError.BAD_SPEED_LIMIT)
            }
            value
        }

        // Cheapest complete check: rebuild and compare wholesale.
        val expected = reportTags(latitude, longitude, type, expiration, speedLimit)
        if (tags != expected) return failed(CompactError.NON_CANONICAL)

        val frame = ByteBuffer.allocate(REPORT_BASE_SIZE + contentBytes.size)
            .put(0.toByte())
            .put(hexToBytes(pubkey))
            .putInt(createdAt.toInt())
            .putInt(latitude)
            .putInt(longitude)
            .put(type.value.toByte())
            .putInt(expiration.toInt())
            .putShort((speedLimit ?: 0).toShort())
            .put(contentBytes.size.toByte())
            .put(contentBytes)
            .put(hexToBytes(signature))
            .array()

        val decoded = decodeReport(frame)
        if (decoded !is CompactOutcome.Ok) return failed(CompactError.SIGNATURE_INVALID)
        val rebuilt = decoded.value.event
        if (rebuilt["id"] != event["id"] || rebuilt["sig"] != signature) {
            return failed(CompactError.SIGNATURE_INVALID)
        }
        return CompactOutcome.Ok(frame)
    }

    fun encodeConfirmation(event: Map<String, Any?>): CompactOutcome<ByteArray> {
        val kind = (event["kind"] as? Number)?.toInt() ?: return failed(CompactError.WRONG_KIND)
        if (kind != KIND_CONFIRMATION) return failed(CompactError.WRONG_KIND)
        val pubkey = event["pubkey"] as? String ?: return failed(CompactError.MALFORMED_EVENT)
        val signature = event["sig"] as? String ?: return failed(CompactError.MALFORMED_EVENT)
        val createdAt = (event["created_at"] as? Number)?.toLong()
            ?: return failed(CompactError.MALFORMED_EVENT)
        if (createdAt !in 0..MAX_UINT32) return failed(CompactError.TIMESTAMP_RANGE)
        if (!isHex(pubkey, 64) || !isHex(signature, 128)) {
            return failed(CompactError.MALFORMED_EVENT)
        }
        if ((event["content"] as? String ?: "").isNotEmpty()) {
            return failed(CompactError.NON_EMPTY_CONTENT)
        }
        val tags = stringTags(event["tags"]) ?: return failed(CompactError.MALFORMED_EVENT)
        val status = tags.firstOrNull { it.size == 2 && it[0] == "status" }?.get(1)
            ?: return failed(CompactError.MALFORMED_EVENT)
        val stillThere = when (status) {
            "still_there" -> true
            "no_longer_there" -> false
            else -> return failed(CompactError.BAD_STATUS)
        }
        val eventId = tags.firstOrNull { it.size == 2 && it[0] == "e" }?.get(1)
            ?: return failed(CompactError.MALFORMED_EVENT)
        if (!isHex(eventId, 64)) return failed(CompactError.MALFORMED_EVENT)
        if (tags != confirmationTags(eventId, stillThere)) {
            return failed(CompactError.NON_CANONICAL)
        }

        val frame = ByteBuffer.allocate(CONFIRMATION_SIZE)
            .put(FLAG_CONFIRMATION.toByte())
            .put(hexToBytes(pubkey))
            .putInt(createdAt.toInt())
            .put(hexToBytes(eventId))
            .put((if (stillThere) 1 else 0).toByte())
            .put(hexToBytes(signature))
            .array()

        val decoded = decodeConfirmation(frame)
        if (decoded !is CompactOutcome.Ok) return failed(CompactError.SIGNATURE_INVALID)
        if (decoded.value.event["id"] != event["id"]) return failed(CompactError.SIGNATURE_INVALID)
        return CompactOutcome.Ok(frame)
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────────────

    private fun verify(draft: NostrEventDraft, signature: String): Boolean =
        NostrSchnorr.verifyEvent(draft, draft.id(), signature)

    private fun failed(error: CompactError): CompactOutcome<ByteArray> =
        CompactOutcome.Failed(error)

    private fun checkFlags(flags: Int, confirmation: Boolean): CompactError? {
        if (flags and VERSION_MASK != VERSION_1) return CompactError.BAD_VERSION
        if (flags and FLAG_RESERVED != 0) return CompactError.RESERVED_BITS
        val isConfirmation = flags and FLAG_CONFIRMATION != 0
        return if (isConfirmation != confirmation) CompactError.BAD_LENGTH else null
    }

    private fun tagValue(tags: List<List<String>>, name: String): String? =
        tags.firstOrNull { it.size == 2 && it[0] == name }?.get(1)

    @Suppress("UNCHECKED_CAST")
    private fun stringTags(value: Any?): List<List<String>>? {
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

    private fun uint32(data: ByteArray, offset: Int): Long =
        ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)

    private fun int32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun hex(data: ByteArray, from: Int, to: Int): String {
        val builder = StringBuilder((to - from) * 2)
        for (index in from until to) {
            val value = data[index].toInt() and 0xFF
            builder.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return builder.toString()
    }

    private fun hexToBytes(value: String): ByteArray {
        val out = ByteArray(value.length / 2)
        for (index in out.indices) {
            out[index] = value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    private fun isHex(value: String, length: Int): Boolean =
        value.length == length && value.all { (it in '0'..'9') || (it in 'a'..'f') }

    /** No C0 control characters and no DEL: a comment is one line of plain text. */
    private fun isPrintable(comment: String): Boolean =
        comment.none { it.code <= 0x1F || it.code == 0x7F }

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        null
    }

    private const val HEX = "0123456789abcdef"
    private val CANONICAL_INTEGER = Regex("^(0|[1-9][0-9]*)$")
}
