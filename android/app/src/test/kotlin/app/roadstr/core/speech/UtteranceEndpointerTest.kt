package app.roadstr.core.speech

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceEndpointerTest {
    private val rate = 16_000

    /** Deterministic noise with the given peak amplitude (0..1). */
    private fun noise(millis: Int, amplitude: Double, seed: Long = 7): ShortArray {
        var state = seed
        return ShortArray(rate * millis / 1000) {
            state = state * 6364136223846793005L + 1442695040888963407L
            val unit = ((state ushr 33).toInt() % 2001) / 1000.0 - 1.0
            (unit * amplitude * 32767).toInt().toShort()
        }
    }

    /** A voiced-sounding tone with a pause-free envelope. */
    private fun speech(millis: Int, amplitude: Double = 0.25): ShortArray = ShortArray(rate * millis / 1000) {
        val t = it.toDouble() / rate
        ((sin(2 * PI * 180 * t) * 0.6 + sin(2 * PI * 540 * t) * 0.4) * amplitude * 32767).toInt().toShort()
    }

    private fun concat(vararg parts: ShortArray): ShortArray = parts.fold(ShortArray(0)) { a, b -> a + b }

    private fun feedAll(endpointer: UtteranceEndpointer, audio: ShortArray, chunk: Int = 1_600) {
        var at = 0
        while (at < audio.size && endpointer.status != UtteranceEndpointer.Status.Finished &&
            endpointer.status != UtteranceEndpointer.Status.NoSpeech
        ) {
            val count = minOf(chunk, audio.size - at)
            endpointer.feed(audio.copyOfRange(at, at + count), count)
            at += count
        }
    }

    @Test
    fun silenceAloneEndsAsNoSpeechAfterTheTimeout() {
        val endpointer = UtteranceEndpointer()
        feedAll(endpointer, noise(7_000, 0.004))
        assertEquals(UtteranceEndpointer.Status.Waiting, endpointer.status)
        feedAll(endpointer, noise(2_000, 0.004, seed = 9))
        assertEquals(UtteranceEndpointer.Status.NoSpeech, endpointer.status)
    }

    @Test
    fun anUtteranceIsCapturedWithPreRollAndOnlyShortTrailingSilence() {
        val endpointer = UtteranceEndpointer()
        feedAll(endpointer, concat(noise(800, 0.004), speech(1_000), noise(3_000, 0.004, seed = 3)))
        assertEquals(UtteranceEndpointer.Status.Finished, endpointer.status)

        val millis = endpointer.utterance().size * 1000 / rate
        // 1 s of speech, up to 0.3 s before it and 0.3 s after it.
        assertTrue("captured $millis ms", millis in 1_000..1_700)
        val samples = endpointer.utterance()
        assertTrue("pre-roll must keep the start of the speech", samples.take(rate / 4).any { it != 0f })
    }

    @Test
    fun speechFromTheVeryFirstSampleIsStillHeard() {
        val endpointer = UtteranceEndpointer()
        feedAll(endpointer, concat(speech(1_200), noise(2_000, 0.004)))
        assertEquals(UtteranceEndpointer.Status.Finished, endpointer.status)
        assertTrue(endpointer.utterance().size * 1000 / rate >= 1_200)
    }

    @Test
    fun aSteadyLoudBackgroundDoesNotCountAsSpeech() {
        val endpointer = UtteranceEndpointer()
        // Noise at ~1% RMS from the start becomes the floor; speech is 20x louder.
        feedAll(endpointer, concat(noise(1_000, 0.02), speech(1_000), noise(3_000, 0.02, seed = 5)))
        assertEquals(UtteranceEndpointer.Status.Finished, endpointer.status)
        val millis = endpointer.utterance().size * 1000 / rate
        assertTrue("captured $millis ms", millis in 1_000..1_700)
    }

    @Test
    fun aClickIsNotAnUtterance() {
        val endpointer = UtteranceEndpointer()
        feedAll(endpointer, concat(noise(600, 0.004), speech(60), noise(3_000, 0.004, seed = 4)))
        assertEquals(UtteranceEndpointer.Status.NoSpeech, endpointer.status)
    }

    @Test
    fun theResultDoesNotDependOnHowTheAudioIsChunked() {
        val audio = concat(noise(500, 0.004), speech(900), noise(2_500, 0.004, seed = 2))
        val whole = UtteranceEndpointer().also { feedAll(it, audio, chunk = audio.size) }
        val tiny = UtteranceEndpointer().also { feedAll(it, audio, chunk = 7) }
        assertEquals(UtteranceEndpointer.Status.Finished, whole.status)
        assertEquals(whole.status, tiny.status)
        assertEquals(whole.utterance().size, tiny.utterance().size)
        assertTrue(whole.utterance().contentEquals(tiny.utterance()))
    }

    @Test
    fun anEndlessMonologueIsCutAtTheMaximumLength() {
        val endpointer = UtteranceEndpointer()
        feedAll(endpointer, concat(noise(500, 0.004), speech(25_000)))
        assertEquals(UtteranceEndpointer.Status.Finished, endpointer.status)
        val millis = endpointer.utterance().size * 1000 / rate
        assertTrue("captured $millis ms", millis in 19_000..20_500)
    }

    @Test
    fun finishNowEndsAnUtteranceInProgressButNotAnEmptyOne() {
        val empty = UtteranceEndpointer()
        feedAll(empty, noise(1_000, 0.004))
        assertEquals(UtteranceEndpointer.Status.NoSpeech, empty.finishNow())

        val speaking = UtteranceEndpointer()
        feedAll(speaking, concat(noise(500, 0.004), speech(900)))
        assertEquals(UtteranceEndpointer.Status.Hearing, speaking.status)
        assertEquals(UtteranceEndpointer.Status.Finished, speaking.finishNow())
        assertTrue(speaking.utterance().isNotEmpty())
    }

    @Test
    fun inputAfterTheEndIsIgnoredAndTheUtteranceIsStable() {
        val endpointer = UtteranceEndpointer()
        feedAll(endpointer, concat(noise(500, 0.004), speech(900), noise(2_000, 0.004, seed = 8)))
        val before = endpointer.utterance()
        endpointer.feed(speech(2_000))
        assertTrue(before.contentEquals(endpointer.utterance()))
    }

    @Test
    fun thePcmIsScaledIntoTheEngineRange() {
        val endpointer = UtteranceEndpointer()
        feedAll(endpointer, concat(noise(500, 0.004), speech(900, amplitude = 0.5), noise(2_000, 0.004, seed = 6)))
        val peak = endpointer.utterance().maxOf { kotlin.math.abs(it) }
        assertTrue("peak $peak", peak in 0.3f..1.0f)
    }

    @Test
    fun theLevelMeterMovesWithTheVoice() {
        val endpointer = UtteranceEndpointer()
        feedAll(endpointer, noise(600, 0.004))
        val quiet = endpointer.level
        endpointer.feed(speech(300))
        assertTrue("quiet $quiet loud ${endpointer.level}", endpointer.level > quiet + 0.2f)
    }
}
