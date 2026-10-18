package app.roadstr.feature.search

/** One activity-result transcription; revision makes repeated identical dictations observable. */
data class NativeSearchSpeechTranscript(
    val revision: Long,
    val text: String,
)
