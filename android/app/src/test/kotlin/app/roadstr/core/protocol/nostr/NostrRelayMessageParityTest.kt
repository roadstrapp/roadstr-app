package app.roadstr.core.protocol.nostr

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NostrRelayMessageParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/nostr_relay_messages_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native decoder matches every Dart accepted and rejected transcript`() {
        assertEquals(25, rows.size)
        for (fields in rows) {
            val result = NostrRelayMessageDecoder.decode(materialize(fields[2]))
            val message = result.message
            val outcome = if (message == null) {
                "reject:${requireNotNull(result.failure).wireName}"
            } else {
                messageType(message)
            }
            val summary = message?.let(::messageSummary)?.let(::b64) ?: "-"

            assertEquals(fields[1], fields[3], outcome)
            assertEquals(fields[1], fields[4], summary)
        }
    }

    @Test
    fun `event envelope deeply isolates parsed JSON`() {
        val result = NostrRelayMessageDecoder.decode(
            """["EVENT","sub",{"id":"not-verified","kind":1315,"tags":[["t","hazard"]]}]""",
        )
        val message = result.message as NostrRelayEventMessage
        val tags = message.event.getValue("tags") as List<*>
        val firstTag = tags.single() as List<*>

        assertEquals("sub", message.subscriptionId)
        assertEquals("hazard", firstTag[1])
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (tags as MutableList<Any?>).add("changed")
        }
    }

    @Test
    fun `malformed OK status is fail closed for publication acknowledgement`() {
        val result = NostrRelayMessageDecoder.decode("""["OK","id",1]""")
        val message = result.message as NostrRelayOkMessage

        assertNull(message.accepted)
        assertFalse(message.accepted == true)
    }

    @Test
    fun `hostile input corpus is rejected without escaping parser errors`() {
        val corpus = listOf<Any?>(
            null,
            1,
            "",
            "null",
            "{}",
            "[".repeat(100),
            "\"unterminated",
            """["EVENT",{},{}]""",
            """["EOSE",null]""",
            """["AUTH",7]""",
            """["NOTICE",01]""",
            """["NOTICE",1e9999]""",
        )

        for (input in corpus) NostrRelayMessageDecoder.decode(input)
    }

    private fun materialize(source: String): Any {
        val parts = source.split(':')
        return when (parts[0]) {
            "text" -> unb64(parts[1])
            "non-string-int" -> 7
            "padded-notice" -> paddedNotice(parts[1].toInt(), parts[2])
            "deep-event" -> deepEvent(parts[1].toInt())
            else -> error("Unknown fixture source: $source")
        }
    }

    private fun paddedNotice(targetLength: Int, alphabet: String): String {
        val prefix = "[\"NOTICE\",\""
        val suffix = "\"]"
        val contentLength = targetLength - prefix.length - suffix.length
        require(contentLength >= 0)
        val content = when (alphabet) {
            "ascii" -> "a".repeat(contentLength)
            "emoji" -> "🚗".repeat(contentLength / 2) +
                if (contentLength % 2 == 1) "a" else ""

            else -> error("Unknown fixture alphabet: $alphabet")
        }
        return "$prefix$content$suffix".also { value ->
            require(value.length == targetLength)
        }
    }

    private fun deepEvent(depth: Int): String {
        require(depth >= 2)
        val nested = depth - 2
        return "[\"EVENT\",\"deep\",{\"kind\":1315,\"nested\":" +
            "[".repeat(nested) + "null" + "]".repeat(nested) + "}]"
    }

    private fun messageType(message: NostrRelayMessage): String = when (message) {
        is NostrRelayEventMessage -> "EVENT"
        is NostrRelayEoseMessage -> "EOSE"
        is NostrRelayOkMessage -> "OK"
        is NostrRelayNoticeMessage -> "NOTICE"
        is NostrRelayClosedMessage -> "CLOSED"
        is NostrRelayAuthMessage -> "AUTH"
    }

    private fun messageSummary(message: NostrRelayMessage): String {
        val summary = when (message) {
            is NostrRelayEventMessage -> linkedMapOf(
                "type" to "EVENT",
                "subscriptionId" to message.subscriptionId,
                "eventId" to message.event["id"],
                "kind" to message.event["kind"],
                "fieldCount" to message.event.size,
            )

            is NostrRelayEoseMessage -> linkedMapOf(
                "type" to "EOSE",
                "subscriptionId" to message.subscriptionId,
            )

            is NostrRelayOkMessage -> linkedMapOf(
                "type" to "OK",
                "eventId" to message.eventId,
                "accepted" to message.accepted,
                "reason" to fingerprint(message.reason),
            )

            is NostrRelayNoticeMessage -> linkedMapOf(
                "type" to "NOTICE",
                "detail" to fingerprint(message.detail),
            )

            is NostrRelayClosedMessage -> linkedMapOf(
                "type" to "CLOSED",
                "subscriptionId" to message.subscriptionId,
                "detail" to fingerprint(message.detail),
            )

            is NostrRelayAuthMessage -> linkedMapOf(
                "type" to "AUTH",
                "challenge" to fingerprint(message.challenge),
            )
        }
        return NostrJson.encode(summary)
    }

    private fun fingerprint(value: Any?): Map<String, Any?> {
        val encoded = NostrJson.encode(value)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(encoded.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return linkedMapOf(
            "jsonUtf16Length" to encoded.length,
            "sha256" to digest,
        )
    }

    private fun b64(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun unb64(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}
