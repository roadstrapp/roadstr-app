package app.roadstr.core.protocol.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class NostrNip19ParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/parity/nostr_nip19_v1.tsv"),
        )
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native codec reproduces every shared encode and decode case`() {
        assertEquals(35, rows.size)
        for (fields in rows) {
            when (fields[0]) {
                "encode" -> assertEncodeCase(fields)
                "decode" -> assertDecodeCase(fields)
                else -> error("Unknown fixture operation: ${fields[0]}")
            }
        }
    }

    @Test
    fun `typed decoders reject the other key type`() {
        val npub = rows.single { fields -> fields[1] == "zero-npub" }[4]
        val nsec = rows.single { fields -> fields[1] == "zero-nsec" }[4]

        assertEquals("00".repeat(32), NostrNip19.decodePublicKey(npub))
        assertEquals("00".repeat(32), NostrNip19.decodePrivateKey(nsec))
        assertThrows(IllegalArgumentException::class.java) {
            NostrNip19.decodePrivateKey(npub)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NostrNip19.decodePublicKey(nsec)
        }
    }

    @Test
    fun `decode errors never echo possible secret input`() {
        val candidateSecret = "nsec1this-must-never-appear-in-an-error"

        val error = assertThrows(IllegalArgumentException::class.java) {
            NostrNip19.decodePrivateKey(candidateSecret)
        }
        assertFalse(error.toString().contains(candidateSecret))
    }

    private fun assertEncodeCase(fields: List<String>) {
        val hex = fields[3].takeUnless { value -> value == "-" }.orEmpty()
        val encode = {
            when (fields[2]) {
                "npub" -> NostrNip19.encodePublicKey(hex)
                "nsec" -> NostrNip19.encodePrivateKey(hex)
                else -> error("Unknown fixture key type: ${fields[2]}")
            }
        }
        if (fields[4] == "reject") {
            assertThrows(fields[1], IllegalArgumentException::class.java) {
                encode()
            }
        } else {
            assertEquals(fields[1], fields[4], encode())
        }
    }

    private fun assertDecodeCase(fields: List<String>) {
        if (fields[3] == "reject") {
            assertThrows(fields[1], IllegalArgumentException::class.java) {
                NostrNip19.decode(fields[2])
            }
            return
        }

        val decoded = NostrNip19.decode(fields[2])
        assertEquals(fields[1], fields[3], decoded.kind.hrp)
        assertEquals(fields[1], fields[4], decoded.hex)
    }
}
