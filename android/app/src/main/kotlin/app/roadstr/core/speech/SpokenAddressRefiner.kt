package app.roadstr.core.speech

import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Street names of the area around the user, indexed for sound-alike lookup. Build it once per area and
 * reuse it: the index does the expensive part (keys) up front.
 */
class StreetIndex(names: Collection<String>) {
    class Entry(val name: String, val key: String, val namePartLength: Int)

    val entries: List<Entry>

    init {
        val byKey = LinkedHashMap<String, Entry>()
        for (name in names) {
            val tokens = SpokenAddressRefiner.tokenize(name)
            if (tokens.size < 2) continue
            val key = SpokenAddressRefiner.keyOf(tokens)
            val typeLength = if (SpokenAddressRefiner.isStreetType(tokens[0].core)) {
                SpokenAddressRefiner.keyOf(tokens.take(1)).length
            } else {
                0
            }
            byKey.getOrPut(key) { Entry(name.trim(), key, key.length - typeLength) }
        }
        entries = byKey.values.toList()
    }

    val size: Int get() = entries.size

    /** What a key resolved to: [name] is null when nothing is close enough or two names are equally close. */
    class Match(val entry: Entry?, val distance: Int)

    fun closest(key: String): Match {
        var best: Entry? = null
        var bestDistance = Int.MAX_VALUE
        var tied = false
        for (entry in entries) {
            val limit = SpokenAddressRefiner.tolerance(entry.namePartLength)
            if (abs(entry.key.length - key.length) > limit) continue
            val distance = SpokenAddressRefiner.boundedDistance(key, entry.key, limit)
            if (distance > limit) continue
            if (distance == 0) return Match(entry, 0)
            if (distance < bestDistance) {
                best = entry
                bestDistance = distance
                tied = false
            } else if (distance == bestDistance) {
                tied = true
            }
        }
        return if (best == null || tied) Match(null, bestDistance) else Match(best, bestDistance)
    }
}

/**
 * Cleans what a speech model wrote for an address search.
 *
 * Two things go wrong on the road, and this fixes both:
 *  * street names come out spelled by sound ("via d'arsena", "via chiavi caromea"), so a search finds
 *    nothing. They are snapped onto the closest street name of the area when one is clearly closest;
 *  * numbers come out as words ("diciassette") that map data does not contain. House numbers become
 *    digits, and "numero" / "civico" in front of them goes, as no address starts that way.
 *
 * It only ever changes what it is sure about. Without a street list nothing is snapped, and a text that
 * does not look like an address passes through untouched.
 */
object SpokenAddressRefiner {
    class Token(val lead: String, val core: String, val trail: String, val fixed: Boolean = false) {
        val lower: String get() = core.lowercase(Locale.ROOT)
        override fun toString(): String = lead + core + trail
    }

    private val streetTypes = setOf(
        "via", "viale", "vicolo", "piazza", "piazzale", "largo", "corso", "strada", "stradone", "lungomare",
        "lungotevere", "lungarno", "lungo", "borgo", "contrada", "salita", "discesa", "rampa", "vico",
        "calle", "avenida", "rua", "rue", "avenue", "boulevard", "street", "road", "square", "plaza",
    )
    private val numberMarkers = setOf("numero", "civico", "n", "nr", "num")
    private val houseNumberSuffixes = setOf("bis", "ter", "quater", "barrato")
    private val months = setOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre",
        "ottobre", "novembre", "dicembre",
    )
    private val articles = setOf("di", "del", "dei", "della", "delle", "degli", "dello", "dell")
    private val piece = Regex("""^([^\p{L}\p{N}]*)(.*?)([^\p{L}\p{N}]*)$""")
    private const val MAX_SPAN = 7

    fun isStreetType(word: String): Boolean = word.lowercase(Locale.ROOT) in streetTypes

    fun tokenize(text: String): List<Token> = text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.mapNotNull {
        val match = piece.matchEntire(it) ?: return@mapNotNull null
        val core = match.groupValues[2]
        if (core.isEmpty()) null else Token(match.groupValues[1], core, match.groupValues[3])
    }

    /** How many letters may differ for a street name part of [length] letters to still count as the same name. */
    fun tolerance(length: Int): Int = when {
        length < 6 -> 0
        length < 11 -> 1
        length < 18 -> 2
        else -> 3
    }

    fun boundedDistance(a: String, b: String, limit: Int): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMinimum = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                rowMinimum = min(rowMinimum, current[j])
            }
            if (rowMinimum > limit) return limit + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** The comparable form of a run of words: number words and Roman numerals become digits, then sound-alike folding. */
    fun keyOf(tokens: List<Token>): String {
        val words = tokens.map { it.core }
        val out = StringBuilder()
        var index = 0
        while (index < words.size) {
            val run = numberRun(words, index)
            if (run != null) {
                out.append(run.first)
                index += run.second
                continue
            }
            out.append(romanOrWord(words[index]))
            index++
        }
        return PhoneticKey.of(out.toString())
    }

    private fun numberRun(words: List<String>, from: Int): Pair<Int, Int>? {
        val run = ItalianNumbers.parseRun(words.map { it.lowercase(Locale.ROOT) }, from) ?: return null
        val single = run.second == 1
        if (single && words[from].lowercase(Locale.ROOT) in ItalianNumbers.ambiguousWords) return null
        return run
    }

    private fun romanOrWord(word: String): String {
        val value = if (word.length in 1..4 && word.all { it in "IVX" }) roman(word) else null
        return value?.toString() ?: word
    }

    private fun roman(word: String): Int? {
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10)
        var total = 0
        for ((index, char) in word.withIndex()) {
            val value = values.getValue(char)
            val next = word.getOrNull(index + 1)?.let { values.getValue(it) } ?: 0
            total += if (value < next) -value else value
        }
        return total.takeIf { it in 1..39 }
    }

    /** Refines [text] for a search. [index] may be null (no street list yet); [italian] turns on number words. */
    fun refine(text: String, index: StreetIndex?, italian: Boolean): String {
        val tokens = tokenize(text).toMutableList()
        if (tokens.isEmpty()) return text
        if (index != null && index.size > 0) snapStreet(tokens, index)
        if (italian) writeNumbersAsDigits(tokens)
        return tokens.joinToString(" ")
    }

    private class Candidate(val start: Int, val end: Int, val entry: StreetIndex.Entry, val distance: Int)

    private fun snapStreet(tokens: MutableList<Token>, index: StreetIndex) {
        var best: Candidate? = null
        for (start in tokens.indices) {
            if (tokens[start].fixed || !isStreetType(tokens[start].core)) continue
            val candidate = bestSpanFrom(tokens, start, index) ?: continue
            if (best == null || better(candidate, best)) best = candidate
        }
        val chosen = best ?: return
        val first = tokens[chosen.start]
        val last = tokens[chosen.end - 1]
        repeat(chosen.end - chosen.start) { tokens.removeAt(chosen.start) }
        tokens.add(chosen.start, Token(first.lead, chosen.entry.name, last.trail, fixed = true))
    }

    private fun better(a: Candidate, b: Candidate): Boolean = when {
        a.distance != b.distance -> a.distance < b.distance
        a.end - a.start != b.end - b.start -> a.end - a.start > b.end - b.start
        else -> a.start < b.start
    }

    private fun bestSpanFrom(tokens: List<Token>, start: Int, index: StreetIndex): Candidate? {
        var best: Candidate? = null
        var end = start + 2
        while (end <= min(tokens.size, start + MAX_SPAN)) {
            val span = tokens.subList(start, end)
            val match = index.closest(keyOf(span))
            val entry = match.entry
            if (entry != null && (best == null || match.distance <= best.distance)) {
                best = Candidate(start, end, entry, match.distance)
            }
            // A comma ends the street name; what follows is the town.
            if (tokens[end - 1].trail.any { it == ',' || it == ';' }) break
            end++
        }
        return best
    }

    private fun writeNumbersAsDigits(tokens: MutableList<Token>) {
        dropNumberMarkers(tokens)
        var at = 0
        while (at < tokens.size) {
            val token = tokens[at]
            if (token.fixed) {
                at++
                continue
            }
            val run = ItalianNumbers.parseRun(tokens.map { it.lower }, at)
            if (run == null || !shouldConvert(tokens, at, run.second, run.first)) {
                at++
                continue
            }
            val last = tokens[at + run.second - 1]
            repeat(run.second) { tokens.removeAt(at) }
            tokens.add(at, Token(token.lead, run.first.toString(), last.trail))
            at++
        }
    }

    /** "via cavour numero 8" is searched as "via cavour 8". */
    private fun dropNumberMarkers(tokens: MutableList<Token>) {
        var at = 0
        while (at < tokens.size - 1) {
            val marker = tokens[at].lower in numberMarkers
            val numeric = marker && (tokens[at + 1].core.all { it.isDigit() } ||
                ItalianNumbers.parseRun(tokens.map { it.lower }, at + 1) != null)
            val afterStreet = marker && at > 0 && (tokens.subList(0, at).any { it.fixed || isStreetType(it.core) })
            if (numeric && afterStreet) tokens.removeAt(at) else at++
        }
    }

    private fun shouldConvert(tokens: List<Token>, at: Int, length: Int, value: Int): Boolean {
        val previous = tokens.getOrNull(at - 1)
        val next = tokens.getOrNull(at + length)
        val last = tokens[at + length - 1]
        val ambiguous = length == 1 && tokens[at].lower in ItalianNumbers.ambiguousWords
        val afterStreetName = previous?.fixed == true
        val hasStreetContext = afterStreetName || tokens.subList(0, at).any { isStreetType(it.core) }
        if (!hasStreetContext) return false
        if (afterStreetName) return true
        if (previous != null && previous.lower in articles) return false
        if (next != null && next.lower in months) return false
        // The first word after a bare street type is the name ("via sei febbraio"), not a number.
        if (previous != null && isStreetType(previous.core)) return false
        val atEnd = next == null || last.trail.any { it in ",;.!?" } || next.lower in houseNumberSuffixes
        // Street names that hold a number hold a small one ("Tre Martiri", "Cinque Giornate") or a date
        // (checked above); 10 and over, in front of a town, is a house number.
        val clearlyAHouseNumber = value >= 10 && !ambiguous
        return atEnd && !ambiguous || atEnd && previous != null || clearlyAHouseNumber
    }
}
