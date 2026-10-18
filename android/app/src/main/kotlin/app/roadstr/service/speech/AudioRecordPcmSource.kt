package app.roadstr.service.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException

/**
 * The microphone as 16 kHz mono PCM16, tuned for speech (`VOICE_RECOGNITION` turns off the
 * automatic gain that pumps background noise). Needs RECORD_AUDIO, granted before [start].
 */
class AudioRecordPcmSource : PcmSource {
    private var record: AudioRecord? = null

    @Throws(IOException::class)
    override fun start() {
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) throw IOException("this device has no usable audio input")
        // Half a second of headroom, so a slow reader does not lose samples.
        val bytes = maxOf(minimum, SAMPLE_RATE * 2 / 2) * 2
        val created = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bytes,
        )
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            throw IOException("the microphone could not be opened")
        }
        record = created
        try {
            created.startRecording()
        } catch (error: IllegalStateException) {
            close()
            throw IOException("the microphone refused to start", error)
        }
        if (created.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            close()
            throw IOException("the microphone is in use by another app")
        }
    }

    override fun read(buffer: ShortArray): Int = record?.read(buffer, 0, buffer.size) ?: -1

    override fun close() {
        val current = record ?: return
        record = null
        runCatching { current.stop() }
        current.release()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
