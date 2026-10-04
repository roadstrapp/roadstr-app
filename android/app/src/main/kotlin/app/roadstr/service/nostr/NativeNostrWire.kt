package app.roadstr.service.nostr

import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrSchnorr
import java.security.SecureRandom

/**
 * Strict helpers for relay events, which are untrusted JSON.
 *
 * Mirrors the Flutter verifyEventJson/nostrEventDraftFromJson pair: any
 * malformed or forged event is rejected by returning null/false, never by
 * throwing into the socket callback.
 */
object NativeNostrWire {
    private val HEX_64 = Regex("^[0-9a-f]{64}$")
    private val random = SecureRandom()

    fun isHex32(value: String?): Boolean = value != null && HEX_64.matches(value)

    /** A NIP-01 subscription id: 16 random hex characters. */
    fun randomSubscriptionId(): String =
        ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    /** Strict `List<List<String>>`; any other shape means the event is unusable. */
    fun tags(raw: Any?): List<List<String>>? {
        val outer = raw as? List<*> ?: return null
        return outer.map { rawTag ->
            val tag = rawTag as? List<*> ?: return null
            tag.map { it as? String ?: return null }
        }
    }

    fun integral(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Short -> value.toLong()
        is Byte -> value.toLong()
        else -> null
    }

    /** Rebuilds the deterministic part of a relay event, or null if malformed. */
    fun draftOrNull(event: Map<String, Any?>): NostrEventDraft? = runCatching {
        val pubkey = event["pubkey"] as? String ?: return null
        val createdAt = integral(event["created_at"]) ?: return null
        val kind = integral(event["kind"])?.takeIf { it in 0..65_535 }?.toInt() ?: return null
        val tags = tags(event["tags"]) ?: return null
        val content = event["content"] as? String ?: ""
        NostrEventDraft(pubkey, createdAt, kind, tags, content)
    }.getOrNull()

    /** Canonical id and BIP-340 signature both have to match. Never throws. */
    fun verify(event: Map<String, Any?>): Boolean = runCatching {
        val draft = draftOrNull(event) ?: return false
        val id = event["id"] as? String ?: return false
        val signature = event["sig"] as? String ?: return false
        draft.id() == id && NostrSchnorr.verifyHash(draft.pubkey, id, signature)
    }.getOrDefault(false)

    /** First value of tag [name], or null. */
    fun tagValue(event: Map<String, Any?>, name: String): String? {
        val tags = tags(event["tags"]) ?: return null
        return tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)
    }

    fun nowSeconds(): Long = System.currentTimeMillis() / 1000
}
