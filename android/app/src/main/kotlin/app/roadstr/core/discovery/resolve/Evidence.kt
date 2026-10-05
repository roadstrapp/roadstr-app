package app.roadstr.core.discovery.resolve

import kotlin.math.min

/** One reason to think a web result is about a known place; the weights are fixed and published. */
enum class EvidenceKind(val weight: Double) {
    OSM_ID(1.0),
    WEBSITE_HOST(0.85),
    PHONE(0.8),
    ADDRESS_EXACT(0.8),
    STRUCTURED_COORDS(0.7),
    NAME_LOCALITY(0.6),
    PROXIMITY(0.2),
    CATEGORY(0.1),
}

data class Evidence(val kind: EvidenceKind, val weight: Double = kind.weight)

enum class MatchClass {
    /** Confident enough to link the result to the place and show it on the map. */
    LINKED,

    /** Plausible: offered as "could this be…?" and never merged. */
    CANDIDATE,

    /** Not tied to any place: a web result, openable and nothing more. */
    WEB_ONLY,
}

object Confidence {
    const val LINK_THRESHOLD = 0.8
    const val CANDIDATE_THRESHOLD = 0.5
    private const val CAP = 0.99

    /** `1 − Π(1 − w)`, capped below certainty unless an OSM id is part of the evidence. */
    fun combine(evidence: Collection<Evidence>): Double {
        if (evidence.isEmpty()) return 0.0
        var remaining = 1.0
        for (item in evidence) remaining *= 1.0 - item.weight.coerceIn(0.0, 1.0)
        val combined = 1.0 - remaining
        val certain = evidence.any { it.kind == EvidenceKind.OSM_ID }
        return if (certain) combined else min(combined, CAP)
    }

    fun classify(confidence: Double): MatchClass = when {
        confidence >= LINK_THRESHOLD -> MatchClass.LINKED
        confidence >= CANDIDATE_THRESHOLD -> MatchClass.CANDIDATE
        else -> MatchClass.WEB_ONLY
    }

    /** Closer than a shop front is strong, within a block is a little, further is nothing. */
    fun proximity(distanceMeters: Double): Evidence? = when {
        distanceMeters <= 30.0 -> Evidence(EvidenceKind.PROXIMITY, 0.3)
        distanceMeters <= 100.0 -> Evidence(EvidenceKind.PROXIMITY, 0.2)
        distanceMeters <= 300.0 -> Evidence(EvidenceKind.PROXIMITY, 0.1)
        else -> null
    }
}
