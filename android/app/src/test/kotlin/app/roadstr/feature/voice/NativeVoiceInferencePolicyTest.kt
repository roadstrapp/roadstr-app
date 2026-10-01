package app.roadstr.feature.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeVoiceInferencePolicyTest {
    @Test
    fun `Kokoro wraps tokens and selects the matching style row`() {
        val inputs = NativeVoiceInferencePolicy.kokoroInputs(
            ipa = "abx",
            vocabulary = mapOf("a" to 10, "b" to 11),
            voiceFloatCount = 510 * 256,
            speed = 1.3,
        )

        assertArrayEquals(longArrayOf(0, 10, 11, 0), inputs.tokenIds)
        assertEquals(2, inputs.styleRow)
        assertEquals(1.3f, inputs.speed, 0f)
    }

    @Test
    fun `Kokoro rejects empty oversized malformed and out of range inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeVoiceInferencePolicy.kokoroInputs("x", emptyMap(), 510 * 256, 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeVoiceInferencePolicy.kokoroInputs("a", mapOf("a" to 1), 1, 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeVoiceInferencePolicy.kokoroInputs("a".repeat(511), mapOf("a" to 1), 510 * 256, 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeVoiceInferencePolicy.kokoroInputs("a", mapOf("a" to 1), 510 * 256, 2.1)
        }
    }

    @Test
    fun `Piper sequence retains BOS PAD symbol PAD and EOS order`() {
        val missing = mutableListOf<String>()
        val inputs = NativeVoiceInferencePolicy.piperInputs(
            ipa = "axb",
            phonemeIdMap = mapOf("^" to listOf(1), "_" to listOf(0), "\$" to listOf(2), "a" to listOf(10), "b" to listOf(11)),
            speed = 2.0,
            onMissing = missing::add,
        )

        assertArrayEquals(longArrayOf(1, 0, 10, 0, 11, 0, 2), inputs.phonemeIds)
        assertEquals(listOf("x"), missing)
        assertArrayEquals(floatArrayOf(0.667f, 0.5f, 0.8f), inputs.scales, 0f)
    }

    @Test
    fun `phonemizer bounds preserve whitespace and cap individual words`() {
        val longWord = "a".repeat(70)
        val bounded = NativeVoiceInferencePolicy.boundPhonemizerText("one\n$longWord  two")

        assertEquals("one\n${"a".repeat(60)}  two", bounded)
        assertThrows(IllegalArgumentException::class.java) {
            NativeVoiceInferencePolicy.boundPhonemizerText("x".repeat(1001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeVoiceInferencePolicy.validatePhonemeOutput("x".repeat(16_001))
        }
    }

    @Test
    fun `Italian IPA corrections remain exact`() {
        assertEquals(
            "bˈivjo ˈɔljo ˈatʃo",
            NativeVoiceInferencePolicy.correctIpa("bˈivio ˈɔlio ˈaʧo", "it"),
        )
        assertEquals("bˈivio", NativeVoiceInferencePolicy.correctIpa("bˈivio", "en"))
    }

    @Test
    fun `WAV encoder emits bounded mono PCM16 little endian`() {
        val wav = NativeVoiceInferencePolicy.encodeMonoPcm16Wav(
            floatArrayOf(-2f, 0f, 0.5f, 2f),
            24_000,
        )
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", wav.copyOfRange(8, 12).decodeToString())
        assertEquals(24_000, buffer.getInt(24))
        assertEquals(8, buffer.getInt(40))
        assertTrue(buffer.getShort(44) < 0)
        assertEquals(0, buffer.getShort(46).toInt())
        assertTrue(buffer.getShort(50) > 0)
    }
}
