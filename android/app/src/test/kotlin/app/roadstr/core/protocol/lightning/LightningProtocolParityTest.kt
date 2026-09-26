package app.roadstr.core.protocol.lightning

import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.NostrRelayEventMessage
import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightningProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/lightning_protocol_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native NIP-47 and NIP-57 core reproduces every Dart case`() {
        assertEquals(69, rows.size)
        for (fields in rows) {
            val operation = fields[0]
            val caseName = fields[1]
            val payload = decodeObject(fields[2])
            val expectedJson = decodeText(fields[3])
            val actual = evaluate(operation, payload)
            assertEquals("$operation/$caseName", expectedJson, NostrJson.encode(actual))
        }
    }

    @Test
    fun `NWC connection string representation redacts the secret`() {
        val secret = "2".repeat(64)
        val connection = NwcConnection.parseOrNull(
            "nostr+walletconnect://${"1".repeat(64)}?secret=$secret",
            "wss://relay.damus.io",
        )
        assertNotNull(connection)
        assertFalse(connection.toString().contains(secret))
        assertTrue(connection.toString().contains("redacted"))
    }

    private fun evaluate(
        operation: String,
        payload: Map<String, Any?>,
    ): Map<String, Any?> = when (operation) {
        "nwc_uri" -> {
            val connection = NwcConnection.parseOrNull(
                payload.string("raw"),
                payload.string("fallback"),
            )
            if (connection == null) {
                linkedMapOf("accepted" to false)
            } else {
                linkedMapOf(
                    "accepted" to true,
                    "walletPubkey" to connection.walletPubkey,
                    "secret" to connection.secret,
                    "relay" to connection.relayUri.toString(),
                )
            }
        }

        "nwc_command" -> linkedMapOf(
            "command" to NwcProtocol.payInvoiceCommand(payload.string("invoice")),
        )

        "nwc_request_draft" -> try {
            val draft = NwcProtocol.requestDraft(
                clientPubkey = payload.string("clientPubkey"),
                createdAt = payload.long("createdAt"),
                walletPubkey = payload.string("walletPubkey"),
                encryptedContent = payload.string("encryptedContent"),
            )
            linkedMapOf("accepted" to true, "wire" to draft.toWireMap())
        } catch (_: IllegalArgumentException) {
            linkedMapOf("accepted" to false)
        }

        "nwc_response_filter" -> try {
            linkedMapOf(
                "accepted" to true,
                "wire" to NwcProtocol.responseRequest(
                    subscriptionId = payload.string("subscriptionId"),
                    walletPubkey = payload.string("walletPubkey"),
                    requestEventId = payload.string("requestEventId"),
                ),
            )
        } catch (_: IllegalArgumentException) {
            linkedMapOf("accepted" to false)
        }

        "nwc_response_binding" -> linkedMapOf(
            "bound" to NwcProtocol.responseEventIsBound(
                event = payload.objectValue("event"),
                walletPubkey = payload.string("walletPubkey"),
                requestEventId = payload.string("requestEventId"),
                clientPubkey = payload.string("clientPubkey"),
            ),
        )

        "nwc_response" -> {
            val invoice = requireNotNull(
                Bolt11Invoice.parseOrNull(payload.string("invoice")),
            )
            val decision = NwcProtocol.inspectResponse(
                payload.objectValue("response"),
                invoice,
            )
            linkedMapOf(
                "decision" to when {
                    !decision.shouldComplete -> "ignore"
                    decision.preimage == null -> "failure"
                    else -> "success"
                },
                "preimage" to decision.preimage,
            )
        }

        "nip57_draft" -> try {
            val draft = Nip57Protocol.zapRequestDraft(
                senderPubkey = payload.string("senderPubkey"),
                createdAt = payload.long("createdAt"),
                recipientPubkey = payload.string("recipientPubkey"),
                eventId = payload.string("eventId"),
                amountMsat = payload.long("amountMsat"),
                relays = payload.stringList("relays"),
            )
            linkedMapOf("accepted" to true, "wire" to draft.toWireMap())
        } catch (_: IllegalArgumentException) {
            linkedMapOf("accepted" to false)
        }

        "nip57_receipt" -> evaluateReceipt(payload)
        else -> error("Unknown fixture operation: $operation")
    }

    private fun evaluateReceipt(payload: Map<String, Any?>): Map<String, Any?> {
        var receiptVerifyCalls = 0
        var requestVerifyCalls = 0
        val envelope = Nip57Protocol.inspectReceipt(
            receipt = payload.objectValue("receipt"),
            receiptSigner = payload.string("receiptSigner"),
            verifySignature = {
                receiptVerifyCalls++
                payload.boolean("receiptSignatureValid")
            },
        )
        var amount: Long? = null
        if (envelope != null) {
            val invoice = Bolt11Invoice.parseOrNull(envelope.bolt11)
            if (invoice != null) {
                amount = Nip57Protocol.boundReceiptAmount(
                    envelope = envelope,
                    invoice = invoice,
                    request = payload.objectValue("request"),
                    verifyRequestSignature = {
                        requestVerifyCalls++
                        payload.boolean("requestSignatureValid")
                    },
                    eventId = payload["expectedEventId"] as? String,
                    recipientPubkey = payload["expectedRecipient"] as? String,
                )
            }
        }
        return linkedMapOf(
            "amountMsat" to amount,
            "receiptVerifyCalls" to receiptVerifyCalls,
            "requestVerifyCalls" to requestVerifyCalls,
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

    private fun Map<String, Any?>.string(key: String): String = get(key) as String

    private fun Map<String, Any?>.long(key: String): Long = when (val value = get(key)) {
        is Long -> value
        is Int -> value.toLong()
        else -> error("$key is not integral")
    }

    private fun Map<String, Any?>.boolean(key: String): Boolean = get(key) as Boolean

    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> {
        val value = get(key) as Map<*, *>
        return value.entries.associateTo(linkedMapOf()) { entry ->
            entry.key as String to entry.value
        }
    }

    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (get(key) as List<*>).map { value -> value as String }
}
