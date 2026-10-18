package app.roadstr.core.speech

import java.text.Normalizer
import java.util.Locale

/**
 * Italian cardinal numbers written out as words, to integers: "diciassette", "centoventitré",
 * "duemilaquindici", also split over several words ("duecento quaranta sette").
 */
object ItalianNumbers {
    private val units = listOf(
        "uno", "due", "tre", "quattro", "cinque", "sei", "sette", "otto", "nove", "dieci", "undici", "dodici",
        "tredici", "quattordici", "quindici", "sedici", "diciassette", "diciotto", "diciannove",
    )
    private val tensWords = listOf("venti", "trenta", "quaranta", "cinquanta", "sessanta", "settanta", "ottanta", "novanta")

    /** Every word for 1..99, spelled out as Italian writes it, including the elisions ("ventuno", "trentotto"). */
    private val below100: Map<String, Int> = buildMap {
        units.forEachIndexed { index, word -> put(word, index + 1) }
        tensWords.forEachIndexed { index, tens ->
            val value = (index + 2) * 10
            put(tens, value)
            // The tens word loses its last vowel before "uno" and "otto": vent-uno, trent-otto.
            val stem = tens.dropLast(1)
            units.take(9).forEachIndexed { unit, word ->
                put(tens + word, value + unit + 1)
                if (word == "uno" || word == "otto") put(stem + word, value + unit + 1)
            }
        }
    }

    /** Words that are also everyday words ("una pizzeria", "sei arrivato") and so need a reason to be read as numbers. */
    val ambiguousWords: Set<String> = setOf("un", "uno", "una", "sei")

    /** The value of a single word, or null if it is not a number. Accents and case do not matter. */
    fun parseWord(word: String): Int? {
        val folded = fold(word)
        if (folded.isEmpty()) return null
        if (folded == "zero") return 0
        if (folded == "un" || folded == "una") return 1
        return parseThousands(folded)
    }

    /**
     * Reads the longest run of number words that makes one number, starting at [from]. Returns the value
     * and how many words it used, or null if [words] does not start with a number there.
     */
    fun parseRun(words: List<String>, from: Int): Pair<Int, Int>? {
        var total = 0
        var count = 0
        var place = Int.MAX_VALUE
        for (index in from until words.size) {
            val value = parseWord(words[index]) ?: break
            if (value == 0 && count > 0) break
            if (count > 0 && value >= place) break
            total += value
            count++
            place = lowestPlace(value)
            if (value == 0) break
        }
        return if (count == 0) null else total to count
    }

    private fun lowestPlace(value: Int): Int {
        if (value == 0) return 1
        var place = 1
        while (value % (place * 10) == 0) place *= 10
        return place
    }

    private fun parseThousands(word: String): Int? {
        if (word.startsWith("mille")) {
            val rest = word.removePrefix("mille")
            return if (rest.isEmpty()) 1000 else parseBelowThousand(rest)?.let { 1000 + it }
        }
        val at = word.indexOf("mila")
        if (at <= 0) return parseBelowThousand(word)
        val head = parseBelowThousand(word.substring(0, at)) ?: return null
        if (head < 2) return null
        val tail = word.substring(at + "mila".length)
        val rest = if (tail.isEmpty()) 0 else parseBelowThousand(tail) ?: return null
        return head * 1000 + rest
    }

    private fun parseBelowThousand(word: String): Int? {
        below100[word]?.let { return it }
        for (hundreds in 1..9) {
            val prefix = if (hundreds == 1) "" else units[hundreds - 1]
            parseAfterHundreds(word, prefix + "cento", hundreds, elided = false)?.let { return it }
            parseAfterHundreds(word, prefix + "cent", hundreds, elided = true)?.let { return it }
        }
        return null
    }

    private fun parseAfterHundreds(word: String, prefix: String, hundreds: Int, elided: Boolean): Int? {
        if (!word.startsWith(prefix)) return null
        val rest = word.removePrefix(prefix)
        if (rest.isEmpty()) return if (elided) null else hundreds * 100
        // "cent" stands before "otto", "ottanta" and "uno": centotto, centottanta, centuno.
        if (elided && !(rest.startsWith("o") || rest.startsWith("u"))) return null
        return below100[rest]?.let { hundreds * 100 + it }
    }

    private fun fold(word: String): String =
        Normalizer.normalize(word.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filter { it in 'a'..'z' }
}
