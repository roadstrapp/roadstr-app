package app.roadstr.core.speech

import java.util.Locale

/** One downloadable offline speech model, pinned by size and SHA-256. */
data class SpeechModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val byteSize: Long,
    val sha256: String,
    /** Names an exact upstream commit, so the bytes behind the URL cannot change. */
    val downloadUrl: String,
    val licenseName: String,
    val licenseUrl: String,
    val attribution: String,
    /** Primary language subtags the model transcribes. */
    val languages: Set<String>,
    /** Below this much RAM in total the model is not offered at all. */
    val minimumTotalMemoryBytes: Long,
    /** Free RAM needed at the moment the model is loaded into memory. */
    val requiredAvailableMemoryBytes: Long,
) {
    init {
        require(downloadUrl.startsWith("https://")) { "Model downloads must use HTTPS" }
        require(Regex("[0-9a-f]{64}").matches(sha256)) { "sha256 must be 64 lowercase hex digits" }
        require(byteSize > 0L)
        require(fileName.none { it == '/' || it == '\\' } && fileName.endsWith(".gguf")) {
            "fileName must be a plain .gguf name"
        }
    }

    /** The two-letter-style primary subtag of [languageTag], lowercase (`it-IT` -> `it`). */
    private fun primary(languageTag: String): String =
        languageTag.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)

    fun supportsLanguage(languageTag: String): Boolean = primary(languageTag) in languages

    /**
     * The hint to give the engine for [languageTag]: the primary subtag when the model knows it,
     * otherwise null (the model detects the language itself, which it does worse on short phrases).
     */
    fun languageHint(languageTag: String): String? =
        primary(languageTag).takeIf { it in languages }
}

object SpeechModelCatalog {
    private const val MIB = 1024L * 1024L
    private const val GIB = 1024L * MIB

    /**
     * NVIDIA Parakeet TDT 0.6B v3, Q4_K_M GGUF conversion by handy-computer for transcribe.cpp.
     * 25 European languages. CC-BY-4.0: the attribution below must stay visible in the app.
     */
    val parakeetV3 = SpeechModelSpec(
        id = "parakeet-tdt-0.6b-v3-q4km",
        displayName = "Parakeet TDT 0.6B v3 (Q4)",
        fileName = "parakeet-tdt-0.6b-v3-Q4_K_M.gguf",
        byteSize = 485_425_504L,
        sha256 = "b68557be1e3c40207fd7c4bd9d63f1d3316b963f15325bfb0cc16a8bb0ffd181",
        downloadUrl = "https://huggingface.co/handy-computer/parakeet-tdt-0.6b-v3-gguf/resolve/" +
            "85ac09ea12fc4b1112fa76810059364bc6adc9de/parakeet-tdt-0.6b-v3-Q4_K_M.gguf?download=true",
        licenseName = "CC-BY-4.0",
        licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
        attribution = "Parakeet TDT 0.6B v3 by NVIDIA, converted to GGUF by handy-computer, " +
            "licensed under CC-BY-4.0",
        languages = setOf(
            "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hu", "it",
            "lv", "lt", "mt", "pl", "pt", "ro", "sk", "sl", "es", "sv", "ru", "uk",
        ),
        // Conservative estimates, not on-device measurements: the Q4 file is ~0.5 GB, plus
        // activations. A phone with less than 3 GB in total would be pushed out of memory.
        minimumTotalMemoryBytes = 3 * GIB,
        requiredAvailableMemoryBytes = 1_536L * MIB,
    )

    val default: SpeechModelSpec = parakeetV3
}
