package app.roadstr.service.speech

import app.roadstr.core.speech.SpeechUnavailableReason
import app.roadstr.offlinestt.OfflineSttEngine
import app.roadstr.offlinestt.OfflineSttException
import java.io.File

/** Adapts the vendored native engine to [SpeechEngine], translating its failures into fallbacks. */
object OfflineSttSpeechEngineFactory : SpeechEngineFactory {
    override fun load(modelFile: File, languageHint: String?): SpeechEngine {
        if (!OfflineSttEngine.isSupported()) {
            throw SpeechUnavailable(SpeechUnavailableReason.UnsupportedDevice, "the offline speech engine cannot run here")
        }
        val engine = try {
            OfflineSttEngine.load(modelFile, languageHint)
        } catch (error: OfflineSttException) {
            throw SpeechUnavailable(reasonFor(error), error.message ?: "the model could not be loaded", error)
        }
        return Adapter(engine)
    }

    private fun reasonFor(error: OfflineSttException) = when (error) {
        is OfflineSttException.Unsupported -> SpeechUnavailableReason.UnsupportedDevice
        is OfflineSttException.ModelUnavailable -> SpeechUnavailableReason.ModelRejected
        else -> SpeechUnavailableReason.Failed
    }

    private class Adapter(private val engine: OfflineSttEngine) : SpeechEngine {
        override fun transcribe(pcm: FloatArray): String = try {
            engine.transcribe(pcm).text
        } catch (error: OfflineSttException.Cancelled) {
            ""
        } catch (error: OfflineSttException.InvalidAudio) {
            // Too short to be speech: nothing was said, which is not a failure.
            ""
        } catch (error: OfflineSttException) {
            throw SpeechUnavailable(SpeechUnavailableReason.Failed, error.message ?: "decoding failed", error)
        }

        override fun cancel() = engine.cancel()

        override fun close() = engine.close()
    }
}
