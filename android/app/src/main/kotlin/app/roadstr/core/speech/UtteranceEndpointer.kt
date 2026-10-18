package app.roadstr.core.speech

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds where one spoken utterance starts and ends in a stream of 16 kHz mono PCM16, so the app can
 * stop recording by itself. Feed it whatever the microphone returns; ask [status] after each call.
 *
 * The noise floor is the quietest recent frame; speech is a run of frames [Config.speechRatio]
 * times louder than it. The floor only follows frames judged to be noise, so a long sentence
 * cannot drag it up. Audio from before the speech was recognised is kept (pre-roll) so the first
 * syllable is never clipped.
 */
class UtteranceEndpointer(private val config: Config = Config()) {
    data class Config(
        val sampleRate: Int = 16_000,
        val frameMillis: Int = 20,
        /** Frames at the start used to learn the noise floor. */
        val calibrationMillis: Int = 240,
        /** RMS (0..1) below which nothing counts as speech, however quiet the room. */
        val minimumThreshold: Float = 0.008f,
        /** Cap on the learned floor, so speech from the first sample cannot raise the bar out of reach. */
        val maximumFloor: Float = 0.02f,
        val speechRatio: Float = 2.2f,
        val startMillis: Int = 60,
        val endSilenceMillis: Int = 1_200,
        val preRollMillis: Int = 300,
        /** Silence kept after the last speech; the engine adds its own right context. */
        val trailingKeepMillis: Int = 300,
        val minimumSpeechMillis: Int = 240,
        val noSpeechTimeoutMillis: Int = 8_000,
        val maximumUtteranceMillis: Int = 20_000,
    )

    enum class Status { Waiting, Hearing, Finished, NoSpeech }

    var status: Status = Status.Waiting
        private set

    /** Smoothed loudness for a level meter, 0..1. */
    var level: Float = 0f
        private set

    private val frameSamples = config.sampleRate * config.frameMillis / 1000
    private val calibrationFrames = config.calibrationMillis / config.frameMillis
    private val startFrames = max(1, config.startMillis / config.frameMillis)
    private val endSilenceFrames = config.endSilenceMillis / config.frameMillis
    private val preRollFrames = config.preRollMillis / config.frameMillis
    private val trailingKeepFrames = config.trailingKeepMillis / config.frameMillis
    private val minimumSpeechFrames = max(1, config.minimumSpeechMillis / config.frameMillis)
    private val noSpeechTimeoutFrames = config.noSpeechTimeoutMillis / config.frameMillis
    private val maximumFrames = config.maximumUtteranceMillis / config.frameMillis

    private val carry = ShortArray(frameSamples)
    private var carried = 0
    private val preRoll = ArrayDeque<ShortArray>()
    private var utterance = ShortArray(frameSamples * 50)
    private var utteranceFrames = 0
    private var floor = config.maximumFloor
    private var framesSeen = 0
    private var loudRun = 0
    private var speechFrames = 0
    private var silentRun = 0

    /** Consumes [count] samples. Returns the status after them; once finished, input is ignored. */
    fun feed(samples: ShortArray, count: Int = samples.size): Status {
        require(count in 0..samples.size)
        var at = 0
        while (at < count && !isDone()) {
            val take = min(frameSamples - carried, count - at)
            System.arraycopy(samples, at, carry, carried, take)
            carried += take
            at += take
            if (carried == frameSamples) {
                carried = 0
                acceptFrame(carry.copyOf())
            }
        }
        return status
    }

    /** Ends the utterance now, as if the user tapped "done". */
    fun finishNow(): Status {
        when (status) {
            Status.Waiting -> status = Status.NoSpeech
            Status.Hearing -> finish()
            else -> Unit
        }
        return status
    }

    /** The captured utterance as floats in -1..1. Meaningful only when [status] is [Status.Finished]. */
    fun utterance(): FloatArray {
        if (status != Status.Finished) return FloatArray(0)
        return FloatArray(utteranceFrames * frameSamples) { utterance[it] / 32768f }
    }

    private fun isDone() = status == Status.Finished || status == Status.NoSpeech

    private fun acceptFrame(frame: ShortArray) {
        val rms = rms(frame)
        framesSeen++
        level = level * 0.6f + min(1f, rms / (max(floor * config.speechRatio, config.minimumThreshold) * 4f)) * 0.4f
        if (status == Status.Waiting) waiting(frame, rms) else hearing(frame, rms)
    }

    private fun threshold() = max(floor * config.speechRatio, config.minimumThreshold)

    private fun waiting(frame: ShortArray, rms: Float) {
        preRoll.addLast(frame)
        while (preRoll.size > preRollFrames + startFrames) preRoll.removeFirst()
        if (framesSeen <= calibrationFrames) {
            floor = min(floor, max(rms, 1e-5f))
            return
        }
        if (rms >= threshold()) {
            loudRun++
        } else {
            loudRun = 0
            followNoise(rms)
        }
        if (loudRun >= startFrames) {
            status = Status.Hearing
            speechFrames = loudRun
            silentRun = 0
            preRoll.forEach(::append)
            preRoll.clear()
        } else if (framesSeen >= noSpeechTimeoutFrames) {
            status = Status.NoSpeech
        }
    }

    private fun hearing(frame: ShortArray, rms: Float) {
        append(frame)
        if (rms >= threshold()) {
            speechFrames++
            silentRun = 0
        } else {
            silentRun++
            followNoise(rms)
        }
        if (silentRun >= endSilenceFrames || utteranceFrames >= maximumFrames) finish()
    }

    /** Falls fast to a quieter room, rises slowly toward a noisier one, never past the cap. */
    private fun followNoise(rms: Float) {
        val next = if (rms < floor) rms else floor + (rms - floor) * 0.02f
        floor = min(max(next, 1e-5f), config.maximumFloor)
    }

    private fun finish() {
        if (speechFrames < minimumSpeechFrames) {
            status = Status.NoSpeech
            return
        }
        utteranceFrames -= max(0, silentRun - trailingKeepFrames)
        status = Status.Finished
    }

    private fun append(frame: ShortArray) {
        val needed = (utteranceFrames + 1) * frameSamples
        if (needed > utterance.size) utterance = utterance.copyOf(max(needed, utterance.size * 2))
        System.arraycopy(frame, 0, utterance, utteranceFrames * frameSamples, frameSamples)
        utteranceFrames++
    }

    private fun rms(frame: ShortArray): Float {
        var sum = 0.0
        for (sample in frame) {
            val value = sample / 32768.0
            sum += value * value
        }
        return sqrt(sum / frame.size).toFloat()
    }
}
