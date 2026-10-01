package app.roadstr.feature.voice

import java.net.URI
import java.util.Collections
import java.util.Locale

enum class NativeVoiceEngine { Kokoro, Piper }

enum class NativeVoiceGender(val storageValue: String) {
    Female("f"),
    Male("m");

    companion object {
        fun fromStorage(value: Any?): NativeVoiceGender = entries.firstOrNull {
            it.storageValue == value?.toString()
        } ?: Male
    }
}

enum class NativeVoiceFixedPhrase { LetsGo, Arrived }

data class NativeVoiceAsset(
    val relativePath: String,
    val remoteUrl: String,
    val expectedBytes: Long,
    val sha256: String,
) {
    init {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/')) {
            "Voice asset path must be relative"
        }
        require(relativePath.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Voice asset path contains an unsafe segment"
        }
        require(expectedBytes > 0) { "Voice asset size must be positive" }
        require(SHA256.matches(sha256)) { "Voice asset hash must be lowercase SHA-256" }
        val uri = runCatching { URI(remoteUrl) }.getOrNull()
        require(
            uri != null &&
                uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.fragment == null,
        ) { "Voice asset URL must be a safe HTTPS URL" }
    }

    private companion object {
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

data class NativeVoiceSelection(
    val languageCode: String,
    val engine: NativeVoiceEngine,
    val voiceName: String,
    val gender: NativeVoiceGender,
    val genderChoiceAvailable: Boolean,
    val sampleRateHz: Int,
)

/** Exact immutable counterpart of the shipped Flutter Kokoro/Piper registry. */
object NativeVoiceCatalog {
    const val KOKORO_REVISION = "a70f0e45c1cc0df9abdfbfa0f6dee9073579ee99"
    const val PIPER_REVISION = "2c11851a427461f4e6a14b5c389386ffb0b01bc7"
    const val KOKORO_SAMPLE_RATE_HZ = 24_000
    const val PIPER_SAMPLE_RATE_HZ = 22_050
    const val DEFAULT_SPEED_STAGE = 4
    const val KOKORO_APPROX_DOWNLOAD_BYTES = 93_000_000L
    const val ESPEAK_SENTINEL_PATH = "espeak-ng-data/.roadstr_extracted"

    val speedStages: List<Double> = Collections.unmodifiableList(
        listOf(0.7, 0.85, 1.0, 1.15, 1.3, 1.5),
    )

    private const val KOKORO_BASE =
        "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/$KOKORO_REVISION"
    private const val PIPER_BASE =
        "https://huggingface.co/Thorsten-Voice/Piper/resolve/$PIPER_REVISION"

    private val voicesByLanguage: Map<String, Map<NativeVoiceGender, String>> = linkedMapOf(
        "en" to linkedMapOf(NativeVoiceGender.Female to "af_heart", NativeVoiceGender.Male to "am_michael"),
        "it" to linkedMapOf(NativeVoiceGender.Female to "if_sara", NativeVoiceGender.Male to "im_nicola"),
        "es" to linkedMapOf(NativeVoiceGender.Female to "ef_dora", NativeVoiceGender.Male to "em_alex"),
        "fr" to linkedMapOf(NativeVoiceGender.Female to "ff_siwis"),
        "ja" to linkedMapOf(NativeVoiceGender.Female to "jf_alpha", NativeVoiceGender.Male to "jm_kumo"),
        "zh" to linkedMapOf(NativeVoiceGender.Female to "zf_xiaobei", NativeVoiceGender.Male to "zm_yunxi"),
        "pt" to linkedMapOf(NativeVoiceGender.Female to "pf_dora", NativeVoiceGender.Male to "pm_alex"),
    )

    private val voiceHashes: Map<String, String> = linkedMapOf(
        "af_heart" to "d583ccff3cdca2f7fae535cb998ac07e9fcb90f09737b9a41fa2734ec44a8f0b",
        "am_michael" to "1d1f21dd8da39c30705cd4c75d039d265e9bc4a2a93ed09bc9e1b1225eb95ba1",
        "ef_dora" to "f66ec66bd295acb18372e37008533a9a3228483ccd294e7538d5d9294ac9a532",
        "em_alex" to "27809e9eafdcbcfff90a3016c697568676531de2a2c39cee29c96c7bd6b83e95",
        "ff_siwis" to "a35f5675ad08948e326ae75fd0ea16ba5d0042e4f76b5f3d1df77d0a48c54861",
        "if_sara" to "409b69248798fcdc2542330c76953d230710f19b057e59cb82fdc3c4cf71265c",
        "im_nicola" to "bc578e510d52a96d6940d46f12e96d7b3df00905dbea075113226d100e6e1ab0",
        "jf_alpha" to "56b479360aad9f367aeb8cef908f9201cf48b4555e488c5f4590c9dfcd978bb6",
        "jm_kumo" to "09e959d239724c734d65661f06f14cdabcddfd476bfaaad905a937099ae9e64f",
        "pf_dora" to "3da7b5b2d91847ebf5646f57631af6ececae3c29a89cd300f06edf9aa6cfe9ee",
        "pm_alex" to "0175c753f59c54e7fd5a995bedef0c5ff2fb67e0043dd3dcb2ae74ec2acbeb2a",
        "zf_xiaobei" to "5dde6e1c9c4f12c8b327bc29c0cee361a23b52b952c04636858ba637ec66e640",
        "zm_yunxi" to "7243892fb4e560d47014090ddf010f8b8b790f3c6b029ff82b2ac06aa4e27c8b",
    )

    val supportedLanguageCodes: Set<String> = Collections.unmodifiableSet(
        linkedSetOf("en", "it", "es", "fr", "ja", "zh", "pt", "de"),
    )

    val downloadAssets: List<NativeVoiceAsset> = Collections.unmodifiableList(
        buildList {
            add(
                NativeVoiceAsset(
                    relativePath = "kokoro/model_q8f16.onnx",
                    remoteUrl = "$KOKORO_BASE/onnx/model_q8f16.onnx",
                    expectedBytes = 86_033_585,
                    sha256 = "04c658aec1b6008857c2ad10f8c589d4180d0ec427e7e6118ceb487e215c3cd0",
                ),
            )
            add(
                NativeVoiceAsset(
                    relativePath = "kokoro/tokenizer.json",
                    remoteUrl = "$KOKORO_BASE/tokenizer.json",
                    expectedBytes = 3_497,
                    sha256 = "77a02c8e164413299b4b4c403b14f8e0e1c1b727db4d46a09d6327b861060a34",
                ),
            )
            voiceHashes.forEach { (voice, hash) ->
                add(
                    NativeVoiceAsset(
                        relativePath = "kokoro/$voice.bin",
                        remoteUrl = "$KOKORO_BASE/voices/$voice.bin",
                        expectedBytes = 522_240,
                        sha256 = hash,
                    ),
                )
            }
            add(
                NativeVoiceAsset(
                    relativePath = "piper/de_DE-thorsten-medium.onnx",
                    remoteUrl = "$PIPER_BASE/de_DE-thorsten-medium.onnx",
                    expectedBytes = 63_201_294,
                    sha256 = "7e64762d8e5118bb578f2eea6207e1a35a8e0c30595010b666f983fc87bb7819",
                ),
            )
            add(
                NativeVoiceAsset(
                    relativePath = "piper/de_DE-thorsten-medium.onnx.json",
                    remoteUrl = "$PIPER_BASE/de_DE-thorsten-medium.onnx.json",
                    expectedBytes = 4_819,
                    sha256 = "974adee790533adb273a1ac88f49027d2a1b8f0f2cf4905954a4791e79264e85",
                ),
            )
        },
    )

    val reusableRelativePaths: Set<String> = Collections.unmodifiableSet(
        linkedSetOf(ESPEAK_SENTINEL_PATH).apply {
            addAll(downloadAssets.map(NativeVoiceAsset::relativePath))
        },
    )

    fun selection(languageCode: String, requestedGender: NativeVoiceGender): NativeVoiceSelection? {
        val language = languageCode.trim().lowercase(Locale.ROOT)
        if (language == "de") {
            return NativeVoiceSelection(
                languageCode = language,
                engine = NativeVoiceEngine.Piper,
                voiceName = "de_DE-thorsten-medium",
                gender = NativeVoiceGender.Male,
                genderChoiceAvailable = false,
                sampleRateHz = PIPER_SAMPLE_RATE_HZ,
            )
        }
        val voices = voicesByLanguage[language] ?: return null
        val resolvedGender = requestedGender.takeIf(voices::containsKey) ?: NativeVoiceGender.Female
        return NativeVoiceSelection(
            languageCode = language,
            engine = NativeVoiceEngine.Kokoro,
            voiceName = requireNotNull(voices[resolvedGender]),
            gender = resolvedGender,
            genderChoiceAvailable = voices.containsKey(NativeVoiceGender.Male),
            sampleRateHz = KOKORO_SAMPLE_RATE_HZ,
        )
    }

    fun eSpeakVoice(languageCode: String): String = when (languageCode.trim().lowercase(Locale.ROOT)) {
        "en" -> "en-us"
        "it" -> "it"
        "es" -> "es"
        "fr" -> "fr"
        "ja" -> "ja"
        "zh" -> "cmn"
        "pt" -> "pt-BR"
        "de" -> "de"
        else -> "en"
    }

    fun speedForStage(stage: Int): Double {
        require(stage in speedStages.indices) { "Invalid voice speed stage" }
        return speedStages[stage]
    }

    fun combinedDownloadProgress(kokoroFraction: Double, piperFraction: Double): Double {
        require(kokoroFraction.isFinite() && kokoroFraction in 0.0..1.0)
        require(piperFraction.isFinite() && piperFraction in 0.0..1.0)
        val piperBytes = 63_201_294L + 4_819L
        val kokoroWeight = KOKORO_APPROX_DOWNLOAD_BYTES.toDouble() /
            (KOKORO_APPROX_DOWNLOAD_BYTES + piperBytes).toDouble()
        return kokoroFraction * kokoroWeight + piperFraction * (1.0 - kokoroWeight)
    }

    fun bundledPhrasePath(
        languageCode: String,
        requestedGender: NativeVoiceGender,
        phrase: NativeVoiceFixedPhrase,
    ): String? {
        val selection = selection(languageCode, requestedGender) ?: return null
        if (selection.engine != NativeVoiceEngine.Kokoro) return null
        val suffix = if (phrase == NativeVoiceFixedPhrase.LetsGo) "letsgo" else "arrived"
        return "assets/kokoro_phrases/${selection.languageCode}_${selection.gender.storageValue}_$suffix.wav"
    }
}
