package app.roadstr.feature.voice

import app.roadstr.core.format.UnitFormatter
import java.util.ArrayDeque

enum class NativeVoiceDropReason { DuplicateAmbient, DuplicateManeuver, DuplicateQueued, StaleAdvance }

data class NativeVoiceUtterance(
    val id: Long,
    val text: String,
    val priority: Boolean,
    val maneuver: Boolean,
    val submittedAtMillis: Long,
)

sealed interface NativeVoiceDirective {
    data class Start(val utterance: NativeVoiceUtterance, val interruptCurrent: Boolean) :
        NativeVoiceDirective

    data class Queued(val utterance: NativeVoiceUtterance, val evictedId: Long?) : NativeVoiceDirective
    data class Dropped(val reason: NativeVoiceDropReason) : NativeVoiceDirective
    data object AcquireFocus : NativeVoiceDirective
    data object ReleaseFocus : NativeVoiceDirective
    data object HoldFocus : NativeVoiceDirective
    data object None : NativeVoiceDirective
}

data class NativeVoiceGuidanceSnapshot(
    val current: NativeVoiceUtterance?,
    val pending: List<NativeVoiceUtterance>,
    val externalCueCount: Int,
)

/**
 * Side-effect-free scheduling state for navigation speech and overlapping
 * beeps. A future playback owner executes the returned directives.
 */
class NativeVoiceGuidanceSession {
    private val lock = Any()
    private val pending = ArrayDeque<NativeVoiceUtterance>()
    private var current: NativeVoiceUtterance? = null
    private var nextId = 0L
    private var externalCueCount = 0
    private var lastAmbientText: String? = null
    private var lastAmbientAtMillis = Long.MIN_VALUE
    private var lastManeuverText: String? = null
    private var lastManeuverAtMillis = Long.MIN_VALUE
    private var lastManeuverWasImminent = false

    fun snapshot(): NativeVoiceGuidanceSnapshot = synchronized(lock) {
        NativeVoiceGuidanceSnapshot(current, pending.toList(), externalCueCount)
    }

    fun submitAmbient(text: String, nowMillis: Long): NativeVoiceDirective = synchronized(lock) {
        val clean = cleanText(text)
        if (clean == lastAmbientText && elapsed(nowMillis, lastAmbientAtMillis) < AMBIENT_DEDUP_MILLIS) {
            return NativeVoiceDirective.Dropped(NativeVoiceDropReason.DuplicateAmbient)
        }
        lastAmbientText = clean
        lastAmbientAtMillis = nowMillis
        submitLocked(clean, priority = false, maneuver = false, nowMillis = nowMillis)
    }

    fun submitPriority(
        text: String,
        maneuver: Boolean,
        nowMillis: Long,
    ): NativeVoiceDirective = synchronized(lock) {
        submitLocked(cleanText(text), priority = true, maneuver = maneuver, nowMillis = nowMillis)
    }

    fun submitManeuver(
        instruction: String,
        distanceMetres: Int,
        languageCode: String,
        imperial: Boolean,
        nowMillis: Long,
    ): NativeVoiceDirective = synchronized(lock) {
        val clean = normalizeOrdinals(cleanText(instruction), languageCode)
        val imminent = distanceMetres <= 0
        if (
            clean == lastManeuverText &&
            elapsed(nowMillis, lastManeuverAtMillis) < MANEUVER_DEDUP_MILLIS &&
            (!imminent || lastManeuverWasImminent)
        ) {
            return NativeVoiceDirective.Dropped(NativeVoiceDropReason.DuplicateManeuver)
        }
        lastManeuverText = clean
        lastManeuverAtMillis = nowMillis
        lastManeuverWasImminent = imminent
        val prefix = UnitFormatter(imperial).ttsDistancePrefix(distanceMetres, languageCode)
        submitLocked(
            text = if (prefix.isEmpty()) clean else "$prefix$clean",
            priority = true,
            maneuver = !imminent,
            nowMillis = nowMillis,
        )
    }

    fun noteChainedMention(instruction: String, languageCode: String, nowMillis: Long) =
        synchronized(lock) {
            lastManeuverText = normalizeOrdinals(cleanText(instruction), languageCode)
            lastManeuverAtMillis = nowMillis
            lastManeuverWasImminent = false
        }

    fun finish(utteranceId: Long): NativeVoiceDirective = synchronized(lock) {
        if (current?.id != utteranceId) return NativeVoiceDirective.None
        current = null
        val next = if (pending.isEmpty()) null else pending.removeFirst()
        if (next != null) {
            current = next
            return NativeVoiceDirective.Start(next, interruptCurrent = false)
        }
        if (externalCueCount == 0) NativeVoiceDirective.ReleaseFocus else NativeVoiceDirective.HoldFocus
    }

    fun beginExternalCue(): NativeVoiceDirective = synchronized(lock) {
        externalCueCount += 1
        NativeVoiceDirective.AcquireFocus
    }

    fun endExternalCue(): NativeVoiceDirective = synchronized(lock) {
        if (externalCueCount > 0) externalCueCount -= 1
        if (externalCueCount == 0 && current == null) {
            NativeVoiceDirective.ReleaseFocus
        } else {
            NativeVoiceDirective.HoldFocus
        }
    }

    fun stop(): NativeVoiceDirective = synchronized(lock) {
        current = null
        pending.clear()
        lastManeuverText = null
        lastManeuverWasImminent = false
        if (externalCueCount == 0) NativeVoiceDirective.ReleaseFocus else NativeVoiceDirective.HoldFocus
    }

    private fun submitLocked(
        text: String,
        priority: Boolean,
        maneuver: Boolean,
        nowMillis: Long,
    ): NativeVoiceDirective {
        val utterance = NativeVoiceUtterance(++nextId, text, priority, maneuver, nowMillis)
        val active = current
        if (active == null) {
            current = utterance
            return NativeVoiceDirective.Start(utterance, interruptCurrent = false)
        }
        if (!priority || elapsed(nowMillis, active.submittedAtMillis) < MIN_AUDIBLE_MILLIS) {
            return enqueue(utterance)
        }
        if (maneuver && active.maneuver) {
            return NativeVoiceDirective.Dropped(NativeVoiceDropReason.StaleAdvance)
        }
        current = utterance
        return NativeVoiceDirective.Start(utterance, interruptCurrent = true)
    }

    private fun enqueue(utterance: NativeVoiceUtterance): NativeVoiceDirective {
        if (pending.any { it.text == utterance.text }) {
            return NativeVoiceDirective.Dropped(NativeVoiceDropReason.DuplicateQueued)
        }
        pending.addLast(utterance)
        val evicted = if (pending.size > MAX_PENDING) pending.removeFirst().id else null
        return NativeVoiceDirective.Queued(utterance, evicted)
    }

    private fun cleanText(value: String): String {
        val clean = value.replace(CONTROLS, " ").trim()
        require(clean.isNotEmpty()) { "Voice utterance must not be empty" }
        require(clean.length <= MAX_UTTERANCE_CHARS) { "Voice utterance is too long" }
        return clean
    }

    private fun elapsed(now: Long, then: Long): Long =
        if (then == Long.MIN_VALUE || now < then) Long.MAX_VALUE else now - then

    companion object {
        const val REINITIALIZE_COOLDOWN_MILLIS = 8_000L
        const val MIN_AUDIBLE_MILLIS = 1_300L
        const val MAX_UTTERANCE_WAIT_MILLIS = 25_000L
        const val MANEUVER_DEDUP_MILLIS = 12_000L
        const val AMBIENT_DEDUP_MILLIS = 8_000L
        const val MAX_PENDING = 2
        const val MAX_UTTERANCE_CHARS = 1_000
        private val CONTROLS = Regex("[\\u0000-\\u001f]")
        private val ORDINAL = Regex("(\\d+)°")

        fun shouldRetryInitialization(lastFailedAtMillis: Long?, nowMillis: Long): Boolean =
            lastFailedAtMillis == null ||
                (nowMillis >= lastFailedAtMillis && nowMillis - lastFailedAtMillis >= REINITIALIZE_COOLDOWN_MILLIS)

        fun normalizeOrdinals(text: String, languageCode: String): String =
            ORDINAL.replace(text) { match ->
                val number = match.groupValues[1].toIntOrNull() ?: 1
                ordinalWord(number, languageCode)
            }

        private fun ordinalWord(number: Int, languageCode: String): String {
            val words = when (languageCode) {
                "it" -> listOf("prima", "seconda", "terza", "quarta", "quinta", "sesta")
                "es" -> listOf("primera", "segunda", "tercera", "cuarta", "quinta", "sexta")
                "fr" -> listOf("première", "deuxième", "troisième", "quatrième", "cinquième", "sixième")
                "pt" -> listOf("primeira", "segunda", "terceira", "quarta", "quinta", "sexta")
                else -> listOf("first", "second", "third", "fourth", "fifth", "sixth")
            }
            return words.getOrNull(number - 1) ?: number.toString()
        }
    }
}
