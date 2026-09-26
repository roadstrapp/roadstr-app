package app.roadstr.core.protocol.nostr

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Nip04CipherParityTest {
    private val rows: List<List<String>> by lazy {
        val resource = requireNotNull(javaClass.getResourceAsStream("/parity/nip04_v1.tsv"))
        resource.bufferedReader().use { reader ->
            reader.readLines()
                .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
                .map { line -> line.split('\t') }
        }
    }

    @Test
    fun `native NIP-04 reproduces every Dart and compatibility vector`() {
        assertEquals(64, rows.size)
        for (fields in rows) {
            when (fields[0]) {
                "shared" -> assertEquals(
                    fields[1],
                    fields[4],
                    Nip04Cipher.sharedSecret(fields[2], fields[3]).toHex(),
                )

                "shared_reject" ->
                    assertThrows(fields[1], IllegalArgumentException::class.java) {
                        Nip04Cipher.sharedSecret(fields[2], fields[3])
                    }

                "encrypt" -> {
                    val plaintext = fields[5].decodeText64()
                    val payload = Nip04Cipher.encryptWithIv(
                        privateKeyHex = fields[2],
                        publicKeyHex = fields[3],
                        plaintext = plaintext,
                        iv = fields[4].hexBytes(),
                    )
                    assertEquals(fields[1], fields[6], payload)
                    assertEquals(
                        fields[1],
                        plaintext,
                        Nip04Cipher.decrypt(fields[2], fields[3], payload),
                    )
                }

                "decrypt" -> assertEquals(
                    fields[1],
                    fields[4].decodeText64(),
                    Nip04Cipher.decryptWithSharedSecret(
                        fields[2].hexBytes(),
                        fields[3].decodeText64(),
                    ),
                )

                "decrypt_reject" ->
                    assertThrows(fields[1], Nip04DecryptException::class.java) {
                        Nip04Cipher.decryptWithSharedSecret(
                            fields[2].hexBytes(),
                            fields[3].takeUnless { it == "-" }?.decodeText64().orEmpty(),
                        )
                    }

                "decrypt_reject_size" ->
                    assertThrows(fields[1], Nip04DecryptException::class.java) {
                        Nip04Cipher.decryptWithSharedSecret(
                            fields[2].hexBytes(),
                            "A".repeat(fields[3].toInt()),
                        )
                    }

                "long" -> {
                    val pattern = fields[4].decodeText64()
                    val plaintext = pattern.repeat(fields[5].toInt())
                    assertEquals(fields[1], fields[6], sha256(plaintext))
                    val payload = Nip04Cipher.encryptWithSharedSecret(
                        secret = fields[2].hexBytes(),
                        plaintext = plaintext,
                        iv = fields[3].hexBytes(),
                    )
                    assertEquals(fields[1], fields[7], sha256(payload))
                    assertEquals(
                        fields[1],
                        plaintext,
                        Nip04Cipher.decryptWithSharedSecret(fields[2].hexBytes(), payload),
                    )
                }

                "encrypt_reject" ->
                    assertThrows(fields[1], IllegalArgumentException::class.java) {
                        Nip04Cipher.encryptWithSharedSecret(
                            secret = fields[2].hexBytes(),
                            plaintext = fields[4].decodeText64().repeat(fields[5].toInt()),
                            iv = fields[3].hexBytes(),
                        )
                    }

                else -> error("Unknown NIP-04 fixture operation: ${fields[0]}")
            }
        }
    }

    @Test
    fun `production encryption uses a fresh IV and peer ECDH is symmetric`() {
        val aliceSecret = "00".repeat(31) + "01"
        val alicePublic =
            "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        val bobSecret = "00".repeat(31) + "02"
        val bobPublic =
            "c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5"
        val first = Nip04Cipher.encrypt(aliceSecret, bobPublic, "Roadstr NWC")
        val second = Nip04Cipher.encrypt(aliceSecret, bobPublic, "Roadstr NWC")

        assertNotEquals(first, second)
        assertEquals("Roadstr NWC", Nip04Cipher.decrypt(bobSecret, alicePublic, first))
        assertEquals(
            Nip04Cipher.sharedSecret(aliceSecret, bobPublic).toHex(),
            Nip04Cipher.sharedSecret(bobSecret, alicePublic).toHex(),
        )
    }

    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun String.decodeText64(): String =
        Base64.getUrlDecoder().decode(this).toString(Charsets.UTF_8)

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toHex()
}
