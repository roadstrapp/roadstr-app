package app.roadstr.core.protocol.nostr

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * What a `bunker://` link says: who signs, where to reach them, and the one-time secret that proves the link
 * was handed to this client.
 */
class NostrBunkerUri(
    val remotePubkeyHex: String,
    relays: List<String>,
    val secret: String?,
) {
    val relays: List<String> = relays.toList()

    companion object {
        const val MAX_RELAYS = 4
        private const val MAX_LENGTH = 2_048
        private val HEX_64 = Regex("[0-9a-f]{64}")

        /** Null for anything that is not a well-formed link with at least one `wss://` relay. */
        fun parse(text: String): NostrBunkerUri? = runCatching {
            val trimmed = text.trim()
            require(trimmed.length in 1..MAX_LENGTH && trimmed.startsWith("bunker://", ignoreCase = true))
            val uri = URI(trimmed.replaceFirst(Regex("^bunker://", RegexOption.IGNORE_CASE), "bunker://"))
            val pubkey = (uri.host ?: uri.authority ?: error("no signer")).lowercase(Locale.ROOT)
            require(HEX_64.matches(pubkey))
            val relays = LinkedHashSet<String>()
            var secret: String? = null
            for (pair in (uri.rawQuery ?: "").split('&')) {
                if (pair.isEmpty()) continue
                val name = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
                val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                when (name) {
                    "relay" -> if (isRelay(value) && relays.size < MAX_RELAYS) relays += value
                    "secret" -> if (value.isNotEmpty() && value.length <= 256) secret = value
                }
            }
            require(relays.isNotEmpty())
            NostrBunkerUri(pubkey, relays.toList(), secret)
        }.getOrNull()

        /** Clear-text relays are refused here as everywhere else: a request carries a signing session. */
        fun isRelay(value: String): Boolean = value.startsWith("wss://") && value.length <= 256 &&
            value.none { it.isWhitespace() }
    }
}

/** A decoded NIP-46 answer. [authUrl] is set when the signer wants the person to approve in a browser first. */
class Nip46Response(val id: String, val result: String?, val error: String?) {
    val authUrl: String? get() = error?.takeIf { result == "auth_url" && it.startsWith("https://") }
}

/** The JSON-RPC layer of NIP-46 (Nostr Connect); the transport and the keys live elsewhere. */
object Nip46 {
    const val KIND = 24_133

    fun requestJson(id: String, method: String, params: List<String>): String =
        NostrJson.encode(linkedMapOf("id" to id, "method" to method, "params" to params))

    /** The event as `sign_event` takes it: everything but the id and the signature, which the signer adds. */
    fun eventJson(draft: NostrEventDraft): String = NostrJson.encode(
        linkedMapOf(
            "pubkey" to draft.pubkey,
            "created_at" to draft.createdAt,
            "kind" to draft.kind,
            "tags" to draft.tags,
            "content" to draft.content,
        ),
    )

    /** Null when [json] is not an object with a string id and a string or null result and error. */
    fun parseResponse(json: String): Nip46Response? = runCatching {
        val map = BoundedJsonParser(json).parse() as? Map<*, *> ?: return null
        val id = map["id"] as? String ?: return null
        val result = map["result"]
        val error = map["error"]
        if (result != null && result !is String) return null
        if (error != null && error !is String) return null
        Nip46Response(id, result as String?, error as String?)
    }.getOrNull()

    /** The event a client publishes to ask the signer for something: kind 24133, tagged for the signer. */
    fun requestDraft(
        clientPubkeyHex: String,
        remotePubkeyHex: String,
        encryptedContent: String,
        createdAt: Long,
    ) = NostrEventDraft(
        pubkey = clientPubkeyHex,
        createdAt = createdAt,
        kind = KIND,
        tags = listOf(listOf("p", remotePubkeyHex)),
        content = encryptedContent,
    )
}
