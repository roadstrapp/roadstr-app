package app.roadstr.core.search

import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** Allocation-conscious fuzzy place-name matching ported from Dart. */
object FuzzyMatch {
    private val stopWords = setOf(
        "via", "viale", "vicolo", "strada", "stradone", "piazza", "piazzale",
        "largo", "corso", "lungomare", "contrada", "localita", "borgo",
        "street", "st", "road", "rd", "avenue", "ave", "lane", "square",
        "rue", "boulevard", "bd", "place", "chemin", "allee",
        "strasse", "str", "platz", "weg", "gasse", "calle", "plaza",
        "avenida", "paseo", "rua", "praca", "straat", "plein", "gata",
        "vej", "katu", "di", "de", "del", "della", "dello", "dei",
        "degli", "delle", "da", "dal", "la", "le", "lo", "il", "gli",
        "las", "los", "the", "of", "and", "e", "y", "et", "und",
    )

    private val accents = mapOf(
        'à' to "a", 'á' to "a", 'â' to "a", 'ã' to "a", 'ä' to "a", 'å' to "a",
        'ā' to "a", 'ă' to "a", 'ą' to "a", 'è' to "e", 'é' to "e", 'ê' to "e",
        'ë' to "e", 'ē' to "e", 'ė' to "e", 'ę' to "e", 'ě' to "e", 'ì' to "i",
        'í' to "i", 'î' to "i", 'ï' to "i", 'ī' to "i", 'į' to "i", 'ò' to "o",
        'ó' to "o", 'ô' to "o", 'õ' to "o", 'ö' to "o", 'ø' to "o", 'ō' to "o",
        'ù' to "u", 'ú' to "u", 'û' to "u", 'ü' to "u", 'ū' to "u", 'ů' to "u",
        'ų' to "u", 'ý' to "y", 'ÿ' to "y", 'ñ' to "n", 'ń' to "n", 'ň' to "n",
        'ņ' to "n", 'ç' to "c", 'ć' to "c", 'č' to "c", 'š' to "s", 'ś' to "s",
        'ş' to "s", 'ș' to "s", 'ž' to "z", 'ź' to "z", 'ż' to "z", 'ł' to "l",
        'ľ' to "l", 'ļ' to "l", 'ť' to "t", 'ţ' to "t", 'ț' to "t", 'ď' to "d",
        'đ' to "d", 'ř' to "r", 'ŕ' to "r", 'ğ' to "g", 'ģ' to "g", 'ķ' to "k",
        'ĺ' to "l", 'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'ð' to "d", 'þ' to "th",
    )

    fun normalize(input: String): String {
        val output = StringBuilder()
        var pendingSpace = false
        for (character in input.lowercase(Locale.ROOT)) {
            val isAsciiAlphanumeric = character in 'a'..'z' || character in '0'..'9'
            val folded = if (isAsciiAlphanumeric) null else accents[character]
            if (!isAsciiAlphanumeric && folded == null) {
                pendingSpace = output.isNotEmpty()
                continue
            }
            if (pendingSpace) {
                output.append(' ')
                pendingSpace = false
            }
            output.append(folded ?: character)
        }
        return output.toString()
    }

    fun wordScore(first: String, second: String): Double {
        if (first == second) return 1.0
        if (first.isEmpty() || second.isEmpty()) return 0.0
        val shorter = if (first.length <= second.length) first else second
        val longer = if (first.length <= second.length) second else first
        if (shorter.length >= 3 && longer.startsWith(shorter)) {
            return 0.98 - 0.1 * (1.0 - shorter.length.toDouble() / longer.length)
        }
        val distance = levenshtein(first, second, maxDistance = 3)
        if (distance < 0) return 0.0
        val ratio = 1.0 - distance.toDouble() / longer.length
        return if (ratio >= 0.66) ratio * 0.95 else 0.0
    }

    fun score(query: String, candidate: String): Double {
        val queryTokens = tokens(query)
        val candidateTokens = tokens(candidate)
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
        val forward = coverage(queryTokens, candidateTokens)
        if (forward.strongTokens > 0 && forward.strongMatches == 0) return 0.0
        val backward = coverage(candidateTokens, queryTokens)
        if (forward.ratio <= 0 || backward.ratio <= 0) return 0.0
        return (2 * forward.ratio * backward.ratio / (forward.ratio + backward.ratio))
            .coerceIn(0.0, 1.0)
    }

    private data class Coverage(val ratio: Double, val strongTokens: Int, val strongMatches: Int)

    private fun tokens(input: String): List<String> =
        normalize(input).split(' ').filter(String::isNotEmpty)

    private fun coverage(from: List<String>, to: List<String>): Coverage {
        var total = 0.0
        var matched = 0.0
        var strongTokens = 0
        var strongMatches = 0
        for (fromToken in from) {
            val isStopWord = fromToken in stopWords
            val weight = if (isStopWord) 0.2 else fromToken.length.toDouble()
            total += weight
            var best = 0.0
            for (toToken in to) {
                best = maxOf(best, wordScore(fromToken, toToken))
                if (best == 1.0) break
            }
            matched += weight * best
            if (!isStopWord) {
                strongTokens++
                if (best >= 0.66) strongMatches++
            }
        }
        return Coverage(if (total == 0.0) 0.0 else matched / total, strongTokens, strongMatches)
    }

    private fun levenshtein(first: String, second: String, maxDistance: Int): Int {
        if (abs(first.length - second.length) > maxDistance) return -1
        var previous = IntArray(second.length + 1) { it }
        var current = IntArray(second.length + 1)
        for (i in 1..first.length) {
            current[0] = i
            var rowMinimum = current[0]
            for (j in 1..second.length) {
                val cost = if (first[i - 1] == second[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                rowMinimum = min(rowMinimum, current[j])
            }
            if (rowMinimum > maxDistance) return -1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[second.length].takeIf { it <= maxDistance } ?: -1
    }
}
