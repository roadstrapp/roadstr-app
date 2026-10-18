package app.roadstr.core.speech

/** Why offline recognition cannot serve this request; the caller falls back to another recognizer. */
enum class SpeechUnavailableReason {
    ModelMissing,
    UnsupportedLanguage,
    UnsupportedDevice,
    LowMemory,
    ModelRejected,
    Microphone,
    Failed,
}

sealed interface SpeechOutcome {
    data class Text(val text: String, val decodeMillis: Long) : SpeechOutcome
    data object NothingHeard : SpeechOutcome
    data object Cancelled : SpeechOutcome
    data class Unavailable(val reason: SpeechUnavailableReason) : SpeechOutcome
}
