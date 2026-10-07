package app.roadstr.core.protocol.nostr

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrSchnorrParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(javaClass.getResourceAsStream("/parity/nostr_schnorr_v1.tsv"))
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `blinding the scalar arithmetic never changes a signature`() {
        val random = java.util.Random(340)
        repeat(200) {
            val key = ByteArray(32).also { random.nextBytes(it) }.also { it[0] = (it[0].toInt() and 0x7f).toByte() }
                .joinToString("") { "%02x".format(it) }
            val message = ByteArray(32).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val aux = ByteArray(32).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

            val first = NostrSchnorr.signHashWithAux(key, message, aux)
            val second = NostrSchnorr.signHashWithAux(key, message, aux)

            assertEquals(first, second)
            assertTrue(NostrSchnorr.verifyHash(NostrSchnorr.publicKey(key), message, first))
        }
    }

    @Test
    fun `native BIP-340 reproduces official and Roadstr vectors`() {
        assertEquals(43, rows.size)
        for (fields in rows) {
            when (fields[0]) {
                "bip340" -> verifyOfficialVector(fields)

                "nostr" -> {
                    val privateKey = fields[2]
                    val auxiliaryRandom = fields[3]
                    val publicKey = fields[4]
                    val eventId = fields[5]
                    val signature = fields[6]
                    val canonicalJson = fields[7].decodeText64()

                    assertTrue(fields[1], canonicalJson.isNotEmpty())
                    assertEquals(fields[1], publicKey, NostrSchnorr.publicKey(privateKey))
                    assertEquals(
                        fields[1],
                        signature,
                        NostrSchnorr.signHashWithAux(privateKey, eventId, auxiliaryRandom),
                    )
                    assertTrue(fields[1], NostrSchnorr.verifyHash(publicKey, eventId, signature))
                }

                "public_reject" ->
                    assertThrows(fields[1], IllegalArgumentException::class.java) {
                        NostrSchnorr.publicKey(fields[2])
                    }

                "sign_reject" ->
                    assertThrows(fields[1], IllegalArgumentException::class.java) {
                        NostrSchnorr.signHashWithAux(fields[2], fields[3], fields[4])
                    }

                "verify_hash" -> assertEquals(
                    fields[1],
                    fields[5].toBooleanStrict(),
                    NostrSchnorr.verifyHash(fields[2], fields[3], fields[4]),
                )

                else -> error("Unknown BIP-340 fixture operation: ${fields[0]}")
            }
        }
    }

    @Test
    fun `production signing uses fresh auxiliary randomness`() {
        val privateKey = "00".repeat(31) + "03"
        val publicKey =
            "f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9"
        val eventId = "00".repeat(32)

        val first = NostrSchnorr.signHash(privateKey, eventId)
        val second = NostrSchnorr.signHash(privateKey, eventId)

        assertNotEquals(first, second)
        assertTrue(NostrSchnorr.verifyHash(publicKey, eventId, first))
        assertTrue(NostrSchnorr.verifyHash(publicKey, eventId, second))
        assertFalse(NostrSchnorr.verifyHash(publicKey, "ff" + eventId.drop(2), first))

        val draft = NostrEventDraft(
            pubkey = publicKey,
            createdAt = 1_700_000_000,
            kind = 1,
            tags = listOf(listOf("client", "roadstr")),
            content = "firma Nostr 🛣️",
        )
        val signed = NostrSchnorr.signEventWithAux(
            draft,
            privateKey,
            "00".repeat(32),
        )
        assertTrue(
            NostrSchnorr.verifyEvent(
                draft,
                signed.getValue("id") as String,
                signed.getValue("sig") as String,
            ),
        )
        assertFalse(
            NostrSchnorr.verifyEvent(
                NostrEventDraft(publicKey, draft.createdAt, draft.kind, draft.tags, "tampered"),
                signed.getValue("id") as String,
                signed.getValue("sig") as String,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            NostrSchnorr.signEvent(draft, "00".repeat(31) + "02")
        }
    }

    private fun verifyOfficialVector(fields: List<String>) {
        val secret = fields[2].takeUnless { it == "-" }
        val publicKey = fields[3]
        val auxiliaryRandom = fields[4].takeUnless { it == "-" }
        val message = fields[5].takeUnless { it == "-" }?.hexBytes() ?: byteArrayOf()
        val signature = fields[6]
        val expected = fields[7].toBooleanStrict()

        assertEquals(fields[1], expected, NostrSchnorr.verify(publicKey, message, signature))
        if (secret != null && auxiliaryRandom != null) {
            assertEquals(fields[1], publicKey, NostrSchnorr.publicKey(secret))
            assertEquals(
                fields[1],
                signature,
                NostrSchnorr.sign(secret, message, auxiliaryRandom.hexBytes()),
            )
        }
    }

    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun String.decodeText64(): String =
        Base64.getUrlDecoder().decode(this).toString(Charsets.UTF_8)
}
