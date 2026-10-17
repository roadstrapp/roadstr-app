package app.roadstr.core.protocol.nostr

import app.roadstr.core.journey.JourneyRecord
import app.roadstr.core.journey.JourneyRecordCodec
import app.roadstr.core.journey.JourneySession
import java.security.MessageDigest

data class JourneySyncChunk(
    val deviceId: String,
    val index: Int,
    val sessions: List<JourneySession>,
)

data class JourneySyncManifest(
    val deviceId: String,
    val chunkHashes: Map<Int, String>,
)

/** Privacy-preserving NIP-78 wire format. Public tags contain no trip facts or raw device id. */
object JourneySyncProtocol {
    const val KIND = 30078
    const val NAMESPACE = "roadstr.journey.v1"
    const val SESSIONS_PER_CHUNK = 128
    const val MAX_CHUNKS = 80
    const val MAX_EVENTS_PER_RELAY = 256
    const val MAX_CONTENT_CHARS = 200_000
    private const val CHUNK = "chunk"
    private const val MANIFEST = "manifest"

    fun chunks(record: JourneyRecord, deviceId: String): List<JourneySyncChunk> =
        record.sessions.asSequence()
            .filter { it.deviceId == deviceId }
            .sortedWith(compareBy<JourneySession> { it.startedAtEpochMillis }.thenBy { it.id })
            .chunked(SESSIONS_PER_CHUNK)
            .mapIndexed { index, sessions -> JourneySyncChunk(deviceId, index, sessions) }
            .toList()
            .also { require(it.size <= MAX_CHUNKS) { "Journey device history is too large to sync" } }

    fun encodeChunk(chunk: JourneySyncChunk): String {
        require(chunk.index in 0 until MAX_CHUNKS)
        require(chunk.sessions.size <= SESSIONS_PER_CHUNK)
        require(chunk.sessions.all { it.deviceId == chunk.deviceId })
        return listOf(
            NAMESPACE,
            CHUNK,
            chunk.deviceId,
            chunk.index.toString(),
            JourneyRecordCodec.encode(JourneyRecord(sessions = chunk.sessions)),
        ).joinToString("\n")
    }

    fun decodeChunk(value: String): JourneySyncChunk? = runCatching {
        val fields = value.trim().split('\n')
        if (fields.size != 5 || fields[0] != NAMESPACE || fields[1] != CHUNK) return null
        val index = fields[3].toIntOrNull()?.takeIf { it in 0 until MAX_CHUNKS } ?: return null
        val record = JourneyRecordCodec.decode(fields[4])
        if (record.unlocks.isNotEmpty() || record.sessions.size > SESSIONS_PER_CHUNK) return null
        if (record.sessions.any { it.deviceId != fields[2] }) return null
        JourneySyncChunk(fields[2], index, record.sessions)
    }.getOrNull()

    fun encodeManifest(manifest: JourneySyncManifest): String {
        require(manifest.chunkHashes.size <= MAX_CHUNKS)
        val rows = manifest.chunkHashes.toSortedMap().map { (index, hash) ->
            require(index in 0 until MAX_CHUNKS && HEX_64.matches(hash))
            "$index:$hash"
        }
        return (listOf(NAMESPACE, MANIFEST, manifest.deviceId) + rows).joinToString("\n")
    }

    fun decodeManifest(value: String): JourneySyncManifest? = runCatching {
        val fields = value.trim().split('\n')
        if (fields.size < 3 || fields[0] != NAMESPACE || fields[1] != MANIFEST) return null
        val hashes = LinkedHashMap<Int, String>()
        fields.drop(3).forEach { row ->
            val index = row.substringBefore(':').toIntOrNull()?.takeIf { it in 0 until MAX_CHUNKS }
                ?: return null
            val hash = row.substringAfter(':', "")
            if (!HEX_64.matches(hash) || hashes.put(index, hash) != null) return null
        }
        JourneySyncManifest(fields[2], hashes)
    }.getOrNull()

    fun chunkHash(encodedChunk: String): String = sha256Hex(encodedChunk)

    fun deviceKey(pubkey: String, deviceId: String): String = sha256Hex("$NAMESPACE:$pubkey:$deviceId")

    fun chunkDTag(pubkey: String, deviceId: String, index: Int): String {
        require(index in 0 until MAX_CHUNKS)
        return "$NAMESPACE:${deviceKey(pubkey, deviceId)}:$CHUNK:$index"
    }

    fun manifestDTag(pubkey: String, deviceId: String): String =
        "$NAMESPACE:${deviceKey(pubkey, deviceId)}:$MANIFEST"

    fun draft(pubkey: String, createdAt: Long, dTag: String, encryptedContent: String): NostrEventDraft {
        require(dTag.startsWith("$NAMESPACE:"))
        require(encryptedContent.isNotEmpty() && encryptedContent.length <= MAX_CONTENT_CHARS)
        return NostrEventDraft(
            pubkey = pubkey,
            createdAt = createdAt,
            kind = KIND,
            tags = listOf(listOf("d", dTag), listOf("t", NAMESPACE)),
            content = encryptedContent,
        )
    }

    fun fetchRequest(subscriptionId: String, pubkey: String): List<Any?> = listOf(
        "REQ",
        subscriptionId,
        linkedMapOf(
            "kinds" to listOf(KIND),
            "authors" to listOf(pubkey),
            "#t" to listOf(NAMESPACE),
            "limit" to MAX_EVENTS_PER_RELAY,
        ),
    )

    fun eventIsBound(event: Map<String, Any?>, pubkey: String, verifySignature: () -> Boolean): Boolean {
        val content = event["content"] as? String ?: return false
        if (content.isEmpty() || content.length > MAX_CONTENT_CHARS) return false
        val kind = event["kind"] as? Number ?: return false
        val tags = event["tags"] as? List<*> ?: return false
        val d = tagValue(tags, "d") ?: return false
        val context = tagValue(tags, "t") ?: return false
        return event["pubkey"] == pubkey && kind.toInt() == KIND &&
            d.startsWith("$NAMESPACE:") && context == NAMESPACE && verifySignature()
    }

    fun newestByAddress(events: Iterable<Map<String, Any?>>): Map<String, Map<String, Any?>> {
        val result = LinkedHashMap<String, Map<String, Any?>>()
        events.distinctBy { it["id"] }.forEach { event ->
            val d = NativeTagReader.value(event, "d") ?: return@forEach
            val old = result[d]
            val time = (event["created_at"] as? Number)?.toLong() ?: return@forEach
            val oldTime = (old?.get("created_at") as? Number)?.toLong() ?: Long.MIN_VALUE
            if (old == null || time > oldTime || time == oldTime && event["id"].toString() > old["id"].toString()) {
                result[d] = event
            }
        }
        return result
    }

    fun dTagMatches(pubkey: String, deviceId: String, dTag: String, index: Int?): Boolean =
        if (index == null) dTag == manifestDTag(pubkey, deviceId)
        else dTag == chunkDTag(pubkey, deviceId, index)

    private fun tagValue(tags: List<*>, name: String): String? = tags.firstNotNullOfOrNull { raw ->
        val tag = raw as? List<*> ?: return@firstNotNullOfOrNull null
        if (tag.size >= 2 && tag[0] == name) tag[1] as? String else null
    }

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private val HEX_64 = Regex("^[0-9a-f]{64}$")
}

private object NativeTagReader {
    fun value(event: Map<String, Any?>, name: String): String? =
        (event["tags"] as? List<*>)?.firstNotNullOfOrNull { raw ->
            val tag = raw as? List<*> ?: return@firstNotNullOfOrNull null
            if (tag.size >= 2 && tag[0] == name) tag[1] as? String else null
        }
}
