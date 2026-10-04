package app.roadstr.service.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeFavoritesCryptoTest {
    // Produced by the Flutter app's FavoritesCrypto.encrypt on main.
    private val dartPlaintext =
        """[{"label":"Casa","address":"Rua Augusta 1, Lisboa","lat":38.7107,"lon":-9.1368}]"""
    private val dartEnvelope: Map<String, Any?> = mapOf(
        "v" to 1,
        "iterations" to 600000,
        "salt" to "p8LuJr5QpgQgBCtudkFAaw==",
        "iv" to "heJtfJbfJQUGz9DC",
        "ciphertext" to "EET+uA16U9mEy/5G2Ikh8JrfXnz6Vv5gYC5+yrf2m6Tk79m7PPE+mIATq8eZ75ICb3LvmVgQfOL4e/2L5NUJwDcNrZwmXWbxV15ZR/GdF0VmwXcM51c6fySDlo2XxYkL",
    )

    @Test
    fun `an envelope made by the Flutter app decrypts here`() {
        assertEquals(
            dartPlaintext,
            NativeFavoritesCrypto.decrypt(dartEnvelope, "correct horse battery staple"),
        )
    }

    @Test
    fun `a round trip works with non-ASCII passwords and text`() {
        val text = """[{"label":"Café ☕","address":"","lat":1.5,"lon":2.5}]"""
        val envelope = NativeFavoritesCrypto.encrypt(text, "pässwörd ✓")
        assertEquals(text, NativeFavoritesCrypto.decrypt(envelope, "pässwörd ✓"))
        assertEquals(600_000, envelope["iterations"])
    }

    @Test
    fun `wrong password and tampering fail the same way`() {
        assertThrows(NativeFavoritesDecryptException::class.java) {
            NativeFavoritesCrypto.decrypt(dartEnvelope, "wrong")
        }
        val tampered = dartEnvelope.toMutableMap().also {
            val raw = java.util.Base64.getDecoder().decode(it["ciphertext"] as String)
            raw[0] = (raw[0].toInt() xor 1).toByte()
            it["ciphertext"] = java.util.Base64.getEncoder().encodeToString(raw)
        }
        assertThrows(NativeFavoritesDecryptException::class.java) {
            NativeFavoritesCrypto.decrypt(tampered, "correct horse battery staple")
        }
    }

    @Test
    fun `an absurd iteration count from an untrusted file is rejected before any work`() {
        for (bad in listOf(0, 999, 1_000_001, Int.MAX_VALUE)) {
            val envelope = dartEnvelope.toMutableMap().also { it["iterations"] = bad }
            val started = System.nanoTime()
            assertThrows(NativeFavoritesDecryptException::class.java) {
                NativeFavoritesCrypto.decrypt(envelope, "x")
            }
            assert((System.nanoTime() - started) < 500_000_000L) { "rejection must be immediate" }
        }
    }

    @Test
    fun `malformed envelopes are rejected`() {
        assertThrows(NativeFavoritesDecryptException::class.java) {
            NativeFavoritesCrypto.decrypt(mapOf("salt" to "!!!"), "x")
        }
        assertThrows(NativeFavoritesDecryptException::class.java) {
            NativeFavoritesCrypto.decrypt(emptyMap<String, Any?>(), "x")
        }
    }
}
