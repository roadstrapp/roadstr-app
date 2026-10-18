package app.roadstr.core.speech

import java.text.Normalizer
import java.util.Locale

/**
 * A spelling-insensitive key for comparing what a speech model heard with a name on a map.
 *
 * Speech models get the sounds nearly right and the writing wrong in the same few ways: they split or join
 * words ("chiavi caromea" for "Chiavica Romea"), drop or add apostrophes ("d'arsena" for "Darsena"), double a
 * consonant or not, and write k for c. A key removes all of that, so that two names that sound alike get
 * keys within a letter or two of each other. It is tuned for Italian, whose spelling is close to its sound,
 * and harmless for other languages: it only ever makes two names more alike, never changes a digit.
 */
object PhoneticKey {
    /** Letters and digits only, lower case, without accents, spaces, apostrophes or hyphens. */
    fun compact(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.filter { it in 'a'..'z' || it in '0'..'9' }
    }

    /** The sound-alike key of [text]. */
    fun of(text: String): String {
        val compacted = collapseRepeats(compact(text))
        val out = StringBuilder(compacted.length)
        var index = 0
        while (index < compacted.length) {
            val letter = compacted[index]
            val next = compacted.getOrNull(index + 1)
            when {
                letter in '0'..'9' -> out.append(letter)
                // "ch" and "gh" are a hard c and g before e and i.
                letter == 'c' && next == 'h' -> { out.append('k'); index++ }
                letter == 'g' && next == 'h' -> { out.append('g'); index++ }
                letter == 'p' && next == 'h' -> { out.append('f'); index++ }
                letter == 'c' && (next == null || next !in "eiy") -> out.append('k')
                letter == 'q' -> out.append('k')
                letter == 'h' -> Unit
                letter == 'x' -> out.append("ks")
                letter == 'y' || letter == 'j' -> out.append('i')
                letter == 'w' -> out.append('v')
                else -> out.append(letter)
            }
            index++
        }
        return collapseRepeats(out)
    }

    /** "ss", "tt", "zz", "kk" sound like one: speech models and maps disagree on them all the time. */
    private fun collapseRepeats(text: CharSequence): String {
        val out = StringBuilder(text.length)
        for (char in text) {
            if (out.isNotEmpty() && out.last() == char && char.isLetter()) continue
            out.append(char)
        }
        return out.toString()
    }
}
