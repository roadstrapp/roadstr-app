package app.roadstr.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeVoiceGuidanceSessionTest {
    @Test
    fun `ambient starts then queues and deduplicates`() {
        val session = NativeVoiceGuidanceSession()

        assertTrue(session.submitAmbient("Hazard ahead", 0) is NativeVoiceDirective.Start)
        assertTrue(session.submitAmbient("Camera ahead", 100) is NativeVoiceDirective.Queued)
        assertEquals(
            NativeVoiceDropReason.DuplicateAmbient,
            (session.submitAmbient("Camera ahead", 200) as NativeVoiceDirective.Dropped).reason,
        )
        assertEquals(1, session.snapshot().pending.size)
    }

    @Test
    fun `queue keeps two newest unique utterances`() {
        val session = NativeVoiceGuidanceSession()
        val first = session.submitPriority("first", maneuver = false, nowMillis = 0) as NativeVoiceDirective.Start
        session.submitAmbient("one", 10)
        session.submitAmbient("two", 20)
        val third = session.submitAmbient("three", 30) as NativeVoiceDirective.Queued

        assertTrue(third.evictedId != null)
        assertEquals(listOf("two", "three"), session.snapshot().pending.map { it.text })
        assertTrue(session.finish(first.utterance.id) is NativeVoiceDirective.Start)
        assertEquals("two", session.snapshot().current?.text)
    }

    @Test
    fun `priority waits through minimum audible window then interrupts`() {
        val session = NativeVoiceGuidanceSession()
        session.submitAmbient("hazard", 1_000)

        assertTrue(session.submitPriority("turn", maneuver = false, nowMillis = 2_299) is NativeVoiceDirective.Queued)
        val interrupt = session.submitPriority("turn now", maneuver = false, nowMillis = 2_300)

        assertTrue(interrupt is NativeVoiceDirective.Start && interrupt.interruptCurrent)
        assertEquals("turn now", session.snapshot().current?.text)
    }

    @Test
    fun `departure completes before the first maneuver`() {
        val session = NativeVoiceGuidanceSession()
        val departure = session.submitDeparture("Partenza", 0) as NativeVoiceDirective.Start

        val maneuver = session.submitManeuver("Svolta a destra", 0, "it", false, 2_000)

        assertTrue(maneuver is NativeVoiceDirective.Queued)
        val next = session.finish(departure.utterance.id) as NativeVoiceDirective.Start
        assertEquals("Svolta a destra", next.utterance.text)
    }

    @Test
    fun `advance maneuver never cuts another advance maneuver`() {
        val session = NativeVoiceGuidanceSession()
        session.submitPriority("first turn", maneuver = true, nowMillis = 0)

        val result = session.submitPriority("later turn", maneuver = true, nowMillis = 2_000)

        assertEquals(
            NativeVoiceDropReason.StaleAdvance,
            (result as NativeVoiceDirective.Dropped).reason,
        )
        assertEquals("first turn", session.snapshot().current?.text)
    }

    @Test
    fun `maneuver dedup allows advance to imminent transition once`() {
        val session = NativeVoiceGuidanceSession()
        val advance = session.submitManeuver("Take the 2° exit", 300, "en", false, 1_000)
        val duplicateAdvance = session.submitManeuver("Take the 2° exit", 300, "en", false, 2_000)
        val imminent = session.submitManeuver("Take the 2° exit", 0, "en", false, 2_500)
        val duplicateImminent = session.submitManeuver("Take the 2° exit", 0, "en", false, 3_000)

        assertEquals("In 300 meters, Take the second exit", (advance as NativeVoiceDirective.Start).utterance.text)
        assertEquals(NativeVoiceDropReason.DuplicateManeuver, (duplicateAdvance as NativeVoiceDirective.Dropped).reason)
        assertTrue(imminent is NativeVoiceDirective.Start)
        assertEquals(NativeVoiceDropReason.DuplicateManeuver, (duplicateImminent as NativeVoiceDirective.Dropped).reason)
    }

    @Test
    fun `chained mention suppresses a nearby repeated advance`() {
        val session = NativeVoiceGuidanceSession()
        session.noteChainedMention("Prendi la 1° uscita", "it", 1_000)

        val result = session.submitManeuver("Prendi la 1° uscita", 300, "it", false, 5_000)

        assertEquals(NativeVoiceDropReason.DuplicateManeuver, (result as NativeVoiceDirective.Dropped).reason)
    }

    @Test
    fun `stale completion cannot release focus owned by replacement`() {
        val session = NativeVoiceGuidanceSession()
        val old = session.submitPriority("old", maneuver = false, nowMillis = 0) as NativeVoiceDirective.Start
        val replacement = session.submitPriority("new", maneuver = false, nowMillis = 2_000) as NativeVoiceDirective.Start

        assertEquals(NativeVoiceDirective.None, session.finish(old.utterance.id))
        assertEquals("new", session.snapshot().current?.text)
        assertEquals(NativeVoiceDirective.ReleaseFocus, session.finish(replacement.utterance.id))
    }

    @Test
    fun `external cue lease retains focus until both speech and cue finish`() {
        val session = NativeVoiceGuidanceSession()
        val speech = session.submitAmbient("warning", 0) as NativeVoiceDirective.Start

        assertEquals(NativeVoiceDirective.AcquireFocus, session.beginExternalCue())
        assertEquals(NativeVoiceDirective.HoldFocus, session.finish(speech.utterance.id))
        assertEquals(NativeVoiceDirective.ReleaseFocus, session.endExternalCue())
        assertEquals(0, session.snapshot().externalCueCount)
    }

    @Test
    fun `retry policy waits eight seconds and rejects backwards clocks`() {
        assertTrue(NativeVoiceGuidanceSession.shouldRetryInitialization(null, 0))
        assertFalse(NativeVoiceGuidanceSession.shouldRetryInitialization(1_000, 8_999))
        assertTrue(NativeVoiceGuidanceSession.shouldRetryInitialization(1_000, 9_000))
        assertFalse(NativeVoiceGuidanceSession.shouldRetryInitialization(10_000, 9_000))
    }

    @Test
    fun `ordinal normalization preserves all shipped language words`() {
        assertEquals("prima seconda", NativeVoiceGuidanceSession.normalizeOrdinals("1° 2°", "it"))
        assertEquals("première sixième", NativeVoiceGuidanceSession.normalizeOrdinals("1° 6°", "fr"))
        assertEquals("third 9", NativeVoiceGuidanceSession.normalizeOrdinals("3° 9°", "de"))
    }
}
