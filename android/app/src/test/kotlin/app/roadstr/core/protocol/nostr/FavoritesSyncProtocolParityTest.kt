package app.roadstr.core.protocol.nostr

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesSyncProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/favorites_sync_protocol_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native NIP-78 core reproduces every Dart case`() {
        assertEquals(72, rows.size)
        for (fields in rows) {
            val operation = fields[0]
            val caseName = fields[1]
            val payload = decodeObject(fields[2])
            val expectedJson = decodeText(fields[3])
            val actual = evaluate(operation, payload)
            assertEquals("$operation/$caseName", expectedJson, NostrJson.encode(actual))
        }
    }

    private fun evaluate(
        operation: String,
        payload: Map<String, Any?>,
    ): Map<String, Any?> = when (operation) {
        "relay" -> linkedMapOf(
            "normalized" to FavoritesSyncProtocol.normaliseRelayUrl(payload.string("input")),
        )

        "d_tag" -> linkedMapOf(
            "dTag" to FavoritesSyncProtocol.hashedDTag(payload.string("pubkey")),
        )

        "padding" -> {
            val value = payload.materialize()
            try {
                val padded = FavoritesSyncProtocol.padToBucket(value)
                linkedMapOf(
                    "accepted" to true,
                    "byteLength" to padded.toByteArray(Charsets.UTF_8).size,
                    "unchanged" to (padded == value),
                )
            } catch (_: IllegalArgumentException) {
                linkedMapOf(
                    "accepted" to false,
                    "byteLength" to 0,
                    "unchanged" to false,
                )
            }
        }

        "timestamp" -> linkedMapOf(
            "createdAt" to FavoritesSyncProtocol.nextCreatedAt(
                nowUnixSeconds = payload.long("now"),
                lastCreatedAt = payload.long("last"),
            ),
        )

        "snapshot_draft" -> linkedMapOf(
            "wire" to FavoritesSyncProtocol.snapshotDraft(
                pubkey = payload.string("pubkey"),
                createdAt = payload.long("createdAt"),
                encryptedContent = payload.string("content"),
            ).toWireMap(),
        )

        "legacy_wipe_draft" -> linkedMapOf(
            "wire" to FavoritesSyncProtocol.legacyWipeDraft(
                pubkey = payload.string("pubkey"),
                createdAt = payload.long("createdAt"),
            ).toWireMap(),
        )

        "legacy_deletion_draft" -> linkedMapOf(
            "wire" to FavoritesSyncProtocol.legacyDeletionDraft(
                pubkey = payload.string("pubkey"),
                createdAt = payload.long("createdAt"),
            ).toWireMap(),
        )

        "fetch_request" -> linkedMapOf(
            "wire" to FavoritesSyncProtocol.fetchRequest(
                subscriptionId = payload.string("subscriptionId"),
                pubkey = payload.string("pubkey"),
                dTag = payload.string("dTag"),
            ),
        )

        "event_binding" -> evaluateBinding(payload)

        "newest" -> {
            val events = payload.list("events").map { value ->
                (value as? Map<*, *>)?.toStringKeyMap()
            }
            linkedMapOf("id" to FavoritesSyncProtocol.newestSnapshot(events)?.get("id"))
        }

        "rollback" -> linkedMapOf(
            "accepted" to FavoritesSyncProtocol.passesRollbackGuard(
                fetchedCreatedAt = payload.long("fetched"),
                lastCreatedAt = payload.optionalLong("last"),
            ),
        )

        "favorites_json" -> linkedMapOf(
            "json" to FavoritesSyncProtocol.encodeFavorites(
                payload.list("favorites").map { value ->
                    (value as Map<*, *>).toStringKeyMap()
                },
            ),
        )

        "passphrase_envelope" -> linkedMapOf(
            "json" to FavoritesSyncProtocol.wrapPassphraseEnvelope(
                payload.objectValue("encrypted"),
            ),
        )

        else -> error("Unknown fixture operation: $operation")
    }

    private fun evaluateBinding(payload: Map<String, Any?>): Map<String, Any?> {
        val event = payload.objectValue("event").toMutableMap()
        val contentLength = payload.long("contentLength").toInt()
        if (contentLength > 0) event["content"] = "x".repeat(contentLength)
        var verifyCalls = 0
        val bound = FavoritesSyncProtocol.snapshotEventIsBound(
            event = event,
            pubkey = payload.string("pubkey"),
            dTag = payload.string("dTag"),
            verifySignature = {
                verifyCalls++
                if (payload["signatureThrows"] == true) {
                    error("synthetic verifier failure")
                }
                payload.boolean("signatureValid")
            },
        )
        return linkedMapOf(
            "bound" to bound,
            "verifyCalls" to verifyCalls,
        )
    }

    private fun decodeObject(encoded: String): Map<String, Any?> {
        val json = decodeText(encoded)
        val decoded = NostrRelayMessageDecoder.decode("[\"EVENT\",\"fixture\",$json]")
        return (decoded.message as NostrRelayEventMessage).event
    }

    private fun decodeText(encoded: String): String {
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8)
    }

    private fun Map<String, Any?>.materialize(): String {
        val value = get("value")
        if (value is String) return value
        return string("repeat").repeat(long("count").toInt())
    }

    private fun Map<String, Any?>.string(key: String): String = get(key) as String

    private fun Map<String, Any?>.boolean(key: String): Boolean = get(key) as Boolean

    private fun Map<String, Any?>.long(key: String): Long = when (val value = get(key)) {
        is Long -> value
        is Int -> value.toLong()
        else -> error("$key is not integral")
    }

    private fun Map<String, Any?>.optionalLong(key: String): Long? =
        if (get(key) == null) null else long(key)

    private fun Map<String, Any?>.list(key: String): List<Any?> = get(key) as List<Any?>

    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> =
        (get(key) as Map<*, *>).toStringKeyMap()

    private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> =
        entries.associateTo(linkedMapOf()) { entry -> entry.key as String to entry.value }
}
