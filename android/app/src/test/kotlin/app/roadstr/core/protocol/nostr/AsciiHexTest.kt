package app.roadstr.core.protocol.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AsciiHexTest {
    private val privateKey = "0000000000000000000000000000000000000000000000000000000000000003"
    private val eventId = "5e4c5b1a4b3a2918f7e6d5c4b3a2918f7e6d5c4b3a2918f7e6d5c4b3a2918000"
    private val aux = "0000000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun `only ASCII characters are hexadecimal digits`() {
        assertEquals(0, asciiHexDigit('0'))
        assertEquals(15, asciiHexDigit('f'))
        assertEquals(10, asciiHexDigit('A'))
        assertEquals(-1, asciiHexDigit('g'))
        assertEquals(-1, asciiHexDigit('٣')) // ARABIC-INDIC DIGIT THREE
        assertEquals(-1, asciiHexDigit('３')) // FULLWIDTH DIGIT THREE
        assertEquals(-1, asciiHexDigit('Ａ')) // FULLWIDTH LATIN CAPITAL A
    }

    @Test
    fun `a valid signature does not verify under a non-ASCII spelling of its key`() {
        val publicKey = NostrSchnorr.publicKey(privateKey)
        val signature = NostrSchnorr.signHashWithAux(privateKey, eventId, aux)
        assertTrue(NostrSchnorr.verifyHash(publicKey, eventId, signature))

        // Same bytes under Character.digit, a different string for every
        // caller that compares authors as text.
        val disguised = publicKey.map { character ->
            if (character in '0'..'9') '٠' + (character - '0') else character
        }.joinToString("")
        assertNotEquals(publicKey, disguised)
        assertFalse(NostrSchnorr.verifyHash(disguised, eventId, signature))
    }

    @Test
    fun `encryption rejects a non-ASCII spelling of a key`() {
        val peer = NostrSchnorr.publicKey(
            "0000000000000000000000000000000000000000000000000000000000000005",
        )
        val disguisedPrivate = privateKey.replace('3', '٣')
        assertThrows(IllegalArgumentException::class.java) {
            Nip44V2.conversationKey(disguisedPrivate, peer)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Nip04Cipher.sharedSecret(disguisedPrivate, peer)
        }
    }
}
