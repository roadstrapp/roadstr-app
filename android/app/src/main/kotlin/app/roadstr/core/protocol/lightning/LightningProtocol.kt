package app.roadstr.core.protocol.lightning

import app.roadstr.core.protocol.nostr.NostrEventDraft
import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.asciiHexDigit
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parsed NIP-47 connection material. Its string form never exposes the secret. */
class NwcConnection private constructor(
    val walletPubkey: String,
    val secret: String,
    val relayUri: URI,
) {
    override fun toString(): String = "NwcConnection(<redacted>)"

    companion object {
        fun parseOrNull(raw: String, fallbackRelay: String): NwcConnection? {
            return try {
                val uri = URI(raw.trim())
                if (uri.scheme?.lowercase() != "nostr+walletconnect") return null
                if (uri.rawUserInfo != null || uri.rawPath.orEmpty().isNotEmpty()) return null
                val query = parseQuery(uri.rawQuery)
                val secrets = query["secret"].orEmpty()
                val relays = query["relay"].orEmpty()
                if (secrets.size != 1 || relays.size > 1) return null
                val walletPubkey = uri.host ?: return null
                val secret = secrets.single()
                val relay = URI(relays.singleOrNull() ?: fallbackRelay)
                if (
                    !walletPubkey.isHex32() ||
                    !secret.isHex32() ||
                    relay.scheme?.lowercase() != "wss" ||
                    relay.host.isNullOrEmpty()
                ) {
                    return null
                }
                NwcConnection(walletPubkey, secret, relay)
            } catch (_: Exception) {
                null
            }
        }

        private fun parseQuery(raw: String?): Map<String, List<String>> {
            if (raw.isNullOrEmpty()) return emptyMap()
            val values = linkedMapOf<String, MutableList<String>>()
            for (component in raw.split('&')) {
                val separator = component.indexOf('=')
                val rawKey = if (separator < 0) component else component.substring(0, separator)
                val rawValue = if (separator < 0) "" else component.substring(separator + 1)
                // Charset overload is API 33; the charset-name overload is
                // available across Roadstr's full minSdk 24 range.
                val key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
                values.getOrPut(key) { mutableListOf() } += value
            }
            return values
        }
    }
}

data class NwcResponseDecision(
    val shouldComplete: Boolean,
    val preimage: String?,
) {
    companion object {
        fun ignored(): NwcResponseDecision = NwcResponseDecision(false, null)
        fun failure(): NwcResponseDecision = NwcResponseDecision(true, null)
        fun success(preimage: String): NwcResponseDecision =
            NwcResponseDecision(true, preimage)
    }
}

enum class NwcEncryptionScheme(val code: String) {
    NIP44_V2("nip44_v2"),
    NIP04("nip04"),
}

class NwcEncryptionSelection private constructor(
    val scheme: NwcEncryptionScheme,
    val isLegacy: Boolean,
) {
    val requestTag: String?
        get() = if (isLegacy) null else scheme.code

    companion object {
        fun explicit(scheme: NwcEncryptionScheme): NwcEncryptionSelection =
            NwcEncryptionSelection(scheme, false)

        fun legacyNip04(): NwcEncryptionSelection =
            NwcEncryptionSelection(NwcEncryptionScheme.NIP04, true)
    }
}

data class NwcInfoDecision(
    val shouldComplete: Boolean,
    val selection: NwcEncryptionSelection?,
) {
    companion object {
        fun ignored(): NwcInfoDecision = NwcInfoDecision(false, null)
        fun incompatible(): NwcInfoDecision = NwcInfoDecision(true, null)
        fun supported(selection: NwcEncryptionSelection): NwcInfoDecision =
            NwcInfoDecision(true, selection)
    }
}

/** Deterministic NIP-47 behavior around crypto and socket adapters. */
object NwcProtocol {
    fun payInvoiceCommand(invoice: String): String = NostrJson.encode(
        linkedMapOf(
            "method" to "pay_invoice",
            "params" to linkedMapOf("invoice" to invoice),
        ),
    )

    fun requestDraft(
        clientPubkey: String,
        createdAt: Long,
        walletPubkey: String,
        encryptedContent: String,
        encryption: NwcEncryptionSelection = NwcEncryptionSelection.legacyNip04(),
    ): NostrEventDraft {
        require(clientPubkey.isHex32() && walletPubkey.isHex32()) {
            "Invalid NWC event key"
        }
        return NostrEventDraft(
            pubkey = clientPubkey,
            createdAt = createdAt,
            kind = 23194,
            tags = buildList {
                encryption.requestTag?.let { tag -> add(listOf("encryption", tag)) }
                add(listOf("p", walletPubkey))
            },
            content = encryptedContent,
        )
    }

    fun infoRequest(
        subscriptionId: String,
        walletPubkey: String,
    ): List<Any?> {
        require(walletPubkey.isHex32()) { "Invalid NWC info filter" }
        return listOf(
            "REQ",
            subscriptionId,
            linkedMapOf(
                "kinds" to listOf(13194),
                "authors" to listOf(walletPubkey),
                "limit" to 1,
            ),
        )
    }

    /**
     * Verifies and interprets a wallet's replaceable kind-13194 info event.
     * Structurally unrelated events skip the potentially expensive verifier.
     */
    fun inspectInfoEvent(
        event: Map<String, Any?>,
        walletPubkey: String,
        verifySignature: () -> Boolean,
    ): NwcInfoDecision {
        return try {
            if (
                event["kind"].asIntegralLong() != 13194L ||
                event["pubkey"] != walletPubkey ||
                event["content"] !is String
            ) {
                return NwcInfoDecision.ignored()
            }
            val tags = strictStringTags(event["tags"]) ?: return NwcInfoDecision.ignored()
            if (!verifySignature()) return NwcInfoDecision.ignored()

            val methods = (event["content"] as String).spaceSeparated()
            if ("pay_invoice" !in methods) return NwcInfoDecision.incompatible()
            val encryptionTags = tags.filter { tag ->
                tag.isNotEmpty() && tag[0] == "encryption"
            }
            if (encryptionTags.isEmpty()) {
                return NwcInfoDecision.supported(NwcEncryptionSelection.legacyNip04())
            }
            if (encryptionTags.size != 1 || encryptionTags.single().size != 2) {
                return NwcInfoDecision.incompatible()
            }
            val modes = encryptionTags.single()[1].spaceSeparated()
            when {
                NwcEncryptionScheme.NIP44_V2.code in modes -> NwcInfoDecision.supported(
                    NwcEncryptionSelection.explicit(NwcEncryptionScheme.NIP44_V2),
                )

                NwcEncryptionScheme.NIP04.code in modes -> NwcInfoDecision.supported(
                    NwcEncryptionSelection.explicit(NwcEncryptionScheme.NIP04),
                )

                else -> NwcInfoDecision.incompatible()
            }
        } catch (_: Exception) {
            NwcInfoDecision.ignored()
        }
    }

    fun responseRequest(
        subscriptionId: String,
        walletPubkey: String,
        requestEventId: String,
    ): List<Any?> {
        require(walletPubkey.isHex32() && requestEventId.isHex32()) {
            "Invalid NWC response filter"
        }
        return listOf(
            "REQ",
            subscriptionId,
            linkedMapOf(
                "kinds" to listOf(23195),
                "authors" to listOf(walletPubkey),
                "#e" to listOf(requestEventId),
            ),
        )
    }

    fun responseEventIsBound(
        event: Map<String, Any?>,
        walletPubkey: String,
        requestEventId: String,
        clientPubkey: String,
    ): Boolean = try {
        val tags = (event["tags"] as? List<*>)
            .orEmpty()
            .filterIsInstance<List<*>>()
            .map { tag -> tag.map { value -> value.toString() } }
        val boundToRequest = tags.any { tag ->
            tag.size >= 2 && tag[0] == "e" && tag[1] == requestEventId
        }
        val addressedToClient = tags.any { tag ->
            tag.size >= 2 && tag[0] == "p" && tag[1] == clientPubkey
        }
        event["kind"].asIntegralLong() == 23195L &&
            event["pubkey"] == walletPubkey &&
            boundToRequest &&
            addressedToClient
    } catch (_: Exception) {
        false
    }

    fun inspectResponse(
        response: Map<String, Any?>,
        invoice: Bolt11Invoice,
    ): NwcResponseDecision {
        return try {
            if (response["result_type"] != "pay_invoice" || response["error"] != null) {
                return NwcResponseDecision.failure()
            }
            val rawResult = response["result"]
            if (rawResult != null && rawResult !is Map<*, *>) {
                return NwcResponseDecision.ignored()
            }
            val rawPreimage = (rawResult as? Map<*, *>)?.get("preimage")
            if (rawPreimage != null && rawPreimage !is String) {
                return NwcResponseDecision.ignored()
            }
            val preimage = rawPreimage as? String
            if (preimage != null && invoice.preimageMatches(preimage)) {
                NwcResponseDecision.success(preimage)
            } else {
                NwcResponseDecision.failure()
            }
        } catch (_: Exception) {
            NwcResponseDecision.ignored()
        }
    }
}

class Nip57ReceiptEnvelope internal constructor(
    val bolt11: String,
    val description: String,
    val preimage: String?,
    val recipient: String,
    val eventId: String?,
)

/** Deterministic NIP-57 layouts and receipt bindings. */
object Nip57Protocol {
    const val MAX_AMOUNT_MSAT: Long = 2_100_000_000_000_000_000L

    fun zapRequestDraft(
        senderPubkey: String,
        createdAt: Long,
        recipientPubkey: String,
        eventId: String,
        amountMsat: Long,
        relays: List<String>,
    ): NostrEventDraft {
        require(
            senderPubkey.isHex32() &&
                recipientPubkey.isHex32() &&
                eventId.isHex32() &&
                amountMsat in 1..MAX_AMOUNT_MSAT,
        ) {
            "Invalid zap request fields"
        }
        return NostrEventDraft(
            pubkey = senderPubkey,
            createdAt = createdAt,
            kind = 9734,
            tags = listOf(
                listOf("p", recipientPubkey),
                listOf("e", eventId),
                listOf("amount", amountMsat.toString()),
                listOf("relays") + relays.toList(),
            ),
            content = "",
        )
    }

    fun inspectReceipt(
        receipt: Map<String, Any?>,
        receiptSigner: String,
        verifySignature: () -> Boolean,
    ): Nip57ReceiptEnvelope? {
        return try {
            if (
                receipt["kind"].asIntegralLong() != 9735L ||
                receipt["pubkey"] != receiptSigner ||
                !verifySignature()
            ) {
                return null
            }
            val tags = strictStringTags(receipt["tags"]) ?: return null
            val bolt11Values = mutableListOf<String>()
            val descriptionValues = mutableListOf<String>()
            val preimageValues = mutableListOf<String>()
            val recipients = mutableListOf<String>()
            val events = mutableListOf<String>()
            for (tag in tags) {
                if (tag.size < 2) continue
                when (tag[0]) {
                    "bolt11" -> bolt11Values += tag[1]
                    "description" -> descriptionValues += tag[1]
                    "preimage" -> preimageValues += tag[1]
                    "p" -> recipients += tag[1]
                    "e" -> events += tag[1]
                }
            }
            if (
                bolt11Values.size != 1 ||
                descriptionValues.size != 1 ||
                preimageValues.size > 1 ||
                recipients.size != 1 ||
                events.size > 1
            ) {
                return null
            }
            Nip57ReceiptEnvelope(
                bolt11 = bolt11Values.single(),
                description = descriptionValues.single(),
                preimage = preimageValues.singleOrNull(),
                recipient = recipients.single(),
                eventId = events.singleOrNull(),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun boundReceiptAmount(
        envelope: Nip57ReceiptEnvelope,
        invoice: Bolt11Invoice,
        request: Map<String, Any?>,
        verifyRequestSignature: () -> Boolean,
        eventId: String? = null,
        recipientPubkey: String? = null,
    ): Long? {
        return try {
            if (
                !invoice.descriptionMatches(envelope.description) ||
                (envelope.preimage != null && !invoice.preimageMatches(envelope.preimage)) ||
                request["kind"].asIntegralLong() != 9734L ||
                !verifyRequestSignature()
            ) {
                return null
            }
            val requestTags = strictStringTags(request["tags"]) ?: return null
            val amounts = mutableListOf<String>()
            val requestEvents = mutableListOf<String>()
            val requestRecipients = mutableListOf<String>()
            for (tag in requestTags) {
                if (tag.size < 2) continue
                when (tag[0]) {
                    "amount" -> amounts += tag[1]
                    "e" -> requestEvents += tag[1]
                    "p" -> requestRecipients += tag[1]
                }
            }
            if (
                amounts.size != 1 ||
                requestRecipients.size != 1 ||
                requestEvents.size > 1 ||
                amounts.single().toLongOrNull() != invoice.amountMillisatoshi ||
                envelope.recipient != requestRecipients.single() ||
                (recipientPubkey != null && requestRecipients.single() != recipientPubkey) ||
                (eventId != null && (requestEvents.size != 1 || requestEvents.single() != eventId)) ||
                (requestEvents.isEmpty() != (envelope.eventId == null)) ||
                (requestEvents.isNotEmpty() && envelope.eventId != requestEvents.single())
            ) {
                return null
            }
            invoice.amountMillisatoshi
        } catch (_: Exception) {
            null
        }
    }
}

private fun String.isHex32(): Boolean =
    length == 64 && all { character -> asciiHexDigit(character) >= 0 }

private fun String.spaceSeparated(): Set<String> =
    trim().split(Regex("\\s+")).filter { part -> part.isNotEmpty() }.toSet()

private fun Any?.asIntegralLong(): Long? = when (this) {
    is Byte -> toLong()
    is Short -> toLong()
    is Int -> toLong()
    is Long -> this
    else -> null
}

private fun strictStringTags(raw: Any?): List<List<String>>? {
    val tags = raw as? List<*> ?: return null
    return tags.map { rawTag ->
        val tag = rawTag as? List<*> ?: return null
        if (tag.any { value -> value !is String }) return null
        tag.filterIsInstance<String>()
    }
}
