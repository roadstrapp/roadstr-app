package app.roadstr.core.protocol.lightning

import app.roadstr.core.protocol.nostr.NostrJson
import app.roadstr.core.protocol.nostr.NostrRelayEventMessage
import app.roadstr.core.protocol.nostr.NostrRelayMessageDecoder
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class LnurlProtocolParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/lnurl_protocol_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native LNURL core reproduces every Dart case`() {
        assertEquals(77, rows.size)
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
        "source" -> linkedMapOf(
            "url" to LnurlProtocol.resolveMetadataUrl(payload.string("input")),
        )

        "safe_https" -> linkedMapOf(
            "safe" to LnurlProtocol.isSafeHttpsUrl(payload.string("url")),
        )

        "metadata" -> {
            val info = LnurlProtocol.parsePayInfo(payload.objectValue("data"))
            if (info == null) {
                linkedMapOf("accepted" to false)
            } else {
                linkedMapOf(
                    "accepted" to true,
                    "callback" to info.callback,
                    "minSendable" to info.minSendable,
                    "maxSendable" to info.maxSendable,
                    "metadata" to info.metadata,
                    "nostrPubkey" to info.nostrPubkey,
                    "allowsNostr" to info.allowsNostr,
                )
            }
        }

        "invoice_request" -> {
            val info = payload.objectValue("payInfo").toPayInfo()
            val request = LnurlProtocol.buildInvoiceRequest(
                payInfo = info,
                amountMillisatoshi = payload.long("amountMsat"),
                zapRequest = payload.optionalObjectValue("zapRequest"),
            )
            linkedMapOf(
                "url" to request?.url,
                "description" to request?.description,
            )
        }

        "invoice_response" -> {
            val request = LnurlInvoiceRequest(
                url = payload.string("requestUrl"),
                description = payload.string("description"),
            )
            val invoice = LnurlProtocol.validateInvoiceResponse(
                data = payload.objectValue("data"),
                request = request,
                amountMillisatoshi = payload.long("amountMsat"),
                nowUnixSeconds = payload.long("now"),
            )
            linkedMapOf(
                "accepted" to (invoice != null),
                "invoice" to invoice,
            )
        }

        else -> error("Unknown fixture operation: $operation")
    }

    private fun Map<String, Any?>.toPayInfo(): LnurlPayInfo = LnurlPayInfo(
        callback = string("callback"),
        minSendable = long("minSendable"),
        maxSendable = long("maxSendable"),
        metadata = string("metadata"),
        nostrPubkey = get("nostrPubkey") as? String,
        allowsNostr = get("allowsNostr") as Boolean,
    )

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

    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> {
        val value = get(key) as Map<*, *>
        return value.entries.associateTo(linkedMapOf()) { entry ->
            entry.key as String to entry.value
        }
    }

    private fun Map<String, Any?>.optionalObjectValue(key: String): Map<String, Any?>? =
        if (get(key) == null) null else objectValue(key)
}
