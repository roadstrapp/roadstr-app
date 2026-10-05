package app.roadstr.core.discovery

import kotlin.math.max
import kotlin.math.min

/** Script-agnostic closeness of two names, from 0 (unrelated) to 1 (same after folding). */
object TextSimilarity {
    fun score(first: String, second: String): Double {
        val a = TextNormalizer.normalize(first)
        val b = TextNormalizer.normalize(second)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (containsWords(a, b) || containsWords(b, a)) return CONTAINED
        val longest = max(a.length, b.length)
        return 1.0 - editDistance(a, b).toDouble() / longest
    }

    /** The best score of [text] against any of [candidates]. */
    fun best(text: String, candidates: Collection<String>): Double =
        candidates.maxOfOrNull { score(text, it) } ?: 0.0

    private fun containsWords(outer: String, inner: String): Boolean =
        " $outer ".contains(" $inner ")

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private const val CONTAINED = 0.9
}
