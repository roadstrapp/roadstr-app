package app.roadstr.offlinestt

import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong

/** What the engine heard. */
data class SttTranscription(
    val text: String,
    /** ISO language code the model detected; null when it was given a hint or does not detect. */
    val detectedLanguage: String?,
    /** Length of the audio that was submitted. */
    val audioMillis: Long,
    /** Wall-clock time the decode took. */
    val decodeMillis: Long,
)

/** Everything [OfflineSttEngine] can fail with. */
sealed class OfflineSttException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** This device cannot run the engine (wrong ABI, or the libraries are missing or incompatible). */
    class Unsupported(message: String, cause: Throwable? = null) : OfflineSttException(message, cause)

    /** The model file is missing, corrupt, or too large to load. */
    class ModelUnavailable(message: String, cause: Throwable? = null) : OfflineSttException(message, cause)

    /** The audio cannot be decoded: empty, shorter than 100 ms, longer than 30 s, or not finite. */
    class InvalidAudio(message: String, cause: Throwable? = null) : OfflineSttException(message, cause)

    /** Another [OfflineSttEngine.transcribe] call is still running on this engine. */
    class Busy(message: String) : OfflineSttException(message)

    /** The engine was closed. */
    class Closed(message: String) : OfflineSttException(message)

    /** [OfflineSttEngine.cancel] aborted the call. */
    class Cancelled(message: String) : OfflineSttException(message)

    /** The native runtime failed while decoding. */
    class Failed(message: String, cause: Throwable? = null) : OfflineSttException(message, cause)
}

/**
 * One loaded speech model. [transcribe] takes one complete 16 kHz mono utterance and returns text.
 *
 * Both [load] and [transcribe] block for seconds and must run off the main thread. The engine
 * holds the whole model in RAM (about 1.1 GB for Parakeet 0.6B Q4) until [close], so hosts should
 * check available memory first and close it when idle.
 */
class OfflineSttEngine private constructor(id: Long) : AutoCloseable {
    private val id = AtomicLong(id)

    /** Transcribes the first [length] samples (16 kHz, mono, -1.0..1.0). Blocking. */
    @Throws(OfflineSttException::class)
    fun transcribe(pcm: FloatArray, length: Int = pcm.size): SttTranscription {
        val current = id.get()
        if (current == 0L) throw OfflineSttException.Closed("the engine is closed")
        val fields = try {
            OfflineSttNative.transcribe(current, pcm, length)
        } catch (error: Throwable) {
            throw translate(error)
        }
        return SttTranscription(
            text = fields[0],
            detectedLanguage = fields[1].takeIf(String::isNotEmpty),
            audioMillis = fields[2].toLong(),
            decodeMillis = fields[3].toLong(),
        )
    }

    /** Aborts the [transcribe] call in flight, which then throws [OfflineSttException.Cancelled]. */
    fun cancel() {
        val current = id.get()
        if (current != 0L) runCatching { OfflineSttNative.cancel(current) }
    }

    /** Frees the model once any call in flight returns. Safe to call more than once. */
    override fun close() {
        val current = id.getAndSet(0L)
        if (current != 0L) runCatching { OfflineSttNative.release(current) }
    }

    companion object {
        /** Whether the native libraries load on this device. Cheap after the first call. */
        fun isSupported(): Boolean = NativeLibraries.ensureLoaded().isSuccess

        /** `offlinestt <version> (transcribe.cpp <version>)`, or null when unsupported. */
        fun nativeVersion(): String? =
            if (isSupported()) runCatching { OfflineSttNative.version() }.getOrNull() else null

        /**
         * Loads [modelFile] (a GGUF file). [language] is an optional hint such as `it` or `de-DE`;
         * a hint the model rejects is degraded, never fatal. [threads] 0 picks the device default.
         */
        @Throws(OfflineSttException::class)
        fun load(modelFile: File, language: String? = null, threads: Int = 0): OfflineSttEngine {
            NativeLibraries.ensureLoaded().onFailure { error ->
                throw OfflineSttException.Unsupported("the offline speech engine cannot run here", error)
            }
            val id = try {
                OfflineSttNative.load(modelFile.absolutePath, language, threads)
            } catch (error: Throwable) {
                throw translate(error)
            }
            return OfflineSttEngine(id)
        }

        private fun translate(error: Throwable): OfflineSttException = when (error) {
            is OfflineSttException -> error
            is IOException -> OfflineSttException.ModelUnavailable(error.message ?: "model unavailable", error)
            is IllegalArgumentException -> OfflineSttException.InvalidAudio(error.message ?: "invalid audio", error)
            is RejectedExecutionException -> OfflineSttException.Busy(error.message ?: "busy")
            is IllegalStateException -> OfflineSttException.Closed(error.message ?: "closed")
            is CancellationException -> OfflineSttException.Cancelled(error.message ?: "cancelled")
            is UnsatisfiedLinkError -> OfflineSttException.Unsupported("native engine missing", error)
            else -> OfflineSttException.Failed(error.message ?: error.javaClass.simpleName, error)
        }
    }
}
