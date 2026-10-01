package app.roadstr.feature.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class NativeKokoroInputs(
    val tokenIds: LongArray,
    val styleRow: Int,
    val speed: Float,
)

data class NativePiperInputs(
    val phonemeIds: LongArray,
    val scales: FloatArray,
)

/** Pure ONNX input policy; runtime sessions remain outside this boundary. */
object NativeVoiceInferencePolicy {
    const val KOKORO_MAX_PHONEMES = 510
    const val KOKORO_STYLE_WIDTH = 256
    const val KOKORO_STYLE_ROWS = 510
    const val MAX_PHONEMIZER_TEXT_CHARS = 1_000
    const val MAX_PHONEMIZER_WORD_CHARS = 60
    const val MAX_PHONEME_OUTPUT_CHARS = 16_000
    const val PIPER_NOISE_SCALE = 0.667f
    const val PIPER_NOISE_W = 0.8f

    fun kokoroInputs(
        ipa: String,
        vocabulary: Map<String, Int>,
        voiceFloatCount: Int,
        speed: Double,
    ): NativeKokoroInputs {
        require(speed.isFinite() && speed in 0.5..2.0) { "Voice speed must be between 0.5 and 2.0" }
        require(voiceFloatCount == KOKORO_STYLE_ROWS * KOKORO_STYLE_WIDTH) {
            "Kokoro voice data has an invalid shape"
        }
        val tokens = buildList {
            ipa.codePoints().forEach { codePoint ->
                vocabulary[String(Character.toChars(codePoint))]?.let(::add)
            }
        }
        require(tokens.isNotEmpty()) { "No supported phonemes to synthesize" }
        require(tokens.size <= KOKORO_MAX_PHONEMES) { "Phoneme sequence exceeds the model limit" }
        return NativeKokoroInputs(
            tokenIds = LongArray(tokens.size + 2).also { output ->
                tokens.forEachIndexed { index, token -> output[index + 1] = token.toLong() }
            },
            styleRow = tokens.size.coerceAtMost(KOKORO_STYLE_ROWS - 1),
            speed = speed.toFloat(),
        )
    }

    fun piperInputs(
        ipa: String,
        phonemeIdMap: Map<String, List<Int>>,
        speed: Double,
        onMissing: (String) -> Unit = {},
    ): NativePiperInputs {
        require(speed.isFinite() && speed in 0.5..2.0) { "Voice speed must be between 0.5 and 2.0" }
        val pad = requireNotNull(phonemeIdMap["_"]) { "Missing Piper PAD id" }
        val bos = requireNotNull(phonemeIdMap["^"]) { "Missing Piper BOS id" }
        val eos = requireNotNull(phonemeIdMap["\$"]) { "Missing Piper EOS id" }
        val ids = ArrayList<Int>(ipa.length * 2 + bos.size + pad.size + eos.size)
        ids.addAll(bos)
        ids.addAll(pad)
        ipa.codePoints().forEach { codePoint ->
            val symbol = String(Character.toChars(codePoint))
            val mapped = phonemeIdMap[symbol]
            if (mapped == null) {
                onMissing(symbol)
            } else {
                ids.addAll(mapped)
                ids.addAll(pad)
            }
        }
        ids.addAll(eos)
        require(ids.size > bos.size + pad.size + eos.size) { "No supported phonemes to synthesize" }
        return NativePiperInputs(
            phonemeIds = ids.map(Int::toLong).toLongArray(),
            scales = floatArrayOf(PIPER_NOISE_SCALE, (1.0 / speed).toFloat(), PIPER_NOISE_W),
        )
    }

    fun boundPhonemizerText(text: String): String {
        require(text.length <= MAX_PHONEMIZER_TEXT_CHARS) { "Text is too long to phonemize" }
        return NON_WHITESPACE.replace(text) { match ->
            match.value.take(MAX_PHONEMIZER_WORD_CHARS)
        }
    }

    fun validatePhonemeOutput(ipa: String): String {
        require(ipa.length <= MAX_PHONEME_OUTPUT_CHARS) { "Phoneme output is unexpectedly large" }
        return ipa.trim()
    }

    fun correctIpa(ipa: String, languageCode: String): String {
        if (languageCode != "it") return ipa
        return ipa
            .replace("ˈivio", "ˈivjo")
            .replace("ˈɔlio", "ˈɔljo")
            .replace("ˈaʧo", "ˈatʃo")
    }

    fun encodeMonoPcm16Wav(samples: FloatArray, sampleRateHz: Int): ByteArray {
        require(sampleRateHz in 8_000..192_000) { "Invalid WAV sample rate" }
        require(samples.size <= MAX_WAV_SAMPLES) { "Voice waveform is too large" }
        val dataBytes = Math.multiplyExact(samples.size, 2)
        val output = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        output.putAscii("RIFF")
        output.putInt(36 + dataBytes)
        output.putAscii("WAVE")
        output.putAscii("fmt ")
        output.putInt(16)
        output.putShort(1.toShort())
        output.putShort(1.toShort())
        output.putInt(sampleRateHz)
        output.putInt(sampleRateHz * 2)
        output.putShort(2.toShort())
        output.putShort(16.toShort())
        output.putAscii("data")
        output.putInt(dataBytes)
        samples.forEach { sample ->
            val pcm = (sample.coerceIn(-1f, 1f) * 32_767f).roundToInt().toShort()
            output.putShort(pcm)
        }
        return output.array()
    }

    private fun ByteBuffer.putAscii(value: String) {
        value.forEach { put(it.code.toByte()) }
    }

    private const val MAX_WAV_SAMPLES = 24_000 * 60
    private val NON_WHITESPACE = Regex("\\S+")
}
