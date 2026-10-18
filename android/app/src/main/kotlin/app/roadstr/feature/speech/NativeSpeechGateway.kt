package app.roadstr.feature.speech

import app.roadstr.core.speech.SpeechOutcome
import java.io.Closeable
import kotlinx.coroutines.flow.StateFlow

enum class NativeSpeechModelStatus {
    /** This device cannot run the offline engine (not arm64, or too little RAM). Nothing is offered. */
    Unsupported,
    Checking,
    NotDownloaded,
    Downloading,
    Ready,
    /** The model is installed but the engine could not use it on this device. */
    Failed,
}

enum class NativeSpeechProblem { Network, Storage, Integrity, EngineUnavailable }

enum class NativeSpeechPhase { Idle, Listening, Transcribing }

data class NativeSpeechSnapshot(
    val model: NativeSpeechModelStatus = NativeSpeechModelStatus.Checking,
    val downloadFraction: Double = 0.0,
    val problem: NativeSpeechProblem? = null,
    val phase: NativeSpeechPhase = NativeSpeechPhase.Idle,
    /** Smoothed microphone level for the listening indicator, 0..1. */
    val level: Float = 0f,
) {
    init {
        require(downloadFraction.isFinite() && downloadFraction in 0.0..1.0)
    }
}

/** Value-only boundary between the shared Compose shell and the Android speech owner. */
interface NativeSpeechGateway : Closeable {
    val state: StateFlow<NativeSpeechSnapshot>

    /** Whether [listen] can serve this language offline right now. */
    fun canRecognize(languageTag: String): Boolean

    fun downloadModel()
    fun cancelDownload()
    fun deleteModel()

    /** Records one utterance and delivers the outcome on the main thread. No-op while already listening. */
    fun listen(languageTag: String, onOutcome: (SpeechOutcome) -> Unit)

    /** The user tapped "done": transcribe what was heard so far. */
    fun finishListening()
    fun cancelListening()

    /** The system is short of memory: let go of the engine unless it is in use. */
    fun trimMemory()
}
