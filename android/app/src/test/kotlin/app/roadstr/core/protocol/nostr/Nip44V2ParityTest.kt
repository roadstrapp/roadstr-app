package app.roadstr.core.protocol.nostr

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Nip44V2ParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(javaClass.getResourceAsStream("/parity/nip44_v2_v1.tsv"))
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native NIP-44 reproduces every Dart and official vector`() {
        assertEquals(77, rows.size)
        for (fields in rows) {
            when (fields[0]) {
                "conversation" -> assertEquals(
                    fields[1],
                    fields[4],
                    Nip44V2.conversationKey(fields[2], fields[3]).toHex(),
                )

                "conversation_reject" -> assertThrows(fields[1], IllegalArgumentException::class.java) {
                    Nip44V2.conversationKey(fields[2], fields[3])
                }

                "message_keys" -> {
                    val keys = Nip44V2.messageKeys(fields[2].hexBytes(), fields[3].hexBytes())
                    assertEquals(fields[1], fields[4], keys.chachaKey.toHex())
                    assertEquals(fields[1], fields[5], keys.chachaNonce.toHex())
                    assertEquals(fields[1], fields[6], keys.hmacKey.toHex())
                }

                "message_keys_reject" ->
                    assertThrows(fields[1], IllegalArgumentException::class.java) {
                        Nip44V2.messageKeys(fields[2].hexBytes(), fields[3].hexBytes())
                    }

                "padded_len" -> assertEquals(
                    fields[1],
                    fields[3].toInt(),
                    Nip44V2.paddedLength(fields[2].toInt()),
                )

                "encrypt" -> {
                    val plaintext = Base64.getUrlDecoder().decode(fields[5]).toString(Charsets.UTF_8)
                    val payload = Nip44V2.encryptWithNonce(
                        privateKeyHex = fields[2],
                        publicKeyHex = fields[3],
                        plaintext = plaintext,
                        nonce = fields[4].hexBytes(),
                    )
                    assertEquals(fields[1], fields[6], payload)
                    assertEquals(fields[1], plaintext, Nip44V2.decrypt(fields[2], fields[3], payload))
                }

                "decrypt" -> {
                    val plaintext = Base64.getUrlDecoder().decode(fields[4]).toString(Charsets.UTF_8)
                    assertEquals(
                        fields[1],
                        plaintext,
                        Nip44V2.decryptWithConversationKey(fields[2].hexBytes(), fields[3]),
                    )
                }

                "long" -> {
                    val pattern = Base64.getUrlDecoder().decode(fields[4]).toString(Charsets.UTF_8)
                    val plaintext = pattern.repeat(fields[5].toInt())
                    assertEquals(fields[1], fields[6], sha256(plaintext))
                    val payload = Nip44V2.encryptWithConversationKey(
                        fields[2].hexBytes(),
                        fields[3].hexBytes(),
                        plaintext,
                    )
                    assertEquals(fields[1], fields[7], sha256(payload))
                    assertEquals(
                        fields[1],
                        plaintext,
                        Nip44V2.decryptWithConversationKey(fields[2].hexBytes(), payload),
                    )
                }

                "decrypt_reject" -> assertThrows(fields[1], Nip44DecryptException::class.java) {
                    Nip44V2.decryptWithConversationKey(
                        fields[2].hexBytes(),
                        fields[3].takeUnless { it == "-" }.orEmpty(),
                    )
                }

                "encrypt_reject" -> assertThrows(fields[1], IllegalArgumentException::class.java) {
                    Nip44V2.encryptWithConversationKey(
                        fields[2].hexBytes(),
                        fields[3].hexBytes(),
                        "x".repeat(fields[4].toInt()),
                    )
                }

                else -> error("Unknown NIP-44 fixture operation: ${fields[0]}")
            }
        }
    }

    @Test
    fun `production encryption uses a fresh nonce and rejects tampering`() {
        val privateKey = "00".repeat(31) + "01"
        val publicKey =
            "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
        val first = Nip44V2.encrypt(privateKey, publicKey, "Roadstr favourites")
        val second = Nip44V2.encrypt(privateKey, publicKey, "Roadstr favourites")
        assertNotEquals(first, second)
        assertEquals("Roadstr favourites", Nip44V2.decrypt(privateKey, publicKey, first))

        val tampered = first.dropLast(4) + "AAAA"
        assertThrows(Nip44DecryptException::class.java) {
            Nip44V2.decrypt(privateKey, publicKey, tampered)
        }
    }

    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toHex()
}
