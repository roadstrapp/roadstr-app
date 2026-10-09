package app.roadstr.core.network

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpSafetyPolicyParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/http_safety_policy_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native HTTP safety policy reproduces every Dart case`() {
        assertEquals(54, rows.size)
        for (fields in rows) {
            val actual = when (fields[0]) {
                "timeout" -> NetworkTimeoutBudget.entries
                    .single { budget -> budget.fixtureName == fields[1] }
                    .milliseconds
                    .toString()

                "limit" -> NetworkResponseLimit.entries
                    .single { limit -> limit.fixtureName == fields[1] }
                    .bytes
                    .toString()

                "redirect" -> BoundedHttpPolicy.followRedirects.toString()
                "content_length" -> BoundedHttpPolicy.acceptsContentLength(
                    contentLength = fields[2].takeUnless { it == "none" }?.toLong(),
                    maxBytes = fields[3].toLong(),
                ).toString()

                "chunks" -> evaluateChunks(fields[2], fields[3])
                "endpoint" -> RoutingEndpointPolicy
                    .graphHopperDecision(decode(fields[2]))
                    .fixtureName

                else -> error("Unknown HTTP safety fixture operation: ${fields[0]}")
            }
            assertEquals("${fields[0]}/${fields[1]}", fields[4], actual)
        }
    }

    @Test
    fun `body budget rejects invalid construction and remains closed after overflow`() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedHttpBodyBudget(0)
        }
        val budget = BoundedHttpBodyBudget(8)
        assertTrue(budget.acceptChunk(8))
        assertFalse(budget.acceptChunk(1))
        assertFalse(budget.acceptChunk(0))
        assertEquals(8, budget.receivedBytes)
        assertTrue(budget.rejected)
        assertThrows(IllegalArgumentException::class.java) {
            BoundedHttpBodyBudget(8).acceptChunk(-1)
        }
    }

    private fun evaluateChunks(maxBytes: String, encodedChunks: String): String {
        val budget = BoundedHttpBodyBudget(maxBytes.toLong())
        val chunks = if (encodedChunks == "-") {
            emptyList()
        } else {
            encodedChunks.split(',').map(String::toInt)
        }
        for ((index, chunk) in chunks.withIndex()) {
            if (!budget.acceptChunk(chunk)) {
                return "rejected:${budget.receivedBytes}:$index"
            }
        }
        return "accepted:${budget.receivedBytes}"
    }

    private fun decode(encoded: String): String {
        val padded = encoded + "=".repeat((4 - encoded.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded).toString(Charsets.UTF_8)
    }
}
